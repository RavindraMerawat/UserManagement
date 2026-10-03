package com.user.management;

import com.user.management.entity.Area;
import com.user.management.entity.Gender;
import com.user.management.entity.Locality;
import com.user.management.entity.Role;
import com.user.management.entity.Sewadar;
import com.user.management.entity.SewadarRole;
import com.user.management.entity.User;
import com.user.management.entity.Zone;
import com.user.management.exception.BadRequestException;
import com.user.management.model.BulkImportResponse;
import com.user.management.repository.AreaRepository;
import com.user.management.repository.SewadarRepository;
import com.user.management.repository.SewadarRoleRepository;
import com.user.management.repository.UserRepository;
import com.user.management.repository.ZoneRepository;
import com.user.management.security.AppUserPrincipal;
import com.user.management.controller.SewadarController;
import com.user.management.service.SewadarImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bulk Add Sewadar: what the upload accepts, what it refuses, and the promise that a
 * file is either loaded whole or not at all.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SewadarImportTest {

    @Autowired SewadarImportService importService;
    @Autowired SewadarController sewadarController;
    @Autowired SewadarRepository sewadarRepository;
    @Autowired SewadarRoleRepository sewadarRoleRepository;
    @Autowired ZoneRepository zoneRepository;
    @Autowired AreaRepository areaRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    /** The template's columns, in the template's order. */
    private static final List<String> COLUMNS = List.of(
            "S.No", "GR. No", "Sewadar Name", "F/H Name", "Age", "Gender", "Mobile No",
            "Email", "Aadhaar No", "Birth Date", "Blood Group", "Zone", "Area", "Grouping",
            "Locality", "Satsang Point", "Address", "Status", "Designation", "Exemption");

    /** The heading line, marking the required columns as the real file does. */
    private static final Set<String> REQUIRED = Set.of(
            "GR. No", "Sewadar Name", "Age", "Gender", "Mobile No", "Zone", "Locality",
            "Designation");

    @BeforeEach
    void seed() {
        zoneRepository.save(Zone.builder().code("N").name("Zone 1 - North").active(true).build());
        // Areas are a list of their own now - no zone, and no narrowing by one.
        // Grouping only applies to Indore, so both have to exist to test either side.
        areaRepository.save(Area.builder().name("Indore").active(true).build());
        areaRepository.save(Area.builder().name("Dewas").active(true).build());
        if (sewadarRoleRepository.findByNameIgnoreCase("Coordinator").isEmpty()) {
            sewadarRoleRepository.save(SewadarRole.builder().name("Coordinator").active(true).build());
        }
        signInAsAdmin();
    }

    // ------------------------------------------------------------------ the good case

    @Test
    @DisplayName("a filled-in template adds one sewadar per row")
    void loadsEveryRow() {
        BulkImportResponse result = upload(file(
                row("S.No", "1", "GR. No", "B-001", "Sewadar Name", "Harpreet Singh",
                        "F/H Name", "Gurdeep Singh", "Age", "34", "Gender", "Male",
                        "Mobile No", "9876543210", "Birth Date", "18-04-1990",
                        "Blood Group", "O+", "Zone", "Zone 1 - North", "Locality", "Local",
                        "Status", "Permanent", "Designation", "Coordinator", "Exemption", "No"),
                row("S.No", "2", "GR. No", "B-002", "Sewadar Name", "Simran Kaur",
                        "Age", "29", "Gender", "Female", "Mobile No", "9876500001",
                        "Zone", "Zone 1 - North", "Locality", "Outstation",
                        "Designation", "Coordinator", "Exemption", "Yes")));

        assertThat(result.problems()).isEmpty();
        assertThat(result.created()).isEqualTo(2);

        Sewadar saved = sewadarRepository.findAll().stream()
                .filter(s -> "B-001".equals(s.getBadgeNumber())).findFirst().orElseThrow();
        assertThat(saved.getName()).isEqualTo("Harpreet Singh");
        assertThat(saved.getAge()).isEqualTo(34);
        assertThat(saved.getGender()).isEqualTo(Gender.MALE);
        assertThat(saved.getDateOfBirth()).isEqualTo("1990-04-18");
        assertThat(saved.getZone().getName()).isEqualTo("Zone 1 - North");
        // The file says "Coordinator" and the designation on file is "Co-ordinator":
        // the import matches on letters, so a hyphen in either does not fail a row.
        assertThat(saved.getRole().getName()).isEqualTo("Co-ordinator");
        assertThat(saved.isExempted()).isFalse();
    }

    @Test
    @DisplayName("headings are matched on their letters, so spelling and spacing do not decide it")
    void headingsAreForgiving() {
        BulkImportResponse result = upload(
                "Sr,GRNo,Name,Father Name,Age,GENDER,mobile,Email ID,Adharcard No,DOB,Blood,"
                        + "zone,Area,Group,Local,Point,Status,Role,Excemtion\n"
                        + "1,B-010,Ravi,,40,M,9000000001,,,,,Zone 1 - North,,,Local,,,Coordinator,\n");

        assertThat(result.problems()).isEmpty();
        assertThat(result.created()).isEqualTo(1);
    }

    // ----------------------------------------------------- grouping and locality

    @Test
    @DisplayName("an address on the sheet lands on the record")
    void addressIsStored() {
        Map<String, String> withAddress = goodRow("B-140", "Has An Address");
        withAddress.put("Address", "12 Nanda Nagar, Indore");

        BulkImportResponse result = upload(file(withAddress));

        assertThat(result.problems()).isEmpty();
        assertThat(saved("B-140").getAddress()).isEqualTo("12 Nanda Nagar, Indore");
    }

    @Test
    @DisplayName("an address longer than the column is refused, not silently cut")
    void anOverlongAddressIsRefused() {
        Map<String, String> tooLong = goodRow("B-141", "Too Much Address");
        tooLong.put("Address", "x".repeat(401));

        BulkImportResponse result = upload(file(tooLong));

        assertThat(result.created()).isZero();
        assertThat(result.problems()).singleElement().satisfies(problem ->
                assertThat(problem.column()).isEqualTo("Address"));
    }

    @Test
    @DisplayName("Locality is stored as typed, either side of it")
    void localityIsStored() {
        BulkImportResponse result = upload(file(
                goodRow("B-100", "Lives Here"),
                withLocality(goodRow("B-101", "Travels In"), "Outstation")));

        assertThat(result.problems()).isEmpty();
        assertThat(saved("B-100").getLocality()).isEqualTo(Locality.LOCAL);
        assertThat(saved("B-101").getLocality()).isEqualTo(Locality.OUTSTATION);
    }

    @Test
    @DisplayName("a row with no Locality is refused - the office asked for it on every row")
    void localityIsRequired() {
        BulkImportResponse result = upload(file(withLocality(goodRow("B-110", "Unsaid"), "")));

        assertThat(result.created()).isZero();
        assertThat(result.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.column()).isEqualTo("Locality");
            assertThat(problem.message()).contains("required");
        });
    }

    @Test
    @DisplayName("Locality takes those two words and nothing else")
    void localityIsOneOfTwo() {
        BulkImportResponse result = upload(file(withLocality(goodRow("B-120", "Village"), "Rural")));

        assertThat(result.created()).isZero();
        assertThat(result.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.column()).isEqualTo("Locality");
            assertThat(problem.message()).contains("Local or Outstation");
        });
    }

    @Test
    @DisplayName("an Indore row carries its Grouping")
    void groupingIsStoredForIndore() {
        Map<String, String> indore = goodRow("B-130", "Indore Sewadar");
        indore.put("Area", "Indore");
        indore.put("Grouping", "Group 4");

        BulkImportResponse result = upload(file(indore));

        assertThat(result.problems()).isEmpty();
        assertThat(saved("B-130").getGrouping()).isEqualTo("Group 4");
    }

    @Test
    @DisplayName("an Indore row with no Grouping still loads - the screen asks for it, the file does not")
    void groupingIsNotDemandedByTheFile() {
        Map<String, String> indore = goodRow("B-131", "No Grouping");
        indore.put("Area", "Indore");

        BulkImportResponse result = upload(file(indore));

        assertThat(result.problems()).isEmpty();
        assertThat(saved("B-131").getGrouping()).isNull();
    }

    @Test
    @DisplayName("a Grouping against any other area is refused, and says which")
    void groupingBelongsOnlyToIndore() {
        Map<String, String> dewas = goodRow("B-132", "Dewas Sewadar");
        dewas.put("Area", "Dewas");
        dewas.put("Grouping", "Group 4");

        BulkImportResponse result = upload(file(dewas));

        assertThat(result.created()).isZero();
        assertThat(result.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.column()).isEqualTo("Grouping");
            assertThat(problem.message()).contains("only applies to Indore").contains("Dewas");
        });
    }

    // ------------------------------------------------------------- what it refuses

    @Test
    @DisplayName("a bad row stops the whole file - nothing is half loaded")
    void nothingIsSavedWhenARowIsWrong() {
        long before = sewadarRepository.count();
        Map<String, String> bad = goodRow("B-021", "Bad Row");
        bad.put("Gender", "Alien");
        BulkImportResponse result = upload(file(goodRow("B-020", "Good Row"), bad));

        assertThat(result.created()).isZero();
        assertThat(sewadarRepository.count()).isEqualTo(before);
        assertThat(result.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.line()).isEqualTo(3);          // heading is line 1
            assertThat(problem.column()).isEqualTo("Gender");
            assertThat(problem.message()).contains("Male or Female");
        });
    }

    @Test
    @DisplayName("a missing value in a required column is reported against that column")
    void requiredColumnsMustBeFilled() {
        Map<String, String> noBadge = goodRow("", "No Badge");
        BulkImportResponse result = upload(file(noBadge));

        assertThat(result.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.column()).isEqualTo("GR. No");
            assertThat(problem.message()).contains("required");
        });
    }

    @Test
    @DisplayName("a row with only a dropdown touched is named as such, not as four missing fields")
    void aRowTouchedByAccident() {
        BulkImportResponse result = upload(file(
                row("Gender", "Male", "Zone", "Zone 1 - North", "Designation", "Coordinator")));

        assertThat(result.created()).isZero();
        assertThat(result.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.line()).isEqualTo(2);
            assertThat(problem.column()).isNull();
            assertThat(problem.message())
                    .contains("no GR. No and no Sewadar Name")
                    .contains("Gender", "Zone", "Designation");
        });
    }

    @Test
    @DisplayName("the same GR. No twice in one file is caught before it reaches the table")
    void duplicateBadgeInTheFile() {
        BulkImportResponse result = upload(file(
                goodRow("B-030", "One"), goodRow("B-030", "Two")));

        assertThat(result.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.line()).isEqualTo(3);
            assertThat(problem.column()).isEqualTo("GR. No");
            assertThat(problem.message()).contains("more than one row");
        });
    }

    @Test
    @DisplayName("a GR. No already in the register is refused")
    void duplicateBadgeInTheRegister() {
        sewadarRepository.save(Sewadar.builder()
                .badgeNumber("B-040").name("Already Here")
                .zone(zoneRepository.findAll().get(0)).build());

        BulkImportResponse result = upload(file(goodRow("B-040", "Again")));

        assertThat(result.problems()).singleElement()
                .satisfies(p -> assertThat(p.message()).contains("already registered"));
    }

    @Test
    @DisplayName("a date written any other way is an error, not a guess")
    void birthDateMustParse() {
        Map<String, String> badDate = goodRow("B-050", "Bad Date");
        badDate.put("Birth Date", "April 1990");
        BulkImportResponse result = upload(file(badDate));

        assertThat(result.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.column()).isEqualTo("Birth Date");
            assertThat(problem.message()).contains("dd-MM-yyyy");
        });
    }

    @Test
    @DisplayName("a zone that is not set up names the ones that are")
    void unknownZone() {
        Map<String, String> moon = goodRow("B-060", "Nowhere");
        moon.put("Zone", "Zone 9 - Moon");
        BulkImportResponse result = upload(file(moon));

        assertThat(result.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.column()).isEqualTo("Zone");
            assertThat(problem.message()).contains("Zone 1 - North");
        });
    }

    @Test
    @DisplayName("every problem in the file is reported, not just the first")
    void reportsEveryProblem() {
        BulkImportResponse result = upload(file(row(
                "S.No", "1", "GR. No", "B-070", "Sewadar Name", "Bad Everything",
                "Age", "999", "Gender", "Alien", "Mobile No", "12345",
                "Zone", "Zone 9 - Moon", "Locality", "Local", "Designation", "Nothing")));

        assertThat(result.problems()).extracting(BulkImportResponse.RowProblem::column)
                .contains("Age", "Gender", "Mobile No", "Zone", "Designation");
    }

    @Test
    @DisplayName("a file without the required columns is refused before any row is read")
    void missingColumns() {
        assertThatThrownBy(() -> upload("S.No,Sewadar Name,Age\n1,Harpreet,34\n"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("GR. No");
    }

    @Test
    @DisplayName("a file with headings and no rows says so")
    void noRows() {
        assertThatThrownBy(() -> upload(headingsOnly()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no sewadars");
    }

    // ---------------------------------------------------------------- the template

    @Test
    @DisplayName("the template is a workbook with one column per field")
    void theTemplateIsAWorkbook() throws Exception {
        try (XSSFWorkbook workbook = openTemplate()) {
            Sheet sheet = workbook.getSheetAt(0);
            List<String> headings = new ArrayList<>();
            sheet.getRow(0).forEach(cell -> headings.add(cell.getStringCellValue()));

            assertThat(headings).containsExactlyElementsOf(COLUMNS);
        }
    }

    @Test
    @DisplayName("a required heading is black bold on red; the rest keep the plain grey")
    void requiredHeadingsAreOnRed() throws Exception {
        try (XSSFWorkbook workbook = openTemplate()) {
            Sheet sheet = workbook.getSheetAt(0);

            for (String heading : List.of("GR. No", "Sewadar Name", "Age", "Gender",
                    "Mobile No", "Zone", "Locality", "Designation")) {
                XSSFColor fill = (XSSFColor) headingCell(sheet, heading)
                        .getCellStyle().getFillForegroundColorColor();
                assertThat(fill.getARGBHex())
                        .as("fill behind %s", heading)
                        .isEqualTo("FFB22222");   // brick red
            }
            for (String heading : List.of("S.No", "F/H Name", "Email", "Blood Group", "Area",
                    "Grouping")) {
                assertThat(headingCell(sheet, heading).getCellStyle().getFillForegroundColor())
                        .as("fill behind %s", heading)
                        .isEqualTo(IndexedColors.GREY_25_PERCENT.getIndex());
            }
        }
    }

    @Test
    @DisplayName("every heading is black, bold and 16 point")
    void headingsAreBoldBlackAndLarge() throws Exception {
        try (XSSFWorkbook workbook = openTemplate()) {
            Sheet sheet = workbook.getSheetAt(0);
            for (String heading : List.of("GR. No", "Email")) {
                Font font = headingFont(workbook, sheet, heading);
                assertThat(font.getBold()).as("bold on %s", heading).isTrue();
                assertThat(font.getColor()).as("colour of %s", heading)
                        .isEqualTo(IndexedColors.BLACK.getIndex());
                assertThat(font.getFontHeightInPoints()).as("size of %s", heading)
                        .isEqualTo((short) 16);
            }
        }
    }

    @Test
    @DisplayName("the columns with a fixed set of values carry a dropdown")
    void dropdownsAreOnTheRightColumns() throws Exception {
        try (XSSFWorkbook workbook = openTemplate()) {
            Sheet sheet = workbook.getSheetAt(0);
            Set<Integer> withDropdown = new HashSet<>();
            sheet.getDataValidations().forEach(v -> {
                for (CellRangeAddress range : v.getRegions().getCellRangeAddresses()) {
                    withDropdown.add(range.getFirstColumn());
                }
            });

            // Gender, Blood Group, Zone, Locality, Status, Designation, Exemption -
            // read off COLUMNS so a new column shifts them without silent drift.
            for (String heading : List.of("Gender", "Blood Group", "Zone", "Locality",
                    "Status", "Designation", "Exemption")) {
                assertThat(withDropdown)
                        .as("dropdown on %s", heading)
                        .contains(COLUMNS.indexOf(heading));
            }
        }
    }

    @Test
    @DisplayName("each list is a dropdown the reader can open, not just a rule that refuses")
    void dropdownArrowsAreVisible() throws Exception {
        String sheet = sheetXml();

        /*
         * showDropDown means the opposite of what it reads like: the format defines it
         * as "suppress the in-cell list", so true hides the arrow. Asserted on the file
         * rather than through POI, because POI's setter inverts it again and a test
         * written against the setter passes while Excel shows nothing to pick from.
         */
        assertThat(sheet).contains("<dataValidation type=\"list\"");
        assertThat(sheet).doesNotContain("showDropDown=\"true\"");

        // And a wrong value typed over the top is still refused.
        assertThat(sheet).contains("errorStyle=\"stop\"");
    }

    @Test
    @DisplayName("a dropdown offers what is in the tables, not a list frozen in the code")
    void dropdownsComeFromTheTables() throws Exception {
        try (XSSFWorkbook workbook = openTemplate()) {
            Sheet lists = workbook.getSheet("Lists");
            assertThat(lists).isNotNull();
            assertThat(workbook.isSheetHidden(workbook.getSheetIndex(lists))).isTrue();

            List<String> values = new ArrayList<>();
            lists.forEach(row -> row.forEach(cell -> values.add(cell.getStringCellValue())));
            assertThat(values).contains("Male", "Female", "Zone 1 - North", "Coordinator",
                    "Permanent", "Open", "O+", "Yes", "No");
        }
    }

    @Test
    @DisplayName("the download carries the moment it was taken in its name")
    void theTemplateIsNamedForWhenItWasTaken() {
        String disposition = sewadarController.template()
                .getHeaders().getFirst("Content-Disposition");

        /*
         * Stamped because these are downloaded again and again: a fixed name leaves
         * a folder of addSewadar (1).xlsx with nothing to say which is which.
         */
        assertThat(disposition)
                .matches("attachment; filename=\"addSewadar-\\d{8}-\\d{6}\\.xlsx\"");
    }

    @Test
    @DisplayName("the workbook saved as CSV is what the upload accepts")
    void theTemplateSavedAsCsvUploads() throws Exception {
        String headings;
        try (XSSFWorkbook workbook = openTemplate()) {
            List<String> cells = new ArrayList<>();
            workbook.getSheetAt(0).getRow(0).forEach(cell -> cells.add(cell.getStringCellValue()));
            headings = String.join(",", cells);
        }

        BulkImportResponse result = upload(headings + "\n"
                + "1,B-090,Saved As Csv,,30,Male,9000000011,,,,,Zone 1 - North,,,Local,,,,Coordinator,\n");

        assertThat(result.problems()).isEmpty();
        assertThat(result.created()).isEqualTo(1);
    }

    // ------------------------------------------------------------------- helpers

    /** The first sheet's raw XML - an xlsx is a zip of these. */
    private String sheetXml() throws Exception {
        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(importService.template()))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if ("xl/worksheets/sheet1.xml".equals(entry.getName())) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new AssertionError("The template has no first sheet");
    }

    private XSSFWorkbook openTemplate() throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(importService.template()));
    }

    private Cell headingCell(Sheet sheet, String heading) {
        for (Cell cell : sheet.getRow(0)) {
            if (heading.equals(cell.getStringCellValue())) {
                return cell;
            }
        }
        throw new AssertionError("No column headed " + heading);
    }

    private Font headingFont(XSSFWorkbook workbook, Sheet sheet, String heading) {
        return workbook.getFontAt(headingCell(sheet, heading).getCellStyle().getFontIndex());
    }



    /** A row of the template, named rather than counted. Anything unsaid is empty. */
    private static Map<String, String> row(String... pairs) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            assertThat(COLUMNS).as("unknown column %s", pairs[i]).contains(pairs[i]);
            values.put(pairs[i], pairs[i + 1]);
        }
        return values;
    }

    /** A row that passes every check, for tests that break one thing on purpose. */
    private static Map<String, String> goodRow(String badge, String name) {
        return row("S.No", "1", "GR. No", badge, "Sewadar Name", name, "Age", "30",
                "Gender", "Male", "Mobile No", "9000000001", "Zone", "Zone 1 - North",
                "Locality", "Local", "Designation", "Coordinator");
    }

    private Sewadar saved(String badge) {
        return sewadarRepository.findAll().stream()
                .filter(s -> badge.equals(s.getBadgeNumber())).findFirst().orElseThrow();
    }

    private static Map<String, String> withLocality(Map<String, String> values, String locality) {
        values.put("Locality", locality);
        return values;
    }

    @SafeVarargs
    private static String file(Map<String, String>... rows) {
        StringBuilder csv = new StringBuilder();
        csv.append(COLUMNS.stream()
                .map(c -> REQUIRED.contains(c) ? c + " *" : c)
                .collect(java.util.stream.Collectors.joining(","))).append('\n');
        for (Map<String, String> values : rows) {
            csv.append(COLUMNS.stream()
                    .map(c -> quoted(values.getOrDefault(c, "")))
                    .collect(java.util.stream.Collectors.joining(","))).append('\n');
        }
        return csv.toString();
    }

    /** A value carrying a comma is one field, and CSV says so with quotes. */
    private static String quoted(String value) {
        return value.contains(",") || value.contains("\"")
                ? '"' + value.replace("\"", "\"\"") + '"'
                : value;
    }

    /** Just the headings, for the "no rows" case. */
    private static String headingsOnly() {
        return file();
    }

    private BulkImportResponse upload(String csv) {
        return importService.importCsv(new MockMultipartFile(
                "file", "addSewadar.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));
    }

    private void signInAsAdmin() {
        User admin = userRepository.save(User.builder()
                .username("import-admin-" + System.nanoTime())
                .passwordHash(passwordEncoder.encode("Test@12345"))
                .fullName("Import Admin")
                .role(Role.ADMIN)
                .enabled(true)
                .build());
        AppUserPrincipal principal = new AppUserPrincipal(admin, null, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
