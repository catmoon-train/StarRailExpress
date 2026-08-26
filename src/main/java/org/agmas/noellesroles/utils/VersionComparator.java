package org.agmas.noellesroles.utils;

import java.util.Comparator;

public class VersionComparator {

    private static boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    public static final Comparator<String> VERSION_COMPARATOR = (v1, v2) -> {
        // 处理 null（根据业务需求调整）
        if (v1 == null && v2 == null) return 0;
        if (v1 == null) return -1;
        if (v2 == null) return 1;

        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int len = Math.min(parts1.length, parts2.length);

        for (int i = 0; i < len; i++) {
            String p1 = parts1[i];
            String p2 = parts2[i];
            boolean num1 = isNumeric(p1);
            boolean num2 = isNumeric(p2);

            if (num1 && num2) {
                int n1 = Integer.parseInt(p1);
                int n2 = Integer.parseInt(p2);
                if (n1 != n2) return Integer.compare(n1, n2);
            } else {
                // 至少一个非数字，按字符串比较（可利用 Collator 实现本地化，这里用默认）
                int cmp = p1.compareTo(p2);
                if (cmp != 0) return cmp;
            }
        }
        // 如果公共部分完全一致，则版本号更长的更大（如 1.0.0 > 1.0）
        return Integer.compare(parts1.length, parts2.length);
    };
}