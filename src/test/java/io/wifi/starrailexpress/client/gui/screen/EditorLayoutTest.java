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

package io.wifi.starrailexpress.client.gui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.wifi.starrailexpress.client.gui.SREPanelStyle;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link EditorLayout} 的不变量测试。
 *
 * <p>
 * 这些断言正是改造前四个编辑器会违反的地方：面板跑出屏幕、字段戳出面板被裁掉、
 * 滚动条画到面板外、预览遮挡字段。渲染没法 headless 验证，所以把几何抽成纯函数在这里守住。
 */
class EditorLayoutTest {

    /** 四个编辑器改造后实际使用的四套参数。 */
    private static EditorLayout.Config[] allConfigs() {
        return new EditorLayout.Config[] {
                EditorLayout.Config.defaults().labelColumn(140),
                EditorLayout.Config.defaults().labelColumn(150),
                EditorLayout.Config.defaults().labelColumn(176).previewColumn(64),
                EditorLayout.Config.defaults().labelColumn(176).previewColumn(64)
        };
    }

    private static int[][] windowsAndGuiScales() {
        int[][] windows = { { 854, 480 }, { 1280, 720 }, { 1920, 1080 }, { 2560, 1440 }, { 320, 240 } };
        // [windowW, windowH, guiScale]
        int[][] cases = new int[windows.length * 4][];
        int index = 0;
        for (int[] window : windows) {
            for (int scale = 1; scale <= 4; scale++) {
                cases[index++] = new int[] { window[0], window[1], scale };
            }
        }
        return cases;
    }

    @Test
    void panelAndContentStayInsideScreenAtCommonWindowSizesAndGuiScales() {
        for (int[] cfgCase : windowsAndGuiScales()) {
            int width = (int) Math.ceil(cfgCase[0] / (double) cfgCase[2]);
            int height = (int) Math.ceil(cfgCase[1] / (double) cfgCase[2]);
            for (EditorLayout.Config cfg : allConfigs()) {
                EditorLayout layout = EditorLayout.of(width, height, cfg, new int[] { 60, 60, 70, 60 }, 120);
                String info = width + "x" + height + " " + layout;
                assertTrue(layout.panelX() >= 0 && layout.panelY() >= 0, info);
                assertTrue(layout.panelX() + layout.panelW() <= width, info);
                assertTrue(layout.panelY() + layout.panelH() <= height, info);
                assertTrue(layout.panelW() >= 1 && layout.panelH() >= 1, info);
                assertTrue(layout.contentY() >= layout.panelY(), info);
                assertTrue(layout.contentBottom() <= layout.panelY() + layout.panelH(), info);
                assertTrue(layout.contentH() >= 1, info);
                assertTrue(layout.contentX() >= layout.panelX(), info);
                // 滚动条必须在面板内（改造前 Modifier/Item/Block 画在 panelX + panelW + 1，窄屏直接出屏）
                assertTrue(layout.sbX() >= layout.panelX(), info);
                assertTrue(layout.sbX() + EditorLayout.SCROLL_W <= layout.panelX() + layout.panelW(), info);
            }
        }
    }

    @Test
    void fieldAreaNeverLeavesTheContentArea() {
        for (int width = 160; width <= 900; width += 7) {
            for (int height : new int[] { 120, 180, 240, 480, 720 }) {
                for (EditorLayout.Config cfg : allConfigs()) {
                    for (int label : new int[] { 0, 40, 120, 400 }) {
                        EditorLayout layout = EditorLayout.of(width, height, cfg, new int[] { 56, 56 }, label);
                        String info = width + "x" + height + " label=" + label + " " + layout;
                        assertTrue(layout.fieldW() >= 1, info);
                        assertTrue(layout.fieldX() >= layout.contentX(), info);
                        // 字段右边界就是内容右边界：字段区不会盖住滚动条槽，更不会戳出面板
                        assertTrue(layout.contentX() + layout.contentW() >= layout.contentRight(), info);
                        assertTrue(layout.contentRight() <= layout.panelX() + layout.panelW() - EditorLayout.PAD,
                                info);
                        assertTrue(layout.labelW() >= 0, info);
                        if (layout.compact()) {
                            assertEquals(0, layout.labelW(), "紧凑模式标签列应为 0：" + info);
                            assertEquals(layout.contentX(), layout.fieldX(), info);
                        } else {
                            assertTrue(layout.labelW() >= EditorLayout.MIN_LABEL_W, info);
                            assertEquals(layout.contentX() + layout.labelW() + EditorLayout.GAP, layout.fieldX(),
                                    info);
                        }
                        if (layout.previewW() > 0) {
                            assertTrue(layout.previewX() >= layout.fieldX(), info);
                            assertTrue(layout.previewX() + layout.previewW() <= layout.contentX() + layout.contentW(),
                                    info);
                        }
                    }
                }
            }
        }
    }

