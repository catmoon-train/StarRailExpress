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

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;
import io.netty.buffer.Unpooled;
import io.wifi.starrailexpress.client.gui.widget.OrbitPreviewWidget;
import io.wifi.starrailexpress.client.network.DisplayBlockClientNetwork;
import io.wifi.starrailexpress.content.block_entity.DisplayAnimation;
import io.wifi.starrailexpress.content.block_entity.DisplayBlockEntityBase;
import io.wifi.starrailexpress.content.block_entity.TextDisplayBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 展示方块编辑界面基类。
 *
 * <p>界面分页签（内容 / 变换 / 外观 / 高级），内容区可以滚动，右侧固定一列实时预览，
 * 字段多也不会挤在一起。风格按 {@code docs/ui_style.md}：深棕底 + 棕褐描边 + 金色强调。
 *
 * <p>数据只有一个来源：{@link #display} 这个 CompoundTag（键名与原版展示实体一致），
 * 所有控件都直接读写它，保存时整体发回服务端。输入框只在能解析成功时写回 NBT，
 * 所以打字中途（空串、"1."、"-"）不会被自己回写打断。
 */
public abstract class DisplayBlockScreenBase extends Screen {

    // ───────── docs/ui_style.md 的色板 ─────────
    protected static final int PANEL_BG_TOP = 0xD81A1008;
    protected static final int PANEL_BG_BOTTOM = 0xD820140A;
    protected static final int PANEL_OUTLINE = 0xFF8B6914;
    protected static final int DECOR_LINE = 0x33FFE8C0;
    protected static final int COLOR_TEXT = 0xFFFFF4DC;
    protected static final int COLOR_MUTED = 0xFF9E8B6E;
    protected static final int COLOR_GOLD = 0xFFD4AF37;
    protected static final int COLOR_BLUE = 0xFF5EB7D8;
    protected static final int COLOR_GREEN = 0xFF72C17B;
    protected static final int COLOR_RED = 0xFFE06B65;
    protected static final int CARD_BORDER = 0xFF5A4530;
    protected static final int CARD_BG = 0x88120A04;
    protected static final int ROW_SEPARATOR = 0x20FFFFFF;

    protected static final int PAD = 8;
    protected static final int ROW_H = 22;
    private static final int TAB_H = 18;
    private static final int TITLE_H = 24;
    private static final int FOOTER_H = 30;
    private static final int PREVIEW_W = 196;
    protected static final int LABEL_W = 96;
    private static final int WIDGET_H = 18;
    private static final int SCROLL_STEP = 20;
    /**
     * 原版 {@code ServerboundCustomPayloadPacket.MAX_PAYLOAD_SIZE} 是 32767 字节，
     * 超过的自定义包会被直接丢弃（还可能把人踢掉），所以这里留点余量做前置拦截。
     */
    protected static final int MAX_SAVE_PAYLOAD_BYTES = 30000;
    /** 编辑框字符上限：全中文按 3 字节/字符算也不超过 {@link #MAX_SAVE_PAYLOAD_BYTES}。 */
    protected static final int EDITOR_CHAR_LIMIT = 8192;

    protected final BlockPos pos;
    protected CompoundTag display;

    protected enum Tab {
        CONTENT("gui.display_block.tab.content"),
        TRANSFORM("gui.display_block.tab.transform"),
        APPEARANCE("gui.display_block.tab.appearance"),
        ANIMATION("gui.display_block.tab.animation"),
        ADVANCED("gui.display_block.tab.advanced");

        private final String translationKey;

        Tab(String translationKey) {
            this.translationKey = translationKey;
        }

        public Component label() {
            return Component.translatable(this.translationKey);
        }
    }

    private Tab tab = Tab.CONTENT;
    private final Map<Tab, Button> tabButtons = new IdentityHashMap<>();

    // 布局
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int tabBarY;
    private int contentLeft;
    private int contentTop;
    private int contentBottom;
    private int contentWidth;
    private int previewLeft;

    // 滚动与登记控件
    private double scroll;
    private int cursor;
    private int usedHeight;
    private boolean rebuildRequested;
    private final List<AbstractWidget> scrolledWidgets = new ArrayList<>();
    private final Map<AbstractWidget, Integer> scrolledBaseY = new IdentityHashMap<>();
    private final List<RowLabel> labels = new ArrayList<>();
    private final List<int[]> separators = new ArrayList<>();
    /** 子类也可以往里登记"每帧同步一次"的动作（比如编辑框文本写回 NBT）。 */
    protected final List<Runnable> pendingFields = new ArrayList<>();

    // 变换本地值（改了直接重组 Transformation 写回 NBT）
    private float translateX;
    private float translateY;
    private float translateZ;
    private float scaleX;
    private float scaleY;
    private float scaleZ;
    private float rotateX;
    private float rotateY;
    private float rotateZ;
    /** 变换只在真的改过时才重组并写回 NBT，避免每帧跑一遍编码器。 */
    private boolean transformDirty;

    private boolean brightnessOverride;
    private int brightnessBlock;
    private int brightnessSky;

    /** 保存失败的原因（比如内容超过自定义包上限），显示在底部。 */
    private Component saveError;

    // ───────── 关键帧动画编辑状态（只在动画页签用） ─────────
    /** 编辑器侧的关键帧副本，改动后打包写回 {@link #display}。 */
    private final List<DisplayAnimation.Keyframe> frames = new ArrayList<>();
    private int selectedFrame = -1;
    private boolean framesDirty;
    private boolean framesLoaded;
    // 选中关键帧的字段镜像
    private int frameStart;
    private float frameTranslateX;
    private float frameTranslateY;
    private float frameTranslateZ;
    private float frameScaleX = 1.0F;
    private float frameScaleY = 1.0F;
    private float frameScaleZ = 1.0F;
    private float frameRotateX;
    private float frameRotateY;
    private float frameRotateZ;
    private int frameOpacity = 0xFF;
    private int frameBackground = TextDisplayBlockEntity.DEFAULT_BACKGROUND;
    private String frameText = "";

    private MultiLineEditBox snbtEditor;
    private String snbtError;
    private int snbtStatusY;
    private String snbtAppliedSnapshot;

    protected record RowLabel(Component text, int x, int baseY, int color, boolean bold) {
    }

    /** 一行里的一组"标签 + 输入框"。 */
    protected record FieldSpec(Component label, String value, int maxLength, Consumer<String> apply) {
    }

    protected DisplayBlockScreenBase(Component title, BlockPos pos, CompoundTag data) {
        super(title);
        this.pos = pos;
        this.display = data.copy();
    }

    // ───────────────────────── 生命周期 ─────────────────────────

    @Override
    protected void init() {
        computeLayout();
        this.cursor = 0;
        this.usedHeight = 0;
        this.labels.clear();
        this.separators.clear();
        this.scrolledWidgets.clear();
        this.scrolledBaseY.clear();
        this.pendingFields.clear();
        this.tabButtons.clear();
        this.scroll = 0.0D;
        this.snbtAppliedSnapshot = null;

        loadTransformFields();
        loadBrightnessFields();
        // 关键帧只在动画页签需要。注意这里**不**重置 framesLoaded：
        // 切换选中关键帧、添加/删除都会触发重建，如果每次都从 NBT 重读，
        // loadFrames() 会把选中项归零，表现就是"点哪一帧都跳回第一帧"。
        // framesLoaded 只在真正进入动画页签、或 display 被整体替换时才清掉。
        if (this.tab == Tab.ANIMATION) {
            loadFrames();
        }

        buildTabBar();
        // 右侧预览框是所有页签共用的，里面自带"信息 / 3D"两个小页签
        buildPreviewBox();
        switch (this.tab) {
            case CONTENT -> buildContentTab();
            case TRANSFORM -> buildTransformTab();
            case APPEARANCE -> buildAppearanceTab();
            case ANIMATION -> buildAnimationTab();
            case ADVANCED -> buildAdvancedTab();
        }
        buildFooter();
        applyScroll();
    }

    private void computeLayout() {
        this.panelW = Math.max(420, Math.min(780, (int) (this.width * 0.94F)));
        this.panelH = Math.max(240, Math.min(460, (int) (this.height * 0.92F)));
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = (this.height - this.panelH) / 2;
        this.tabBarY = this.panelY + TITLE_H;
        this.contentLeft = this.panelX + PAD;
        this.contentTop = this.tabBarY + TAB_H + 4;
        this.contentBottom = this.panelY + this.panelH - FOOTER_H;
        this.previewLeft = this.panelX + this.panelW - PAD - PREVIEW_W;
        this.contentWidth = this.previewLeft - 6 - this.contentLeft;
    }

    /**
     * 重建控件。不能在按钮回调里直接重建——那时界面正在遍历自己的控件列表，
     * 清空会撞上并发修改；所以这里只打标记，下一帧渲染前再重建。
     */
    private void requestRebuild() {
        this.rebuildRequested = true;
    }

    private void rebuildNow() {
        // 先提交输入框，再重建：否则同帧的编辑会被 init() 重新读 NBT 覆盖掉。
        applyPendingFields();
        this.clearWidgets();
        this.clearFocus();
        this.init();
    }

    private void switchTab(Tab target) {
        if (target == this.tab) {
            return;
        }
        applyPendingFields();
        flushFrames();
        if (this.tab == Tab.ADVANCED) {
            // 离开高级页先把 SNBT 的改动吃掉，免得用户忘了点"应用"。
            applySnbtEdits();
        }
        this.tab = target;
        // 换页签时让关键帧下次从 NBT 重新载入（内存里的副本属于上一个页签的编辑过程）。
        this.framesLoaded = false;
        requestRebuild();
    }

    // ───────────────────────── 页签与底部 ─────────────────────────

    private void buildTabBar() {
        int x = this.contentLeft;
        for (Tab candidate : Tab.values()) {
            int width = this.font.width(candidate.label()) + 18;
            Button button = Button.builder(candidate.label(), b -> switchTab(candidate))
                    .bounds(x, this.tabBarY, width, TAB_H)
                    .build();
            button.active = candidate != this.tab;
            addRenderableWidget(button);
            this.tabButtons.put(candidate, button);
            x += width + 4;
        }
    }

    private void buildFooter() {
        int buttonY = this.panelY + this.panelH - FOOTER_H + 5;
        int width = 76;
        int right = this.panelX + this.panelW - PAD;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> saveAndClose())
                .bounds(right - width * 2 - 6, buttonY, width, WIDGET_H + 2).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(right - width, buttonY, width, WIDGET_H + 2).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.display_block.reset"),
                b -> resetToDefaults())
                .bounds(this.contentLeft, buttonY, 70, WIDGET_H + 2).build());
    }

    /** 一键清空所有展示键，等于回到默认值。 */
    private void resetToDefaults() {
        this.display = new CompoundTag();
        // display 被整体换掉了，内存里的关键帧副本作废，下次进入动画页签重新读。
        this.frames.clear();
        this.selectedFrame = -1;
        this.framesLoaded = false;
        requestRebuild();
    }

    private void saveAndClose() {
        applyPendingFields();
        flushFrames();
        if (this.tab == Tab.ADVANCED) {
            applySnbtEdits();
        }
        int encodedSize = encodedSaveSize();
        if (encodedSize > MAX_SAVE_PAYLOAD_BYTES) {
            // 拦在这里比让包被网络层丢掉强（超限的 C2S 自定义包会被直接丢弃）。
            this.saveError = Component.translatable("gui.display_block.save_too_large",
                    encodedSize / 1024, MAX_SAVE_PAYLOAD_BYTES / 1024);
            return;
        }
        this.saveError = null;
        DisplayBlockClientNetwork.sendSave(this.pos, this.display);
        onClose();
    }

    /**
     * 展示数据编码成包体之后的字节数。只在点保存时算一次，
     * 用来挡住超过原版自定义包上限的内容。
     */
    private int encodedSaveSize() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeNbt(this.display);
            return buffer.readableBytes();
        } finally {
            buffer.release();
        }
    }

    // ───────────────────────── 行布局工具 ─────────────────────────

    protected void advance(int height) {
        this.cursor += height;
        this.usedHeight = Math.max(this.usedHeight, this.cursor);
    }

    /** 当前行的基准 Y（屏幕坐标，未减滚动偏移）。 */
    protected int rowBaseY() {
        return this.contentTop + this.cursor;
    }

    protected int contentLeft() {
        return this.contentLeft;
    }

    protected int contentWidth() {
        return this.contentWidth;
    }

    protected int contentTop() {
        return this.contentTop;
    }

    protected int contentBottom() {
        return this.contentBottom;
    }

    /** 当前页签。子类在 render 里要用它判断自己那一页的控件是否还在。 */
    protected Tab activeTab() {
        return this.tab;
    }

    protected int previewLeft() {
        return this.previewLeft;
    }

    protected <T extends AbstractWidget> T scrollable(T widget, int baseY) {
        addRenderableWidget(widget);
        this.scrolledWidgets.add(widget);
        this.scrolledBaseY.put(widget, baseY);
        return widget;
    }

    protected void addLabel(Component text, int x, int baseY, int color) {
        this.labels.add(new RowLabel(text, x, baseY, color, false));
    }

    protected void addBoldLabel(Component text, int x, int baseY, int color) {
        this.labels.add(new RowLabel(text, x, baseY, color, true));
    }

    /** 提示性小字，占一行的部分高度。 */
    protected void hint(String labelKey) {
        addLabel(Component.translatable(labelKey), this.contentLeft, rowBaseY() + 2, COLOR_MUTED);
        advance(14);
    }

    /** 小节标题：金色粗体 + 分隔线。 */
    protected void section(String labelKey) {
        int baseY = rowBaseY();
        addBoldLabel(Component.translatable(labelKey), this.contentLeft, baseY + 4, COLOR_GOLD);
        this.separators.add(new int[] { baseY + 16, this.contentLeft, this.contentWidth });
        advance(ROW_H - 2);
    }

    protected EditBox createBox(int offsetX, int width, String value, int maxLength) {
        EditBox box = new EditBox(this.font, this.contentLeft + offsetX, 0, width, WIDGET_H, Component.empty());
        box.setValue(value);
        box.setMaxLength(maxLength);
        box.setTextColor(COLOR_TEXT);
        box.setTextColorUneditable(COLOR_MUTED);
        return box;
    }

    /**
     * 标签要占多宽：按文字实际宽度算（外加一点间距），再保证至少留出 {@code minBoxWidth} 给控件。
     * 以前是写死的固定值，结果"不透明度(0-255)"这类长标签会压到输入框上。
     */
    protected int labelWidthFor(Component label, int available, int minBoxWidth) {
        int textWidth = this.font.width(label) + 4;
        return Math.max(20, Math.min(textWidth, Math.max(20, available - minBoxWidth)));
    }

    /** 一行放若干组"标签 + 输入框"，按列平分内容区宽度。 */
    protected void fieldRow(FieldSpec... specs) {
        if (specs.length == 0) {
            return;
        }
        int columnWidth = this.contentWidth / specs.length;
        int baseY = rowBaseY();
        for (int i = 0; i < specs.length; i++) {
            FieldSpec spec = specs[i];
            int columnX = this.contentLeft + i * columnWidth;
            int labelWidth = labelWidthFor(spec.label(), columnWidth, 44);
            addLabel(spec.label(), columnX, baseY + 5, COLOR_TEXT);
            int boxWidth = Math.max(24, columnWidth - labelWidth - 4);
            EditBox box = createBox(columnX + labelWidth - this.contentLeft, boxWidth, spec.value(),
                    spec.maxLength());
            box.setY(baseY);
            scrollable(box, baseY);
            // 记住"上次提交过的文本"：内容没变就不再解析、不再写 NBT。
            // 否则每帧都会给标签塞一遍值，白造一堆 NBT 对象。
            String[] lastSubmitted = { spec.value() };
            this.pendingFields.add(() -> {
                String raw = box.getValue().trim();
                if (raw.isEmpty() || raw.equals(lastSubmitted[0])) {
                    return;
                }
                lastSubmitted[0] = raw;
                spec.apply().accept(raw);
            });
        }
        advance(ROW_H);
    }

    /** 一行只放一个控件，前面带长标签（标签多宽按文字实际宽度算）。 */
    protected void wideRow(Component label, AbstractWidget widget) {
        int baseY = rowBaseY();
        addLabel(label, this.contentLeft, baseY + 5, COLOR_TEXT);
        int labelWidth = labelWidthFor(label, this.contentWidth, 60);
        widget.setX(this.contentLeft + labelWidth);
        widget.setY(baseY);
        widget.setWidth(Math.max(60, this.contentWidth - labelWidth - 4));
        scrollable(widget, baseY);
        advance(ROW_H);
    }

    /** 一行平铺若干个控件（按钮之类），按数量平分内容区宽度。 */
    protected void widgetRow(AbstractWidget... widgets) {
        if (widgets.length == 0) {
            return;
        }
        int columnWidth = this.contentWidth / widgets.length;
        int baseY = rowBaseY();
        for (int i = 0; i < widgets.length; i++) {
            AbstractWidget widget = widgets[i];
            widget.setX(this.contentLeft + i * columnWidth);
            widget.setY(baseY);
            widget.setWidth(Math.max(30, columnWidth - 4));
            scrollable(widget, baseY);
        }
        advance(ROW_H);
    }

    protected void spacer(int height) {
        advance(height);
    }

    /** 提交所有输入框里的改动（解析失败就跳过，避免打字被打断）。 */
    protected void applyPendingFields() {
        for (Runnable action : this.pendingFields) {
            action.run();
        }
    }

    // ───────────────────────── 变换 / 亮度 ─────────────────────────

    private void loadTransformFields() {
        Transformation transformation = DisplayBlockEntityBase.readTransformation(this.display);
        Vector3f translation = transformation.getTranslation();
        Vector3f scale = transformation.getScale();
        this.translateX = translation.x();
        this.translateY = translation.y();
        this.translateZ = translation.z();
        this.scaleX = scale.x();
        this.scaleY = scale.y();
        this.scaleZ = scale.z();

        Vector3f euler = new Quaternionf(transformation.getLeftRotation()).getEulerAnglesYXZ(new Vector3f());
        this.rotateY = (float) Math.toDegrees(euler.y());
        this.rotateX = (float) Math.toDegrees(euler.x());
        this.rotateZ = (float) Math.toDegrees(euler.z());
    }

    private void writeTransformFields() {
        if (!this.transformDirty) {
            // 没有实际改动就别重组：编码器会造一串 NBT 对象。
            return;
        }
        this.transformDirty = false;
        Quaternionf rotation = new Quaternionf().rotationYXZ(
                (float) Math.toRadians(this.rotateY),
                (float) Math.toRadians(this.rotateX),
                (float) Math.toRadians(this.rotateZ));
        Transformation transformation = new Transformation(
                new Vector3f(this.translateX, this.translateY, this.translateZ),
                rotation,
                new Vector3f(this.scaleX, this.scaleY, this.scaleZ),
                new Quaternionf());
        DisplayBlockEntityBase.writeTransformation(this.display, transformation);
    }

    private void setTranslate(float value, int axis) {
        if (axis == 0 && value != this.translateX) {
            this.translateX = value;
            this.transformDirty = true;
        } else if (axis == 1 && value != this.translateY) {
            this.translateY = value;
            this.transformDirty = true;
        } else if (axis == 2 && value != this.translateZ) {
            this.translateZ = value;
            this.transformDirty = true;
        }
    }

    private void setScale(float value, int axis) {
        if (axis == 0 && value != this.scaleX) {
            this.scaleX = value;
            this.transformDirty = true;
        } else if (axis == 1 && value != this.scaleY) {
            this.scaleY = value;
            this.transformDirty = true;
        } else if (axis == 2 && value != this.scaleZ) {
            this.scaleZ = value;
            this.transformDirty = true;
        }
    }

    private void setRotate(float value, int axis) {
        if (axis == 0 && value != this.rotateX) {
            this.rotateX = value;
            this.transformDirty = true;
        } else if (axis == 1 && value != this.rotateY) {
            this.rotateY = value;
            this.transformDirty = true;
        } else if (axis == 2 && value != this.rotateZ) {
            this.rotateZ = value;
            this.transformDirty = true;
        }
    }

    private void loadBrightnessFields() {
        CompoundTag brightness = this.display.getCompound(DisplayBlockEntityBase.TAG_BRIGHTNESS);
        this.brightnessOverride = !brightness.isEmpty();
        this.brightnessBlock = brightness.contains("block") ? brightness.getInt("block") : 15;
        this.brightnessSky = brightness.contains("sky") ? brightness.getInt("sky") : 15;
    }

    private void writeBrightnessFields() {
        if (this.brightnessOverride) {
            CompoundTag brightness = new CompoundTag();
            brightness.putInt("block", Math.max(0, Math.min(15, this.brightnessBlock)));
            brightness.putInt("sky", Math.max(0, Math.min(15, this.brightnessSky)));
            this.display.put(DisplayBlockEntityBase.TAG_BRIGHTNESS, brightness);
        } else {
            this.display.remove(DisplayBlockEntityBase.TAG_BRIGHTNESS);
        }
    }

    // ───────────────────────── 共享页签 ─────────────────────────

    private void buildTransformTab() {
        fieldRow(
                floatField(Component.translatable("gui.display_block.translate_x"), this.translateX,
                        v -> setTranslate(v, 0)),
                floatField(Component.translatable("gui.display_block.translate_y"), this.translateY,
                        v -> setTranslate(v, 1)),
                floatField(Component.translatable("gui.display_block.translate_z"), this.translateZ,
                        v -> setTranslate(v, 2)));
        fieldRow(
                floatField(Component.translatable("gui.display_block.scale_x"), this.scaleX,
                        v -> setScale(v, 0)),
                floatField(Component.translatable("gui.display_block.scale_y"), this.scaleY,
                        v -> setScale(v, 1)),
                floatField(Component.translatable("gui.display_block.scale_z"), this.scaleZ,
                        v -> setScale(v, 2)));
        fieldRow(
                floatField(Component.translatable("gui.display_block.rotate_x"), this.rotateX,
                        v -> setRotate(v, 0)),
                floatField(Component.translatable("gui.display_block.rotate_y"), this.rotateY,
                        v -> setRotate(v, 1)),
                floatField(Component.translatable("gui.display_block.rotate_z"), this.rotateZ,
                        v -> setRotate(v, 2)));
        hint("gui.display_block.rotate_hint");
        hint("gui.display_block.anchor_hint");
        // 只有变换页签会注册这个动作，离开页签就不会再碰 transformation 键。
        this.pendingFields.add(this::writeTransformFields);

        spacer(4);
        section("gui.display_block.section.billboard");
        wideRow(Component.translatable("gui.display_block.billboard"),
                cycleButton(DisplayBlockEntityBase.readString(this.display, DisplayBlockEntityBase.TAG_BILLBOARD,
                                Display.BillboardConstraints.FIXED.getSerializedName()),
                        BILLBOARDS,
                        "gui.display_block.billboard.",
                        value -> this.display.putString(DisplayBlockEntityBase.TAG_BILLBOARD, value)));

        spacer(4);
        section("gui.display_block.section.interpolation");
        fieldRow(
                intField(Component.translatable("gui.display_block.interpolation_duration"),
                        DisplayBlockEntityBase.readInt(this.display,
                                DisplayBlockEntityBase.TAG_INTERPOLATION_DURATION, 0),
                        v -> this.display.putInt(DisplayBlockEntityBase.TAG_INTERPOLATION_DURATION, Math.max(0, v))),
                intField(Component.translatable("gui.display_block.start_interpolation"),
                        DisplayBlockEntityBase.readInt(this.display,
                                DisplayBlockEntityBase.TAG_START_INTERPOLATION, 0),
                        v -> this.display.putInt(DisplayBlockEntityBase.TAG_START_INTERPOLATION, Math.max(0, v))));
        hint("gui.display_block.interpolation_hint");
    }

    private static final List<String> BILLBOARDS = List.of("fixed", "vertical", "horizontal", "center");
    private static final List<String> BRIGHTNESS_MODES = List.of("none", "override");

    private void buildAppearanceTab() {
        buildAppearanceFields();

        spacer(4);
        section("gui.display_block.section.brightness");
        wideRow(Component.translatable("gui.display_block.brightness"),
                cycleButton(this.brightnessOverride ? "override" : "none", BRIGHTNESS_MODES,
                        "gui.display_block.brightness_mode.", value -> {
                            this.brightnessOverride = "override".equals(value);
                            writeBrightnessFields();
                        }));
        fieldRow(
                intField(Component.translatable("gui.display_block.brightness_block"), this.brightnessBlock, v -> {
                    this.brightnessBlock = v;
                    writeBrightnessFields();
                }),
                intField(Component.translatable("gui.display_block.brightness_sky"), this.brightnessSky, v -> {
                    this.brightnessSky = v;
                    writeBrightnessFields();
                }));

        spacer(4);
        section("gui.display_block.section.culling");
        fieldRow(floatField(Component.translatable("gui.display_block.view_range"),
                DisplayBlockEntityBase.readFloat(this.display, DisplayBlockEntityBase.TAG_VIEW_RANGE,
                        DisplayBlockEntityBase.DEFAULT_VIEW_RANGE),
                v -> this.display.putFloat(DisplayBlockEntityBase.TAG_VIEW_RANGE, v)));
        fieldRow(
                floatField(Component.translatable("gui.display_block.width"),
                        DisplayBlockEntityBase.readFloat(this.display, DisplayBlockEntityBase.TAG_WIDTH, 0.0F),
                        v -> this.display.putFloat(DisplayBlockEntityBase.TAG_WIDTH, v)),
                floatField(Component.translatable("gui.display_block.height"),
                        DisplayBlockEntityBase.readFloat(this.display, DisplayBlockEntityBase.TAG_HEIGHT, 0.0F),
                        v -> this.display.putFloat(DisplayBlockEntityBase.TAG_HEIGHT, v)));

        spacer(4);
        section("gui.display_block.section.shadow");
        fieldRow(
                floatField(Component.translatable("gui.display_block.shadow_radius"),
                        DisplayBlockEntityBase.readFloat(this.display, DisplayBlockEntityBase.TAG_SHADOW_RADIUS,
                                DisplayBlockEntityBase.DEFAULT_SHADOW_RADIUS),
                        v -> this.display.putFloat(DisplayBlockEntityBase.TAG_SHADOW_RADIUS, v)),
                floatField(Component.translatable("gui.display_block.shadow_strength"),
                        DisplayBlockEntityBase.readFloat(this.display, DisplayBlockEntityBase.TAG_SHADOW_STRENGTH,
                                DisplayBlockEntityBase.DEFAULT_SHADOW_STRENGTH),
                        v -> this.display.putFloat(DisplayBlockEntityBase.TAG_SHADOW_STRENGTH, v)));
        hint("gui.display_block.shadow_hint");

        spacer(4);
        section("gui.display_block.section.glow");
        fieldRow(colorField(Component.translatable("gui.display_block.glow_color"),
                DisplayBlockEntityBase.readInt(this.display, DisplayBlockEntityBase.TAG_GLOW_COLOR_OVERRIDE,
                        DisplayBlockEntityBase.NO_GLOW_COLOR_OVERRIDE),
                v -> this.display.putInt(DisplayBlockEntityBase.TAG_GLOW_COLOR_OVERRIDE, v)));
        hint("gui.display_block.glow_hint");
    }

    // ───────────────────────── 右侧预览框（信息 / 3D 两个小页签） ─────────────────────────

    /** 预览框内部的小页签。 */
    private enum PreviewMode {
        INFO("gui.display_block.preview.tab.info"),
        VIEW_3D("gui.display_block.preview.tab.view");

        private final String translationKey;

        PreviewMode(String translationKey) {
            this.translationKey = translationKey;
        }

        Component label() {
            return Component.translatable(this.translationKey);
        }
    }

    private PreviewMode previewMode = PreviewMode.INFO;
    private OrbitPreviewWidget orbitPreview;
    private final Map<PreviewMode, Button> previewModeButtons = new IdentityHashMap<>();
    private int previewTabY;
    private int previewBodyTop;
    private int previewBodyBottom;

    /**
     * 右侧预览框：顶部是"信息 / 3D"两个小页签和一个复位视角按钮，
     * 中间是内容（信息卡或可环绕的 3D 视口），底部留给动画状态。
     */
    private void buildPreviewBox() {
        this.previewModeButtons.clear();
        this.previewTabY = this.contentTop + 14;

        int x = this.previewLeft + 2;
        for (PreviewMode mode : PreviewMode.values()) {
            int width = this.font.width(mode.label()) + 12;
            Button button = Button.builder(mode.label(), b -> {
                if (this.previewMode != mode) {
                    this.previewMode = mode;
                    requestRebuild();
                }
            }).bounds(x, this.previewTabY, width, 14).build();
            button.active = this.previewMode != mode;
            addRenderableWidget(button);
            this.previewModeButtons.put(mode, button);
            x += width + 2;
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.display_block.preview.reset_view"), b -> {
            if (this.orbitPreview != null) {
                this.orbitPreview.resetView();
            }
        }).bounds(this.previewLeft + PREVIEW_W - 54, this.previewTabY, 52, 14).build());

        this.previewBodyTop = this.previewTabY + 18;
        this.previewBodyBottom = this.contentBottom - 22;
        int bodyHeight = Math.max(40, this.previewBodyBottom - this.previewBodyTop);
        this.orbitPreview = new OrbitPreviewWidget(this.previewLeft + 2, this.previewBodyTop,
                PREVIEW_W - 4, bodyHeight, (graphics, poseStack, cameraYaw, cameraPitch) ->
                        renderOrbitPreview(graphics, poseStack, cameraYaw, cameraPitch));
        this.orbitPreview.visible = this.previewMode == PreviewMode.VIEW_3D;
        addRenderableWidget(this.orbitPreview);
    }

    // ───────────────────────── 关键帧动画页签 ─────────────────────────

    private void buildAnimationTab() {
        section("gui.display_block.section.animation");
        wideRow(Component.translatable("gui.display_block.animation_enabled"),
                cycleButton(Boolean.toString(DisplayAnimation.isEnabled(this.display)),
                        List.of("false", "true"), "gui.display_block.bool.",
                        value -> this.display.putBoolean(DisplayAnimation.TAG_ENABLED,
                                Boolean.parseBoolean(value))));
        fieldRow(intField(Component.translatable("gui.display_block.animation_length"),
                DisplayAnimation.length(this.display),
                v -> this.display.putInt(DisplayAnimation.TAG_LENGTH, v)));
        wideRow(Component.translatable("gui.display_block.animation_loop"),
                cycleButton(Boolean.toString(DisplayAnimation.isLooping(this.display)),
                        List.of("true", "false"), "gui.display_block.bool.",
                        value -> this.display.putBoolean(DisplayAnimation.TAG_LOOP,
                                Boolean.parseBoolean(value))));
        hint("gui.display_block.animation_hint");

        spacer(4);
        section("gui.display_block.section.keyframes");
        widgetRow(
                Button.builder(Component.translatable("gui.display_block.frame_add"), b -> addFrame()).build(),
                Button.builder(Component.translatable("gui.display_block.frame_duplicate"), b -> duplicateFrame())
                        .build(),
                Button.builder(Component.translatable("gui.display_block.frame_remove"), b -> removeFrame())
                        .build());
        if (this.frames.isEmpty()) {
            hint("gui.display_block.frame_empty_hint");
        } else {
            for (int i = 0; i < this.frames.size(); i++) {
                int index = i;
                boolean selected = i == this.selectedFrame;
                String text = frameLabelText(i, this.frames.get(i));
                // 选中态只用"▶ + 金色"表示，绝不设 active=false——那会连点击一起禁掉。
                Component label = selected
                        ? Component.literal("▶ " + text).withStyle(ChatFormatting.GOLD)
                        : Component.literal(text);
                wideRow(Component.empty(), Button.builder(label, b -> selectFrame(index)).build());
            }
            hint("gui.display_block.frame_order_hint");
        }

        if (this.selectedFrame >= 0 && this.selectedFrame < this.frames.size()) {
            spacer(4);
            section("gui.display_block.section.frame");
            fieldRow(intField(Component.translatable("gui.display_block.frame_start"), this.frameStart,
                    v -> {
                        this.frameStart = v;
                        this.framesDirty = true;
                    }));
            fieldRow(
                    floatField(Component.translatable("gui.display_block.translate_x"), this.frameTranslateX,
                            v -> setFrameTranslate(v, 0)),
                    floatField(Component.translatable("gui.display_block.translate_y"), this.frameTranslateY,
                            v -> setFrameTranslate(v, 1)),
                    floatField(Component.translatable("gui.display_block.translate_z"), this.frameTranslateZ,
                            v -> setFrameTranslate(v, 2)));
            fieldRow(
                    floatField(Component.translatable("gui.display_block.scale_x"), this.frameScaleX,
                            v -> setFrameScale(v, 0)),
                    floatField(Component.translatable("gui.display_block.scale_y"), this.frameScaleY,
                            v -> setFrameScale(v, 1)),
                    floatField(Component.translatable("gui.display_block.scale_z"), this.frameScaleZ,
                            v -> setFrameScale(v, 2)));
            fieldRow(
                    floatField(Component.translatable("gui.display_block.rotate_x"), this.frameRotateX,
                            v -> setFrameRotate(v, 0)),
                    floatField(Component.translatable("gui.display_block.rotate_y"), this.frameRotateY,
                            v -> setFrameRotate(v, 1)),
                    floatField(Component.translatable("gui.display_block.rotate_z"), this.frameRotateZ,
                            v -> setFrameRotate(v, 2)));
            buildAnimationFrameFields();
        } else if (!this.frames.isEmpty()) {
            // 兜底：正常流程走不到（载入/添加/删除后都会选好一帧），万一走到就下一帧补齐字段。
            this.selectedFrame = 0;
            loadSelectedFrameFields();
            requestRebuild();
        }

        // 帧字段的改动每帧打包一次写回 NBT。
        this.pendingFields.add(this::flushFrames);
    }

    /** 动画页签里"选中关键帧"的额外字段（文本方块有透明度/背景色/文本覆盖）。 */
    protected void buildAnimationFrameFields() {
    }

    // 关键帧字段的写入入口：子类的额外字段也要顺手置脏，才会被打包写回。
    protected void setFrameOpacity(int value) {
        this.frameOpacity = Math.max(0, Math.min(255, value));
        this.framesDirty = true;
    }

    protected void setFrameBackground(int value) {
        this.frameBackground = value;
        this.framesDirty = true;
    }

    protected void setFrameText(String value) {
        this.frameText = value == null ? "" : value;
        this.framesDirty = true;
    }

    protected int frameOpacity() {
        return this.frameOpacity;
    }

    protected int frameBackground() {
        return this.frameBackground;
    }

    protected String frameText() {
        return this.frameText;
    }

    /** 关键帧列表里那一行的文字：紧凑、不依赖语言，方便对照调到第几帧。 */
    private String frameLabelText(int index, DisplayAnimation.Keyframe frame) {
        Vector3f translation = frame.transformation().getTranslation();
        Vector3f scale = frame.transformation().getScale();
        String textMark = frame.text() == null ? "" : " +text";
        return String.format(Locale.ROOT, "#%d @%dt T%.2f,%.2f,%.2f S%.2f,%.2f,%.2f%s",
                index, frame.start(), translation.x(), translation.y(), translation.z(),
                scale.x(), scale.y(), scale.z(), textMark);
    }

    private void loadFrames() {
        if (this.framesLoaded) {
            // 已经在编辑这份数据了：重建时直接沿用内存里的关键帧与选中项，别从 NBT 重读。
            return;
        }
        this.frames.clear();
        this.frames.addAll(DisplayAnimation.readKeyframes(this.display));
        this.selectedFrame = this.frames.isEmpty() ? -1 : 0;
        if (this.selectedFrame >= 0) {
            loadSelectedFrameFields();
        }
        this.framesLoaded = true;
    }

    private void loadSelectedFrameFields() {
        if (this.selectedFrame < 0 || this.selectedFrame >= this.frames.size()) {
            return;
        }
        DisplayAnimation.Keyframe frame = this.frames.get(this.selectedFrame);
        this.frameStart = frame.start();
        Vector3f translation = frame.transformation().getTranslation();
        this.frameTranslateX = translation.x();
        this.frameTranslateY = translation.y();
        this.frameTranslateZ = translation.z();
        Vector3f scale = frame.transformation().getScale();
        this.frameScaleX = scale.x();
        this.frameScaleY = scale.y();
        this.frameScaleZ = scale.z();
        Vector3f euler = new Quaternionf(frame.transformation().getLeftRotation()).getEulerAnglesYXZ(new Vector3f());
        this.frameRotateY = (float) Math.toDegrees(euler.y());
        this.frameRotateX = (float) Math.toDegrees(euler.x());
        this.frameRotateZ = (float) Math.toDegrees(euler.z());
        this.frameOpacity = frame.textOpacity() & 0xFF;
        this.frameBackground = frame.background();
        this.frameText = frame.text() == null ? "" : frame.text();
    }

    private DisplayAnimation.Keyframe buildFrameFromFields() {
        Quaternionf rotation = new Quaternionf().rotationYXZ(
                (float) Math.toRadians(this.frameRotateY),
                (float) Math.toRadians(this.frameRotateX),
                (float) Math.toRadians(this.frameRotateZ));
        Transformation transformation = new Transformation(
                new Vector3f(this.frameTranslateX, this.frameTranslateY, this.frameTranslateZ),
                rotation,
                new Vector3f(this.frameScaleX, this.frameScaleY, this.frameScaleZ),
                new Quaternionf());
        return new DisplayAnimation.Keyframe(this.frameStart, transformation,
                Math.max(0, Math.min(255, this.frameOpacity)), this.frameBackground,
                this.frameText.isEmpty() ? null : this.frameText);
    }

    /** 把字段里的改动打包进 {@code frames} 并写回 NBT；没改过就什么都不做。 */
    private void flushFrames() {
        if (!this.framesDirty || !this.framesLoaded) {
            return;
        }
        this.framesDirty = false;
        if (this.selectedFrame >= 0 && this.selectedFrame < this.frames.size()) {
            this.frames.set(this.selectedFrame, buildFrameFromFields());
        }
        DisplayAnimation.writeKeyframes(this.display, this.frames);
    }

    private void selectFrame(int index) {
        // 先把手上的改动落地，再换选中项，否则会把 A 帧的值写进 B 帧。
        flushFrames();
        this.selectedFrame = index;
        loadSelectedFrameFields();
        requestRebuild();
    }

    private void addFrame() {
        flushFrames();
        int length = DisplayAnimation.length(this.display);
        int start = this.frames.isEmpty() ? 0
                : Math.min(length, this.frames.get(this.frames.size() - 1).start() + 20);
        DisplayAnimation.Keyframe last = this.frames.isEmpty() ? null : this.frames.get(this.frames.size() - 1);
        Transformation transformation = last == null
                ? DisplayBlockEntityBase.readTransformation(this.display)
                : last.transformation();
        int opacity = last == null ? TextDisplayBlockEntity.effectiveTextOpacity(this.display) : last.textOpacity();
        int background = last == null
                ? DisplayBlockEntityBase.readInt(this.display, TextDisplayBlockEntity.TAG_BACKGROUND,
                        TextDisplayBlockEntity.DEFAULT_BACKGROUND)
                : last.background();
        this.frames.add(new DisplayAnimation.Keyframe(start, transformation, opacity, background, null));
        this.selectedFrame = this.frames.size() - 1;
        loadSelectedFrameFields();
        this.framesDirty = true;
        requestRebuild();
    }

    private void duplicateFrame() {
        flushFrames();
        if (this.selectedFrame < 0 || this.selectedFrame >= this.frames.size()) {
            return;
        }
        DisplayAnimation.Keyframe source = this.frames.get(this.selectedFrame);
        int length = DisplayAnimation.length(this.display);
        this.frames.add(new DisplayAnimation.Keyframe(Math.min(length, source.start() + 20),
                source.transformation(), source.textOpacity(), source.background(), source.text()));
        this.selectedFrame = this.frames.size() - 1;
        loadSelectedFrameFields();
        this.framesDirty = true;
        requestRebuild();
    }

    private void removeFrame() {
        flushFrames();
        if (this.selectedFrame < 0 || this.selectedFrame >= this.frames.size()) {
            return;
        }
        this.frames.remove(this.selectedFrame);
        this.selectedFrame = this.frames.isEmpty() ? -1 : Math.min(this.selectedFrame, this.frames.size() - 1);
        if (this.selectedFrame >= 0) {
            loadSelectedFrameFields();
        }
        this.framesDirty = true;
        requestRebuild();
    }

    private void setFrameTranslate(float value, int axis) {
        if (axis == 0) {
            this.frameTranslateX = value;
        } else if (axis == 1) {
            this.frameTranslateY = value;
        } else {
            this.frameTranslateZ = value;
        }
        this.framesDirty = true;
    }

    private void setFrameScale(float value, int axis) {
        if (axis == 0) {
            this.frameScaleX = value;
        } else if (axis == 1) {
            this.frameScaleY = value;
        } else {
            this.frameScaleZ = value;
        }
        this.framesDirty = true;
    }

    private void setFrameRotate(float value, int axis) {
        if (axis == 0) {
            this.frameRotateX = value;
        } else if (axis == 1) {
            this.frameRotateY = value;
        } else {
            this.frameRotateZ = value;
        }
        this.framesDirty = true;
    }

    private void buildAdvancedTab() {        int editorHeight = Math.max(60, this.contentBottom - this.contentTop - ROW_H * 2 - 6);
        MultiLineEditBox editor = new MultiLineEditBox(this.font, this.contentLeft, this.contentTop,
                this.contentWidth + 5, editorHeight,
                Component.translatable("gui.display_block.snbt_hint"), Component.empty());
        editor.setCharacterLimit(EDITOR_CHAR_LIMIT);
        editor.setValue(this.display.getAsString());
        addRenderableWidget(editor);
        this.snbtEditor = editor;
        this.snbtError = null;
        this.snbtAppliedSnapshot = this.display.getAsString();

        int baseY = this.contentTop + editorHeight + 4;
        addRenderableWidget(Button.builder(Component.translatable("gui.display_block.snbt_apply"), b -> {
            applySnbtEdits();
            if (this.snbtError == null) {
                requestRebuild();
            }
        }).bounds(this.contentLeft, baseY, 96, WIDGET_H).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.display_block.snbt_refresh"), b -> {
            editor.setValue(this.display.getAsString());
            this.snbtError = null;
        }).bounds(this.contentLeft + 102, baseY, 96, WIDGET_H).build());
        this.snbtStatusY = baseY + 1;
    }

    /** 解析高级页的 SNBT 并替换成新的展示数据；失败时把错误记下来给界面显示。 */
    private void applySnbtEdits() {
        if (this.snbtEditor == null) {
            return;
        }
        String text = this.snbtEditor.getValue();
        if (text.equals(this.snbtAppliedSnapshot)) {
            return;
        }
        try {
            this.display = TagParser.parseTag(text);
            this.snbtError = null;
            this.snbtAppliedSnapshot = text;
            // 解析成功后本地字段要重新同步，否则变换页会拿旧值覆盖回去。
            loadTransformFields();
            loadBrightnessFields();
            // display 整个被换掉了，动画页签的内存副本也作废。
            this.frames.clear();
            this.selectedFrame = -1;
            this.framesLoaded = false;
        } catch (Exception exception) {
            this.snbtError = exception.getMessage();
        }
    }

    // ───────────────────────── 字段构造 ─────────────────────────

    protected FieldSpec floatField(Component label, float value, Consumer<Float> setter) {
        return new FieldSpec(label, formatFloat(value), 20, text -> {
            Float parsed = parseFloat(text);
            if (parsed != null) {
                setter.accept(parsed);
            }
        });
    }

    protected FieldSpec intField(Component label, int value, Consumer<Integer> setter) {
        return new FieldSpec(label, Integer.toString(value), 12, text -> {
            Integer parsed = parseInt(text);
            if (parsed != null) {
                setter.accept(parsed);
            }
        });
    }

    protected FieldSpec textField(Component label, String value, int maxLength, Consumer<String> setter) {
        return new FieldSpec(label, value == null ? "" : value, maxLength, setter);
    }

    /** 颜色字段：支持 {@code #RRGGBB} / {@code #AARRGGBB} / 十进制（-1 表示不发光）。 */
    protected FieldSpec colorField(Component label, int value, Consumer<Integer> setter) {
        String initial = value < 0 ? Integer.toString(value) : String.format(Locale.ROOT, "#%08X", value);
        return new FieldSpec(label, initial, 16, text -> {
            Integer parsed = parseColor(text);
            if (parsed != null) {
                setter.accept(parsed);
            }
        });
    }

    /**
     * 循环按钮：每点一次换下一个候选值，并把新值写回 NBT。
     * 故意不继承 Button（原版构造函数是 protected，跨包受限），用可变的单元素数组保存下标。
     */
    protected Button cycleButton(String current, List<String> values, String translationPrefix,
            Consumer<String> setter) {
        int start = Math.max(0, values.indexOf(current == null ? values.get(0) : current));
        int[] index = { start };
        return Button.builder(Component.translatable(translationPrefix + values.get(start)), b -> {
            index[0] = (index[0] + 1) % values.size();
            String value = values.get(index[0]);
            b.setMessage(Component.translatable(translationPrefix + value));
            setter.accept(value);
        }).bounds(0, 0, 120, WIDGET_H).build();
    }

    // ───────────────────────── 滚动 ─────────────────────────

    private void applyScroll() {
        int viewport = this.contentBottom - this.contentTop;
        int maxScroll = Math.max(0, this.usedHeight - viewport);
        this.scroll = Math.max(0.0D, Math.min(maxScroll, this.scroll));
        int offset = (int) this.scroll;
        for (AbstractWidget widget : this.scrolledWidgets) {
            Integer base = this.scrolledBaseY.get(widget);
            if (base == null) {
                continue;
            }
            widget.setY(base - offset);
            boolean inView = widget.getY() + widget.getHeight() > this.contentTop
                    && widget.getY() < this.contentBottom;
            widget.visible = inView;
            if (!inView && getFocused() == widget) {
                setFocused(null);
            }
        }
    }

    private boolean isOverContent(double mouseX, double mouseY) {
        return mouseX >= this.contentLeft && mouseX < this.previewLeft + PREVIEW_W
                && mouseY >= this.contentTop && mouseY < this.contentBottom;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // 先让控件自己处理（比如方块网格要滚自己的列表），没人要才滚整页。
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        if (isOverContent(mouseX, mouseY)) {
            applyPendingFields();
            this.scroll -= scrollY * SCROLL_STEP;
            applyScroll();
            return true;
        }
        return false;
    }

    // ───────────────────────── 渲染 ─────────────────────────

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        // 面板三步：上下渐变 + 棕褐描边 + 顶部装饰线
        graphics.fillGradient(this.panelX, this.panelY, this.panelX + this.panelW,
                this.panelY + this.panelH, PANEL_BG_TOP, PANEL_BG_BOTTOM);
        graphics.renderOutline(this.panelX, this.panelY, this.panelW, this.panelH, PANEL_OUTLINE);
        graphics.fill(this.panelX + 1, this.panelY + 1, this.panelX + this.panelW - 1, this.panelY + 2, DECOR_LINE);

        // 当前页签的金色下划线（要在提前返回之前画，否则预览页签没有下划线）
        Button active = this.tabButtons.get(this.tab);
        if (active != null) {
            graphics.fill(active.getX(), active.getY() + active.getHeight(),
                    active.getX() + active.getWidth(), active.getY() + active.getHeight() + 2, COLOR_GOLD);
        }

        // 右栏预览卡片
        graphics.fillGradient(this.previewLeft, this.contentTop, this.previewLeft + PREVIEW_W,
                this.contentBottom, CARD_BG, CARD_BG);
        graphics.renderOutline(this.previewLeft, this.contentTop, PREVIEW_W,
                this.contentBottom - this.contentTop, CARD_BORDER);

        // 内容区与预览之间的分隔线
        int dividerX = this.contentLeft + this.contentWidth + 3;
        graphics.fill(dividerX, this.contentTop, dividerX + 1, this.contentBottom, ROW_SEPARATOR);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.rebuildRequested) {
            this.rebuildRequested = false;
            rebuildNow();
        }
        // 每帧把输入框的值同步进 NBT，右侧预览才能实时反映改动。
        applyPendingFields();

        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawString(this.font, this.title, this.panelX + PAD, this.panelY + 8, COLOR_GOLD, false);

        if (this.tab == Tab.ADVANCED) {
            renderSnbtStatus(graphics);
        } else {
            renderScrolledLabels(graphics);
        }

        renderPreview(graphics);
        renderFooterHints(graphics);
    }

    private void renderScrolledLabels(GuiGraphics graphics) {
        int offset = (int) this.scroll;
        graphics.enableScissor(this.contentLeft, this.contentTop, this.contentLeft + this.contentWidth + 4,
                this.contentBottom);
        for (int[] separator : this.separators) {
            int y = separator[0] - offset;
            if (y < this.contentTop || y > this.contentBottom) {
                continue;
            }
            graphics.fill(separator[1], y, separator[1] + separator[2], y + 1, ROW_SEPARATOR);
        }
        for (RowLabel label : this.labels) {
            int y = label.baseY() - offset;
            if (y + 9 < this.contentTop || y > this.contentBottom) {
                continue;
            }
            Component text = label.bold() ? label.text().copy().withStyle(ChatFormatting.BOLD) : label.text();
            graphics.drawString(this.font, text, label.x(), y, label.color(), false);
        }
        graphics.disableScissor();
    }

    private void renderSnbtStatus(GuiGraphics graphics) {
        Component status = this.snbtError != null
                ? Component.translatable("gui.display_block.snbt_error", this.snbtError)
                : Component.translatable("gui.display_block.snbt_ok");
        int color = this.snbtError != null ? COLOR_RED : COLOR_GREEN;
        int x = this.contentLeft + 208;
        int maxWidth = this.contentWidth + 5 - 208;
        int y = this.snbtStatusY;
        for (var line : this.font.split(status, Math.max(40, maxWidth))) {
            if (y + 9 > this.contentBottom) {
                break;
            }
            graphics.drawString(this.font, line, x, y, color, false);
            y += 10;
        }
    }

    private void renderFooterHints(GuiGraphics graphics) {
        int y = this.panelY + this.panelH - FOOTER_H + 11;
        if (this.saveError != null) {
            graphics.drawString(this.font, this.saveError, this.contentLeft + 76, y, COLOR_RED, false);
            return;
        }
        graphics.drawString(this.font,
                Component.translatable("gui.display_block.pos", this.pos.getX(), this.pos.getY(), this.pos.getZ()),
                this.contentLeft + 76, y, COLOR_MUTED, false);
        if (this.tab != Tab.ADVANCED) {
            int maxScroll = Math.max(0, this.usedHeight - (this.contentBottom - this.contentTop));
            if (maxScroll > 0) {
                Component scrollHint = Component.translatable("gui.display_block.scroll_hint");
                graphics.drawString(this.font, scrollHint,
                        this.previewLeft - 6 - this.font.width(scrollHint), y, COLOR_MUTED, false);
            }
        }
    }

    private void renderPreview(GuiGraphics graphics) {
        int x = this.previewLeft + 6;
        int y = this.contentTop + 6;
        int width = PREVIEW_W - 12;
        graphics.drawString(this.font, Component.translatable("gui.display_block.preview"), x, y, COLOR_GOLD, false);
        graphics.fill(x, y + 12, x + width, y + 13, ROW_SEPARATOR);

        // 当前小页签的金色下划线
        Button active = this.previewModeButtons.get(this.previewMode);
        if (active != null) {
            graphics.fill(active.getX(), active.getY() + active.getHeight(),
                    active.getX() + active.getWidth(), active.getY() + active.getHeight() + 1, COLOR_GOLD);
        }

        if (this.previewMode == PreviewMode.INFO) {
            renderPreviewContent(graphics, x, this.previewBodyTop, width,
                    this.previewBodyBottom - this.previewBodyTop);
        }
        renderAnimationStatus(graphics, x, this.contentBottom - 12, width);
    }

    /** 预览卡片底部显示动画状态（是否启用、时长、当前时间、第几帧）。 */
    private void renderAnimationStatus(GuiGraphics graphics, int x, int y, int width) {
        if (!DisplayAnimation.isEnabled(this.display)) {
            return;
        }
        int length = DisplayAnimation.length(this.display);
        int frameCount = keyframeCount();
        Level level = Minecraft.getInstance().level;
        long gameTime = level == null ? 0L : level.getGameTime();
        int local;
        if (DisplayAnimation.isLooping(this.display)) {
            local = (int) Math.floorMod(gameTime, (long) Math.max(1, length));
        } else {
            long start = DisplayBlockEntityBase.readLong(this.display, DisplayAnimation.TAG_START, 0L);
            local = (int) Math.min(Math.max(0L, gameTime - start), Math.max(1, length));
        }
        Component status = Component.translatable("gui.display_block.animation_status", local, length, frameCount,
                Component.translatable(DisplayAnimation.isLooping(this.display)
                        ? "gui.display_block.animation_loop.on"
                        : "gui.display_block.animation_loop.off"));
        graphics.drawString(this.font, status, x, y, COLOR_BLUE, false);
    }

    /**
     * 关键帧数量。动画页签下直接用编辑器里的列表；其他页签靠 ListTag 的对象身份做缓存
     * （写入关键帧时一定是 put 一个新的 ListTag，所以身份变了就是数据变了），不每帧重新解析。
     */
    private int keyframeCount() {
        if (this.tab == Tab.ANIMATION && this.framesLoaded) {
            return this.frames.size();
        }
        ListTag tag = this.display.contains(DisplayAnimation.TAG_KEYFRAMES, Tag.TAG_LIST)
                ? this.display.getList(DisplayAnimation.TAG_KEYFRAMES, Tag.TAG_COMPOUND)
                : null;
        if (tag != this.animationFramesTag) {
            this.animationFramesTag = tag;
            this.cachedAnimationFrames = tag == null ? 0
                    : Math.min(tag.size(), DisplayAnimation.MAX_KEYFRAMES);
        }
        return this.cachedAnimationFrames;
    }

    private ListTag animationFramesTag;
    private int cachedAnimationFrames;

    /** 预览卡片里的自动换行文字，返回下一行的 y。 */
    protected int previewWrapped(GuiGraphics graphics, int x, int y, int width, Component text, int color) {
        int lineY = y;
        for (var line : this.font.split(text, Math.max(20, width))) {
            graphics.drawString(this.font, line, x, lineY, color, false);
            lineY += 10;
        }
        return lineY;
    }

    /** 预览卡片里的信息行：小号标签 + 自动换行的值。 */
    protected void previewInfo(GuiGraphics graphics, int x, int y, int width, String labelKey, Component value,
            int color) {
        graphics.drawString(this.font, Component.translatable(labelKey), x, y, COLOR_MUTED, false);
        previewWrapped(graphics, x, y + 10, width, value, color);
    }

    // ───────────────────────── 子类接口 ─────────────────────────

    /** "内容"页签：只有这个页签两个方块不一样。 */
    protected abstract void buildContentTab();

    /** 外观页里属于该方块自己的字段（文本方块是行宽、背景色等）。 */
    protected void buildAppearanceFields() {
    }

    // ───────────────────────── 预览共用的取值 ─────────────────────────

    private final DisplayAnimation.Sampler previewSampler = new DisplayAnimation.Sampler();
    private List<DisplayAnimation.Keyframe> previewFrames = List.of();
    private ListTag previewFramesTag;
    private int previewAnimationLength = DisplayAnimation.DEFAULT_LENGTH;
    private boolean previewAnimationLoop = true;
    private long previewAnimationStart;

    /**
     * 按当前游戏时间采样动画，失败/未启用时返回 null（调用方回退到静态值）。
     *
     * <p>关键帧只在数据真的变了才重新解析：缓存键用 ListTag 的**对象身份**
     * （写关键帧时一定是 put 一个新 ListTag），比每帧比较内容便宜得多。
     */
    @Nullable
    protected DisplayAnimation.Sampler sampleAnimation() {
        if (!DisplayAnimation.isEnabled(this.display)) {
            this.previewFramesTag = null;
            this.previewFrames = List.of();
            return null;
        }
        ListTag tag = this.display.contains(DisplayAnimation.TAG_KEYFRAMES, Tag.TAG_LIST)
                ? this.display.getList(DisplayAnimation.TAG_KEYFRAMES, Tag.TAG_COMPOUND)
                : null;
        int length = DisplayAnimation.length(this.display);
        boolean loop = DisplayAnimation.isLooping(this.display);
        long start = DisplayBlockEntityBase.readLong(this.display, DisplayAnimation.TAG_START, 0L);
        if (tag != this.previewFramesTag || length != this.previewAnimationLength
                || loop != this.previewAnimationLoop || start != this.previewAnimationStart) {
            this.previewFramesTag = tag;
            this.previewAnimationLength = length;
            this.previewAnimationLoop = loop;
            this.previewAnimationStart = start;
            this.previewFrames = DisplayAnimation.readKeyframes(this.display);
        }
        Level level = Minecraft.getInstance().level;
        long gameTime = level == null ? 0L : level.getGameTime();
        return this.previewSampler.sample(this.previewFrames, this.previewAnimationLength,
                this.previewAnimationLoop, this.previewAnimationStart, gameTime) ? this.previewSampler : null;
    }

    /** 当前应当使用的变换：动画启用时用采样值，否则用静态 transformation。 */
    protected Transformation effectiveTransformation() {
        DisplayAnimation.Sampler sampler = sampleAnimation();
        return sampler != null ? sampler.transformation()
                : DisplayBlockEntityBase.readTransformation(this.display);
    }

    /** 预览页签里画内容。pose 已经保证"1 方块 = 1 单位、y 轴向上"。 */
    protected abstract void renderOrbitPreview(GuiGraphics graphics, PoseStack poseStack, float cameraYaw,
            float cameraPitch);

    /** 右栏预览内容，坐标已经在预览卡片内边距之后。 */
    protected abstract void renderPreviewContent(GuiGraphics graphics, int x, int y, int width, int height);

    // ───────────────────────── 解析工具 ─────────────────────────

    protected static Float parseFloat(String text) {
        try {
            return Float.parseFloat(text.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    protected static Integer parseInt(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** 支持 #RRGGBB / #AARRGGBB / 十进制（含负数）。 */
    protected static Integer parseColor(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            if (trimmed.startsWith("#")) {
                String hex = trimmed.substring(1);
                long value = Long.parseLong(hex, 16);
                if (hex.length() <= 6) {
                    value |= 0xFF000000L;
                }
                return (int) value;
            }
            return (int) Long.parseLong(trimmed, 10);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** 输入框里显示的浮点数：整数就不带小数点，其余最多保留 4 位并去掉尾部 0。 */
    protected static String formatFloat(float value) {
        if (value == Math.rint(value) && Math.abs(value) < 1.0E7F) {
            return Integer.toString((int) value);
        }
        String text = String.format(Locale.ROOT, "%.4f", value);
        while (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }
}
