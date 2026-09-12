package com.user.management.model;

import com.user.management.entity.Gender;
import com.user.management.entity.SewaType;
import com.user.management.entity.Sewadar;
import com.user.management.security.AadharMask;

import java.time.Instant;
import java.time.LocalDate;

public record SewadarResponse(
        Long id,
        String badgeNumber,
        boolean badgeIssued,
        boolean badgeReceived,

        // registration form fields
        String name,
        String fatherOrHusbandName,
        LocalDate dateOfBirth,
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
        String area,
        String centerPoint,

        // additional details
        Gender gender,
        String email,
        String city,
        String pincode,
        String department,
        SewaType primarySewaType,
        LocalDate joiningDate,
        boolean active,
        boolean hasPhoto,
        Instant photoUpdatedAt,
        boolean hasLogin,
        String loginUsername
) {
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
                s.getMobile(),
                s.getZone() == null ? null : s.getZone().getId(),
                s.getZone() == null ? null : s.getZone().getName(),
                s.getZone() == null ? null : s.getZone().getCode(),
                s.getAddress(),
                masked ? AadharMask.mask(aadhar) : aadhar,
                masked,
                s.getBloodGroup(),
                s.getArea(),
                s.getCenterPoint(),
                s.getGender(),
                s.getEmail(),
                s.getCity(),
                s.getPincode(),
                s.getDepartment(),
                s.getPrimarySewaType(),
                s.getJoiningDate(),
                s.isActive(),
                s.getPhotoUpdatedAt() != null,
                s.getPhotoUpdatedAt(),
                s.getUser() != null,
                s.getUser() == null ? null : s.getUser().getUsername());
    }
}
