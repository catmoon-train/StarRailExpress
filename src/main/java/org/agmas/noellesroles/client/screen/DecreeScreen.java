package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.client.gui.SREPanelStyle;
import io.wifi.starrailexpress.client.gui.screen.WithParentScreenPauseScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.game.roles.innocence.decree.Decree;
import org.agmas.noellesroles.packet.DecreeC2SPacket;
import org.agmas.noellesroles.role_data.innocence.DecreeMageRoleData;
import org.agmas.noellesroles.utils.MoneyUtils;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** 律令词库只提交已知 id；扣费、次数和冷却均由服务端确认。 */
public final class DecreeScreen extends Screen {
    private static final String KEY = "screen.noellesroles.decree.";
    private final List<Button> words = new ArrayList<>();
    private EditBox input;
    private Button confirm;
    private int x, y, panelWidth, panelHeight, columns, visibleRows, firstRow;

    public DecreeScreen() {
        super(Component.translatable(KEY + "title"));
    }

    @Override
    protected void init() {
        String previous = input == null ? "" : input.getValue();
        words.clear();
        confirm = null;
        panelWidth = Math.min(620, width - 16);
        panelHeight = Math.min(350, height - 16);
        x = (width - panelWidth) / 2;
        y = (height - panelHeight) / 2;
        columns = panelWidth >= 500 ? 4 : 2;
        visibleRows = Math.min((Decree.values().length + columns - 1) / columns,
                Math.max(1, (panelHeight - 128) / 27));
        input = addRenderableWidget(new EditBox(font, x + 12, y + 34, panelWidth - 24, 20,
                Component.translatable(KEY + "hint")));
        input.setMaxLength(64);
        input.setHint(SREPanelStyle.hint(Component.translatable(KEY + "hint")));
        input.setValue(previous);
        int cellWidth = (panelWidth - 24 - (columns - 1) * 4) / columns;
        for (Decree decree : Decree.values()) {
            words.add(addRenderableWidget(new GoldButton(0, 0, cellWidth, 23, Component.empty(),
                    button -> input.setValue(Component.translatable(decree.translationKey()).getString()))));
        }
        confirm = addRenderableWidget(new GoldButton(x + 12, y + panelHeight - 30, panelWidth - 24, 20,
                Component.translatable(KEY + "confirm"), button -> submit()));
        input.setResponder(value -> updateButtons());
        setInitialFocus(input);
        updateButtons();
    }

    private DecreeMageRoleData data() {
        return minecraft == null || minecraft.player == null ? null
                : RoleData.getNullable(DecreeMageRoleData.class, minecraft.player);
    }

    private int balance() {
        return minecraft == null || minecraft.player == null ? 0 : MoneyUtils.getBalance(minecraft.player);
    }

    private Decree selected() {
        if (input != null) {
            for (Decree decree : Decree.values()) {
                // 不做模糊匹配、去空格或从用户输入直接生成网络 id。
                if (Component.translatable(decree.translationKey()).getString().equals(input.getValue())) return decree;
            }
        }
        return null;
    }

    private boolean canBuy(Decree decree) {
        var state = data();
        return decree != null && state != null && !state.isUsed(decree) && balance() >= decree.cost();
    }

    private void updateButtons() {
        if (confirm == null) return;
        firstRow = Math.max(0, Math.min(firstRow, maxScroll()));
        var state = data();
        for (int i = 0; i < words.size(); i++) {
            Decree decree = Decree.values()[i];
            Button button = words.get(i);
            boolean used = state != null && state.isUsed(decree);
            button.setMessage(Component.translatable(used ? KEY + "word_used" : KEY + "word_price",
                    Component.translatable(decree.translationKey()), decree.cost()));
            button.active = canBuy(decree);
            int row = i / columns - firstRow;
            button.visible = row >= 0 && row < visibleRows;
            button.setPosition(x + 12 + (i % columns) * (button.getWidth() + 4), y + 65 + row * 27);
        }
        confirm.active = canBuy(selected());
    }

