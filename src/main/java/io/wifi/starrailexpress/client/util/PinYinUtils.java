package io.wifi.starrailexpress.client.util;

import com.github.promeg.pinyinhelper.Pinyin;

public class PinYinUtils {
    static {
        Pinyin.init(null);
    }

    public static String toSearchablePinyin(String text) {
        if (text == null || text.isEmpty())
            return "";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Pinyin.isChinese(c)) {
                // 汉字转拼音（TinyPinyin 返回全大写）
                String pinyin = Pinyin.toPinyin(c);
                sb.append(pinyin.toLowerCase()); // 转小写以便匹配
            } else {
                // 英文字母保留，转小写
                sb.append(c);
            }
            // 其他字符（标点、空格等）直接跳过
        }
        return sb.toString();
    }

    public static boolean contains(String search, String pattern) {
        if (search == null || pattern == null)
            return false;
        if (search.toLowerCase().contains(toSearchablePinyin(pattern).toLowerCase())) {
            return true;
        } else {
            return false;
        }
    }
}
