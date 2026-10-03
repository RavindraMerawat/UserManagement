package com.user.management.model;

import com.user.management.entity.Gender;
import com.user.management.entity.Locality;
import com.user.management.entity.SewadarStatus;
import com.user.management.entity.SewaType;
import com.user.management.entity.Sewadar;
import com.user.management.entity.Zone;
import com.user.management.security.AadharMask;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.List;

public record SewadarResponse(
        Long id,
        String badgeNumber,
        boolean badgeIssued,
        boolean badgeReceived,

        // registration form fields
        String name,
        String fatherOrHusbandName,
        LocalDate dateOfBirth,
        Integer age,
        String mobile,
        Long zoneId,
        String zoneName,
        String zoneCode,
        String address,

        /** Full 12 digits, or {@code XXXX XXXX 9012} when the caller may not see them. */
        String aadharNumber,

        /** True when {@code aadharNumber} is the masked form, so the UI leaves it alone. */
        boolean aadharMasked,
        String bloodGroup,
        Set<Long> extraZoneIds,
        List<String> extraZoneNames,
        String area,
        String grouping,
        Locality locality,
        String localityLabel,
        String centerPoint,

        // additional details
        Gender gender,
        String email,
        String department,
        SewadarStatus status,
        String statusLabel,
        Long designationId,
        String designationName,
        Long sewaPointId,
        String sewaPointName,
        LocalDate joiningDate,
        boolean exempted,
        boolean hasPhoto,
        Instant photoUpdatedAt,
        boolean hasLogin,
        String loginUsername,
        /**
         * Hours on one particular day - check in to check out.
         *
         * <p>Null everywhere except the dashboard tile list, which is about a day
         * and is the only place the question "how long were they here" belongs. It
         * is the day's figure, not a running total.</p>
         */
        Double hoursOnDate
) {

    /** The same record with the day's hours filled in. */
    public SewadarResponse withHoursOnDate(Double hours) {
        return new SewadarResponse(id, badgeNumber, badgeIssued, badgeReceived, name,
                fatherOrHusbandName, dateOfBirth, age, mobile, zoneId, zoneName, zoneCode,
                address, aadharNumber, aadharMasked, bloodGroup, extraZoneIds, extraZoneNames,
                area, grouping, locality, localityLabel, centerPoint,
                gender, email, department, status, statusLabel, designationId, designationName,
                sewaPointId, sewaPointName, joiningDate, exempted, hasPhoto, photoUpdatedAt,
                hasLogin, loginUsername, hours);
    }

    /**
     * @param fullAadhar from {@code CurrentUserService.canViewFullAadhar(id)}. There is
     *                   no overload that defaults it: the decision has to be made at
     *                   every call site rather than being forgotten into a leak.
     */
    public static SewadarResponse from(Sewadar s, boolean fullAadhar) {
        String aadhar = s.getAadharNumber();
        boolean masked = aadhar != null && !aadhar.isBlank() && !fullAadhar;
        return new SewadarResponse(
                s.getId(),
                s.getBadgeNumber(),
                s.isBadgeIssued(),
                s.isBadgeReceived(),
                s.getName(),
                s.getFatherOrHusbandName(),
                s.getDateOfBirth(),
                s.getAge(),
                s.getMobile(),
                s.getZone() == null ? null : s.getZone().getId(),
                s.getZone() == null ? null : s.getZone().getName(),
                s.getZone() == null ? null : s.getZone().getCode(),
                s.getAddress(),
                masked ? AadharMask.mask(aadhar) : aadhar,
                masked,
                s.getBloodGroup(),
                s.getExtraZones().stream().map(Zone::getId)
                        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)),
                s.getExtraZones().stream().map(Zone::getName).toList(),
                s.getArea(),
                s.getGrouping(),
                s.getLocality(),
                s.getLocality() == null ? null : s.getLocality().getDisplayName(),
                s.getCenterPoint(),
                s.getGender(),
                s.getEmail(),
                s.getDepartment(),
                s.getStatus(),
                s.getStatus() == null ? null : s.getStatus().getDisplayName(),
                s.getRole() == null ? null : s.getRole().getId(),
                s.getRole() == null ? null : s.getRole().getName(),
                s.getSewaPoint() == null ? null : s.getSewaPoint().getId(),
                s.getSewaPoint() == null ? null : s.getSewaPoint().getName(),
                s.getJoiningDate(),
                s.isExempted(),
                s.getPhotoUpdatedAt() != null,
                s.getPhotoUpdatedAt(),
                s.getUser() != null,
                s.getUser() == null ? null : s.getUser().getUsername(),
                // Filled in only where a day is in question - see withHoursOnDate.
                null);
    }
}
