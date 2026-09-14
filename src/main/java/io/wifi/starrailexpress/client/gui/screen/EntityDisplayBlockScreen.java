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
import io.wifi.starrailexpress.client.gui.widget.EntityListWidget;
import io.wifi.starrailexpress.client.render.block_entity.DisplayBlockRenderSupport;
import io.wifi.starrailexpress.client.util.SafeNames;
import io.wifi.starrailexpress.content.block_entity.DisplayBlockEntityBase;
import io.wifi.starrailexpress.content.block_entity.EntityDisplayBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 实体展示方块的编辑界面。
 *
 * <p>"内容"页签：实体 ID 输入框（同时是搜索词）+ 实体数据编辑框 + 可滚动的实体类型列表。
 *
 * <p>为什么没有"取准心实体"：这个界面是**右键方块**打开的，那一刻玩家看的是展示方块自己，
 * 根本瞄不到想复制的实体。所以改成贴 F3+I 的输出——把实体数据框做成 SNBT 编辑器，
 * 再用「从剪贴板解析」一键把 {@code /summon minecraft:pig 8.26 -2.88 42.72 {…}} 整段读进来
 * （类型、坐标、NBT 都会拆开，坐标和 Pos/Motion/UUID 丢掉，Rotation 保留当基础朝向）。
 */
public class EntityDisplayBlockScreen extends DisplayBlockScreenBase {

    private static final String NO_FILTER = "\u0000";

    private EditBox idBox;
    private MultiLineEditBox dataBox;
    private EntityListWidget list;
    private String lastFilter = NO_FILTER;
    private int hintY;
    private int parseStatusY;
    private boolean contentBuilt;
    /** 解析失败的提示，成功时为 null。 */
    private Component parseError;

    public EntityDisplayBlockScreen(BlockPos pos, CompoundTag data) {
        super(Component.translatable("gui.display_block.entity_display.title"), pos, data);
    }

    // ───────────────────────── 内容页签 ─────────────────────────

    @Override
    protected void buildContentTab() {
        int rowY = contentTop();
        addLabel(Component.translatable("gui.display_block.entity_id"), contentLeft(), rowY + 5, COLOR_TEXT);
        EditBox box = createBox(LABEL_W, Math.max(60, contentWidth() - LABEL_W - 4), currentTypeId(), 128);
        box.setY(rowY);
        addRenderableWidget(box);
        this.idBox = box;
        this.lastFilter = box.getValue();

        this.hintY = rowY + ROW_H;
        this.contentBuilt = true;

        // 实体数据编辑框：既能直接改 SNBT，也能接 F3+I 拷出来的整段 /summon 文本
        int dataTop = this.hintY + 12;
        int dataHeight = 52;
        MultiLineEditBox data = new MultiLineEditBox(this.font, contentLeft(), dataTop, contentWidth() + 5,
                dataHeight, Component.translatable("gui.display_block.entity_data_hint"), Component.empty());
        data.setCharacterLimit(4096);
        data.setValue(currentEntitySnbt());
        addRenderableWidget(data);
        this.dataBox = data;

        int buttonsY = dataTop + dataHeight + 3;
        addRenderableWidget(Button.builder(Component.translatable("gui.display_block.entity_from_clipboard"),
                b -> loadFromClipboard()).bounds(contentLeft(), buttonsY, 104, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.display_block.entity_apply"),
                b -> applyPastedData(this.dataBox.getValue()))
                .bounds(contentLeft() + 108, buttonsY, 88, 16).build());
        this.parseStatusY = buttonsY + 4;

        int listTop = buttonsY + 22;
        int listHeight = Math.max(36, contentBottom() - listTop);
        EntityListWidget widget = new EntityListWidget(contentLeft(), listTop, contentWidth() + 5, listHeight,
                type -> applyType(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(), true));
        widget.setSelected(currentTypeIdOrNull());
        widget.setFilter(box.getValue());
        addRenderableWidget(widget);
        this.list = widget;

        // 输入框同时是搜索词；手输完整 ID 时直接切换实体类型（过滤走控件，不重建界面）
        this.pendingFields.add(() -> {
            if (this.idBox == null || this.list == null) {
                return;
            }
            String text = this.idBox.getValue();
            if (text.equals(this.lastFilter)) {
                return;
            }
            this.lastFilter = text;
            this.list.setFilter(text);
            ResourceLocation exact = resolveTypeId(text);
            if (exact != null) {
                applyType(exact.toString(), false);
            }
        });

        // 数据框里的改动实时写回（语法错就先留着，由状态行报错）
        String[] lastSubmitted = { data.getValue() };
        this.pendingFields.add(() -> {
            String text = data.getValue();
            if (text.equals(lastSubmitted[0])) {
                return;
            }
            lastSubmitted[0] = text;
            CompoundTag parsed;
            try {
                parsed = EntityDisplayBlockEntity.parseSummonData(text, currentTypeIdOrNull());
            } catch (Exception failure) {
                this.parseError = Component.literal(String.valueOf(failure.getMessage()));
                return;
            }
            if (!parsed.contains(EntityDisplayBlockEntity.TAG_ID, Tag.TAG_STRING)) {
                this.parseError = Component.translatable("gui.display_block.entity_parse_no_id");
                return;
            }
            this.parseError = null;
            this.display.put(EntityDisplayBlockEntity.TAG_ENTITY, parsed);
            if (this.list != null) {
                this.list.setSelected(ResourceLocation.tryParse(
                        parsed.getString(EntityDisplayBlockEntity.TAG_ID)));
            }
        });
    }

