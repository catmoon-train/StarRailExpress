package org.agmas.noellesroles.client.hud;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.killer.dream.DreamHealthComponent;

/** 本人虚拟血量：残血显示、数值变化闪烁、掉血时四周红边淡出。 */
@Environment(EnvType.CLIENT)
public final class VirtualHealthHud {
    private static final VirtualHealthHudState STATE = new VirtualHealthHudState();
    private static Player trackedPlayer;

    private VirtualHealthHud() { }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(VirtualHealthHud::tick);
        HudRenderCallback.EVENT.register((graphics, delta) ->
                render(graphics, delta.getGameTimeDeltaPartialTick(true)));
    }

    private static void tick(Minecraft client) {
        Player player = client.player;
        if (player != trackedPlayer || !canRender(client)) {
            STATE.reset();
            trackedPlayer = player;
        }
        if (!canRender(client) || client.isPaused()) {
            return;
        }
        STATE.tick(DreamHealthComponent.KEY.get(player).currentHealth(), DreamHealthComponent.maxHealth());
    }

    private static boolean canRender(Minecraft client) {
        return client.level != null && client.player != null
                && client.player.isAlive() && !client.player.isSpectator();
    }

    private static void render(GuiGraphics g, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        if (!canRender(client) || client.player != trackedPlayer || client.options.hideGui
                || io.wifi.starrailexpress.client.gui.OpeningPresentationCoordinator.shouldSuppressGameplayHud()) {
            return;
        }
        int width = g.guiWidth();
        int height = g.guiHeight();
        drawDamageEdges(g, width, height, STATE.damageStrength(partialTick));
        // 即便刚发生回血闪烁，回满后也立即隐藏，不能为了动画继续显示满血条。
        if (!STATE.visible()) {
            return;
        }
        int current = DreamHealthComponent.KEY.get(client.player).currentHealth();
        int max = DreamHealthComponent.maxHealth();
        if (current >= max) {
            return;
        }
        Component label = Component.translatable("hud.noellesroles.virtual_health", current, max);
        int barWidth = Math.min(width - 16, Math.max(140, client.font.width(label) + 12));
        int x = (width - barWidth) / 2;
        int y = Math.max(4, height - 88);
        boolean flash = STATE.flashing();
        g.fillGradient(x - 4, y - 4, x + barWidth + 4, y + 19, 0xD81A1008, 0xD820140A);
        g.renderOutline(x - 4, y - 4, barWidth + 8, 23, flash ? 0xFFFFF4DC : 0xFF8B6914);
        g.fill(x - 3, y - 3, x + barWidth + 3, y - 2, 0x33FFE8C0);
        g.drawCenteredString(client.font, label, width / 2, y, flash ? 0xFFFFFFFF : 0xFFFFF4DC);
        g.fill(x, y + 11, x + barWidth, y + 15, 0xAA000000);
        int fill = Mth.ceil(barWidth * Mth.clamp(current / (float) max, 0F, 1F));
        g.fill(x, y + 11, x + fill, y + 15, flash ? 0xFFFFF4DC : 0xFFE06B65);
    }

    /** 分层矩形环由外向内变淡，中心保持透明，不修改原版暗角或后处理状态。 */
    private static void drawDamageEdges(GuiGraphics g, int width, int height, float strength) {
        if (strength <= 0F) {
            return;
        }
        int band = Math.max(1, Math.min(width, height) / 7);
        int strips = Math.min(24, band);
        for (int i = 0; i < strips; i++) {
            int outer = i * band / strips;
            int inner = (i + 1) * band / strips;
            float fade = 1F - i / (float) strips;
            int alpha = (int) (150F * strength * fade * fade);
            int color = (alpha << 24) | 0x00C81818;
            g.fill(outer, outer, width - outer, inner, color);
            g.fill(outer, height - inner, width - outer, height - outer, color);
            g.fill(outer, inner, inner, height - inner, color);
            g.fill(width - inner, inner, width - outer, height - inner, color);
        }
    }
}
