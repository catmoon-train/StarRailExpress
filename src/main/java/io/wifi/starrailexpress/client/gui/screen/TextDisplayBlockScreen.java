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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.client.render.block_entity.DisplayBlockRenderSupport;
import io.wifi.starrailexpress.content.block_entity.DisplayAnimation;
import io.wifi.starrailexpress.content.block_entity.DisplayBlockEntityBase;
import io.wifi.starrailexpress.content.block_entity.TextDisplayBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 文本展示方块的编辑界面。
 *
 * <p>"内容"页签是一块占满内容区的大文本框，直接编辑原始 JSON 聊天文本（原版 {@code text} 键），
 * 旁边给格式化和压缩按钮；右侧预览按当前的对齐 / 背景 / 阴影 / 透明度实时画出效果。
 */
public class TextDisplayBlockScreen extends DisplayBlockScreenBase {

    private static final Gson COMPACT = new Gson();
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();

    private MultiLineEditBox editor;
    private String cachedJson;
    private Component cachedText;
    private String cachedError;
    private int statusY;

    public TextDisplayBlockScreen(BlockPos pos, CompoundTag data) {
        super(Component.translatable("gui.display_block.text_display.title"), pos, data);
    }

    // ───────────────────────── 内容页签 ─────────────────────────

    @Override
    protected void buildContentTab() {
        int buttonRow = ROW_H + 4;
        int editorHeight = Math.max(60, contentBottom() - contentTop() - buttonRow - 6);
        MultiLineEditBox box = new MultiLineEditBox(this.font, contentLeft(), contentTop(),
                contentWidth() + 5, editorHeight, Component.translatable("gui.display_block.json_hint"),
                Component.empty());
        box.setCharacterLimit(EDITOR_CHAR_LIMIT);
        box.setValue(DisplayBlockEntityBase.readString(this.display, TextDisplayBlockEntity.TAG_TEXT,
                TextDisplayBlockEntity.DEFAULT_TEXT));
        addRenderableWidget(box);
        this.editor = box;

        int baseY = contentTop() + editorHeight + 4;
        addRenderableWidget(Button.builder(Component.translatable("gui.display_block.json_format"),
                b -> reformat(true)).bounds(contentLeft(), baseY, 76, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.display_block.json_compact"),
                b -> reformat(false)).bounds(contentLeft() + 82, baseY, 76, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.display_block.json_sample"),
                b -> setEditorValue(SAMPLE)).bounds(contentLeft() + 164, baseY, 76, 18).build());
        this.statusY = baseY + 2;

        // 编辑框里的文本就是 text 键；只在文本真的变了才写回 NBT，别每帧造一个 StringTag。
        this.pendingFields.add(() -> {
            if (this.editor == null) {
                return;
            }
            String text = this.editor.getValue();
            if (!text.equals(this.lastSubmittedText)) {
                this.lastSubmittedText = text;
                this.display.putString(TextDisplayBlockEntity.TAG_TEXT, text);
            }
        });
    }

    private String lastSubmittedText;

    /** 示例文本，方便快速看到效果。 */
    private static final String SAMPLE = "{\"text\":\"文本展示方块\",\"color\":\"gold\",\"bold\":true}";

    private void setEditorValue(String value) {
        if (this.editor != null) {
            this.editor.setValue(value);
        }
    }

    private void reformat(boolean pretty) {
        if (this.editor == null) {
            return;
        }
        try {
            JsonElement element = JsonParser.parseString(this.editor.getValue());
            this.editor.setValue((pretty ? PRETTY : COMPACT).toJson(element));
        } catch (Exception exception) {
            // 不是合法 JSON 就原样留着，状态栏会提示错误。
        }
    }

    // ───────────────────────── 外观页签（文本专有字段） ─────────────────────────

    @Override
    protected void buildAppearanceFields() {
        section("gui.display_block.section.text");
        fieldRow(
                intField(Component.translatable("gui.display_block.line_width"),
                        DisplayBlockEntityBase.readInt(this.display, TextDisplayBlockEntity.TAG_LINE_WIDTH,
                                TextDisplayBlockEntity.DEFAULT_LINE_WIDTH),
                        v -> this.display.putInt(TextDisplayBlockEntity.TAG_LINE_WIDTH, Math.max(1, v))),
                intField(Component.translatable("gui.display_block.text_opacity"),
                        TextDisplayBlockEntity.effectiveTextOpacity(this.display),
                        v -> TextDisplayBlockEntity.setTextOpacity(this.display, v)));
        wideRow(Component.translatable("gui.display_block.alignment"),
                cycleButton(TextDisplayBlockEntity.readAlignment(this.display).getSerializedName(),
                        List.of("center", "left", "right"), "gui.display_block.alignment.",
                        value -> this.display.putString(TextDisplayBlockEntity.TAG_ALIGNMENT, value)));
        fieldRow(colorField(Component.translatable("gui.display_block.background"),
                DisplayBlockEntityBase.readInt(this.display, TextDisplayBlockEntity.TAG_BACKGROUND,
                        TextDisplayBlockEntity.DEFAULT_BACKGROUND),
                v -> this.display.putInt(TextDisplayBlockEntity.TAG_BACKGROUND, v)));
        wideRow(Component.translatable("gui.display_block.default_background"),
                boolCycle(TextDisplayBlockEntity.TAG_DEFAULT_BACKGROUND));
        wideRow(Component.translatable("gui.display_block.shadow"),
                boolCycle(TextDisplayBlockEntity.TAG_SHADOW));
        wideRow(Component.translatable("gui.display_block.see_through"),
                boolCycle(TextDisplayBlockEntity.TAG_SEE_THROUGH));
    }

