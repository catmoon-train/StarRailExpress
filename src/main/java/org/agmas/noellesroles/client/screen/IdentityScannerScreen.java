package org.agmas.noellesroles.client.screen;

import java.util.List;
import org.agmas.noellesroles.client.IdentityScannerClient;
import org.agmas.noellesroles.packet.IdentityScannerRequestC2SPacket;
import org.agmas.noellesroles.packet.IdentityScannerStateS2CPacket;
import org.agmas.noellesroles.packet.IdentityScannerStateS2CPacket.Entry;
import org.agmas.noellesroles.utils.MoneyUtils;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** 黑底青绿终端；历史从旧到新，默认停在底部，所有交易只提交服务端请求。 */
public final class IdentityScannerScreen extends Screen {
    public static final String KEY = "gui.noellesroles.identity_scanner.";
    public static final int SCAN_PRICE = 50;
    private static final int TEXT = 0xFF42E8B4;
    private static final int MUTED = 0xFF419985;
    private static final int BORDER = 0xFF216B60;
    private static final int ROW_HEIGHT = 14;
    private final BlockPos pos;
    private final ClientLevel openedLevel;
    private List<Entry> history;
    private int cooldownTicks;
    private int refreshTicks;
    private int scrollOffset;
    private int x, y, panelWidth, panelHeight;
    private Button scanButton, olderButton, newerButton;

    public IdentityScannerScreen(IdentityScannerStateS2CPacket state, ClientLevel level) {
        super(Component.translatable(KEY + "title"));
        pos = state.pos();
        openedLevel = level;
        history = state.history();
        cooldownTicks = state.cooldownTicks();
    }

    public BlockPos pos() {
        return pos;
    }

    public boolean isValid(Minecraft client) {
        return client.level == openedLevel && IdentityScannerClient.canView(client, pos);
    }

    /** 同一机器原位更新，保留用户浏览位置；新记录到达时默认跟随底部。 */
    public void updateState(IdentityScannerStateS2CPacket state) {
        if (!pos.equals(state.pos())) {
            return;
        }
        if (scrollOffset > 0) {
            scrollOffset += Math.max(0, state.history().size() - history.size());
        }
        history = state.history();
        cooldownTicks = state.cooldownTicks();
        scrollOffset = Math.min(scrollOffset, maxScroll());
        updateButtons();
    }

    /** 由统一客户端 tick 调用，轮询不因局内时间冻结而停止。 */
    public void clientTick() {
        if (!minecraft.isPaused() && !SREGameTimeComponent.KEY.get(openedLevel).isTimeFrozen()
                && cooldownTicks > 0) {
            cooldownTicks--;
        }
        if (++refreshTicks >= 20) {
            refreshTicks = 0;
            ClientPlayNetworking.send(new IdentityScannerRequestC2SPacket(pos, false));
        }
        updateButtons();
    }

    @Override
    protected void init() {
        panelWidth = Math.min(540, width - 16);
        panelHeight = Math.min(330, height - 16);
        x = (width - panelWidth) / 2;
        y = (height - panelHeight) / 2;
        int buttonY = y + panelHeight - 28;
        scanButton = addRenderableWidget(new TerminalButton(x + 10, buttonY, panelWidth - 120,
                Component.translatable(KEY + "scan"), button -> {
                    if (isValid(minecraft) && cooldownTicks == 0 && balance() >= SCAN_PRICE) {
                        ClientPlayNetworking.send(new IdentityScannerRequestC2SPacket(pos, true));
                    }
                }));
        olderButton = addRenderableWidget(new TerminalButton(x + panelWidth - 102, buttonY, 42,
                Component.translatable(KEY + "page", "▲"), button -> scroll(visibleRows())));
        newerButton = addRenderableWidget(new TerminalButton(x + panelWidth - 52, buttonY, 42,
                Component.translatable(KEY + "page", "▼"), button -> scroll(-visibleRows())));
        scrollOffset = Math.min(scrollOffset, maxScroll());
        updateButtons();
    }

    private int historyTop() {
        return y + 88;
    }

    private int historyBottom() {
        return y + panelHeight - 48;
    }

    private int visibleRows() {
        return Math.max(1, (historyBottom() - historyTop()) / ROW_HEIGHT);
    }

    private int maxScroll() {
        return Math.max(0, history.size() - visibleRows());
    }

