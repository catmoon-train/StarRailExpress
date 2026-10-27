package org.agmas.noellesroles.game.modes.echo.client;

import io.wifi.starrailexpress.client.gui.widget.SreButton;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.game.modes.echo.economy.EchoEconomy;
import org.agmas.noellesroles.game.modes.echo.economy.EchoPackOffer;
import org.agmas.noellesroles.game.modes.echo.economy.EchoShopPayload;
import org.agmas.noellesroles.game.modes.echo.economy.EchoShopService;

/**
 * 商店：左侧卡包（道具 / 技能两页），右侧售货机固定商品。
 *
 * <p>价格与余额都由服务端判定；这里只做展示与发请求，不本地扣款。
 * 售货机按钮在买过后显示为「已售出」，但仍然可见，避免玩家以为商品消失。
 */
public final class EchoShopScreen extends Screen {
    /** 打开方式：仅卡包，或带一台售货机。 */
    public enum Mode { PACKS, VENDING }

    private final Screen parent;
    private final Mode mode;
    private final long machinePos;
    private EchoPackOffer.EchoCategory category = EchoPackOffer.EchoCategory.ITEM;
    private int x, y, w, h;

    public EchoShopScreen(Screen parent, Mode mode, long machinePos) {
        super(Component.translatable("echo.shop.title"));
        this.parent = parent;
        this.mode = mode;
        this.machinePos = machinePos;
    }

    @Override protected void init() {
        clearWidgets();
        w = Math.min(560, width - 16);
        h = Math.min(340, height - 16);
        x = (width - w) / 2;
        y = (height - h) / 2;
        if (mode == Mode.PACKS) buildPacks(); else buildVending();
        button(Component.translatable("echo.ui.back"), x + w - 76, y + h - 26, 68, 18, this::onClose);
    }

    private void buildPacks() {
        var itemTab = button(Component.translatable("echo.shop.tab.item"), x + 8, y + 28, 84, 18,
                () -> { category = EchoPackOffer.EchoCategory.ITEM; init(); });
        itemTab.active = category != EchoPackOffer.EchoCategory.ITEM;
        var skillTab = button(Component.translatable("echo.shop.tab.skill"), x + 96, y + 28, 84, 18,
                () -> { category = EchoPackOffer.EchoCategory.SKILL; init(); });
        skillTab.active = category != EchoPackOffer.EchoCategory.SKILL;

        var offers = EchoPackOffer.of(category);
        for (int n = 0; n < offers.size(); n++) {
            var offer = offers.get(n);
            button(Component.translatable(offer.nameKey()), x + 8, y + 54 + n * 26, w - 16, 22,
                    () -> EchoShopClient.buyPack(offer));
        }
    }

    private void buildVending() {
        var goods = EchoShopService.VendingGood.values();
        for (int n = 0; n < goods.length; n++) {
            var good = goods[n];
            var label = EchoShopClient.soldOut(machinePos, good)
                    ? Component.translatable("echo.shop.sold").copy().append(" · ")
                            .append(Component.translatable(good.nameKey()))
                    : Component.translatable(good.nameKey());
            var button = button(label, x + 8, y + 40 + n * 26, w - 16, 22,
                    () -> EchoShopClient.buyGood(machinePos, good));
            button.active = !EchoShopClient.soldOut(machinePos, good);
        }
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(x, y, x + w, y + h, 0xE6120905);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xD0241109);
        g.drawCenteredString(font, title.copy().withStyle(ChatFormatting.GOLD), x + w / 2, y + 8, 0xFFFFE3A0);
        g.drawString(font, Component.translatable("echo.shop.balance", EchoShopClient.balance()),
                x + 8, y + 8, 0xFFFFD166);
        if (mode == Mode.PACKS) {
            var offers = EchoPackOffer.of(category);
            for (int n = 0; n < offers.size(); n++) {
                var offer = offers.get(n);
                g.drawString(font, Component.translatable("echo.shop.price", offer.price()),
                        x + w - 66, y + 60 + n * 26, 0xFFFFD166);
            }
        } else {
            var goods = EchoShopService.VendingGood.values();
            for (int n = 0; n < goods.length; n++)
                g.drawString(font, Component.translatable("echo.shop.price", goods[n].price()),
                        x + w - 66, y + 46 + n * 26, 0xFFFFD166);
        }
        super.render(g, mouseX, mouseY, delta);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }

    private SreButton button(Component text, int bx, int by, int bw, int bh, Runnable action) {
        var b = new SreButton(text, ignored -> action.run());
        b.setX(bx); b.setY(by); b.setWidth(bw); b.setHeight(bh);
        b.refreshTooltip();
        return addRenderableWidget(b);
    }
}