    @Nullable
    private static ResourceLocation resolveTypeId(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(trimmed);
        if (id == null) {
            id = ResourceLocation.tryParse("minecraft:" + trimmed);
        }
        if (id == null || BuiltInRegistries.ENTITY_TYPE.get(id) == null) {
            return null;
        }
        return id;
    }

    /** 写入实体类型（只保留 id）。 */
    private void applyType(String typeId, boolean updateIdBox) {
        this.display.put(EntityDisplayBlockEntity.TAG_ENTITY, EntityDisplayBlockEntity.entityTagOf(typeId));
        if (this.list != null) {
            this.list.setSelected(ResourceLocation.tryParse(typeId));
        }
        if (updateIdBox && this.idBox != null) {
            this.idBox.setValue(typeId);
            this.lastFilter = typeId;
        }
        if (this.dataBox != null) {
            this.dataBox.setValue(currentEntitySnbt());
        }
        this.parseError = null;
    }

    private String currentEntitySnbt() {
        CompoundTag entity = this.display.getCompound(EntityDisplayBlockEntity.TAG_ENTITY);
        return entity.isEmpty() ? "" : entity.getAsString();
    }

    /** 从剪贴板读 F3+I 的输出并直接应用。 */
    private void loadFromClipboard() {
        String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (clipboard == null || clipboard.isBlank()) {
            this.parseError = Component.translatable("gui.display_block.entity_clipboard_empty");
            return;
        }
        if (this.dataBox != null) {
            this.dataBox.setValue(clipboard.strip());
        }
        applyPastedData(clipboard);
    }

    /** 解析并应用一段实体数据（支持 {@code /summon} 整条命令）。 */
    private void applyPastedData(String text) {
        CompoundTag parsed;
        try {
            parsed = EntityDisplayBlockEntity.parseSummonData(text, currentTypeIdOrNull());
        } catch (Exception failure) {
            this.parseError = Component.literal(String.valueOf(failure.getMessage()));
            return;
        }
        if (!parsed.contains(EntityDisplayBlockEntity.TAG_ID, Tag.TAG_STRING)) {
            this.parseError = Component.translatable("gui.display_block.entity_parse_no_id");
            return;
        }
        this.parseError = null;
        this.display.put(EntityDisplayBlockEntity.TAG_ENTITY, parsed);
        String id = parsed.getString(EntityDisplayBlockEntity.TAG_ID);
        if (this.list != null) {
            this.list.setSelected(ResourceLocation.tryParse(id));
        }
        if (this.idBox != null) {
            this.idBox.setValue(id);
            this.lastFilter = id;
        }
        if (this.dataBox != null) {
            this.dataBox.setValue(parsed.getAsString());
        }
    }

    private String currentTypeId() {
        ResourceLocation id = currentTypeIdOrNull();
        return id == null ? "" : id.toString();
    }

    @Nullable
    private ResourceLocation currentTypeIdOrNull() {
        CompoundTag entity = this.display.getCompound(EntityDisplayBlockEntity.TAG_ENTITY);
        if (!entity.contains(EntityDisplayBlockEntity.TAG_ID, Tag.TAG_STRING)) {
            return null;
        }
        return ResourceLocation.tryParse(entity.getString(EntityDisplayBlockEntity.TAG_ID));
    }

    // ───────────────────────── 预览 ─────────────────────────

    @Override
    protected void renderPreviewContent(GuiGraphics graphics, int x, int y, int width, int height) {
        ResourceLocation id = currentTypeIdOrNull();
        EntityType<?> type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.get(id);
        if (type == null) {
            graphics.drawString(this.font, Component.translatable("gui.display_block.preview.invalid"), x, y,
                    COLOR_RED, false);
            return;
        }
        int cursorY = previewWrapped(graphics, x, y, width, Component.literal(SafeNames.of(type)), COLOR_TEXT);
        cursorY = previewWrapped(graphics, x, cursorY, width, Component.literal(id.toString()), COLOR_MUTED);
        cursorY += 6;
        CompoundTag entity = this.display.getCompound(EntityDisplayBlockEntity.TAG_ENTITY);
        previewInfo(graphics, x, cursorY, width, "gui.display_block.preview.entity_data",
                Component.literal(Integer.toString(entity.getAllKeys().size())), COLOR_BLUE);
    }

