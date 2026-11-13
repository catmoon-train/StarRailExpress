package org.agmas.noellesroles.client.rolescreen;

import java.awt.Point;
import java.util.List;
import java.util.UUID;

import org.agmas.noellesroles.client.RouletteDevilClient;
import org.agmas.noellesroles.packet.RouletteDevilC2SPacket;
import org.agmas.noellesroles.role.ModRoles;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.RoleScreenHelper;
import io.wifi.starrailexpress.util.SREPlayerUtils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/** 低理智和成熟种子可挑战；尚在生长的种子也显示，但不能绕过服务端条件。 */
public final class RouletteDevilRoleScreenExtension extends PlayerListRoleScreenExtension<PlayerInfo> {
    private static final String KEY = "hud.noellesroles.roulette_devil.";
    private long lastRefresh = Long.MIN_VALUE;

    @Override
    protected RoleScreenHelper<PlayerInfo> createHelper(LocalPlayer player) {
        return new RoleScreenHelper<PlayerInfo>(player, ModRoles.ROULETTE_DEVIL,
                (screen, x, y, target, index) -> screen.addRoleWidget(new TargetHead(screen, x, y, target)),
                TEXT_PROVIDER, this::drawHint, this::getTargets)
                .setNameExtractor(info -> info.getProfile().getName());
    }

    private List<PlayerInfo> getTargets() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.getConnection() == null
                || SREClient.gameComponent == null) return List.of();
        return client.getConnection().getOnlinePlayers().stream().filter(info -> {
            UUID id = info.getProfile().getId();
            return !id.equals(client.player.getUUID())
                    && SREPlayerUtils.isPlayerAlive(client.level, id)
                    && (RouletteDevilClient.isLowSan(id) || RouletteDevilClient.isSeeded(id)
                            || RouletteDevilClient.isMature(id));
        }).toList();
    }

    @Override
    public void onInventoryScreenRender(LimitedInventoryScreen screen, GuiGraphics graphics, int mouseX, int mouseY,
            float delta) {
        // 不重建整张背包；只在条目改变时刷新本扩展控件，保留搜索条件和正常翻页。
        long now = screen.player.level().getGameTime();
        if (now != lastRefresh) {
            lastRefresh = now;
            var pagination = getHelper(screen.player).getPaginationHelper();
            List<PlayerInfo> targets = getTargets();
            if (!targets.equals(pagination.getPlayerEntries())) {
                int page = pagination.getCurrentPage();
                pagination.setPlayerEntries(targets);
                pagination.clearManagedWidgets(screen);
                pagination.addPageWidgets(screen);
                pagination.jumpToPage(screen, page);
            }
        }
        super.onInventoryScreenRender(screen, graphics, mouseX, mouseY, delta);
    }

    private void drawHint(GuiGraphics graphics, Point center) {
        var client = Minecraft.getInstance();
        var font = client.font;
        var state = RouletteDevilClient.state();
        int line = font.lineHeight + 2;
        graphics.drawCenteredString(font, Component.translatable(KEY + "progress", state.wins(), state.required(),
                state.lives()), center.x, center.y + 40 - line * 2, 0xFFD1B1ED);
        graphics.drawCenteredString(font, Component.translatable(KEY + "seeds", state.seeded().size(),
                state.mature().size()), center.x, center.y + 40 - line, 0xFFB2A5BD);
        Component selection = Component.translatable(KEY + (state.duelOpponent() != null ? "dueling" : "select"));
        graphics.drawCenteredString(font, selection, center.x, center.y + 40, 0xFFDDCCEE);
        int y = 8;
        for (var text : font.split(Component.translatable(KEY + "skill"), Math.max(40, graphics.guiWidth() - 16))) {
            graphics.drawString(font, text, (graphics.guiWidth() - font.width(text)) / 2, y, 0xFFCCBBDD);
            y += font.lineHeight + 1;
        }
    }

    private static Component seedStatus(UUID target) {
        String stage = RouletteDevilClient.isMature(target) ? "mature"
                : RouletteDevilClient.isSeeded(target) ? "growing" : "none";
        return Component.translatable(KEY + "seed." + stage);
    }

    private static final class TargetHead extends Button {
        private final PlayerInfo target;

        private TargetHead(LimitedInventoryScreen screen, int x, int y, PlayerInfo target) {
            super(x, y, 16, 16, Component.literal(target.getProfile().getName()), button -> {
                UUID id = target.getProfile().getId();
                if (RouletteDevilClient.canChallenge(id)) {
                    // 只有挑战请求；绝不在本地种植、扣命、传送或开局。
                    ClientPlayNetworking.send(new RouletteDevilC2SPacket(1, id));
                    screen.onClose();
                }
            }, DEFAULT_NARRATION);
            this.target = target;
            active = RouletteDevilClient.canChallenge(target.getProfile().getId());
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            UUID id = target.getProfile().getId();
            active = RouletteDevilClient.canChallenge(id);
            super.renderWidget(graphics, mouseX, mouseY, delta);
            int color = RouletteDevilClient.isMature(id) ? 0xFFB477EE
                    : RouletteDevilClient.isLowSan(id) ? 0xFF3388FF : 0xFF766680;
            graphics.fill(getX() - 2, getY() - 2, getX() + 18, getY() + 18, 0xE0100A18);
            graphics.renderOutline(getX() - 2, getY() - 2, 20, 20, isHoveredOrFocused() ? 0xFFEEE0FF : color);
            PlayerFaceRenderer.draw(graphics, target.getSkin().texture(), getX(), getY(), 16);
            // 小点表示种子阶段；未种植不显示，成熟为亮紫，生长为灰紫。
            if (RouletteDevilClient.isSeeded(id) || RouletteDevilClient.isMature(id)) {
                graphics.fill(getX() + 12, getY() + 12, getX() + 17, getY() + 17,
                        RouletteDevilClient.isMature(id) ? 0xFFCC88FF : 0xFF766680);
            }
            if (!active) graphics.fill(getX(), getY(), getX() + 16, getY() + 16, 0x88000000);
            if (isHoveredOrFocused()) {
                graphics.renderComponentTooltip(Minecraft.getInstance().font, List.of(getMessage(), seedStatus(id),
                        Component.translatable(KEY + (RouletteDevilClient.isLowSan(id) ? "low_san" : "not_low_san")),
                        Component.translatable(KEY + (active ? "challenge" : "unavailable"))), mouseX, mouseY);
            }
        }

        @Override
        public void renderString(GuiGraphics graphics, Font font, int color) {
            // 头像替代文字；玩家名仍用于悬浮提示、搜索和无障碍旁白。
        }
    }
}
