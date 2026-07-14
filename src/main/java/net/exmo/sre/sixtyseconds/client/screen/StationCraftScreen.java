package net.exmo.sre.sixtyseconds.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsRecipes;
import net.exmo.sre.sixtyseconds.network.OpenStationS2CPacket;
import net.exmo.sre.sixtyseconds.network.StationCraftC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 合成站界面：列出该站（书桌/灶台/浴缸）的全部配方；材料/科技/供电不满足则按钮置灰。
 * 服务端最终校验，客户端置灰只是提示。
 *
 * <p>风格参考 {@code docs/ui_style.md}：深棕渐变面板 + 棕褐描边 + 金色装饰线。</p>
 */
public class StationCraftScreen extends Screen {

    // ── 配色常量（来自 ui_style.md §2.1）─────────────────────────
    private static final int BG_TOP = 0xD81A1008;
    private static final int BG_BOTTOM = 0xD820140A;
    private static final int BORDER = 0xFF8B6914;
    private static final int DECORATION_LINE = 0x33FFE8C0;
    private static final int GOLD = 0xFFD4AF37;
    private static final int TEXT = 0xFFFFF4DC;
    private static final int MUTED = 0xFF9E8B6E;
    private static final int GREEN = 0xFF72C17B;
    private static final int RED = 0xFFE06B65;
    private static final int HOVER_BG = 0x22FFFFFF;
    private static final int ROW_SEPARATOR = 0x20FFFFFF;

    // ── 布局常量 ─────────────────────────────────────────────────
    private static final int PAD = 10;
    private static final int ROW_H = 36;
    private static final int HEADER_H = 52;

    private final SixtySecondsRecipes.Station station;
    private final BlockPos pos;
    private final Set<String> unlockedTech;
    private final boolean powered;
    private final List<SixtySecondsRecipes.Recipe> recipes;

    private int scrollOffset;
    private int panelX, panelY, panelW, panelH;
    private int listTop, listBottom;
    private int hoveredRow = -1;

    public StationCraftScreen(OpenStationS2CPacket data) {
        super(Component.translatable(
                SixtySecondsRecipes.Station.values()[data.station()].translationKey()));
        this.station = SixtySecondsRecipes.Station.values()[data.station()];
        this.pos = data.pos();
        this.unlockedTech = new HashSet<>(Arrays.asList(data.unlockedTech()));
        this.powered = data.powered();
        this.recipes = SixtySecondsRecipes.forStation(station);
    }

    // ── 布局计算 ──────────────────────────────────────────────────

