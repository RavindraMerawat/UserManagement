package com.user.management.service;

import com.user.management.entity.Area;
import com.user.management.entity.Gender;
import com.user.management.entity.Locality;
import com.user.management.entity.SatsangPoint;
import com.user.management.entity.Sewadar;
import com.user.management.entity.SewadarRole;
import com.user.management.entity.SewadarStatus;
import com.user.management.entity.Zone;
import com.user.management.exception.BadRequestException;
import com.user.management.exception.ForbiddenException;
import com.user.management.model.BulkImportResponse;
import com.user.management.model.BulkImportResponse.RowProblem;
import com.user.management.repository.AreaRepository;
import com.user.management.repository.SatsangPointRepository;
import com.user.management.repository.SewadarRepository;
import com.user.management.repository.SewadarRoleRepository;
import com.user.management.repository.ZoneRepository;
import com.user.management.security.CurrentUserService;
import com.user.management.security.DataScope;
import com.user.management.util.Csv;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The Bulk Add Sewadar template, and the upload that fills the register from it.
 *
 * <p>The template is a CSV, so it cannot carry dropdowns or colour - the headings
 * mark a required column with a star and everything else is checked here, on the way
 * in. That check is the whole point of this class: a file typed by hand will have a
 * misspelled zone or a date written the other way round in it, and the office needs
 * to be told which line and which column, not that "the upload failed".</p>
 *
 * <p>Nothing is saved unless every row is good. See {@link BulkImportResponse}.</p>
 */
@Service
@RequiredArgsConstructor
public class SewadarImportService {

    /** The hidden sheet the dropdown lists live on. */
    private static final String LIST_SHEET = "Lists";

    /** Cells beyond this are a runaway file, not a register. */
    private static final int MAX_ROWS = 2000;

    private static final List<String> BLOOD_GROUPS =
            List.of("A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-");