    @Test
    void previewColumnOnlyReservedWhenItFits() {
        EditorLayout.Config cfg = EditorLayout.Config.defaults().labelColumn(176).previewColumn(64);
        // 宽屏：留出预览列
        assertTrue(EditorLayout.of(900, 480, cfg, new int[] { 56, 56 }, 120).previewW() > 0);
        // 窄屏：预览列让位，界面改用内容区里的一行来画预览（改造前它会压在字段上）
        EditorLayout narrow = EditorLayout.of(320, 240, cfg, new int[] { 56, 56 }, 120);
        assertEquals(0, narrow.previewW(), narrow.toString());
        assertEquals(0, narrow.previewH(), narrow.toString());
    }

    @Test
    void tabsWrapInsideThePanelAndPushContentDown() {
        EditorLayout.Config cfg = EditorLayout.Config.defaults();
        int[] sixTabs = { 74, 74, 88, 88, 88, 74 };
        EditorLayout wide = EditorLayout.of(900, 360, cfg, sixTabs, 120);
        assertEquals(1, wide.tabRows(), wide.toString());
        assertEquals(6, wide.tabs().size());

        EditorLayout narrow = EditorLayout.of(300, 240, cfg, sixTabs, 120);
        assertTrue(narrow.tabRows() > 1, narrow.toString());
        for (EditorLayout.TabSlot slot : narrow.tabs()) {
            assertTrue(slot.x() >= narrow.panelX(), narrow.toString());
            assertTrue(slot.x() + slot.w() <= narrow.panelX() + narrow.panelW(), narrow.toString());
            assertTrue(slot.y() + EditorLayout.TAB_H <= narrow.contentY(), narrow.toString());
        }
        // 折行后内容区必须下移，不能压住第二行页签（按面板内偏移比较，面板位置本身也随高度变）
        assertTrue(narrow.contentY() - narrow.panelY()
                > wide.contentY() - wide.panelY() + EditorLayout.TAB_ROW_H - 1, narrow.toString());
    }

    @Test
    void titleStripSitsAboveTheTabBar() {
        for (int[] cfgCase : windowsAndGuiScales()) {
            int width = (int) Math.ceil(cfgCase[0] / (double) cfgCase[2]);
            int height = (int) Math.ceil(cfgCase[1] / (double) cfgCase[2]);
            EditorLayout layout = EditorLayout.of(width, height, EditorLayout.Config.defaults(),
                    new int[] { 60, 60, 60, 60, 60 }, 120);
            String info = width + "x" + height + " " + layout;
            // 标题在面板内的专属条里，和页签不重叠（改造前 Role 把标题居中画在页签栏上）
            assertTrue(layout.titleY() >= layout.panelY(), info);
            assertTrue(layout.titleY() + EditorLayout.LINE_H <= layout.tabs().get(0).y(), info);
            // 面板贴满屏幕高度时标题也不能跑到屏幕外（改造前画在 panelY - 16）
            assertTrue(layout.titleY() >= 0, info);
        }
    }

