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
import com.mojang.math.Axis;
import io.wifi.starrailexpress.client.gui.widget.ItemGridWidget;
import io.wifi.starrailexpress.client.render.block_entity.DisplayBlockRenderSupport;
import io.wifi.starrailexpress.client.util.SafeNames;
import io.wifi.starrailexpress.content.block_entity.DisplayBlockEntityBase;
import io.wifi.starrailexpress.content.block_entity.ItemDisplayBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 物品展示方块的编辑界面。
 *
 * <p>"内容"页签：物品 ID 输入框（同时当搜索词）+ 取主手 + 可滚动的物品网格；
 * item_display（物品变换）放在"外观"页签里。
 */
public class ItemDisplayBlockScreen extends DisplayBlockScreenBase {

    private static final List<String> TRANSFORMS = List.of("none", "thirdperson_lefthand", "thirdperson_righthand",
            "firstperson_lefthand", "firstperson_righthand", "head", "gui", "ground", "fixed");
    private static final int BUTTON_WIDTH = 100;
    private static final String NO_FILTER = "\u0000";

    private EditBox idBox;
    private ItemGridWidget grid;
    private String lastFilter = NO_FILTER;
    private int hintY;
    private boolean contentBuilt;

    public ItemDisplayBlockScreen(BlockPos pos, CompoundTag data) {
        super(Component.translatable("gui.display_block.item_display.title"), pos, data);
    }

    // ───────────────────────── 内容页签 ─────────────────────────

    @Override
    protected void buildContentTab() {
        int rowY = contentTop();
        addLabel(Component.translatable("gui.display_block.item_id"), contentLeft(), rowY + 5, COLOR_TEXT);

        int boxWidth = Math.max(60, contentWidth() - LABEL_W - BUTTON_WIDTH - 6);
        EditBox box = createBox(LABEL_W, boxWidth, currentItemId(), 128);
        box.setY(rowY);
        addRenderableWidget(box);
        this.idBox = box;
        this.lastFilter = box.getValue();

        addRenderableWidget(Button.builder(Component.translatable("gui.display_block.pick_held"),
                b -> pickHeldItem()).bounds(contentLeft() + contentWidth() - BUTTON_WIDTH, rowY,
                        BUTTON_WIDTH, 18).build());

        this.hintY = rowY + ROW_H;
        this.contentBuilt = true;

        int gridTop = this.hintY + 14;
        int gridHeight = Math.max(36, contentBottom() - gridTop);
        ItemGridWidget widget = ItemGridWidget.forItems(contentLeft(), gridTop, contentWidth() + 5, gridHeight,
                this::onItemPicked);
        widget.setSelected(currentItem());
        widget.setFilter(box.getValue());
        addRenderableWidget(widget);
        this.grid = widget;

        // 输入框同时是搜索词；手输完整 ID 时直接切换物品。
        this.pendingFields.add(() -> {
            if (this.idBox == null || this.grid == null) {
                return;
            }
            String text = this.idBox.getValue();
            if (text.equals(this.lastFilter)) {
                return;
            }
            this.lastFilter = text;
            this.grid.setFilter(text);
            ItemStack exact = resolveItem(text);
            if (!exact.isEmpty()) {
                applyItem(exact, false);
            }
        });
    }

    private void onItemPicked(ItemStack stack) {
        applyItem(stack, true);
    }

