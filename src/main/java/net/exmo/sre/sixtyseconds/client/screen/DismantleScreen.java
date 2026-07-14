package net.exmo.sre.sixtyseconds.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsDismantle;
import net.exmo.sre.sixtyseconds.network.DismantleC2SPacket;
import net.exmo.sre.sixtyseconds.network.OpenDismantleS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 拆解台界面：列出背包里<b>可拆解</b>的物品与拆解产物（{@link SixtySecondsDismantle} 两端共享
 * 静态表，返还率 -60%），点「拆解」发 C2S 由服务端校验成交。背包变化时自动刷新行。
 *
 * <p>风格与 {@link StationCraftScreen} 一致（docs/ui_style.md：深棕渐变面板 + 金色装饰）。</p>
 */
public class DismantleScreen extends Screen {

    // ── 配色常量（来自 ui_style.md §2.1，与 StationCraftScreen 相同）──
    private static final int BG_TOP = 0xD81A1008;
    private static final int BG_BOTTOM = 0xD820140A;
    private static final int BORDER = 0xFF8B6914;
    private static final int DECORATION_LINE = 0x33FFE8C0;
    private static final int GOLD = 0xFFD4AF37;
    private static final int TEXT = 0xFFFFF4DC;
    private static final int MUTED = 0xFF9E8B6E;
    private static final int HOVER_BG = 0x22FFFFFF;
    private static final int ROW_SEPARATOR = 0x20FFFFFF;

    // ── 布局常量 ─────────────────────────────────────────────────
    private static final int PAD = 10;
    private static final int ROW_H = 36;
    private static final int HEADER_H = 52;

    private final BlockPos pos;
    /** 当前可拆解的行（背包里持有且在拆解表中的物品）。 */
    private List<SixtySecondsDismantle.Entry> rows = List.of();

    private int scrollOffset;
    private int panelX, panelY, panelW, panelH;
    private int listTop, listBottom;
    private int hoveredRow = -1;

    public DismantleScreen(OpenDismantleS2CPacket data) {
        super(Component.translatable("station.noellesroles.sixty_seconds.dismantler"));
        this.pos = data.pos();
    }

    // ── 行数据：背包持有 ∩ 拆解表（保持拆解表顺序，两端一致）────────

    private static List<SixtySecondsDismantle.Entry> computeRows() {
        Minecraft client = Minecraft.getInstance();
        List<SixtySecondsDismantle.Entry> result = new ArrayList<>();
        if (client.player == null) {
            return result;
        }
        for (SixtySecondsDismantle.Entry entry : SixtySecondsDismantle.all().values()) {
            if (countInInventory(entry.input()) > 0) {
                result.add(entry);
            }
        }
        return result;
    }