    /**
     * Date formats the office actually writes. The first is what Excel gives an Indian
     * locale, the last is what a machine-generated file gives.
     */
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd-MM-uuuu"),
            DateTimeFormatter.ofPattern("dd/MM/uuuu"),
            DateTimeFormatter.ofPattern("uuuu-MM-dd"));

    private final SewadarRepository sewadarRepository;
    private final SewadarRoleRepository sewadarRoleRepository;
    private final ZoneRepository zoneRepository;
    private final AreaRepository areaRepository;
    private final SatsangPointRepository satsangPointRepository;
    private final CurrentUserService currentUser;

    /**
     * A column of the template.
     *
     * <p>The aliases exist because the same column is called different things by the
     * people filling it in - and because a file that has been opened, edited and saved
     * a few times loses the star from the heading. Matching is on letters and digits
     * only, so spacing, case and punctuation never decide whether an upload works.</p>
     */
    private enum Col {
        SERIAL("S.No", false, "sno", "serialno", "srno", "sr"),
        BADGE("GR. No", true, "grno", "grnumber", "badgeno", "badgenumber"),
        NAME("Sewadar Name", true, "name"),
        FH("F/H Name", false, "fhname", "fathername", "fatherorhusbandname", "fatherhusbandname"),
        AGE("Age", true),
        GENDER("Gender", true),
        MOBILE("Mobile No", true, "mobile", "mobilenumber", "phone", "phoneno"),
        EMAIL("Email", false, "emailid"),
        AADHAAR("Aadhaar No", false, "aadharno", "adharcardno", "aadharcardno", "aadhaar", "adhar"),
        BIRTH_DATE("Birth Date", false, "dob", "dateofbirth"),
        BLOOD_GROUP("Blood Group", false, "blood"),
        ZONE("Zone", true, "zonename"),
        AREA("Area", false),
        GROUPING("Grouping", false, "group"),
        LOCALITY("Locality", true, "local"),
        POINT("Satsang Point", false, "point", "centerpoint", "centrepoint"),
        ADDRESS("Address", false, "addr"),
        STATUS("Status", false),
        DESIGNATION("Designation", true, "role"),
        EXEMPTION("Exemption", false, "excemtion", "exempted", "exempt");

        private final String heading;
        private final boolean required;
        private final Set<String> keys;

        Col(String heading, boolean required, String... aliases) {
            this.heading = heading;
            this.required = required;
            Set<String> all = new HashSet<>();
            all.add(key(heading));
            all.addAll(Arrays.stream(aliases).map(SewadarImportService::key).toList());
            this.keys = Set.copyOf(all);
        }
    }

    // ------------------------------------------------------------------ template

    /** Rows of the template that carry a dropdown, which is further than anyone fills. */
    private static final int VALIDATED_ROWS = 1000;

    /**
     * The empty template, as a workbook.
     *
     * <p>A spreadsheet rather than a CSV because of what the columns need: Gender,
     * Zone, Designation and the rest are chosen from a list, and a CSV is plain text
     * that can hold neither a dropdown nor the red that marks a required column. The
     * lists are read from the tables as the file is built, so a zone or a designation
     * added under Setup this morning is in the file downloaded this afternoon.</p>
     */
    @Transactional(readOnly = true)
    public byte[] template() {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Sewadars");
            CellStyle required = headingStyle(workbook, true);
            CellStyle optional = headingStyle(workbook, false);
            Map<Col, List<String>> lists = dropdownValues();

            Row headings = sheet.createRow(0);
            for (Col col : Col.values()) {
                Cell cell = headings.createCell(col.ordinal());
                cell.setCellValue(col.heading);
                cell.setCellStyle(col.required ? required : optional);
                // Wide enough for the heading and for the longest thing its dropdown
                // offers - a column that has to be widened before it can be read is a
                // column that gets filled in wrong.
                int longest = lists.getOrDefault(col, List.of()).stream()
                        .mapToInt(String::length).max().orElse(0);
                // Column widths are counted in characters of the sheet's ordinary font,
                // and the heading is half again as large, so its own length is scaled
                // before it competes with the dropdown values for the width.
                int forHeading = (int) Math.ceil(col.heading.length() * 1.4);
                sheet.setColumnWidth(col.ordinal(),
                        Math.min(Math.max(forHeading, longest) + 4, 42) * 256);
            }
            sheet.createFreezePane(0, 1);

            addDropdowns(workbook, sheet, lists);
            addOtherRules(workbook, sheet);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not build the addSewadar template", e);
        }
    }

    /**
     * The dropdowns.
     *
     * <p>Each list lives in a sheet of its own, which the file then hides. Excel caps
     * a validation written inline at 255 characters and the designation list alone is
     * longer than that, so a list held in cells is the only form that holds all of
     * them - and it is the form Excel shows with a proper dropdown arrow.</p>
     */
    private void addDropdowns(XSSFWorkbook workbook, Sheet sheet, Map<Col, List<String>> lists) {
        Sheet source = workbook.createSheet(LIST_SHEET);
        DataValidationHelper helper = sheet.getDataValidationHelper();
        int column = 0;

        for (Map.Entry<Col, List<String>> entry : lists.entrySet()) {
            List<String> values = entry.getValue();
            if (values.isEmpty()) {
                // Nothing set up under Setup yet. A dropdown with no options would
                // block the column altogether, so it is left as free text and the
                // upload reports anything that does not match.
                continue;
            }
            Col col = entry.getKey();
            row(source, 0).createCell(column).setCellValue(col.heading);
            for (int i = 0; i < values.size(); i++) {
                row(source, i + 1).createCell(column).setCellValue(values.get(i));
            }

            String letter = CellReference.convertNumToColString(column);
            String reference = LIST_SHEET + "!$" + letter + "$2:$" + letter + "$" + (values.size() + 1);

            DataValidation validation = helper.createValidation(
                    helper.createFormulaListConstraint(reference),
                    new CellRangeAddressList(1, VALIDATED_ROWS, col.ordinal(), col.ordinal()));
            /*
             * True, counter-intuitively, is what shows the arrow. The attribute this
             * sets in the file is showDropDown, which the format defines the other way
             * round - it means "suppress the in-cell list" - and POI passes the flag
             * straight through. Set to false, every list here was a validation with no
             * visible dropdown: the value was still refused if typed wrongly, but there
             * was nothing to pick from, which is the whole point of the template.
             */
            validation.setSuppressDropDownArrow(true);
            validation.setShowErrorBox(true);
            validation.createErrorBox(col.heading, "Choose one of the values in the list.");
            sheet.addValidationData(validation);
            column++;
        }

        workbook.setSheetHidden(workbook.getSheetIndex(source), true);
    }

    /** Age, and the formats that stop Excel rewriting what was typed. */
    private void addOtherRules(XSSFWorkbook workbook, Sheet sheet) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidation age = helper.createValidation(
                helper.createIntegerConstraint(
                        DataValidationConstraint.OperatorType.BETWEEN, "1", "120"),
                new CellRangeAddressList(1, VALIDATED_ROWS, Col.AGE.ordinal(), Col.AGE.ordinal()));
        age.setShowErrorBox(true);
        age.createErrorBox("Age", "Age is a whole number between 1 and 120.");
        sheet.addValidationData(age);

        // Dated so the column reads back as 18-04-1990 whatever the machine's locale
        // would otherwise have made of a typed date.
        CellStyle dates = workbook.createCellStyle();
        dates.setDataFormat(workbook.createDataFormat().getFormat("dd-mm-yyyy"));
        sheet.setDefaultColumnStyle(Col.BIRTH_DATE.ordinal(), dates);

        // GR. No, Mobile and Aadhaar are text. Left as numbers, Excel drops a leading
        // zero and turns twelve digits into 1.23457E+11.
        CellStyle text = workbook.createCellStyle();
        text.setDataFormat(workbook.createDataFormat().getFormat("@"));
        for (Col col : List.of(Col.BADGE, Col.MOBILE, Col.AADHAAR)) {
            sheet.setDefaultColumnStyle(col.ordinal(), text);
        }
    }

    /** What each dropdown column offers, in the order the columns appear. */
    private Map<Col, List<String>> dropdownValues() {
        Map<Col, List<String>> values = new LinkedHashMap<>();
        values.put(Col.GENDER, List.of("Male", "Female"));
        values.put(Col.BLOOD_GROUP, BLOOD_GROUPS);
        values.put(Col.ZONE, zonesInScope().stream().map(Zone::getName).toList());
        values.put(Col.LOCALITY, List.of("Local", "Outstation"));
        values.put(Col.STATUS, List.of("Permanent", "Open"));
        values.put(Col.DESIGNATION, sewadarRoleRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(SewadarRole::getName).toList());
        values.put(Col.EXEMPTION, List.of("Yes", "No"));
        return values;
    }

    /** Brick red, #B22222, behind the headings that must be filled in. */
    private static final byte[] BRICK_RED = { (byte) 0xB2, (byte) 0x22, (byte) 0x22 };

    /** Point size of the headings, large enough to read across the sheet. */
    private static final short HEADING_POINTS = 16;

    /**
     * How a heading is drawn: black bold at {@value #HEADING_POINTS} point on either a
     * red fill, for a column that must be filled in, or the plain grey the rest share.
     * The fill carries the meaning rather than the letters, so it stays visible however
     * far the column is from the one being typed in.
     */
    private CellStyle headingStyle(XSSFWorkbook workbook, boolean required) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints(HEADING_POINTS);
        font.setColor(IndexedColors.BLACK.getIndex());

        XSSFCellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setBorderBottom(BorderStyle.THIN);
        if (required) {
            // An exact colour rather than one of the workbook's indexed few: plain
            // red is the colour of an error, and these headings are not an error -
            // they are the columns that have to be filled in.
            style.setFillForegroundColor(new XSSFColor(BRICK_RED, null));
        } else {
            style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        }
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private Row row(Sheet sheet, int index) {
        Row existing = sheet.getRow(index);
        return existing != null ? existing : sheet.createRow(index);
    }

    // -------------------------------------------------------------------- import

    @Transactional
    public BulkImportResponse importCsv(MultipartFile file) {
        requireManagePermission();
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Choose a filled-in addSewadar.csv file to upload");
        }

        List<List<String>> lines = Csv.parse(read(file));
        if (lines.isEmpty()) {
            throw new BadRequestException("That file is empty");
        }
        Map<Col, Integer> columns = mapHeadings(lines.get(0));

        List<Draft> drafts = new ArrayList<>();
        List<RowProblem> problems = new ArrayList<>();
        Reference reference = loadReference();
        int rows = 0;

        for (int i = 1; i < lines.size(); i++) {
            List<String> line = lines.get(i);
            if (Csv.isBlank(line)) {
                continue;
            }
            rows++;
            if (rows > MAX_ROWS) {
                throw new BadRequestException(
                        "That file has more than " + MAX_ROWS + " rows. Split it and upload in parts.");
            }
            // Line numbers are what the person sees in their spreadsheet, so the
            // heading is line 1 and the first sewadar is line 2.
            Draft draft = readRow(i + 1, line, columns, reference, problems);
            if (draft != null) {
                drafts.add(draft);
            }
        }

        if (rows == 0) {
            throw new BadRequestException("That file has headings but no sewadars in it");
        }
        if (!problems.isEmpty()) {
            return BulkImportResponse.rejected(rows, problems);
        }

        drafts.forEach(draft -> sewadarRepository.save(draft.toSewadar()));
        return BulkImportResponse.saved(rows, drafts.size());
    }

    // ----------------------------------------------------------------- the rows

    /** One row's worth of checked values, ready to save once the whole file passes. */
    private record Draft(String badgeNumber, String name, String fatherOrHusbandName, Integer age,
                         Gender gender, String mobile, String email, String aadhar,
                         LocalDate dateOfBirth, String bloodGroup, Zone zone, String area,
                         String grouping, Locality locality, String centerPoint,
                         String address, SewadarStatus status, SewadarRole role,
                         boolean exempted) {

        Sewadar toSewadar() {
            return Sewadar.builder()
                    .badgeNumber(badgeNumber)
                    .name(name)
                    .fatherOrHusbandName(fatherOrHusbandName)
                    .age(age)
                    .gender(gender)
                    .mobile(mobile)
                    .email(email)
                    .aadharNumber(aadhar)
                    .dateOfBirth(dateOfBirth)
                    .bloodGroup(bloodGroup)
                    .zone(zone)
                    .area(area)
                    .grouping(grouping)
                    .locality(locality)
                    .centerPoint(centerPoint)
                    .address(address)
                    .status(status)
                    .role(role)
                    .exempted(exempted)
                    .build();
        }
    }

    private Draft readRow(int line, List<String> cells, Map<Col, Integer> columns,
                          Reference reference, List<RowProblem> problems) {
        RowReader row = new RowReader(line, cells, columns, problems);

        /*
         * A row with neither a GR. No nor a name is nearly always a row that was
         * touched rather than filled - most often by opening a dropdown to see what
         * was in it. Four separate "is required" lines do not say that, and the
         * person reads them as the upload failing to see what they typed. One line
         * that names what the row does contain says where to look.
         */
        if (row.optional(Col.BADGE) == null && row.optional(Col.NAME) == null) {
            String filled = Arrays.stream(Col.values())
                    .filter(col -> row.optional(col) != null)
                    .map(col -> col.heading)
                    .collect(Collectors.joining(", "));
            problems.add(new RowProblem(line, null, filled,
                    "Row " + line + " has no GR. No and no Sewadar Name - only "
                            + filled + " is filled in. Complete the row, or clear it if it "
                            + "was not meant to be a sewadar."));
            return null;
        }

        String badge = row.required(Col.BADGE);
        if (badge != null) {
            if (badge.length() > 40) {
                row.problem(Col.BADGE, badge, "GR. No cannot be longer than 40 characters");
            } else if (!reference.badges.add(badge.toLowerCase(Locale.ROOT))) {
                row.problem(Col.BADGE, badge, "This GR. No is on more than one row of the file");
            } else if (sewadarRepository.existsByBadgeNumberIgnoreCase(badge)) {
                row.problem(Col.BADGE, badge, "A sewadar with this GR. No is already registered");
            }
        }

        String name = row.required(Col.NAME);
        if (name != null && name.length() > 150) {
            row.problem(Col.NAME, name, "Name cannot be longer than 150 characters");
        }

        Integer age = null;
        String ageCell = row.required(Col.AGE);
        if (ageCell != null) {
            try {
                age = Integer.valueOf(ageCell);
                if (age < 1 || age > 120) {
                    row.problem(Col.AGE, ageCell, "Age must be a number between 1 and 120");
                    age = null;
                }
            } catch (NumberFormatException e) {
                row.problem(Col.AGE, ageCell, "Age must be a number between 1 and 120");
            }
        }

        Gender gender = null;
        String genderCell = row.required(Col.GENDER);
        if (genderCell != null) {
            gender = switch (genderCell.toLowerCase(Locale.ROOT)) {
                case "male", "m" -> Gender.MALE;
                case "female", "f" -> Gender.FEMALE;
                default -> null;
            };
            if (gender == null) {
                row.problem(Col.GENDER, genderCell, "Gender must be Male or Female");
            }
        }

        String mobile = null;
        String mobileCell = row.required(Col.MOBILE);
        if (mobileCell != null) {
            mobile = mobileCell.replaceAll("[^0-9]", "");
            if (mobile.length() != 10) {
                row.problem(Col.MOBILE, mobileCell, "Mobile No must be 10 digits");
                mobile = null;
            }
        }

        String email = row.optional(Col.EMAIL);
        if (email != null) {
            if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                row.problem(Col.EMAIL, email, "That is not an email address");
                email = null;
            } else if (!reference.emails.add(email.toLowerCase(Locale.ROOT))) {
                row.problem(Col.EMAIL, email, "This email is on more than one row of the file");
                email = null;
            } else if (sewadarRepository.existsByEmailIgnoreCase(email)) {
                row.problem(Col.EMAIL, email, "This email is already on another sewadar record");
                email = null;
            }
        }

        String aadhar = row.optional(Col.AADHAAR);
        if (aadhar != null) {
            String digits = aadhar.replaceAll("[^0-9]", "");
            if (digits.length() != 12) {
                row.problem(Col.AADHAAR, aadhar, "Aadhaar No must be 12 digits");
                aadhar = null;
            } else if (!reference.aadhars.add(digits)) {
                row.problem(Col.AADHAAR, aadhar, "This Aadhaar No is on more than one row of the file");
                aadhar = null;
            } else if (sewadarRepository.existsByAadharNumber(digits)) {
                row.problem(Col.AADHAAR, aadhar,
                        "This Aadhaar No is already registered to another sewadar");
                aadhar = null;
            } else {
                aadhar = digits;
            }
        }

        LocalDate birthDate = null;
        String birthCell = row.optional(Col.BIRTH_DATE);
        if (birthCell != null) {
            birthDate = parseDate(birthCell);
            if (birthDate == null) {
                row.problem(Col.BIRTH_DATE, birthCell,
                        "Birth Date must be written as dd-MM-yyyy, for example 18-04-1990");
            } else if (birthDate.isAfter(LocalDate.now())) {
                row.problem(Col.BIRTH_DATE, birthCell, "Birth Date cannot be in the future");
                birthDate = null;
            }
        }

        String bloodCell = row.optional(Col.BLOOD_GROUP);
        String bloodGroup = null;
        if (bloodCell != null) {
            String typed = bloodCell.replace(" ", "");
            bloodGroup = BLOOD_GROUPS.stream()
                    .filter(g -> g.equalsIgnoreCase(typed))
                    .findFirst().orElse(null);
            if (bloodGroup == null) {
                row.problem(Col.BLOOD_GROUP, bloodCell,
                        "Blood Group must be one of " + String.join(", ", BLOOD_GROUPS));
            }
        }

        Zone zone = null;
        String zoneCell = row.required(Col.ZONE);
        if (zoneCell != null) {
            zone = reference.zones.get(key(zoneCell));
            if (zone == null) {
                row.problem(Col.ZONE, zoneCell, "No zone of that name. Use one of: "
                        + reference.zoneNames());
            }
        }

        // Area and point are lists of their own, matched on their own names -
        // neither is narrowed by the zone or by each other.
        String area = row.optional(Col.AREA);
        if (area != null) {
            Area areaRow = reference.areas.get(key(area));
            if (areaRow == null) {
                row.problem(Col.AREA, area, "No area of that name. Add it under Setup first.");
                area = null;
            } else {
                area = areaRow.getName();
            }
        }

        /*
         * Grouping belongs to Indore's areas and nowhere else, which is the same rule
         * the Add Sewadar screen follows: the field is offered there and left alone
         * elsewhere. A grouping against another area is a mistake worth naming.
         *
         * An Indore row that leaves it blank is still loaded. The screen asks for it
         * because someone is sitting there to answer; a file of a thousand rows is
         * not the place to refuse the lot over a column that can be filled in later.
         */
        String grouping = row.optional(Col.GROUPING);
        boolean indore = area != null && area.trim().equalsIgnoreCase("Indore");
        if (!indore && grouping != null) {
            row.problem(Col.GROUPING, grouping,
                    "Grouping only applies to Indore. Leave it empty for " + (area == null
                            ? "an unset area" : area) + ".");
            grouping = null;
        }

        Locality locality = null;
        String localityCell = row.required(Col.LOCALITY);
        if (localityCell != null) {
            locality = switch (localityCell.toLowerCase(Locale.ROOT)) {
                case "local" -> Locality.LOCAL;
                case "outstation" -> Locality.OUTSTATION;
                default -> null;
            };
            if (locality == null) {
                row.problem(Col.LOCALITY, localityCell, "Locality must be Local or Outstation");
            }
        }

        String point = row.optional(Col.POINT);
        if (point != null) {
            SatsangPoint match = reference.points.get(key(point));
            if (match == null) {
                row.problem(Col.POINT, point,
                        "No satsang point of that name. Add it under Setup first.");
                point = null;
            } else {
                point = match.getName();
            }
        }

        SewadarStatus status = null;
        /*
         * Address is free text and the only long column on the sheet, which is why
         * it is last: a register exported from a spreadsheet keeps the identifying
         * columns together on the left where they can be read down.
         */
        String address = row.optional(Col.ADDRESS);
        if (address != null && address.length() > 400) {
            row.problem(Col.ADDRESS, address, "Address cannot be longer than 400 characters");
            address = null;
        }

        String statusCell = row.optional(Col.STATUS);
        if (statusCell != null) {
            status = switch (statusCell.toLowerCase(Locale.ROOT)) {
                case "permanent" -> SewadarStatus.PERMANENT;
                case "open" -> SewadarStatus.OPEN;
                default -> null;
            };
            if (status == null) {
                row.problem(Col.STATUS, statusCell, "Status must be Permanent or Open");
            }
        }

        SewadarRole role = null;
        String designationCell = row.required(Col.DESIGNATION);
        if (designationCell != null) {
            role = reference.roles.get(key(designationCell));
            if (role == null) {
                row.problem(Col.DESIGNATION, designationCell,
                        "No designation of that name. Use one of: " + reference.roleNames());
            }
        }

        boolean exempted = false;
        String exemptionCell = row.optional(Col.EXEMPTION);
        if (exemptionCell != null) {
            switch (exemptionCell.toLowerCase(Locale.ROOT)) {
                case "yes", "y", "true" -> exempted = true;
                case "no", "n", "false" -> exempted = false;
                default -> row.problem(Col.EXEMPTION, exemptionCell, "Exemption must be Yes or No");
            }
        }

        String fh = row.optional(Col.FH);
        if (fh != null && fh.length() > 150) {
            row.problem(Col.FH, fh, "F/H Name cannot be longer than 150 characters");
            fh = null;
        }

        return new Draft(badge, name, fh, age, gender, mobile, email, aadhar, birthDate,
                bloodGroup, zone, area, grouping, locality, point, address, status, role,
                exempted);
    }

    /** Reads one row's cells, recording a problem for anything required and missing. */
    private final class RowReader {
        private final int line;
        private final List<String> cells;
        private final Map<Col, Integer> columns;
        private final List<RowProblem> problems;

        RowReader(int line, List<String> cells, Map<Col, Integer> columns, List<RowProblem> problems) {
            this.line = line;
            this.cells = cells;
            this.columns = columns;
            this.problems = problems;
        }

        String optional(Col col) {
            Integer at = columns.get(col);
            if (at == null || at >= cells.size()) {
                return null;
            }
            String value = cells.get(at).trim();
            return value.isEmpty() ? null : value;
        }

        String required(Col col) {
            String value = optional(col);
            if (value == null) {
                problem(col, "", col.heading + " is required");
            }
            return value;
        }

        void problem(Col col, String value, String message) {
            problems.add(new RowProblem(line, col.heading, value, message));
        }
    }

    // ------------------------------------------------------------- the headings

    private Map<Col, Integer> mapHeadings(List<String> headings) {
        Map<Col, Integer> found = new EnumMap<>(Col.class);
        for (int i = 0; i < headings.size(); i++) {
            String cell = key(headings.get(i));
            if (cell.isEmpty()) {
                continue;
            }
            for (Col col : Col.values()) {
                if (col.keys.contains(cell) && !found.containsKey(col)) {
                    found.put(col, i);
                }
            }
        }
        List<String> missing = Arrays.stream(Col.values())
                .filter(c -> c.required && !found.containsKey(c))
                .map(c -> c.heading)
                .toList();
        if (!missing.isEmpty()) {
            throw new BadRequestException(
                    "That file is missing these columns: " + String.join(", ", missing)
                            + ". Download the addSewadar template and fill that in.");
        }
        return found;
    }

    // ------------------------------------------------------------- reference data

    /**
     * Everything a row is checked against, read once for the whole file rather than
     * once per row - and the values already seen, so a file cannot repeat itself.
     */
    private record Reference(Map<String, Zone> zones, Map<String, Area> areas,
                             Map<String, SatsangPoint> points, Map<String, SewadarRole> roles,
                             Set<String> badges,
                             Set<String> aadhars, Set<String> emails) {

        String zoneNames() {
            return zones.values().stream().map(Zone::getName).distinct()
                    .collect(Collectors.joining(", "));
        }

        String roleNames() {
            return roles.values().stream().map(SewadarRole::getName).distinct()
                    .collect(Collectors.joining(", "));
        }
    }

    private Reference loadReference() {
        List<Zone> zones = zonesInScope();
        Map<String, Zone> byZoneName = new LinkedHashMap<>();
        for (Zone zone : zones) {
            byZoneName.put(key(zone.getName()), zone);
            // A zone code is what some of the older registers use in this column.
            byZoneName.putIfAbsent(key(zone.getCode()), zone);
        }

        Map<String, Area> areas = areaRepository.findAllInOrder(true).stream()
                .collect(Collectors.toMap(a -> key(a.getName()),
                        a -> a, (first, second) -> first, LinkedHashMap::new));

        Map<String, SatsangPoint> points = satsangPointRepository.findAllInOrder(true).stream()
                .collect(Collectors.toMap(p -> key(p.getName()),
                        p -> p, (first, second) -> first, LinkedHashMap::new));

        Map<String, SewadarRole> roles = sewadarRoleRepository.findByActiveTrueOrderByNameAsc()
                .stream()
                .collect(Collectors.toMap(r -> key(r.getName()), r -> r,
                        (first, second) -> first, LinkedHashMap::new));

        return new Reference(byZoneName, areas, points, roles,
                new HashSet<>(), new HashSet<>(), new HashSet<>());
    }

    /**
     * The zones this upload may add into. A zone-scoped role can only load its own
     * zones, exactly as it can only add one sewadar at a time into its own zones.
     */
    private List<Zone> zonesInScope() {
        DataScope scope = currentUser.scope();
        List<Zone> all = zoneRepository.findAll().stream().filter(Zone::isActive).toList();
        if (scope.zoneIds() == null) {
            return all;
        }
        return all.stream().filter(z -> scope.zoneIds().contains(z.getId())).toList();
    }

    // ------------------------------------------------------------------- helpers

    private String read(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BadRequestException("That file could not be read");
        }
    }

    private LocalDate parseDate(String value) {
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, format);
            } catch (DateTimeParseException ignored) {
                // Try the next one; the caller reports the failure once.
            }
        }
        return null;
    }

    private void requireManagePermission() {
        if (!currentUser.canManageSewadars()) {
            throw new ForbiddenException("Your role cannot add sewadar records");
        }
    }

    /** Letters and digits only, lower case - so spacing and punctuation never decide a match. */
    private static String key(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
