package com.user.management.model;

import com.user.management.entity.Gender;
import com.user.management.entity.SewaType;
import com.user.management.entity.Sewadar;

import java.time.LocalDate;

public record SewadarResponse(
        Long id,
        String badgeNumber,

        // registration form fields
        String name,
        String fatherOrHusbandName,
        LocalDate dateOfBirth,
        String mobile,
        Long zoneId,
        String zoneName,
        String zoneCode,
        String address,
        String aadharNumber,
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
        boolean hasLogin,
        String loginUsername
) {
    public static SewadarResponse from(Sewadar s) {
        return new SewadarResponse(
                s.getId(),
                s.getBadgeNumber(),
                s.getName(),
                s.getFatherOrHusbandName(),
                s.getDateOfBirth(),
                s.getMobile(),
                s.getZone() == null ? null : s.getZone().getId(),
                s.getZone() == null ? null : s.getZone().getName(),
                s.getZone() == null ? null : s.getZone().getCode(),
                s.getAddress(),
                s.getAadharNumber(),
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
                s.getUser() != null,
                s.getUser() == null ? null : s.getUser().getUsername());
    }
}
