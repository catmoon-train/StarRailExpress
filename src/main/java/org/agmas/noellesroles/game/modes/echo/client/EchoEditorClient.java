package org.agmas.noellesroles.game.modes.echo.client;

import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;
import org.agmas.noellesroles.game.modes.echo.content.EchoContent;
import org.agmas.noellesroles.game.modes.echo.content.EchoEditorItem;
import org.agmas.noellesroles.game.modes.echo.editor.EchoEditorActionPayload;
import org.agmas.noellesroles.game.modes.echo.editor.EchoEditorStatePayload;

import java.util.Locale;

/**
 * 编辑工具物品的客户端缓存、HUD 与界面入口。
 *
 * <p>客户端不读文件、不写世界，也不上报机器类型或坐标：所有操作都只是一条 {@link EchoEditorActionPayload}，
 * 服务端回一条 {@link EchoEditorStatePayload} 作为唯一事实来源。HUD 手持工具时常驻显示会话状态。
 */
public final class EchoEditorClient {
    private static EchoEditorStatePayload state;
    private static int age;
    private static int sequence;

    private EchoEditorClient() { }

    public static void initialize() {
        EchoEditorItem.openScreenCallback = () -> open(Minecraft.getInstance());
        ClientPlayNetworking.registerGlobalReceiver(EchoEditorStatePayload.ID, (payload, context) ->
                context.client().execute(() -> { state = payload; age = 0; }));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (state == null) return;
            if (client.player == null || client.level == null || ++age > 1200) state = null;
        });
        CommonHudRenderCallback.EVENT.register((graphics, delta) -> renderHud(graphics));
    }

    public static EchoEditorStatePayload state() { return state; }

    /** 当前选中点位类型的逐条列表（id / 人数档 / 模板坐标）；没选或没有会话时为 null。 */
    public static EchoEditorStatePayload.Section detail() {
        return state == null ? null : state.detail();
    }

    /** 发一条操作请求；返回 false 表示手上没有编辑工具。 */
    public static boolean send(EchoEditorActionPayload.Op op, String a, String b, String c) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !holding(client)) return false;
        ClientPlayNetworking.send(new EchoEditorActionPayload(++sequence, op, a, b, c));
        return true;
    }

    public static void open(Minecraft client) {
        if (client.player == null) return;
        if (client.screen instanceof EchoEditorScreen) return;
        client.setScreen(new EchoEditorScreen(client.screen));
        send(EchoEditorActionPayload.Op.LIST, "", "", "");
    }

    public static boolean holding(Minecraft client) {
        if (client.player == null) return false;
        return client.player.getMainHandItem().is(EchoContent.EDITOR)
                || client.player.getOffhandItem().is(EchoContent.EDITOR);
    }

    /** 服务端最近一条反馈（已本地化）。 */
    public static Component message() {
        if (state == null || state.messageKey().isEmpty()) return Component.empty();
        return Component.translatable(state.messageKey(), state.messageArgs().toArray());
    }

    private static void renderHud(FakeGuiGraphics g) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui || !holding(client) || state == null) return;
        int lines = 5 + state.sections().size() + (state.issues().isEmpty() ? 0 : state.issues().size()) + 1;
        int pw = Math.min(240, g.guiWidth() - 16);
        int px = 8, py = 60;
        int height = lines * 10 + 8;
        g.fillGradient(px, py, px + pw, py + height, 0xE01A1008, 0xE020140A);
        g.renderOutline(px, py, pw, height, 0xFF8B6914);
        g.fill(px + 1, py + 1, px + pw - 1, py + 3, EchoAnim.fade(0xFFD4AF37, 0.85F));
        int y = py + 5;
        y = row(g, Component.translatable("echo.editor.hud.title"), px + 5, y, pw - 10, 0xFFD4AF37);
        y = row(g, Component.translatable("echo.editor.machine.hud_hint"), px + 5, y, pw - 10, 0xFF9E8B6E);
        y = row(g, Component.translatable("echo.editor.hud.map",
                state.mapName().isEmpty() ? Component.translatable("echo.editor.none") : Component.literal(state.mapName()),
                state.mapId().isEmpty() ? Component.translatable("echo.editor.none") : Component.literal(state.mapId()),
                state.revision()), px + 5, y, pw - 10, 0xFFFFF4DC);
        y = row(g, Component.translatable(state.declared() ? "echo.editor.hud.gate_on" : "echo.editor.hud.gate_off"),
                px + 5, y, pw - 10, state.declared() ? 0xFF72C17B : 0xFFE06B65);
        y = row(g, Component.translatable("echo.editor.hud.session",
                state.session() ? Component.translatable("echo.editor.hud.active") : Component.translatable("echo.editor.hud.idle"),
                state.cornerA() ? "A" : "-", state.cornerB() ? "B" : "-"), px + 5, y, pw - 10, 0xFFC8B898);
        for (var section : state.sections()) {
            y = row(g, Component.literal(String.format(Locale.ROOT, "%s %d", section.field(), section.count())),
                    px + 5, y, pw - 10, 0xFF9E8B6E);
        }
        for (var issue : state.issues()) y = row(g, Component.literal(issue), px + 5, y, pw - 10, 0xFFE06B65);
        Component message = message();
        if (!message.getString().isEmpty()) row(g, message, px + 5, y, pw - 10, 0xFFFFA080);
    }

    private static int row(FakeGuiGraphics g, Component text, int x, int y, int width, int color) {
        g.enableScissor(x, y, x + width, y + 10);
        g.drawString(Minecraft.getInstance().font, text, x, y, color, false);
        g.disableScissor();
        return y + 10;
    }
}