    @Override
    protected void renderOrbitPreview(GuiGraphics graphics, PoseStack poseStack, float cameraYaw, float cameraPitch) {
        Level level = Minecraft.getInstance().level;
        CompoundTag entity = this.display.getCompound(EntityDisplayBlockEntity.TAG_ENTITY);
        if (level == null || entity.isEmpty()) {
            return;
        }
        Entity preview = EntityPreview.resolve(entity, level);
        if (preview == null) {
            return;
        }
        float[] rotation = EntityDisplayBlockEntity.rotationOf(entity);
        preview.setYRot(rotation[0]);
        preview.yRotO = rotation[0];
        preview.setXRot(rotation[1]);
        preview.xRotO = rotation[1];
        if (preview instanceof LivingEntity living) {
            living.yHeadRot = rotation[0];
            living.yHeadRotO = rotation[0];
            living.yBodyRot = rotation[0];
            living.yBodyRotO = rotation[0];
        }
        preview.tickCount = (int) Math.floorMod(level.getGameTime(), 100000L);

        poseStack.pushPose();
        DisplayBlockRenderSupport.applyDisplayTransform(poseStack,
                DisplayBlockEntityBase.readBillboard(this.display), effectiveTransformation(), cameraYaw, cameraPitch);
        // 脚踩方块底面
        poseStack.translate(0.0D, -0.5D, 0.0D);
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        try {
            dispatcher.render(preview, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F, poseStack, graphics.bufferSource(),
                    DisplayBlockRenderSupport.GUI_LIGHT);
        } catch (RuntimeException | StackOverflowError | LinkageError failure) {
            EntityPreview.markFailed();
        } finally {
            dispatcher.setRenderShadow(true);
            poseStack.popPose();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!this.contentBuilt || activeTab() != Tab.CONTENT) {
            return;
        }
        graphics.drawString(this.font, Component.translatable("gui.display_block.entity_hint"), contentLeft(),
                this.hintY, COLOR_MUTED, false);
        if (this.list != null && this.list.visible) {
            this.list.renderEmptyHint(graphics, this.font);
        }
        renderParseStatus(graphics);
    }

    /** 解析状态：成功就说一下当前是什么实体，失败把原因摊开。 */
    private void renderParseStatus(GuiGraphics graphics) {
        Component status;
        int color;
        ResourceLocation id = currentTypeIdOrNull();
        EntityType<?> type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.get(id);
        if (this.parseError != null) {
            status = Component.translatable("gui.display_block.entity_parse_error", this.parseError);
            color = COLOR_RED;
        } else if (type != null) {
            status = Component.translatable("gui.display_block.entity_parse_ok", SafeNames.of(type));
            color = COLOR_GREEN;
        } else {
            return;
        }
        int x = contentLeft() + 202;
        int maxWidth = Math.max(40, contentWidth() + 5 - 202);
        int y = this.parseStatusY;
        for (var line : this.font.split(status, maxWidth)) {
            if (y + 9 > contentBottom()) {
                break;
            }
            graphics.drawString(this.font, line, x, y, color, false);
            y += 10;
        }
    }

    /**
     * 预览用的假实体缓存：只在实体 NBT 的字符串内容变化时重建。
     * 缓存在界面类上而不是方块实体上——界面里改的是编辑副本，和世界渲染各管一份。
     */
    private static final class EntityPreview {

        private static String cachedTag;
        @Nullable
        private static Entity cachedEntity;
        private static boolean cachedFailed;

        @Nullable
        static Entity resolve(CompoundTag entityTag, Level level) {
            String key = entityTag.toString();
            if (!key.equals(cachedTag)) {
                cachedTag = key;
                cachedFailed = false;
                cachedEntity = create(entityTag, level);
            }
            return cachedFailed ? null : cachedEntity;
        }

        static void markFailed() {
            cachedFailed = true;
            cachedEntity = null;
        }

        @Nullable
        private static Entity create(CompoundTag entityTag, Level level) {
            try {
                var created = EntityType.create(entityTag, level);
                if (created.isEmpty()) {
                    cachedFailed = true;
                    return null;
                }
                return created.get();
            } catch (RuntimeException | StackOverflowError | LinkageError failure) {
                cachedFailed = true;
                return null;
            }
        }
    }
}
