package com.smartenergy.backend.utils;

import java.util.Map;
import java.util.regex.Pattern;

public final class AccountUsernameRules {

    public static final String BUILT_IN_ADMIN_USERNAME = "2026010001";
    public static final String FORMAT_DESCRIPTION = "账号格式必须为：入职年份 + 两位身份标识 + 四位顺序号，例如 2026010001";

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[12]\\d{3}\\d{2}(?!0000)\\d{4}$");
    private static final Map<String, String> ROLE_MARKERS = Map.of(
            "ADMIN", "01",
            "MANAGER", "02",
            "MAINTENANCE_ENGINEER", "03",
            "HR_MANAGER", "04",
            "OPERATOR", "05",
            "DEVICE_MANAGER", "06"
    );

    private AccountUsernameRules() {
    }

    public static void validate(String username, String role) {
        String normalized = username == null ? "" : username.trim();
        if (!USERNAME_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(FORMAT_DESCRIPTION);
        }

        String expectedMarker = ROLE_MARKERS.get(role);
        if (expectedMarker == null) {
            throw new IllegalArgumentException("角色不合法");
        }
        if (!expectedMarker.equals(normalized.substring(4, 6))) {
            throw new IllegalArgumentException("账号身份标识与角色不匹配，" + role + " 应使用 " + expectedMarker);
        }
    }
}
