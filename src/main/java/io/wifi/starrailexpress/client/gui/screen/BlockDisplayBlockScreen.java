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
import io.wifi.starrailexpress.client.gui.widget.BlockGridWidget;
import io.wifi.starrailexpress.client.render.block_entity.DisplayBlockRenderSupport;
import io.wifi.starrailexpress.client.util.BlockNames;
import io.wifi.starrailexpress.content.block_entity.BlockDisplayBlockEntity;
import io.wifi.starrailexpress.content.block_entity.DisplayBlockEntityBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Map;

/**
 * 方块展示方块的编辑界面。
 *
 * <p>"内容"页签上面是方块 ID 输入框（同时当搜索词）和"取主手方块"按钮，
 * 下面是可以滚动的方块网格，点图标即选中；右侧预览画物品图标、方块名和方块状态。
 */
public class BlockDisplayBlockScreen extends DisplayBlockScreenBase {

    private static final int BUTTON_WIDTH = 100;
    private static final String NO_FILTER = "\u0000";

    private EditBox idBox;
    private BlockGridWidget grid;
    private String lastFilter = NO_FILTER;
    private int hintY;
    private boolean contentBuilt;

    /** 预览和 ID 框每帧都要问"现在展示的是哪个方块"，按 block_state 的内容缓存一次解析结果。 */
    private BlockState cachedBlockState;
    private String cachedBlockKey;

    public BlockDisplayBlockScreen(BlockPos pos, CompoundTag data) {
        super(Component.translatable("gui.display_block.block_display.title"), pos, data);
    }

    // ───────────────────────── 内容页签 ─────────────────────────