    @Test
    void headerExtraPushesTabsAndContentDownAndStaysInsidePanel() {
        // 地图工具用这段高度放坐标行与偏移控件
        EditorLayout.Config plain = EditorLayout.Config.defaults();
        EditorLayout.Config withHeader = plain.headerExtra(56);
        assertTrue(withHeader.headerExtra() > 0);
        assertEquals(plain.headerExtra(), 0, "默认不能给编辑器凭空加高度");

        for (int[] size : new int[][] { { 500, 400 }, { 900, 600 }, { 340, 260 }, { 300, 200 } }) {
            EditorLayout base = EditorLayout.of(size[0], size[1], plain, new int[] { 60, 60, 60 }, 120);
            EditorLayout extra = EditorLayout.of(size[0], size[1], withHeader, new int[] { 60, 60, 60 }, 120);
            String info = size[0] + "x" + size[1] + " " + extra;

            // 页签栏、内容区整体下移
            assertEquals(base.tabs().get(0).y() + 56, extra.tabs().get(0).y(), info);
            assertTrue(extra.contentY() > base.contentY(), info);
            // 头部区域夹在标题条与页签栏之间，且非空
            assertTrue(extra.headerTop() >= extra.titleY() + extra.titleH(), info);
            assertTrue(extra.headerBottom() > extra.headerTop(), info);
            assertTrue(extra.headerBottom() <= extra.tabs().get(0).y(), info);
            // 面板仍然完整落在屏幕内
            assertTrue(extra.panelY() >= 0 && extra.panelY() + extra.panelH() <= size[1], info);
            assertTrue(extra.contentH() >= 1, info);
        }
    }

    @Test
    void headerExtraStillFitsWhenThePanelIsShort() {
        // 头部很高时内容区可能被挤没：至少要保证 contentH >= 1、面板不出屏（由界面自己去调 headerExtra）
        EditorLayout layout = EditorLayout.of(400, 200, EditorLayout.Config.defaults().headerExtra(60),
                new int[] { 60, 60 }, 120);
        assertTrue(layout.contentH() >= 1, layout.toString());
        assertTrue(layout.panelY() + layout.panelH() <= 200, layout.toString());
        assertTrue(layout.headerTop() < layout.headerBottom(), layout.toString());
    }

    @Test
    void topStripSitsBelowTheTabsAndAboveTheContent() {
        // 地图工具「全部设置」的搜索框：贴在页签按钮下面、不随内容滚动
        EditorLayout.Config plain = EditorLayout.Config.defaults();
        EditorLayout.Config withStrip = plain.topStrip(24);
        assertEquals(0, plain.topStrip(), "默认不能给编辑器凭空加常驻带");
        assertEquals(0, EditorLayout.of(800, 600, plain, new int[] { 60, 60 }, 120).stripH());

        for (int[] size : new int[][] { { 900, 600 }, { 500, 400 }, { 340, 260 } }) {
            EditorLayout base = EditorLayout.of(size[0], size[1], plain, new int[] { 60, 60 }, 120);
            EditorLayout strip = EditorLayout.of(size[0], size[1], withStrip, new int[] { 60, 60 }, 120);
            String info = size[0] + "x" + size[1] + " " + strip;

            assertEquals(24, strip.stripH(), info);
            // 带子在页签下面，不该把页签顶下去
            assertEquals(base.tabs().get(0).y(), strip.tabs().get(0).y(), info);
            // 带子正好填在「页签下面 / 内容上面」，内容整体让开这一段
            assertTrue(strip.stripTop() >= strip.tabs().get(0).y() + EditorLayout.TAB_H, info);
            assertEquals(base.contentY() + 24, strip.contentY(), info);
            assertEquals(strip.contentY(), strip.stripTop() + strip.stripH(), info);
            // 内容裁剪区与滚动条槽都跟着下移，带子里的控件不会被滚动内容盖住
            assertEquals(strip.contentY(), strip.sbTop(), info);
            assertTrue(strip.stripTop() + 18 <= strip.contentY(), "搜索框放不进这一段就白留了：" + info);
            assertTrue(strip.contentH() >= 1, info);
            assertTrue(strip.panelY() >= 0 && strip.panelY() + strip.panelH() <= size[1], info);
        }
    }

