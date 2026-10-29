package org.agmas.noellesroles.game.modes.echo.client;

import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;
import org.agmas.noellesroles.game.modes.echo.specialization.EchoSpecializationPayload;
import org.agmas.noellesroles.game.modes.echo.specialization.EchoSpecializationSelectPayload;
import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;

import java.util.Locale;

/** 开局揭晓卡与选择入口；不接管相机、不阻塞移动，3 秒后自动打开选择界面。 */
public final class EchoSpecializationClient {
    private static final int REVEAL_TICKS = 60;
    private static EchoSpecializationPayload view;
    private static int reveal;
    private static boolean opened;
    private static long sequence;
    private EchoSpecializationClient() { }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(EchoSpecializationPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    view = payload;
                    opened = false;
                    reveal = payload.locked() ? 0 : REVEAL_TICKS;
                    // 服务器确认了新选择：在玩家身边重新显示一次专长空间文字。
                    EchoIntroClient.showcaseSpecialization();
                }));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            var data = EchoBagClient.data();
            if (view == null) return;
            if (data == null || !view.match().equals(data.matchId()) || client.player == null || client.player.isSpectator()) {
                view = null; return;
            }
            // 开局运镜与专长展示期间不揭晓、不抢屏：运镜结束后再按原节奏倒计时并打开选择界面。
            if (EchoIntroClient.blocksUi()) { reveal = Math.max(reveal, REVEAL_TICKS); return; }
            if (reveal > 0) {
                if (moving(client)) { reveal = 0; }
                else { reveal--; return; }
            }
            if (!opened && !view.locked() && client.screen == null) { opened = true; open(client); }
        });
        CommonHudRenderCallback.EVENT.register((graphics, delta) -> renderReveal(graphics));
    }

    private static boolean moving(Minecraft client) {
        var options = client.options;
        return options.keyUp.isDown() || options.keyDown.isDown() || options.keyLeft.isDown()
                || options.keyRight.isDown() || options.keyJump.isDown();
    }

    public static EchoSpecializationPayload view() { return view; }

    public static void open(Minecraft client) {
        if (view == null) return;
        if (client.screen instanceof EchoSpecializationScreen) return;
        client.setScreen(new EchoSpecializationScreen(client.screen));
    }

    /** 打开选择界面；已锁定或没有快照时返回 false。 */
    public static boolean openFromCommand(Minecraft client) {
        if (view == null || view.locked()) return false;
        open(client);
        return true;
    }

    public static void select(int option) {
        if (view == null || view.locked() || option < 0 || option >= view.options().size()) return;
        ClientPlayNetworking.send(new EchoSpecializationSelectPayload(view.match(), ++sequence, option));
    }

    public static int selectedIndex() {
        if (view == null) return -1;
        for (int n = 0; n < view.options().size(); n++) if (view.options().get(n).id().equals(view.selected())) return n;
        return -1;
    }

    public static int factionColor(EchoFaction faction) {
        return switch (faction) {
            case CARRIER -> 0xFF44BB66;
            case INTERCEPTOR -> 0xFFCC2233;
            case BROKER -> 0xFFCCAA22;
        };
    }

    private static void renderReveal(FakeGuiGraphics g) {
        // 运镜期间由 EchoIntroClient 的解说文字占屏，揭晓卡等运镜结束再出现。
        if (view == null || reveal <= 0 || EchoIntroClient.isActive()) return;
        var client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;
        int width = client.getWindow().getGuiScaledWidth(), height = client.getWindow().getGuiScaledHeight();
        int pw = Math.min(320, width - 24), px = (width - pw) / 2, py = Math.max(20, height / 2 - 56);
        float alpha = Math.min(1F, Math.min((REVEAL_TICKS - reveal) / 10F, reveal / 15F));
        if (alpha <= 0F) return;
        int accent = factionColor(view.faction());
        g.fillGradient(px, py, px + pw, py + 88, EchoAnim.fade(0xF01A1008, alpha), EchoAnim.fade(0xF020140A, alpha));
        g.renderOutline(px, py, pw, 88, EchoAnim.fade(0xFFD4AF37, alpha));
        g.fill(px + 1, py + 1, px + pw - 1, py + 3, EchoAnim.fade(accent, alpha));
        var font = client.font;
        String factionName = Component.translatable("echo.faction." + view.faction().name().toLowerCase(Locale.ROOT)).getString();
        g.drawString(font, factionName, px + 10, py + 10, EchoAnim.fade(accent, alpha), false);
        if (!view.contract().isEmpty()) {
            String contract = Component.translatable("echo.contract." + view.contract().toLowerCase(Locale.ROOT) + ".name").getString();
            g.drawString(font, contract, px + 10, py + 24, EchoAnim.fade(0xFFD4AF37, alpha), false);
        }
        g.drawString(font, Component.translatable("echo.spec.reveal_hint").getString(), px + 10, py + 42, EchoAnim.fade(0xFFFFF4DC, alpha), false);
        int progress = Math.max(0, Math.min(pw - 20, (pw - 20) * (REVEAL_TICKS - reveal) / REVEAL_TICKS));
        g.fill(px + 10, py + 66, px + pw - 10, py + 70, EchoAnim.fade(0xD8382A1B, alpha));
        g.fill(px + 10, py + 66, px + 10 + progress, py + 70, EchoAnim.fade(accent, alpha));
    }
}
