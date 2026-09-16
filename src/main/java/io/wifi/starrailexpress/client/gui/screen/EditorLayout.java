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

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义内容编辑器（{@link CustomEditorScreen}）的响应式布局，按 {@code docs/ui_style.md} §4 计算。
 *
 * <p>
 * 这里只做几何，<b>不引用任何 Minecraft 类型</b>：页签文字宽度、提示文字折行数这些需要字体测量的
 * 结果一律由调用方算好传进来，因此这个类可以在普通 JVM 测试里直接跑（见 {@code EditorLayoutTest}）。
 *
 * <p>
 * 布局分块（自外向内）：
 * <ol>
 * <li>面板：按屏幕比例计算并 clamp 进屏幕，永远完整可见（含最小宽度，不会比屏幕还大）；</li>
 * <li>标题条：面板内顶部一条，标题不再和页签抢位置；</li>
 * <li>页签栏：宽度按标签实测，放不下自动折成多行，内容区随之下移；</li>
 * <li>常驻带（可选，{@code Config.topStrip}）：页签栏下方的一条固定控件带，内容区从它下面开始；</li>
 * <li>内容区：字段区 + 右侧预览列（放得下才有）；</li>
 * <li>滚动条槽：面板内右侧永久预留，内容永远不会画到滚动条底下，也不会跑到面板外。</li>
 * </ol>
 *
 * <p>
 * 窄屏（内容区不够宽）时进入<b>紧凑模式</b>：标签换行到字段上方，字段独占整行，
 * 行内多个控件由 {@link #pack} 自动折行堆叠 —— 而不是把控件挤出面板。
 */
public record EditorLayout(
        int panelX, int panelY, int panelW, int panelH,
        int titleX, int titleY, int titleH,
        int tabH, int tabRows, List<TabSlot> tabs,
        int contentX, int contentY, int contentW, int contentH, int contentBottom,
        int labelW, int fieldX, int fieldW, boolean compact,
        int sbX, int sbTop, int sbH,
        int previewX, int previewY, int previewW, int previewH,
        int stripH) {

    /** 面板内边距。 */
    public static final int PAD = 6;
    /** 同行控件之间的间距。 */
    public static final int GAP = 4;
    /** 一行控件的行距（控件 18 高 + 4 间距）。 */
    public static final int ROW_H = 22;
    /** 控件高度（原版 EditBox / Button 的常用高度）。 */
    public static final int WIDGET_H = 18;
    /** 标题条高度。 */
    public static final int TITLE_H = 14;
    /** 页签高度。 */
    public static final int TAB_H = 20;
    /** 页签行距（页签高 + 2）。 */
    public static final int TAB_ROW_H = TAB_H + 2;
    /** 页脚高度（保存/管理/取消三个按钮）。 */
    public static final int FOOTER_H = 30;
    /** 滚动条宽度（与 {@code SREPanelStyle.SCROLL_WIDTH} 保持一致，由测试保证）。 */
    public static final int SCROLL_W = 7;
    /** 滚动条 thumb 最小高度（与 {@code SREPanelStyle.SCROLL_MIN_THUMB} 一致）。 */
    public static final int SCROLL_MIN_THUMB = 20;
    /** 紧凑模式下标签自己占的一行。 */
    public static final int LABEL_LINE_H = 10;
    /** 字段区至少要有这么宽，否则把标签列压窄、再不够就进紧凑模式。 */
    public static final int MIN_FIELD_W = 200;
    /** 标签列最小宽度（紧凑模式下为 0，标签走到字段上方）。 */
    public static final int MIN_LABEL_W = 48;
    /** 行内单个控件的兜底最小宽度。 */
    public static final int MIN_CELL_W = 24;
    /** 滚动条槽与内容之间再留一点缝。 */
    public static final int GUTTER_GAP = 4;

    /** 一个页签的按钮框。 */
    public record TabSlot(int x, int y, int w) {
    }

    /** 行内一个单元（控件或纯文字）的排布结果，{@code x} 相对所在行的左边界。 */
    public record CellBox(int index, int row, int x, int width) {
    }

    /** 行的种类：决定行高。 */
    public enum RowKind {
        /** 一行控件（可以有标签）。 */
        ROW,
        /** 小节标题（金色粗体 + 分隔线）。 */
        SECTION,
        /** 整段说明文字（会折行）。 */
        NOTE,
        /** 纯留白。 */
        GAP
    }

    /**
     * 各类界面的尺寸参数。
     *
     * <p>
     * 四个编辑器原本各写一套 {@code USABLE_RATIO / MAX_PANEL_*}，值还不一样；现在统一到
     * {@link #defaults()}，各界面只声明自己没有共性的部分（有没有预览列）。
     *
     * @param headerExtra 标题条下方、页签栏上方的自绘头部高度（地图工具用来放坐标行与偏移控件）；
     *                    编辑器用不到，保持 0
     * @param topStrip    页签栏下方、内容区上方的常驻条高度（地图工具「全部设置」用它放搜索框：
     *                    贴在页签下面、不随内容滚动）；编辑器用不到，保持 0
     */
    public record Config(
            float ratio, int maxPanelW, int maxPanelH, int minPanelW, int minPanelH,
            int labelColW, int previewW, int titleH, int footerH, int headerExtra, int topStrip) {

        /** 面板占屏幕的比例与上下限（文档 §4：按比例算并 clamp）。 */
        public static Config defaults() {
            return new Config(0.92F, 700, 560, 300, 200, 176, 0, TITLE_H, FOOTER_H, 0, 0);
        }

        /** 带右侧预览列（自定义物品 / 自定义方块用）。 */
        public Config previewColumn(int width) {
            return new Config(ratio, maxPanelW, maxPanelH, minPanelW, minPanelH,
                    labelColW, width, titleH, footerH, headerExtra, topStrip);
        }

        /** 标签列宽度上限（长标签会被省略号截断并给悬停全文）。 */
        public Config labelColumn(int width) {
            return new Config(ratio, maxPanelW, maxPanelH, minPanelW, minPanelH,
                    width, previewW, titleH, footerH, headerExtra, topStrip);
        }

        public Config panelSize(float ratio, int maxW, int maxH, int minW, int minH) {
            return new Config(ratio, maxW, maxH, minW, minH, labelColW, previewW, titleH, footerH, headerExtra,
                    topStrip);
        }

        /** 标题条下方预留一段自绘头部（放坐标、状态行这类不属于页签内容的东西）。 */
        public Config headerExtra(int height) {
            return new Config(ratio, maxPanelW, maxPanelH, minPanelW, minPanelH,
                    labelColW, previewW, titleH, footerH, Math.max(0, height), topStrip);
        }

        /** 页签栏下方预留一条常驻控件带（搜索框这类要一直看得见的东西）。 */
        public Config topStrip(int height) {
            return new Config(ratio, maxPanelW, maxPanelH, minPanelW, minPanelH,
                    labelColW, previewW, titleH, footerH, headerExtra, Math.max(0, height));
        }
    }

    /**
     * 计算一屏的布局。
     *
     * @param width         屏幕宽（GUI 像素）
     * @param height        屏幕高（GUI 像素）
     * @param cfg           尺寸参数
     * @param tabWidths     每个页签按文字实测的期望宽度（字数不一样的界面传入不同长度）
     * @param maxLabelWidth 当前页签里最宽的字段标签实测宽度，用来决定标签列宽
     */
    public static EditorLayout of(int width, int height, Config cfg, int[] tabWidths, int maxLabelWidth) {
        int availW = Math.max(1, width);
        int availH = Math.max(1, height);

        // ── 面板：按比例算，再 clamp 进屏幕。最小尺寸同样不能超过屏幕，否则面板会溢出 ──
        int maxW = Math.max(1, Math.min(cfg.maxPanelW(), availW - 8));
        int panelW = (int) (availW * cfg.ratio());
        panelW = Math.min(panelW, maxW);
        panelW = Math.max(panelW, Math.min(cfg.minPanelW(), maxW));
        panelW = Math.max(1, panelW);

        int maxH = Math.max(1, Math.min(cfg.maxPanelH(), availH - 4));
        int panelH = (int) (availH * cfg.ratio());
        panelH = Math.min(panelH, maxH);
        panelH = Math.max(panelH, Math.min(cfg.minPanelH(), maxH));
        panelH = Math.max(1, panelH);

        int panelX = (availW - panelW) / 2;
        int panelY = (availH - panelH) / 2;

        // ── 标题条 ──
        int titleX = panelX + PAD;
        int titleY = panelY + 4;
        int titleH = Math.max(0, cfg.titleH());

        // ── 页签栏：宽度不够就折行，内容区随行数下移 ──
        // 标题条与页签栏之间可以再留一段自绘头部（Config.headerExtra），地图工具用它放坐标行与偏移控件
        int firstTabY = panelY + titleH + Math.max(0, cfg.headerExtra()) + 4;
        int tabsLeft = panelX + PAD;
        int tabsMaxW = Math.max(1, panelW - PAD * 2);

        List<TabSlot> tabs = new ArrayList<>();
        int tabRows = 0;
        if (tabWidths != null && tabWidths.length > 0) {
            int row = 0;
            int used = 0;
            for (int tabWidth : tabWidths) {
                int w = Math.max(MIN_CELL_W, Math.min(tabWidth, tabsMaxW));
                if (used > 0 && used + GAP + w > tabsMaxW) {
                    row++;
                    used = 0;
                }
                int x = tabsLeft + (used == 0 ? 0 : used + GAP);
                tabs.add(new TabSlot(x, firstTabY + row * TAB_ROW_H, w));
                used = used == 0 ? w : used + GAP + w;
            }
            tabRows = row + 1;
        }
        int tabH = tabRows == 0 ? 0 : TAB_H;

        // 页签栏下方可以再留一条常驻控件带（Config.topStrip）：搜索框这类控件贴在页签下面、
        // 不随内容滚动，内容区整体让开这一段（裁剪、滚动、滚动条槽都跟着下移）。
        int stripH = Math.max(0, cfg.topStrip());
        int contentY = firstTabY + tabRows * TAB_ROW_H + (tabRows == 0 ? 0 : GAP) + stripH;
        int contentBottom = panelY + panelH - cfg.footerH();

        // ── 滚动条槽：面板内右侧，永久预留（内容不会画到滚动条底下，滚动条也不会跑到面板外）──
        int sbX = panelX + panelW - PAD - SCROLL_W;
        int sbTop = contentY;
        int sbH = Math.max(1, contentBottom - contentY);
        int usableLeft = panelX + PAD;
        int usableRight = Math.max(usableLeft + 1, sbX - GUTTER_GAP);

        // ── 预览列：只有放得下（不挤坏字段区）才保留，否则由界面改成内容区里的一行 ──
        int previewW = 0;
        if (cfg.previewW() > 0) {
            int room = usableRight - usableLeft;
            int needed = cfg.previewW() + GAP + MIN_LABEL_W + GAP + MIN_FIELD_W;
            if (room >= needed) {
                previewW = cfg.previewW();
            }
        }
        // 字段区右边界（预览列左侧）；内容区右边界仍是 usableRight —— 裁剪区域必须把预览列包进去
        int contentRight = usableRight - (previewW > 0 ? previewW + GAP : 0);
        int contentW = Math.max(1, usableRight - usableLeft);

        // ── 标签列：按当前页签最宽标签实测宽度决定；压到最小仍挤不出字段区就进紧凑模式 ──
        int labelW = clamp(maxLabelWidth <= 0 ? 0 : maxLabelWidth + GAP, MIN_LABEL_W, Math.max(MIN_LABEL_W, cfg.labelColW()));
        boolean compact = false;
        if (contentW - labelW - GAP < MIN_FIELD_W) {
            int squeezed = contentW - GAP - MIN_FIELD_W;
            labelW = Math.max(MIN_LABEL_W, Math.min(labelW, squeezed));
            if (contentW - labelW - GAP < MIN_FIELD_W) {
                compact = true;
                labelW = 0;
            }
        }
        int fieldX = usableLeft + (compact ? 0 : labelW + GAP);
        int fieldW = Math.max(1, contentRight - fieldX);

        // ── 预览列的位置（贴内容区右侧，正方形区域放不下就只留出高度）──
        int previewX = previewW > 0 ? usableRight - previewW : 0;
        int previewY = previewW > 0 ? contentY + 2 : 0;
        int previewH = previewW > 0 ? Math.min(previewW, Math.max(0, contentBottom - previewY)) : 0;

        return new EditorLayout(
                panelX, panelY, panelW, panelH,
                titleX, titleY, titleH,
                tabH, tabRows, List.copyOf(tabs),
                usableLeft, contentY, contentW, Math.max(1, contentBottom - contentY), contentBottom,
                labelW, fieldX, fieldW, compact,
                sbX, sbTop, sbH,
                previewX, previewY, previewW, previewH,
                stripH);
    }

    /**
     * 字段区右边界，也是说明文字换行的右边界。
     *
     * <p>
     * 不等于 {@code contentX + contentW}（那是含预览列的裁剪区右边界）：文字与字段一律停在预览列左侧，
     * 不会被预览盖住，也不会戳进滚动条槽。
     */
    public int contentRight() {
        return fieldX + fieldW;
    }

    /**
     * 自绘头部区域的上边界（标题条下方）。
     *
     * <p>
     * 只有当 {@code Config.headerExtra > 0} 时这段才有高度；地图工具在这里画「坐标来源 / 生效坐标」
     * 两行文字与 dx/dy/dz 控件。区域是 {@code [headerTop(), headerBottom())}，夹在标题条与页签栏之间，
     * 因此不会和标题、页签或滚动内容重叠。
     */
    public int headerTop() {
        return titleY + titleH + 2;
    }

    /** 自绘头部区域的下边界（页签栏上方；没有页签时就是内容区上方）。 */
    public int headerBottom() {
        return (tabs.isEmpty() ? contentY - stripH : tabs.get(0).y()) - 2;
    }

    /**
     * 页签栏下方的常驻控件带（{@code Config.topStrip}），区域是 {@code [stripTop(), contentY)}。
     *
     * <p>
     * 高度为 0 时这一段不存在（返回的 top 等于 {@link #contentY()}）。搜索框这类「要一直看得见」的
     * 控件放这里：贴在页签按钮下面、不随内容滚动，内容区从它下面开始排，两边不会互相盖住。
     */
    public int stripTop() {
        return contentY - stripH;
    }

    /** 整段说明文字可用的宽度（从左内边距一直到字段区右边界，紧凑模式下与字段区等宽）。 */
    public int textW() {
        return Math.max(1, contentRight() - contentX);
    }

    /** 提示文字的行高（字体 9 + 1 余量，与 {@code HintText.LINE_H} 一致）。 */
    public static final int LINE_H = 10;

    /**
     * 行高。
     *
     * @param kind         行种类
     * @param compact      是否紧凑模式（标签另占一行）
     * @param physicalRows 该行实际占用的控件行数（行内控件折行后 &gt; 1）
     * @param measuredLines NOTE 行的折行数（由调用方用字体量好）
     */
    public static int rowHeight(RowKind kind, boolean compact, int physicalRows, int measuredLines) {
        int rows = Math.max(1, physicalRows);
        return switch (kind) {
            case ROW -> (compact ? LABEL_LINE_H : 0) + rows * ROW_H;
            case SECTION -> LINE_H + 4 + 4;
            case NOTE -> Math.max(1, measuredLines) * LINE_H + 2;
            case GAP -> 8;
        };
    }

    /** 控件在行内的 Y 偏移（紧凑模式下标签占了一行，控件要往下让）。 */
    public static int widgetOffsetY(RowKind kind, boolean compact) {
        return kind == RowKind.ROW && compact ? LABEL_LINE_H : 0;
    }

    /** 标签文字的 Y 偏移（非紧凑时与 18 高的控件垂直居中）。 */
    public static int labelOffsetY(boolean compact) {
        return compact ? 0 : (WIDGET_H - LINE_H) / 2 + 1;
    }

    /**
     * 把一行里的若干单元排进可用宽度，放不下就折行（贪心装箱）。
     *
     * <p>
     * 这是「一行里放好几个控件」的统一实现：以前每个界面都写
     * {@code fieldX() + 116}、{@code fieldX() + 332} 这类写死偏移，窄屏时直接戳出面板；
     * 现在按最小宽度装箱，装不下就换到下一行，字段区多窄都不会越界。
     *
     * @param available 可用宽度
     * @param gap       单元间距
     * @param minW      每个单元的最小宽度（固定宽度单元就是它的实际宽度）
     * @param maxW      每个单元的最大宽度（{@code <=0} 表示不限）
     * @param weights   每个单元的弹性权重；{@code <=0} 表示固定宽度、不参与拉伸
     * @return 每个单元的位置；{@code x} 相对行左边界，{@code row} 从 0 开始（0 就是本行）
     */
    public static List<CellBox> pack(int available, int gap, int[] minW, int[] maxW, float[] weights) {
        List<CellBox> out = new ArrayList<>();
        int n = minW == null ? 0 : minW.length;
        if (n == 0) {
            return out;
        }
        int avail = Math.max(1, available);

        // 1) 贪心分行：按最小宽度累加，装不下就开新行
        List<int[]> bands = new ArrayList<>();
        int start = 0;
        while (start < n) {
            int end = start;
            int used = 0;
            while (end < n) {
                int need = Math.max(1, minW[end]);
                int total = used == 0 ? need : used + gap + need;
                if (used > 0 && total > avail) {
                    break;
                }
                used = total;
                end++;
            }
            if (end == start) {
                end = start + 1;
            }
            bands.add(new int[] { start, end });
            start = end;
        }

        // 2) 每个物理行内部按权重分配宽度，右边界一律截在 avail 之内
        for (int band = 0; band < bands.size(); band++) {
            int from = bands.get(band)[0];
            int to = bands.get(band)[1];
            int count = to - from;
            int budget = Math.max(count, avail - gap * (count - 1));
            int fixed = 0;
            float weightSum = 0F;
            for (int i = from; i < to; i++) {
                float weight = weights != null && i < weights.length ? weights[i] : 0F;
                if (weight > 0F) {
                    weightSum += weight;
                } else {
                    fixed += Math.max(1, minW[i]);
                }
            }
            int flexRoom = Math.max(0, budget - fixed);
            int x = 0;
            for (int i = from; i < to; i++) {
                float weight = weights != null && i < weights.length ? weights[i] : 0F;
                int width;
                if (weight > 0F && weightSum > 0F) {
                    width = Math.round(flexRoom * (weight / weightSum));
                    int cap = maxW != null && i < maxW.length && maxW[i] > 0 ? maxW[i] : Integer.MAX_VALUE;
                    width = Math.max(Math.max(1, minW[i]), Math.min(cap, width));
                } else {
                    width = Math.max(1, minW[i]);
                }
                if (x + width > avail) {
                    width = Math.max(1, avail - x);
                }
                out.add(new CellBox(i, band, x, width));
                x += width + gap;
            }
        }
        return out;
    }

    /** 内容总高超出视口时的最大滚动距离。 */
    public static int maxScroll(int contentHeight, int viewportHeight) {
        return Math.max(0, contentHeight - Math.max(1, viewportHeight));
    }

    /**
     * 滚动条 thumb 的高度：可视部分占内容总高的比例。
     *
     * <p>
     * 绘制与拖动共用这一个结果 —— 以前拖动那边用 {@code SCROLL_MIN_THUMB} 当 thumb 高，
     * thumb 画得比它高的时候位置就对不上了（不跟手）。
     */
    public static int thumbHeight(int trackH, int maxScroll) {
        int track = Math.max(1, trackH);
        int total = track + Math.max(0, maxScroll);
        int height = Math.round(track * ((float) track / total));
        return Math.max(SCROLL_MIN_THUMB, Math.min(track, height));
    }

    /** 滚动条 thumb 顶端 Y。 */
    public static int thumbY(int trackY, int trackH, int thumbH, float scroll, int maxScroll) {
        if (maxScroll <= 0) {
            return trackY;
        }
        int track = Math.max(0, trackH - thumbH);
        return trackY + Math.round(track * (Math.max(0F, Math.min(maxScroll, scroll)) / maxScroll));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