    private static int countInInventory(Item item) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return 0;
        int have = 0;
        for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = client.player.getInventory().getItem(slot);
            if (stack.is(item)) have += stack.getCount();
        }
        return have;
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
        return Math.max(0, rows.size() - visibleRows());
    }

    // ── 初始化 / 刷新 ─────────────────────────────────────────────

    @Override
    protected void init() {
        rows = computeRows();
        rebuildButtons();
    }

    /** 背包变化（拆解成交/捡拾）→ 重算行并重建按钮。 */
    @Override
    public void tick() {
        super.tick();
        List<SixtySecondsDismantle.Entry> next = computeRows();
        if (!next.equals(rows)) {
            rows = next;
            rebuildButtons();
        }
    }

    private void rebuildButtons() {
        clearWidgets();
        computeLayout();
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll());
        final int btnY = panelY + panelH - PAD - 22;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(panelX + panelW - 52, btnY, 42, 20).build());

        int visible = visibleRows();
        for (int row = 0; row < visible; row++) {
            int index = scrollOffset + row;
            if (index >= rows.size()) break;
            SixtySecondsDismantle.Entry entry = rows.get(index);
            String itemId = BuiltInRegistries.ITEM.getKey(entry.input()).toString();
            int btnX = panelX + panelW - PAD - 64;
            int btnRowY = listTop + row * ROW_H + 4;
            addRenderableWidget(Button.builder(
                    Component.translatable("message.noellesroles.sixty_seconds.dismantle_btn"),
                    b -> ClientPlayNetworking.send(new DismantleC2SPacket(itemId, pos)))
                    .bounds(btnX, btnRowY, 54, 20).build());
        }
    }

    // ── 滚动 ──────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = maxScroll();
        int next = Mth.clamp(scrollOffset - (int) Math.signum(scrollY), 0, max);
        if (next != scrollOffset) {
            scrollOffset = next;
            rebuildButtons();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mouseY);
        int old = hoveredRow;
        hoveredRow = -1;
        if (mouseX >= panelX && mouseX < panelX + panelW - PAD - 72
                && mouseY >= listTop && mouseY < listBottom) {
            int row = ((int) mouseY - listTop) / ROW_H;
            if (row >= 0 && row < visibleRows() && scrollOffset + row < rows.size()) {
                hoveredRow = row;
            }
        }
        if (hoveredRow != old) {
            rebuildButtons();
        }
    }

    // ── 绘制 ──────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        drawPanel(g);
        drawHeader(g);
        drawRows(g);
    }

    private void drawPanel(GuiGraphics g) {
        g.fillGradient(panelX, panelY, panelX + panelW, panelY + panelH, BG_TOP, BG_BOTTOM);
        g.renderOutline(panelX, panelY, panelW, panelH, BORDER);
        g.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + 2, DECORATION_LINE);
    }

    private void drawHeader(GuiGraphics g) {
        g.drawCenteredString(this.font, this.title,
                panelX + panelW / 2, panelY + PAD + 2, GOLD);
        g.drawCenteredString(this.font,
                Component.translatable("message.noellesroles.sixty_seconds.dismantle_hint"),
                panelX + panelW / 2, panelY + PAD + 16, MUTED);
        int sepY = panelY + HEADER_H;
        g.fill(panelX + PAD, sepY, panelX + panelW - PAD, sepY + 1, ROW_SEPARATOR);
    }

    private void drawRows(GuiGraphics g) {
        if (rows.isEmpty()) {
            g.drawCenteredString(this.font,
                    Component.translatable("message.noellesroles.sixty_seconds.dismantle_none"),
                    panelX + panelW / 2, (listTop + listBottom) / 2 - 4, MUTED);
            return;
        }
        int visible = visibleRows();
        RenderSystem.enableScissor(
                (int) (panelX * Minecraft.getInstance().getWindow().getGuiScale()),
                (int) (Minecraft.getInstance().getWindow().getHeight()
                        - (listBottom) * Minecraft.getInstance().getWindow().getGuiScale()),
                (int) ((panelW - PAD * 2) * Minecraft.getInstance().getWindow().getGuiScale()),
                (int) ((listBottom - listTop) * Minecraft.getInstance().getWindow().getGuiScale()));

        for (int row = 0; row < visible; row++) {
            int index = scrollOffset + row;
            if (index >= rows.size()) break;
            SixtySecondsDismantle.Entry entry = rows.get(index);
            int y = listTop + row * ROW_H;

            if (row == hoveredRow) {
                g.fill(panelX + PAD, y, panelX + panelW - PAD, y + ROW_H - 2, HOVER_BG);
            }

            // 输入物品图标 + 名称 ×持有数
            ItemStack input = new ItemStack(entry.input());
            g.renderFakeItem(input, panelX + PAD + 2, y + (ROW_H - 18) / 2);
            MutableComponent name = Component.empty()
                    .append(input.getHoverName().copy())
                    .append(Component.literal(" x" + countInInventory(entry.input()))
                            .withStyle(ChatFormatting.GRAY));
            g.drawString(this.font, name, panelX + PAD + 24, y + 3, TEXT);

            // 产物行
            MutableComponent outputs = Component.literal("→ ").withStyle(ChatFormatting.DARK_GRAY);
            for (int i = 0; i < entry.outputs().size(); i++) {
                ItemStack output = entry.outputs().get(i);
                if (i > 0) outputs.append(Component.literal(" + ").withStyle(ChatFormatting.DARK_GRAY));
                outputs.append(Component.literal(output.getCount() + "x ")
                        .withStyle(ChatFormatting.WHITE))
                        .append(output.getHoverName().copy());
            }
            g.drawString(this.font, outputs, panelX + PAD + 24, y + 17, MUTED);

            if (row < visible - 1 && index < rows.size() - 1) {
                g.fill(panelX + PAD + 4, y + ROW_H - 1,
                        panelX + panelW - PAD - 76, y + ROW_H, ROW_SEPARATOR);
            }
        }

        RenderSystem.disableScissor();
        drawScrollBar(g);
    }

    private void drawScrollBar(GuiGraphics g) {
        int max = maxScroll();
        if (max <= 0) return;
        int trackX = panelX + panelW - PAD - 70;
        int trackH = listBottom - listTop;
        g.fill(trackX, listTop, trackX + 4, listTop + trackH, 0x20FFFFFF);
        int thumbH = Math.max(20, trackH * visibleRows() / rows.size());
        int thumbY = listTop + (trackH - thumbH) * scrollOffset / max;
        g.fill(trackX, thumbY, trackX + 4, thumbY + thumbH, GOLD);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