    @Test
    void packWrapsWhenTheRowIsTooNarrow() {
        List<EditorLayout.CellBox> boxes = EditorLayout.pack(240, 4, new int[] { 112, 64, 40, 18 },
                new int[] { 0, 0, 0, 0 }, new float[] { 0, 0, 0, 0 });
        assertEquals(4, boxes.size());
        int maxRow = 0;
        for (EditorLayout.CellBox box : boxes) {
            maxRow = Math.max(maxRow, box.row());
            assertTrue(box.width() >= 1, box.toString());
            // 右边界永不越界 —— 这正是改造前 fieldX()+332 这类写死偏移会违反的
            assertTrue(box.x() + box.width() <= 240, box.toString());
        }
        assertTrue(maxRow >= 1, "总宽 234 + 间距 12 > 240，应该折行：" + boxes);
        // 行的编号必须从 0 开始连续
        for (int row = 0; row <= maxRow; row++) {
            final int expected = row;
            assertTrue(boxes.stream().anyMatch(b -> b.row() == expected), "缺第 " + row + " 行：" + boxes);
        }
        assertEquals(0, boxes.get(0).x());
    }

    @Test
    void packGivesAnOversizedCellTheWholeRow() {
        List<EditorLayout.CellBox> boxes = EditorLayout.pack(200, 4, new int[] { 400, 60 },
                new int[] { 0, 0 }, new float[] { 0, 0 });
        assertEquals(2, boxes.size());
        assertEquals(0, boxes.get(0).row());
        assertEquals(200, boxes.get(0).width(), boxes.toString());
        assertEquals(1, boxes.get(1).row(), boxes.toString());
        assertEquals(0, boxes.get(1).x(), boxes.toString());
    }

    @Test
    void packDistributesFlexWidthEvenlyAndFillsTheRow() {
        List<EditorLayout.CellBox> boxes = EditorLayout.pack(300, 4, new int[] { 60, 60, 60 },
                new int[] { 0, 0, 0 }, new float[] { 1F, 1F, 1F });
        assertEquals(3, boxes.size());
        assertEquals(boxes.get(0).row(), boxes.get(1).row());
        assertEquals(boxes.get(1).row(), boxes.get(2).row());
        int first = boxes.get(0).width();
        for (EditorLayout.CellBox box : boxes) {
            assertTrue(Math.abs(box.width() - first) <= 1, boxes.toString());
        }
        EditorLayout.CellBox last = boxes.get(2);
        assertTrue(last.x() + last.width() <= 300, boxes.toString());
        assertTrue(last.x() + last.width() >= 297, "弹性单元应撑满一行：" + boxes);
    }

    @Test
    void packRespectsMaxWidthOnFlexCells() {
        // 第一个单元被 maxW 卡住，第二个吃掉剩下的空间；行左对齐，右边界不越界
        List<EditorLayout.CellBox> boxes = EditorLayout.pack(600, 4, new int[] { 60, 60 },
                new int[] { 100, 0 }, new float[] { 1F, 1F });
        assertEquals(100, boxes.get(0).width(), boxes.toString());
        assertEquals(298, boxes.get(1).width(), boxes.toString());
        assertTrue(boxes.get(1).x() + boxes.get(1).width() <= 600, boxes.toString());
    }

    @Test
    void compactModeOnlyOnVeryNarrowPanels() {
        EditorLayout.Config cfg = EditorLayout.Config.defaults().labelColumn(176);
        assertFalse(EditorLayout.of(640, 480, cfg, new int[] { 56, 56 }, 120).compact(), "常规窗口不该进紧凑模式");
        assertTrue(EditorLayout.of(240, 320, cfg, new int[] { 56, 56 }, 120).compact(), "极窄窗口应进紧凑模式");
        // 紧凑模式下标签让出整行给字段区
        EditorLayout narrow = EditorLayout.of(240, 320, cfg, new int[] { 56, 56 }, 120);
        assertEquals(narrow.contentX(), narrow.fieldX());
        assertTrue(narrow.fieldW() > 120, narrow.toString());
    }

