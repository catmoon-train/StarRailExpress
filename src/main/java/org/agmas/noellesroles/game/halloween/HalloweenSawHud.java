package org.agmas.noellesroles.game.halloween;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** 私有血锯倒计时，使用服务端快照；断线、换世界或丢失更新自动清除。 */
@Environment(EnvType.CLIENT)
public final class HalloweenSawHud {
    private static ClientLevel level;
    private static HalloweenSawS2CPayload state;
    private static long receivedAt;
    private static boolean registered;

    private HalloweenSawHud() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ClientPlayNetworking.registerGlobalReceiver(HalloweenSawS2CPayload.ID, (packet, context) ->
                context.client().execute(() -> {
                    var world = context.client().level;
                    if (world == null || !world.dimension().location().equals(packet.dimension())) return;
                    if (packet.remaining() <= 0 || packet.total() <= 0) { clear(); return; }
                    level = world;
                    state = packet;
                    receivedAt = world.getGameTime();
                }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        HudRenderCallback.EVENT.register((graphics, delta) -> render(graphics));
    }

    private static void clear() { state = null; level = null; }

    private static void render(GuiGraphics graphics) {
        var client = Minecraft.getInstance();
        if (state == null) return;
        if (client.level != level || client.player == null || !client.player.isAlive()
                || client.player.isSpectator() || level.getGameTime() - receivedAt > 10
                || !HalloweenRuntime.isEnabled(level)) { clear(); return; }
        if (client.options.hideGui) return;
        int width = Math.min(160, client.getWindow().getGuiScaledWidth() - 16);
        int x = (client.getWindow().getGuiScaledWidth() - width) / 2;
        int y = client.getWindow().getGuiScaledHeight() / 2 + 28;
        String seconds = String.format(Locale.ROOT, "%.1f", state.remaining() / 20.0);
        Component text = Component.translatable(state.victim() ? "hud.noellesroles.halloween.saw_victim"
                : "hud.noellesroles.halloween.saw_actor", seconds);
        if (state.paused()) text = Component.translatable("hud.noellesroles.halloween.saw_paused", text);
        int color = state.paused() ? 0xFFFFCC80 : state.victim() ? 0xFFFF6060 : 0xFFFFB080;
        graphics.fill(x - 5, y - 4, x + width + 5, y + 18, 0xB0181010);
        graphics.drawCenteredString(client.font, text, x + width / 2, y, color);
        graphics.fill(x, y + 12, x + width, y + 15, 0xFF493333);
        double progress = Math.clamp(1.0 - state.remaining() / (double) state.total(), 0.0, 1.0);
        graphics.fill(x, y + 12, x + (int) (width * progress), y + 15, color);
    }
}