    /** 把选中的物品写进 NBT（连同组件，所以取主手能带上附魔/自定义名）。 */
    private void applyItem(ItemStack stack, boolean updateIdBox) {
        if (stack.isEmpty()) {
            return;
        }
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            this.display.put(ItemDisplayBlockEntity.TAG_ITEM, stack.save(level.registryAccess()));
        }
        if (this.grid != null) {
            this.grid.setSelected(stack);
        }
        if (updateIdBox && this.idBox != null) {
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            this.idBox.setValue(id);
            // 已经同步过了，别让过滤逻辑再按这个 ID 把列表筛成一条。
            this.lastFilter = id;
        }
    }

    private void pickHeldItem() {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            return;
        }
        applyItem(held.copy(), true);
    }

    private String currentItemId() {
        ItemStack stack = currentItem();
        return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** 解析 item 键（按数据版本缓存由方块实体负责，这里只是一次性读取）。 */
    private ItemStack currentItem() {
        CompoundTag tag = this.display.getCompound(ItemDisplayBlockEntity.TAG_ITEM);
        Level level = Minecraft.getInstance().level;
        if (tag.isEmpty() || level == null) {
            return ItemStack.EMPTY;
        }
        return ItemStack.parse(level.registryAccess(), tag).orElse(ItemStack.EMPTY);
    }

    /** 把输入解析成物品；空/无效返回空堆。 */
    private static ItemStack resolveItem(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(trimmed);
        if (id == null) {
            id = ResourceLocation.tryParse("minecraft:" + trimmed);
        }
        if (id == null) {
            return ItemStack.EMPTY;
        }
        var item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }

    // ───────────────────────── 外观页签：物品变换 ─────────────────────────

    @Override
    protected void buildAppearanceFields() {
        section("gui.display_block.section.item");
        wideRow(Component.translatable("gui.display_block.item_transform"),
                cycleButton(currentTransform().getSerializedName(), TRANSFORMS, "gui.display_block.item_transform.",
                        value -> this.display.putString(ItemDisplayBlockEntity.TAG_ITEM_DISPLAY, value)));
        hint("gui.display_block.item_transform_hint");
    }

    private ItemDisplayContext currentTransform() {
        return parseTransform(DisplayBlockEntityBase.readString(this.display,
                ItemDisplayBlockEntity.TAG_ITEM_DISPLAY,
                ItemDisplayBlockEntity.DEFAULT_TRANSFORM.getSerializedName()));
    }

    private static ItemDisplayContext parseTransform(String name) {
        for (ItemDisplayContext context : ItemDisplayContext.values()) {
            if (context.getSerializedName().equals(name)) {
                return context;
            }
        }
        return ItemDisplayBlockEntity.DEFAULT_TRANSFORM;
    }

    // ───────────────────────── 预览 ─────────────────────────

    @Override
    protected void renderPreviewContent(GuiGraphics graphics, int x, int y, int width, int height) {
        ItemStack stack = currentItem();
        if (stack.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("gui.display_block.preview.invalid"), x, y,
                    COLOR_RED, false);
            return;
        }
        // 2 倍大的图标：renderItem(stack,0,0) 在 2 倍缩放下正好铺满 (x,y)-(x+32,y+32)
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(2.0F, 2.0F, 1.0F);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
        graphics.renderOutline(x, y, 32, 32, CARD_BORDER);

        int cursorY = y + 40;
        cursorY = previewWrapped(graphics, x, cursorY, width, Component.literal(SafeNames.of(stack.getItem())),
                COLOR_TEXT);
        cursorY = previewWrapped(graphics, x, cursorY, width, Component.literal(currentItemId()), COLOR_MUTED);
        if (stack.getCount() > 1) {
            cursorY = previewWrapped(graphics, x, cursorY, width,
                    Component.literal("x" + stack.getCount()), COLOR_BLUE);
        }
        cursorY += 6;
        previewInfo(graphics, x, cursorY, width, "gui.display_block.preview.item_transform",
                Component.translatable("gui.display_block.item_transform." + currentTransform().getSerializedName()),
                COLOR_BLUE);
    }

    @Override
    protected void renderOrbitPreview(GuiGraphics graphics, PoseStack poseStack, float cameraYaw, float cameraPitch) {
        ItemStack stack = currentItem();
        Level level = Minecraft.getInstance().level;
        if (stack.isEmpty() || level == null) {
            return;
        }
        int light = DisplayBlockRenderSupport.GUI_LIGHT;
        int seed = (int) Math.floorMod(this.pos.asLong(), 1024L);

        poseStack.pushPose();
        DisplayBlockRenderSupport.applyDisplayTransform(poseStack,
                DisplayBlockEntityBase.readBillboard(this.display), effectiveTransformation(), cameraYaw, cameraPitch);
        // 物品模型没有 renderItem 那种内置平移，直接按 1 方块 = 1 单位缩放即可
        poseStack.mulPose(Axis.YP.rotation((float) Math.PI));
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, currentTransform(), light,
                OverlayTexture.NO_OVERLAY, poseStack,
                graphics.bufferSource(), level, seed);
        poseStack.popPose();
    }
}