    private int balance() {
        return minecraft == null || minecraft.player == null ? 0 : MoneyUtils.getBalance(minecraft.player);
    }

    private void scroll(int amount) {
        scrollOffset = Math.max(0, Math.min(maxScroll(), scrollOffset + amount));
        updateButtons();
    }

    private void updateButtons() {
        if (scanButton != null) {
            scanButton.active = cooldownTicks == 0 && balance() >= SCAN_PRICE;
            olderButton.active = scrollOffset < maxScroll();
            newerButton.active = scrollOffset > 0;
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xB8000000);
        g.fillGradient(x, y, x + panelWidth, y + panelHeight, 0xFA030B0A, 0xFA020505);
        g.renderOutline(x, y, panelWidth, panelHeight, BORDER);
        g.fill(x + 1, y + 1, x + panelWidth - 1, y + 2, 0x5542E8B4);
        g.fill(x + 10, y + 28, x + panelWidth - 10, y + 29, BORDER);
        g.fill(x + 10, y + 80, x + panelWidth - 10, y + 81, BORDER);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, title, x + 12, y + 12, TEXT, false);
        g.drawString(font, Component.translatable(KEY + "balance", balance()), x + 12, y + 36, TEXT, false);
        g.drawString(font, Component.translatable(KEY + "price", SCAN_PRICE), x + 12, y + 49, MUTED, false);
        Component cooldown = cooldownTicks > 0
                ? Component.translatable(KEY + "cooldown", (cooldownTicks + 19) / 20)
                : Component.translatable(KEY + "ready");
        g.drawString(font, cooldown, x + panelWidth / 2, y + 36, TEXT, false);
        g.drawString(font, Component.translatable(KEY + "history", history.size()), x + 12, y + 66, TEXT, false);
        int end = history.size() - scrollOffset;
        int start = Math.max(0, end - visibleRows());
        g.enableScissor(x + 10, historyTop(), x + panelWidth - 10, historyBottom());
        if (history.isEmpty()) {
            g.drawString(font, Component.translatable(KEY + "empty"), x + 12, historyTop(), MUTED, false);
        }
        for (int i = start; i < end; i++) {
            Entry entry = history.get(i);
            ResourceLocation roleId = ResourceLocation.tryParse(entry.roleId());
            Component role = Component.translatable("announcement.star.role."
                    + (roleId == null ? "unassigned" : roleId.getPath()))
                    .withStyle(style -> style.withColor(entry.color() & 0xFFFFFF));
            Component line = Component.literal("> " + entry.name() + "  ").append(role);
            // 固定底部对齐，最新记录始终处于日志底部。
            int rowY = historyBottom() - (end - i) * ROW_HEIGHT;
            g.drawString(font, line, x + 12, rowY, TEXT, false);
        }
        g.disableScissor();
        if (maxScroll() > 0) {
            int trackHeight = Math.max(1, historyBottom() - historyTop());
            int thumbHeight = Math.min(trackHeight, Math.max(10, trackHeight * visibleRows() / history.size()));
            int thumbY = historyTop() + (trackHeight - thumbHeight) * (maxScroll() - scrollOffset) / maxScroll();
            g.fill(x + panelWidth - 7, historyTop(), x + panelWidth - 5, historyBottom(), BORDER);
            g.fill(x + panelWidth - 7, thumbY, x + panelWidth - 5, thumbY + thumbHeight, TEXT);
        }
        Component page = Component.translatable(KEY + "page",
                Math.max(1, (end + visibleRows() - 1) / visibleRows()) + "/"
                        + Math.max(1, (history.size() + visibleRows() - 1) / visibleRows()));
        g.drawString(font, page, x + 12, y + panelHeight - 42, MUTED, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= x && mouseX < x + panelWidth && mouseY >= historyTop() && mouseY < historyBottom()) {
            scroll((int) Math.signum(verticalAmount) * 3);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 与终端同色的按钮，保留原版点击音效、焦点及无障碍描述。 */
    private static final class TerminalButton extends Button {
        private TerminalButton(int x, int y, int width, Component label, OnPress action) {
            super(x, y, width, 20, label, action, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(),
                    active && isHoveredOrFocused() ? 0xFF123A32 : 0xFF071310);
            g.renderOutline(getX(), getY(), getWidth(), getHeight(), active ? TEXT : BORDER);
            renderScrollingString(g, Minecraft.getInstance().font, 4, active ? TEXT : MUTED);
        }
    }
}
