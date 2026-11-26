/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.client.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 守住「调色板只有一个真源」这条约定（{@code docs/ui_unified_style_plan.md} §3 M0）。
 *
 * <h2>为什么</h2>
 * 同一套色值曾经在 {@code SREPanelStyle}（游戏内面板）/ {@code SreUiStyle}（主菜单、加载、连接中）/
 * {@code VoteFlowFrame}（投票旅程）里各声明一遍。三份数值一致时看不出问题，
 * 但只要有人单独调一处，界面就会悄悄分裂成两种观感。
 *
 * <h2>现在守什么</h2>
 * <ol>
 * <li><b>不许再声明色板常量</b>：这三个类里每个 {@code public static final int} 都必须转发到
 * {@code SRETheme.某字段}（不允许写死 8 位色值）。</li>
 * <li><b>转发必须指对</b>：指向的 {@code SRETheme} 字段得真的存在，拼错名字立刻失败。</li>
 * <li><b>绘制代码里不许再手写色板色</b>：用色板色画东西时必须引用 {@code SRETheme}
 * （需要改透明度就用 {@link SRETheme#withAlpha}）。个别刻意不归入色板的装饰纹，
 * 用 {@link #ALLOW_MARKER} 在其上一行标注后豁免。</li>
 * <li><b>文档与代码互相钉住</b>：{@code SRETheme} 的核心色值必须等于 {@code docs/ui_style.md} §2。</li>
 * </ol>
 *
 * <h2>为什么解析源码而不是 {@code Class.forName}</h2>
 * 这三个类里有静态初始化会碰 Minecraft（{@code SreUiStyle} 会 new
 * {@code FrameAnimationRenderer} / {@code ResourceLocation}），在无 bootstrap 的单元测试环境里
 * 加载它们并不可靠。{@link SRETheme} 本身只含常量与纯函数，反射它是安全的。
 */
class UiStyleTokenTest {

    /** 曾经各自为政的三份调色板。 */
    private static final List<Path> FORWARDER_SOURCES = List.of(
            Path.of("src/main/java/io/wifi/starrailexpress/client/gui/SREPanelStyle.java"),
            Path.of("src/main/java/net/exmo/sre/loading/SreUiStyle.java"),
            Path.of("src/main/java/io/wifi/starrailexpress/content/vote/client/VoteFlowFrame.java"));

    /**
     * 豁免标记：写在这类「刻意不进色板」的字面色值所在行的<b>正上方一行</b>。
     *
     * <p>
     * 只有真的属于独立视觉元素（例如背景那条比 {@code DECOR_LINE} 更暖的斜向纹）才该用它；
     * 「同一个色只是换了 alpha」不算理由 —— 那应该写 {@code SRETheme.withAlpha(...)}。
     */
    private static final String ALLOW_MARKER = "sre:allow-literal-color";

    /** 一行 {@code public static final int NAME = ...;}。 */
    private static final Pattern DECL = Pattern.compile(
            "^\\s*public\\s+static\\s+final\\s+int\\s+(\\w+)\\s*=\\s*(.+?);\\s*$");

    /** {@code SRETheme.FIELD} 引用。 */
    private static final Pattern THEME_REF = Pattern.compile("^SRETheme\\.(\\w+)$");

    /** 8 位十六进制色值字面量。 */
    private static final Pattern HEX_COLOR = Pattern.compile("0x[0-9A-Fa-f]{8}\\b");

    // ==================== 1. 色板常量必须是转发 ====================

    @Test
    void paletteConstantsAreForwardersNotLiterals() throws IOException {
        assertRepoRoot();
        Map<String, String> forwarded = new LinkedHashMap<>();
        List<String> literals = new ArrayList<>();

        for (Path source : FORWARDER_SOURCES) {
            for (String rawLine : readIfPresent(source)) {
                Matcher decl = DECL.matcher(stripComment(rawLine));
                if (!decl.matches()) {
                    continue;
                }
                String name = decl.group(1);
                String value = decl.group(2).strip();
                String where = simpleNameOf(source) + "." + name;
                if (THEME_REF.matcher(value).matches()) {
                    forwarded.put(where, value.substring("SRETheme.".length()));
                } else if (HEX_COLOR.matcher(value).find()) {
                    literals.add(where + " = " + value);
                }
            }
        }

        assertFalse(forwarded.isEmpty(),
                "三个调色板类里一个转发都没找到 —— 要么改动被回滚了，要么解析规则已过期");
        assertTrue(literals.isEmpty(),
                () -> "这些色板常量又写死成了字面量，应当改成 = SRETheme.某字段：\n  "
                        + String.join("\n  ", literals));

        List<String> dangling = new ArrayList<>();
        for (Map.Entry<String, String> entry : forwarded.entrySet()) {
            if (!hasStaticIntField(SRETheme.class, entry.getValue())) {
                dangling.add(entry.getKey() + " 转发的 SRETheme." + entry.getValue() + " 不存在");
            }
        }
        assertTrue(dangling.isEmpty(),
                () -> "这些转发指向了不存在的 SRETheme 字段：\n  " + String.join("\n  ", dangling));
    }

    // ==================== 2. 绘制代码不许再手写色板色 ====================

    /**
     * 绘制代码里出现「与某个色板色完全相同」的字面量就算违规。
     *
     * <p>
     * 这一条比第 1 条严格得多：它抓的是「明明有名字的色，却在画的时候又抄了一遍数字」，
     * 那正是界面分裂的真正来源。需要别的透明度请用 {@link SRETheme#withAlpha}。
     */
    @Test
    void drawingCodeReferencesThemeInsteadOfRepeatingPaletteLiterals() throws IOException {
        assertRepoRoot();
        List<String> palette = paletteLiterals();
        assertFalse(palette.isEmpty(), "没能从 SRETheme 里读到色板，反射可能失败了");

        List<String> offenders = new ArrayList<>();
        for (Path source : FORWARDER_SOURCES) {
            List<String> lines = readIfPresent(source);
            boolean exemptNext = false;
            for (String rawLine : lines) {
                if (rawLine.contains(ALLOW_MARKER)) {
                    exemptNext = true;
                    continue;
                }
                String code = stripComment(rawLine);
                boolean exempt = exemptNext;
                exemptNext = false;
                if (exempt || code.isBlank()) {
                    continue;
                }
                Matcher matcher = HEX_COLOR.matcher(code);
                while (matcher.find()) {
                    String literal = matcher.group();
                    if (palette.contains(literal.toUpperCase())) {
                        offenders.add(simpleNameOf(source) + ": " + code.strip()
                                + "   ← 请写 SRETheme.某个字段");
                        break;
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                () -> "绘制代码里重复了色板色，应当引用 SRETheme：\n  " + String.join("\n  ", offenders));
    }

    // ==================== 3. 文档与代码互相钉住 ====================

    @Test
    void themeMatchesUiStyleDoc() {
        // docs/ui_style.md §2.1
        assertEquals(0xD81A1008, SRETheme.SURFACE_TOP, "面板背景（上）");
        assertEquals(0xD820140A, SRETheme.SURFACE_BOTTOM, "面板背景（下）");
        assertEquals(0xD80B1722, SRETheme.SURFACE_BOTTOM_ALT, "面板背景（下，偏蓝棕黑）");
        assertEquals(0xF018120A, SRETheme.SCREEN_TOP, "全屏背景（上）");
        assertEquals(0xF0061018, SRETheme.SCREEN_BOTTOM, "全屏背景（下）");
        assertEquals(0xFF8B6914, SRETheme.BORDER, "面板边框");
        assertEquals(0x33FFE8C0, SRETheme.DECOR_LINE, "顶部装饰线");
        assertEquals(0xFFD4AF37, SRETheme.GOLD, "亮金色");
        assertEquals(0xFFC9A84C, SRETheme.GOLD_DIM, "棕金色");
        assertEquals(0xFFFFF4DC, SRETheme.TEXT, "主文字");
        assertEquals(0xFFF5E8C8, SRETheme.TITLE, "标题文字");
        assertEquals(0xFF9E8B6E, SRETheme.MUTED, "次要文字");
        assertEquals(0xFFC8B898, SRETheme.BODY, "暗米色正文");
        assertEquals(0xFF5EB7D8, SRETheme.BLUE, "功能蓝");
        assertEquals(0xFF72C17B, SRETheme.GREEN, "功能绿");
        assertEquals(0xFFE06B65, SRETheme.RED, "功能红");
        assertEquals(0xFFE0AD5B, SRETheme.AMBER, "功能琥珀（任务方块）");
        // 基础色
        assertEquals(0xFF000000, SRETheme.BLACK, "纯黑");
        assertEquals(0x00000000, SRETheme.TRANSPARENT, "全透明");
        assertEquals(0x00FFFFFF, SRETheme.RGB, "RGB 掩码");
        // §2.3
        assertEquals(0x22FFFFFF, SRETheme.ROW_HOVER, "hover 高亮背景");
        assertEquals(0x20FFFFFF, SRETheme.ROW_SEPARATOR, "行分隔线");
        assertEquals(0xFF5A4530, SRETheme.CARD_BORDER, "非活跃卡片边框");
    }

    @Test
    void placeholdersAndSelectedRowsKeepTheirDocumentedDerivation() {
        // HINT 就是 MUTED：EditBox 画 hint 用输入框文字色，不同色用户会以为框里已有内容
        assertEquals(SRETheme.MUTED, SRETheme.HINT, "占位提示色必须等于 MUTED");
        // 选中行按 §2.3 与 GOLD_DIM 混色得到
        assertEquals(SRETheme.blendColors(0xFF1A1008, SRETheme.GOLD_DIM, 0.32F), SRETheme.SELECTED_TOP);
        assertEquals(SRETheme.blendColors(0xFF120A04, SRETheme.GOLD_DIM, 0.18F), SRETheme.SELECTED_BOTTOM);
    }

    // ==================== 4. 工具函数 ====================

    @Test
    void blendColorsIsTheDocumentedArgbLerp() {
        // 端点原样返回
        assertEquals(0xFF112233, SRETheme.blendColors(0xFF112233, 0xFF445566, 0F));
        assertEquals(0xFF445566, SRETheme.blendColors(0xFF112233, 0xFF445566, 1F));
        // 逐通道独立插值，且按 int 截断而不是四舍五入
        assertEquals(0xFF7F7F7F, SRETheme.blendColors(0xFF000000, 0xFFFFFFFF, 0.5F));
        assertEquals(0xFF7F0000, SRETheme.blendColors(0xFF000000, 0xFFFF0000, 0.5F));
        // alpha 通道也一起插值
        assertEquals(0x7F000000, SRETheme.blendColors(0xFF000000, 0x00000000, 0.5F));
    }

    @Test
    void withAlphaReplacesOnlyTheAlphaChannel() {
        assertEquals(0x80D4AF37, SRETheme.withAlpha(0xFFD4AF37, 0x80));
        assertEquals(0x00D4AF37, SRETheme.withAlpha(0xFFD4AF37, 0));
        assertEquals(0xFFD4AF37, SRETheme.withAlpha(0x00D4AF37, 0xFF));
    }

    // ==================== 辅助 ====================

    /**
     * 把 {@code SRETheme} 里所有常量拼成 {@code 0xXXXXXXXX} 字符串集合，用来识别「手写的色板色」。
     *
     * <p>
     * 只反射 {@code SRETheme}：它没有静态初始化副作用，不需要 Minecraft 运行时。
     */
    private static List<String> paletteLiterals() {
        List<String> literals = new ArrayList<>();
        for (var field : SRETheme.class.getDeclaredFields()) {
            if (field.getType() != int.class || !java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            try {
                literals.add(String.format("0X%08X", field.getInt(null)));
            } catch (IllegalAccessException ignored) {
                // private 字段跳过；SRETheme 的色板全是 public，正常不会走到这里
            }
        }
        return literals;
    }

    /** {@code SRETheme} 是否声明了该 {@code static int} 字段。 */
    private static boolean hasStaticIntField(Class<?> owner, String field) {
        try {
            var declared = owner.getDeclaredField(field);
            return declared.getType() == int.class
                    && java.lang.reflect.Modifier.isStatic(declared.getModifiers());
        } catch (NoSuchFieldException missing) {
            return false;
        }
    }

    /** 读文件；文件不在了就返回空表（另一条断言会报「一个转发都没找到」）。 */
    private static List<String> readIfPresent(Path source) throws IOException {
        return Files.isRegularFile(source)
                ? Files.readAllLines(source, StandardCharsets.UTF_8)
                : List.of();
    }

    private static String simpleNameOf(Path source) {
        return source.getFileName().toString().replaceFirst("\\.java$", "");
    }

    /** 剥掉行注释。色值写在注释里不该被当成违规。 */
    private static String stripComment(String line) {
        String trimmed = line.strip();
        if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
            return "";
        }
        int lineComment = line.indexOf("//");
        return lineComment >= 0 ? line.substring(0, lineComment) : line;
    }

    /** Gradle 的测试工作目录是模块根。 */
    private static void assertRepoRoot() {
        assertTrue(Files.isDirectory(Path.of("src/main/java")),
                "找不到 src/main/java —— 本测试要求工作目录是仓库根");
    }
}