    private void computeLayout() {
        panelW = Mth.clamp((int) (this.width * 0.78f), 420, 700);
        panelH = Mth.clamp((int) (this.height * 0.76f), 280, 460);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        listTop = panelY + HEADER_H + PAD;
        listBottom = panelY + panelH - PAD - 28;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom - listTop) / ROW_H);
    }

    private int maxScroll() {
        return Math.max(0, recipes.size() - visibleRows());
    }

    // ── 初始化 ────────────────────────────────────────────────────

    @Override
    protected void init() {
        computeLayout();
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll());

        final int btnY = panelY + panelH - PAD - 22;
        // 关闭按钮 —— 右上角
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(panelX + panelW - 52, btnY, 42, 20).build());
        rebuildCraftButtons();
    }

    private void rebuildCraftButtons() {
        clearWidgets();
        computeLayout();
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll());
        final int btnY = panelY + panelH - PAD - 22;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(panelX + panelW - 52, btnY, 42, 20).build());

        int visible = visibleRows();
        for (int row = 0; row < visible; row++) {
            int index = scrollOffset + row;
            if (index >= recipes.size()) break;
            SixtySecondsRecipes.Recipe recipe = recipes.get(index);
            boolean canCraft = canCraft(recipe);
            int btnX = panelX + panelW - PAD - 64;
            int btnRowY = listTop + row * ROW_H + 4;
            Button craft = Button.builder(
                    Component.translatable("message.noellesroles.sixty_seconds.craft_btn"),
                    b -> ClientPlayNetworking.send(new StationCraftC2SPacket(recipe.id(), pos)))
                    .bounds(btnX, btnRowY, 54, 20).build();
            craft.active = canCraft;
            addRenderableWidget(craft);
        }
    }

    // ── 材料检查 ──────────────────────────────────────────────────

    private boolean canCraft(SixtySecondsRecipes.Recipe recipe) {
        if (!unlockedTech.contains(recipe.techId())) return false;
        if (recipe.needsPower() && !powered) return false;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return false;
        for (SixtySecondsRecipes.Ingredient input : recipe.inputs()) {
            int have = 0;
            for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = client.player.getInventory().getItem(slot);
                if (stack.is(input.item())) have += stack.getCount();
            }
            if (have < input.count()) return false;
        }
        return true;
    }

    // ── 滚动 ──────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = maxScroll();
        int next = Mth.clamp(scrollOffset - (int) Math.signum(scrollY), 0, max);
        if (next != scrollOffset) {
            scrollOffset = next;
            rebuildCraftButtons();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mouseY);
        int old = hoveredRow;
        hoveredRow = -1;
        if (isInPanel(mouseX, mouseY)) {
            int relY = (int) mouseY - listTop;
            int row = relY / ROW_H;
            int idx = scrollOffset + row;
            if (row >= 0 && row < visibleRows() && idx < recipes.size()) {
                int contentX0 = panelX + PAD;
                int contentX1 = panelX + panelW - PAD - 72;
                if (mouseX >= contentX0 && mouseX < contentX1) {
                    hoveredRow = row;
                }
            }
        }
        if (hoveredRow != old) {
            rebuildCraftButtons();
        }
    }

    private boolean isInPanel(double mx, double my) {
        return mx >= panelX && mx < panelX + panelW && my >= panelY && my < panelY + panelH;
    }

    // ── 绘制 ──────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        drawPanel(g);
        drawHeader(g);
        drawRecipeList(g, mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
    }

    /** 绘制深棕渐变面板 + 边框 + 装饰线（§3 范式）。 */
    private void drawPanel(GuiGraphics g) {
        // 上下渐变背景
        g.fillGradient(panelX, panelY, panelX + panelW, panelY + panelH, BG_TOP, BG_BOTTOM);
        // 棕褐色描边
        g.renderOutline(panelX, panelY, panelW, panelH, BORDER);
        // 上边缘装饰线（内侧 1px）
        g.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + 2, DECORATION_LINE);
    }

    /** 绘制标题栏（标题 + 电力状态）。 */
    private void drawHeader(GuiGraphics g) {
        // 标题：金色粗体
        g.drawCenteredString(this.font, this.title,
                panelX + panelW / 2, panelY + PAD + 2, GOLD);

        // 供电状态
        MutableComponent powerLine = Component.translatable(powered
                ? "message.noellesroles.sixty_seconds.station_powered"
                : "message.noellesroles.sixty_seconds.station_unpowered")
                .withStyle(powered ? ChatFormatting.GREEN : ChatFormatting.GRAY);
        g.drawCenteredString(this.font, powerLine,
                panelX + panelW / 2, panelY + PAD + 16, powered ? GREEN : MUTED);

        // 标题下方分隔线
        int sepY = panelY + HEADER_H;
        g.fill(panelX + PAD, sepY, panelX + panelW - PAD, sepY + 1, ROW_SEPARATOR);
    }

    /** 绘制配方列表（scissor 裁剪 + hover 高亮）。 */
    private void drawRecipeList(GuiGraphics g, int mouseX, int mouseY) {
        int visible = visibleRows();
        if (visible <= 0) return;

        // scissor 裁剪区域
        RenderSystem.enableScissor(
                (int) (panelX * Minecraft.getInstance().getWindow().getGuiScale()),
                (int) (Minecraft.getInstance().getWindow().getHeight()
                        - (listBottom) * Minecraft.getInstance().getWindow().getGuiScale()),
                (int) ((panelW - PAD * 2) * Minecraft.getInstance().getWindow().getGuiScale()),
                (int) ((listBottom - listTop) * Minecraft.getInstance().getWindow().getGuiScale()));

        for (int row = 0; row < visible; row++) {
            int index = scrollOffset + row;
            if (index >= recipes.size()) break;

            SixtySecondsRecipes.Recipe recipe = recipes.get(index);
            int y = listTop + row * ROW_H;

            // hover 高亮
            if (row == hoveredRow) {
                g.fill(panelX + PAD, y, panelX + panelW - PAD, y + ROW_H - 2, HOVER_BG);
            }

            boolean techOk = unlockedTech.contains(recipe.techId());
            boolean canCraft = techOk && (!recipe.needsPower() || powered);

            // 输出物品图标 + 名称
            ItemStack output = SixtySecondsRecipes.outputStack(recipe);
            g.renderFakeItem(output, panelX + PAD + 2, y + (ROW_H - 18) / 2);

            MutableComponent outputName = Component.empty()
                    .append(output.getHoverName().copy())
                    .append(Component.literal(" x" + recipe.outputCount()));
            int nameColor = canCraft ? TEXT : MUTED;
            g.drawString(this.font, outputName,
                    panelX + PAD + 24, y + 3, nameColor);

            // 材料行
            MutableComponent inputs = Component.literal("← ").withStyle(ChatFormatting.DARK_GRAY);
            for (int i = 0; i < recipe.inputs().size(); i++) {
                SixtySecondsRecipes.Ingredient input = recipe.inputs().get(i);
                if (i > 0) inputs.append(Component.literal(" + ").withStyle(ChatFormatting.DARK_GRAY));

                // 检查该材料是否足够
                int have = countInInventory(input);
                boolean enough = have >= input.count();
                int matColor = enough ? 0xFFC8B898 : RED;

                inputs.append(Component.literal("" + input.count() + "x ")
                        .withStyle(enough ? ChatFormatting.WHITE : ChatFormatting.RED))
                        .append(input.item().getDescription().copy());
            }

            // 条件标签：供电 / 科技未解锁
            if (recipe.needsPower()) {
                inputs.append(Component.literal(" ")
                        .append(Component.translatable("message.noellesroles.sixty_seconds.needs_power")
                                .withStyle(ChatFormatting.AQUA)));
            }
            if (!techOk) {
                inputs.append(Component.literal(" [")
                        .append(Component.translatable("tech.noellesroles.sixty_seconds." + recipe.techId()))
                        .append(Component.literal("]")).withStyle(ChatFormatting.RED));
            }

            g.drawString(this.font, inputs, panelX + PAD + 24, y + 17, MUTED);

            // 行间分隔线
            if (row < visible - 1 && index < recipes.size() - 1) {
                g.fill(panelX + PAD + 4, y + ROW_H - 1,
                        panelX + panelW - PAD - 76, y + ROW_H, ROW_SEPARATOR);
            }
        }

        RenderSystem.disableScissor();

        // 滚动条
        drawScrollBar(g);
    }

    /** 绘制右侧复古滚动条（§4 样式）。 */
    private void drawScrollBar(GuiGraphics g) {
        int max = maxScroll();
        if (max <= 0) return;

        int trackX = panelX + panelW - PAD - 70;
        int trackW = 4;
        int trackH = listBottom - listTop;
        int trackY = listTop;

        // 轨道
        g.fill(trackX, trackY, trackX + trackW, trackY + trackH, 0x20FFFFFF);

        // thumb
        int thumbH = Math.max(20, trackH * visibleRows() / recipes.size());
        int thumbY = trackY + (trackH - thumbH) * scrollOffset / max;
        g.fill(trackX, thumbY, trackX + trackW, thumbY + thumbH, GOLD);
    }

    /** 统计背包中某物品的总数。 */
    private int countInInventory(SixtySecondsRecipes.Ingredient input) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return 0;
        int have = 0;
        for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = client.player.getInventory().getItem(slot);
            if (stack.is(input.item())) have += stack.getCount();
        }
        return have;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
