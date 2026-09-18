package org.agmas.noellesroles.game.roles.neutral.priest;

/**
 * 神父咏诵台词。服务端按三种语言原文校验，避免只认翻译键。
 */
public final class PriestLyrics {

    public static final int COUNT = 15;

    public static final String[] ZH = {
            "螺旋阶梯",
            "独角仙",
            "废墟街道",
            "无花果塔",
            "独角仙",
            "苦伤道",
            "独角仙",
            "特异点",
            "乔托",
            "天使",
            "绣球花",
            "独角仙",
            "特异点",
            "秘密皇帝",
            "时间要开始加速了 made in heaven"
    };

    public static final String[] TW = {
            "螺旋階梯",
            "獨角仙",
            "廢墟街道",
            "無花果塔",
            "獨角仙",
            "苦傷道",
            "獨角仙",
            "特異點",
            "喬托",
            "天使",
            "繡球花",
            "獨角仙",
            "特異點",
            "秘密皇帝",
            "時間要開始加速了 made in heaven"
    };

    public static final String[] EN = {
            "Spiral Staircase",
            "Rhinoceros Beetle",
            "Desolation Row",
            "Fig Tart",
            "Rhinoceros Beetle",
            "Via Dolorosa",
            "Rhinoceros Beetle",
            "Singularity Point",
            "Giotto",
            "Angel",
            "Hydrangea",
            "Rhinoceros Beetle",
            "Singularity Point",
            "Secret Emperor",
            "Time is about to accelerate Made in Heaven"
    };

    private PriestLyrics() {
    }

    public static String translationKey(int index) {
        return "screen.noellesroles.priest.lyric." + index;
    }

    public static boolean matches(int index, String input) {
        if (input == null || index < 0 || index >= COUNT) {
            return false;
        }
        String text = normalize(input);
        if (text.isEmpty()) {
            return false;
        }
        return text.equals(normalize(ZH[index]))
                || text.equals(normalize(TW[index]))
                || text.equalsIgnoreCase(normalize(EN[index]));
    }

    private static String normalize(String text) {
        return text.trim().replaceAll("\\s+", " ");
    }
}