    @Test
    void rowHeightsMatchTheWidgetHeight() {
        assertEquals(EditorLayout.ROW_H, EditorLayout.rowHeight(EditorLayout.RowKind.ROW, false, 1, 1));
        assertEquals(EditorLayout.LABEL_LINE_H + EditorLayout.ROW_H,
                EditorLayout.rowHeight(EditorLayout.RowKind.ROW, true, 1, 1));
        assertEquals(2 * EditorLayout.ROW_H, EditorLayout.rowHeight(EditorLayout.RowKind.ROW, false, 2, 1));
        assertEquals(2 * EditorLayout.LINE_H + 2, EditorLayout.rowHeight(EditorLayout.RowKind.NOTE, false, 1, 2));
        assertTrue(EditorLayout.rowHeight(EditorLayout.RowKind.SECTION, false, 1, 1) >= EditorLayout.LINE_H + 4);
        for (EditorLayout.RowKind kind : EditorLayout.RowKind.values()) {
            assertTrue(EditorLayout.rowHeight(kind, false, 1, 1) > 0, kind.name());
        }
    }

    @Test
    void scrollRangeAndThumbGeometryAgree() {
        assertEquals(0, EditorLayout.maxScroll(100, 100), "内容不足一屏时不可滚动");
        assertEquals(0, EditorLayout.maxScroll(80, 100));
        assertEquals(100, EditorLayout.maxScroll(200, 100));

        int trackH = 200;
        int maxScroll = 400;
        int thumbH = EditorLayout.thumbHeight(trackH, maxScroll);
        assertTrue(thumbH >= EditorLayout.SCROLL_MIN_THUMB && thumbH <= trackH, "thumbH=" + thumbH);
        // 未滚动时 thumb 停在轨道顶，滚到底时停在轨道底
        assertEquals(10, EditorLayout.thumbY(10, trackH, thumbH, 0, maxScroll));
        assertEquals(10 + trackH - thumbH, EditorLayout.thumbY(10, trackH, thumbH, maxScroll, maxScroll));

        // 没有滚动空间时 thumb 占满轨道
        assertEquals(trackH, EditorLayout.thumbHeight(trackH, 0));
    }

    @Test
    void degenerateScreenSizesStayValid() {
        for (int[] size : new int[][] { { 1, 1 }, { 0, 0 }, { 40, 40 }, { 160, 120 }, { 320, 180 } }) {
            for (EditorLayout.Config cfg : allConfigs()) {
                EditorLayout layout = EditorLayout.of(size[0], size[1], cfg, new int[] { 60, 60 }, 120);
                String info = size[0] + "x" + size[1] + " " + layout;
                assertTrue(layout.panelW() >= 1 && layout.panelH() >= 1, info);
                assertTrue(layout.panelX() + layout.panelW() <= Math.max(1, size[0]), info);
                assertTrue(layout.contentW() >= 1 && layout.contentH() >= 1, info);
                assertTrue(layout.fieldW() >= 1, info);
                assertTrue(layout.sbH() >= 1, info);
            }
        }
    }

    @Test
    void noTabsMeansNoTabStrip() {
        EditorLayout layout = EditorLayout.of(640, 480, EditorLayout.Config.defaults(), new int[0], 120);
        assertEquals(0, layout.tabRows());
        assertEquals(0, layout.tabH());
        assertTrue(layout.tabs().isEmpty());
        assertTrue(layout.contentY() > layout.panelY(), layout.toString());
    }

    @Test
    void scrollConstantsMatchThePanelStyle() {
        assertEquals(SREPanelStyle.SCROLL_WIDTH, EditorLayout.SCROLL_W);
        assertEquals(SREPanelStyle.SCROLL_MIN_THUMB, EditorLayout.SCROLL_MIN_THUMB);
    }

    @Test
    void configHelpersKeepTheOtherFields() {
        EditorLayout.Config base = EditorLayout.Config.defaults();
        EditorLayout.Config withPreview = base.previewColumn(64);
        assertEquals(64, withPreview.previewW());
        assertEquals(base.labelColW(), withPreview.labelColW());
        assertEquals(base.ratio(), withPreview.ratio(), 0.0001F);
        EditorLayout.Config withLabel = withPreview.labelColumn(120);
        assertEquals(120, withLabel.labelColW());
        assertEquals(64, withLabel.previewW());
        assertEquals(0.92F, withLabel.ratio(), 0.0001F);
        EditorLayout.Config resized = withLabel.panelSize(0.9F, 640, 520, 320, 320);
        assertEquals(640, resized.maxPanelW());
        assertEquals(320, resized.minPanelH());
        assertEquals(120, resized.labelColW());
        assertEquals(64, resized.previewW());
    }
}