    @Override
    protected void buildContentTab() {
        int rowY = contentTop();
        addLabel(Component.translatable("gui.display_block.block_id"), contentLeft(), rowY + 5, COLOR_TEXT);

        int boxWidth = Math.max(60, contentWidth() - LABEL_W - BUTTON_WIDTH - 6);
        EditBox box = createBox(LABEL_W, boxWidth, currentBlockId(), 128);
        box.setY(rowY);
        addRenderableWidget(box);
        this.idBox = box;
        this.lastFilter = box.getValue();

        addRenderableWidget(Button.builder(Component.translatable("gui.display_block.pick_held"), b -> pickHeldBlock())
                .bounds(contentLeft() + contentWidth() - BUTTON_WIDTH, rowY, BUTTON_WIDTH, 18).build());

        this.hintY = rowY + ROW_H;
        this.contentBuilt = true;

        int gridTop = this.hintY + 14;
        int gridHeight = Math.max(36, contentBottom() - gridTop);
        BlockGridWidget widget = new BlockGridWidget(contentLeft(), gridTop, contentWidth() + 5, gridHeight,
                this::onBlockPicked);
        widget.setSelected(currentBlockState().getBlock());
        widget.setFilter(box.getValue());
        addRenderableWidget(widget);
        this.grid = widget;

        // 输入框同时是搜索词；手输完整 ID 时直接切换展示方块。
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
            Block exact = resolveBlock(text);
            if (exact != null) {
                applyBlock(exact, false);
            }
        });
    }

    /** 网格里点了图标。 */
    private void onBlockPicked(Block block) {
        applyBlock(block, true);
    }

    private void applyBlock(Block block, boolean updateIdBox) {
        if (block == null) {
            return;
        }
        this.display.put(BlockDisplayBlockEntity.TAG_BLOCK_STATE, NbtUtils.writeBlockState(block.defaultBlockState()));
        if (this.grid != null) {
            this.grid.setSelected(block);
        }
        if (updateIdBox && this.idBox != null) {
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            this.idBox.setValue(id);
            // 已经同步过了，别让过滤逻辑再按这个 ID 把列表筛成一条。
            this.lastFilter = id;
        }
    }

    /** 取玩家主手的方块，方便直接复用手里那个方块。 */
    private void pickHeldBlock() {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            return;
        }
        Block block = Block.byItem(held.getItem());
        if (block == Blocks.AIR) {
            return;
        }
        applyBlock(block, true);
    }

    /** 当前展示的方块 ID。 */
    private String currentBlockId() {
        return BuiltInRegistries.BLOCK.getKey(currentBlockState().getBlock()).toString();
    }

    /**
     * 解析 block_state 得到的完整状态（含属性，属性是 SNBT 里手改的话也认），
     * 结果按 NBT 内容缓存，避免每帧解析一次。
     */
    private BlockState currentBlockState() {
        CompoundTag stateTag = this.display.getCompound(BlockDisplayBlockEntity.TAG_BLOCK_STATE);
        String key = stateTag.toString();
        if (this.cachedBlockState != null && key.equals(this.cachedBlockKey)) {
            return this.cachedBlockState;
        }
        BlockState resolved = BlockDisplayBlockEntity.DEFAULT_BLOCK_STATE;
        if (!stateTag.isEmpty()) {
            BlockState parsed = NbtUtils.readBlockState(blockHolderGetter(), stateTag);
            if (!parsed.isAir()) {
                resolved = parsed;
            }
        }
        this.cachedBlockState = resolved;
        this.cachedBlockKey = key;
        return resolved;
    }

    private HolderGetter<Block> blockHolderGetter() {
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            return level.holderLookup(Registries.BLOCK);
        }
        return BuiltInRegistries.BLOCK.asLookup();
    }

    /** 把输入解析成方块；没有对应物品的方块（水、火等）不接受，免得选了看不见。 */
    private static Block resolveBlock(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(trimmed);
        if (id == null) {
            id = ResourceLocation.tryParse("minecraft:" + trimmed);
        }
        if (id == null) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block == null || block.asItem() == Items.AIR) {
            return null;
        }
        return block;
    }

    // ───────────────────────── 预览 ─────────────────────────

    @Override
    protected void renderPreviewContent(GuiGraphics graphics, int x, int y, int width, int height) {
        BlockState state = currentBlockState();
        ItemStack stack = new ItemStack(state.getBlock());

        // 2 倍大的图标，外面套一圈描边。
        // renderItem(stack, i, j) 内部是把 16×16 图标的**中心**放到 (i+8, j+8)，
        // 外面套了 2 倍缩放后中心就落在 (x + 2*(i+8), y + 2*(j+8))，
        // 所以要图标正好铺满 (x,y)-(x+32,y+32)，i/j 必须是 0。
        int iconSize = 32;
        if (!stack.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0.0F);
            graphics.pose().scale(2.0F, 2.0F, 1.0F);
            graphics.renderItem(stack, 0, 0);
            graphics.pose().popPose();
        }
        graphics.renderOutline(x, y, iconSize, iconSize, CARD_BORDER);

        int cursorY = y + iconSize + 8;
        cursorY = previewWrapped(graphics, x, cursorY, width, Component.literal(blockName(state)), COLOR_TEXT);
        cursorY = previewWrapped(graphics, x, cursorY, width, Component.literal(currentBlockId()), COLOR_MUTED);
        cursorY += 6;

        Map<Property<?>, Comparable<?>> values = state.getValues();
        if (values.isEmpty()) {
            previewInfo(graphics, x, cursorY, width, "gui.display_block.preview.properties",
                    Component.translatable("gui.display_block.preview.no_properties"), COLOR_MUTED);
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<Property<?>, Comparable<?>> entry : values.entrySet()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(entry.getKey().getName()).append('=').append(propertyValue(entry));
        }
        previewInfo(graphics, x, cursorY, width, "gui.display_block.preview.properties",
                Component.literal(builder.toString()), COLOR_BLUE);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String propertyValue(Map.Entry<Property<?>, Comparable<?>> entry) {
        return ((Property) entry.getKey()).getName((Comparable) entry.getValue());
    }

    /**
     * 预览里的方块名走安全取名字：个别第三方方块（例如 wathextras 的 WallCandelabreBlock）
     * 的 getDescriptionId 会和自己物品互相递归，直接调用会栈溢出。
     */
    private static String blockName(BlockState state) {
        return BlockNames.of(state.getBlock());
    }

    /**
     * 预览页签的 3D 视口：用原版在 GUI 里画 3D 物品的那条路径（{@code GuiGraphics.renderItem}）
     * 画方块模型，再叠上展示数据自己的变换，所以旋转/缩放/动画在预览里都能看到。
     */
    @Override
    protected void renderOrbitPreview(GuiGraphics graphics, PoseStack poseStack, float cameraYaw, float cameraPitch) {
        BlockState state = currentBlockState();
        ItemStack stack = new ItemStack(state.getBlock());
        if (stack.isEmpty()) {
            // 没有对应物品的方块（水、火之类）拿不到物品模型，画不出来
            return;
        }

        poseStack.pushPose();
        DisplayBlockRenderSupport.applyDisplayTransform(poseStack,
                DisplayBlockEntityBase.readBillboard(this.display), effectiveTransformation(), cameraYaw, cameraPitch);
        // 预览约定"1 方块 = 1 单位"，而 renderItem 内部会按 16 倍缩放并把 y 轴翻到 GUI 方向，
        // 所以这里预先抵消掉这两件事，方块就正好占一个单位、而且是正立向上的。
        poseStack.scale(1.0F / 16.0F, -1.0F / 16.0F, 1.0F);
        graphics.renderItem(stack, 0, 0);
        poseStack.popPose();
    }

    // ───────────────────────── 渲染 ─────────────────────────

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // 这几个控件只属于内容页签；切页签后字段是上一轮的残留，别再画一遍。
        if (!this.contentBuilt || activeTab() != Tab.CONTENT) {
            return;
        }
        graphics.drawString(this.font, Component.translatable("gui.display_block.block_search_hint"), contentLeft(),
                this.hintY, COLOR_MUTED, false);
        if (this.grid != null && this.grid.visible) {
            this.grid.renderEmptyHint(graphics, this.font);
        }
    }
}