    private int maxScroll() {
        return Math.max(0, (Decree.values().length + columns - 1) / columns - visibleRows);
    }

    @Override
    public void tick() {
        updateButtons();
    }

    private void submit() {
        Decree decree = selected();
        if (!canBuy(decree)) return;
        ClientPlayNetworking.send(new DecreeC2SPacket(decree.id()));
        // 一次确认只发一包；不在客户端扣款，也不在打开页面时启动冷却。
        minecraft.setScreen(null);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x66000000);
        SREPanelStyle.drawPanel(g, x, y, panelWidth, panelHeight);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, y + 12, SREPanelStyle.GOLD);
        Decree decree = selected();
        String status;
        if (data() == null) status = "message.noellesroles.decree.unavailable";
        else if (decree == null) status = input.getValue().isEmpty() ? KEY + "exact" : "message.noellesroles.decree.invalid";
        else if (data().isUsed(decree)) status = "message.noellesroles.decree.used";
        else if (balance() < decree.cost()) status = "message.noellesroles.decree.funds";
        else status = KEY + "ready";
        g.drawString(font, font.plainSubstrByWidth(Component.translatable(status).getString(), panelWidth - 24),
                x + 12, y + panelHeight - 59, confirm.active ? SREPanelStyle.TEXT : SREPanelStyle.MUTED, false);
        g.drawString(font, Component.translatable(KEY + "balance", balance()), x + 12,
                y + panelHeight - 45, SREPanelStyle.GOLD, false);
        if (maxScroll() > 0) {
            int trackHeight = visibleRows * 27 - 4;
            int thumb = Math.max(12, trackHeight * visibleRows / (visibleRows + maxScroll()));
            int top = y + 65 + (trackHeight - thumb) * firstRow / maxScroll();
            g.fill(x + panelWidth - 6, y + 65, x + panelWidth - 3, y + 65 + trackHeight, SREPanelStyle.CARD_BORDER);
            g.fill(x + panelWidth - 6, top, x + panelWidth - 3, top + thumb, SREPanelStyle.GOLD);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX >= x && mouseX < x + panelWidth && mouseY >= y + 65 && mouseY < y + panelHeight - 65) {
            firstRow -= (int) Math.signum(vertical);
            updateButtons();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }


    @Override
    public void onClose() {
        minecraft.setScreen(new WithParentScreenPauseScreen(this));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class GoldButton extends Button {
        private float hover;

        private GoldButton(int x, int y, int width, int height, Component text, OnPress action) {
            super(x, y, width, height, text, action, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            hover += ((active && isHoveredOrFocused() ? 1F : 0F) - hover) * 0.22F;
            int bg = SREPanelStyle.blendColors(SREPanelStyle.PANEL_BG_TOP, SREPanelStyle.GOLD_DIM, hover * 0.25F);
            g.fill(getX(), getY(), getX() + width, getY() + height, bg);
            g.renderOutline(getX(), getY(), width, height, active && isHoveredOrFocused()
                    ? SREPanelStyle.GOLD : SREPanelStyle.CARD_BORDER);
            var font = Minecraft.getInstance().font;
            String text = getMessage().getString();
            int separator = text.indexOf(" · ");
            if (height >= 23 && separator >= 0) {
                String word = font.plainSubstrByWidth(text.substring(0, separator), width - 6);
                String price = font.plainSubstrByWidth(text.substring(separator + 3), width - 6);
                g.drawString(font, word, getX() + (width - font.width(word)) / 2, getY() + 3,
                        active ? SREPanelStyle.TEXT : SREPanelStyle.MUTED, false);
                g.drawString(font, price, getX() + (width - font.width(price)) / 2, getY() + 13,
                        active ? SREPanelStyle.GOLD : SREPanelStyle.MUTED, false);
            } else {
                text = font.plainSubstrByWidth(text, width - 6);
                g.drawString(font, text, getX() + (width - font.width(text)) / 2,
                        getY() + (height - 8) / 2, active ? SREPanelStyle.TEXT : SREPanelStyle.MUTED, false);
            }
        }
    }
}