    private Button boolCycle(String tagKey) {
        return cycleButton(Boolean.toString(DisplayBlockEntityBase.readBoolean(this.display, tagKey, false)),
                List.of("false", "true"), "gui.display_block.bool.",
                value -> this.display.putBoolean(tagKey, Boolean.parseBoolean(value)));
    }

    // ───────────────────────── 动画页签里关键帧的文本相关字段 ─────────────────────────

    @Override
    protected void buildAnimationFrameFields() {
        fieldRow(
                intField(Component.translatable("gui.display_block.text_opacity"), frameOpacity(),
                        this::setFrameOpacity),
                colorField(Component.translatable("gui.display_block.background"), frameBackground(),
                        this::setFrameBackground));
        EditBox textBox = createBox(LABEL_W, Math.max(60, contentWidth() - LABEL_W - 4), frameText(), 512);
        wideRow(Component.translatable("gui.display_block.frame_text"), textBox);
        // 文本只在真的变了才提交，避免每帧重打包整个关键帧列表
        String[] lastSubmitted = { frameText() };
        this.pendingFields.add(() -> {
            String raw = textBox.getValue();
            if (!raw.equals(lastSubmitted[0])) {
                lastSubmitted[0] = raw;
                setFrameText(raw);
            }
        });
        hint("gui.display_block.frame_text_hint");
    }

    // ───────────────────────── 预览 ─────────────────────────

    @Override
    protected void renderPreviewContent(GuiGraphics graphics, int x, int y, int width, int height) {
        String json = this.editor == null ? "" : this.editor.getValue();
        parse(json);

        int cursorY = y;
        boolean valid = this.cachedText != null;
        if (valid) {
            cursorY = renderTextPreview(graphics, x, cursorY, width, height);
        } else {
            graphics.drawString(this.font, Component.translatable("gui.display_block.preview.invalid"), x, cursorY,
                    COLOR_RED, false);
            cursorY += 12;
        }

        previewInfo(graphics, x, cursorY, width, "gui.display_block.preview.json",
                Component.literal(valid ? "OK" : "ERROR"), valid ? COLOR_GREEN : COLOR_RED);
        cursorY += 22;
        previewInfo(graphics, x, cursorY, width, "gui.display_block.preview.length",
                Component.literal(Integer.toString(json.length())), COLOR_TEXT);
        cursorY += 22;
        if (!valid && this.cachedError != null) {
            previewInfo(graphics, x, cursorY, width, "gui.display_block.preview.error",
                    Component.literal(this.cachedError), COLOR_RED);
        }
    }

    /** 按当前对齐 / 背景 / 阴影 / 透明度把文本画出来，行宽限制在预览栏宽度内。 */
    private int renderTextPreview(GuiGraphics graphics, int x, int y, int width, int height) {
        // 启用动画时预览也跟着动：用同一套采样器按当前游戏时间取值
        DisplayAnimation.Sampler sampler = sampleAnimation();
        int opacity = TextDisplayBlockEntity.effectiveTextOpacity(this.display);
        int background = DisplayBlockEntityBase.readInt(this.display, TextDisplayBlockEntity.TAG_BACKGROUND,
                TextDisplayBlockEntity.DEFAULT_BACKGROUND);
        if (sampler != null) {
            opacity = sampler.textOpacity() & 0xFF;
            background = sampler.background();
        }

        // 关键帧可以逐帧换文本；解析失败就退回静态文本
        Component previewText = this.cachedText;
        if (sampler != null && sampler.text() != null) {
            Level level = Minecraft.getInstance().level;
            Component animated = level == null ? null
                    : TextDisplayBlockEntity.parseText(sampler.text(), level.registryAccess());
            if (animated != null) {
                previewText = animated;
            }
        }

        if (DisplayBlockEntityBase.readBoolean(this.display, TextDisplayBlockEntity.TAG_DEFAULT_BACKGROUND, false)) {
            background = TextDisplayBlockEntity.DEFAULT_BACKGROUND;
        }
        boolean shadow = DisplayBlockEntityBase.readBoolean(this.display, TextDisplayBlockEntity.TAG_SHADOW, false);
        Display.TextDisplay.Align align = TextDisplayBlockEntity.readAlignment(this.display);

        List<FormattedCharSequence> lines = previewText == null ? List.of()
                : this.font.split(previewText, Math.max(20, width - 6));
        int maxLines = Math.max(1, (height - 66) / 10);
        int shown = Math.min(lines.size(), maxLines);
        int boxHeight = shown * 10 + 6;

        if (background != 0) {
            graphics.fill(x, y, x + width, y + boxHeight, background);
            graphics.renderOutline(x, y, width, boxHeight, CARD_BORDER);
        }

        float alpha = Math.max(0.0F, Math.min(1.0F, opacity / 255.0F));
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        int lineY = y + 3;
        for (int i = 0; i < shown; i++) {
            FormattedCharSequence line = lines.get(i);
            int lineWidth = this.font.width(line);
            int lineX = switch (align) {
                case LEFT -> x + 3;
                case RIGHT -> x + width - 3 - lineWidth;
                case CENTER -> x + (width - lineWidth) / 2;
            };
            graphics.drawString(this.font, line, lineX, lineY, 0xFFFFFFFF, shadow);
            lineY += 10;
        }
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        if (lines.size() > shown) {
            graphics.drawString(this.font, Component.translatable("gui.display_block.preview.more",
                    lines.size() - shown), x, y + boxHeight + 2, COLOR_MUTED, false);
        }
        return y + boxHeight + 14;
    }

