package com.user.management.repository.projection;

import com.user.management.entity.AttendanceStatus;

public interface StatusCountRow {

    AttendanceStatus getStatus();

    Long getCount();
}