    /**
     * 预览页签的 3D 视口：把世界里那套文本绘制直接搬过来，只是换成预览视角的相机参数，
     * 所以旋转、缩放、对齐、背景板、动画和实际效果完全一致。
     */
    @Override
    protected void renderOrbitPreview(GuiGraphics graphics, PoseStack poseStack, float cameraYaw, float cameraPitch) {
        // 内容页签在时用编辑框里的文本，否则用存下来的 text 键
        String json = this.editor != null ? this.editor.getValue()
                : DisplayBlockEntityBase.readString(this.display, TextDisplayBlockEntity.TAG_TEXT,
                        TextDisplayBlockEntity.DEFAULT_TEXT);
        parse(json);
        DisplayAnimation.Sampler sampler = sampleAnimation();

        Component text = this.cachedText;
        int opacity = TextDisplayBlockEntity.effectiveTextOpacity(this.display);
        int background = DisplayBlockEntityBase.readInt(this.display, TextDisplayBlockEntity.TAG_BACKGROUND,
                TextDisplayBlockEntity.DEFAULT_BACKGROUND);
        if (sampler != null) {
            opacity = sampler.textOpacity() & 0xFF;
            background = sampler.background();
            if (sampler.text() != null) {
                Level level = Minecraft.getInstance().level;
                Component animated = level == null ? null
                        : TextDisplayBlockEntity.parseText(sampler.text(), level.registryAccess());
                if (animated != null) {
                    text = animated;
                }
            }
        }
        if (text == null) {
            return;
        }
        if (DisplayBlockEntityBase.readBoolean(this.display, TextDisplayBlockEntity.TAG_DEFAULT_BACKGROUND, false)) {
            background = TextDisplayBlockEntity.DEFAULT_BACKGROUND;
        }

        int lineWidth = Math.max(1, DisplayBlockEntityBase.readInt(this.display,
                TextDisplayBlockEntity.TAG_LINE_WIDTH, TextDisplayBlockEntity.DEFAULT_LINE_WIDTH));
        Display.TextDisplay.CachedInfo info = DisplayBlockRenderSupport.splitLines(text, lineWidth);
        if (info.lines().isEmpty()) {
            return;
        }

        poseStack.pushPose();
        DisplayBlockRenderSupport.applyDisplayTransform(poseStack,
                DisplayBlockEntityBase.readBillboard(this.display), effectiveTransformation(), cameraYaw, cameraPitch);
        DisplayBlockRenderSupport.renderText(info, poseStack, graphics.bufferSource(),
                DisplayBlockRenderSupport.GUI_LIGHT, opacity, background,
                TextDisplayBlockEntity.readFlags(this.display));
        poseStack.popPose();
    }

    /** 解析 JSON 文本，结果按字符串缓存，避免每帧都重新解析。 */
    private void parse(String json) {
        if (json.equals(this.cachedJson)) {
            return;
        }
        this.cachedJson = json;
        this.cachedText = null;
        this.cachedError = null;
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        try {
            this.cachedText = Component.Serializer.fromJson(json, level.registryAccess());
        } catch (Exception exception) {
            this.cachedError = exception.getMessage() == null ? exception.getClass().getSimpleName()
                    : exception.getMessage();
        }
    }

    // ───────────────────────── 渲染 ─────────────────────────

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // 编辑器只属于内容页签；切页签后 this.editor 是上一轮的残留，别再画它的状态。
        if (this.statusY == 0 || activeTab() != Tab.CONTENT || this.editor == null || !this.editor.visible) {
            return;
        }
        String json = this.editor.getValue();
        parse(json);
        Component status = this.cachedText != null
                ? Component.translatable("gui.display_block.json.valid")
                : Component.translatable("gui.display_block.json.invalid");
        int color = this.cachedText != null ? COLOR_GREEN : COLOR_RED;
        graphics.drawString(this.font, status, contentLeft() + 248, this.statusY, color, false);
    }
}
