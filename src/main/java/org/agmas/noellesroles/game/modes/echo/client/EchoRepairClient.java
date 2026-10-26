package org.agmas.noellesroles.game.modes.echo.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.agmas.noellesroles.game.modes.echo.repair.EchoRepairPayload;
import org.agmas.noellesroles.game.modes.echo.repair.EchoRepairSubmitPayload;

/** 修机小游戏的客户端入口：收到判定就开界面，按下/取消各发一包。 */
public final class EchoRepairClient {
    private static EchoRepairPayload pending;
    private EchoRepairClient() { }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(EchoRepairPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    var client = Minecraft.getInstance();
                    if (client.player == null) return;
                    pending = payload;
                    if (client.screen instanceof EchoRepairScreen) return;
                    client.setScreen(new EchoRepairScreen(payload));
                }));
    }

    /** 按下按钮：上报本地指针位置，交给服务端重算判定。 */
    static void submit(EchoRepairPayload check, int position) {
        pending = null;
        ClientPlayNetworking.send(new EchoRepairSubmitPayload(check.matchId(), check.checkId(),
                Math.floorMod(position, 1000)));
    }

    /** 直接关界面：等价于放弃这一轮，服务端会按超时处理。 */
    static void cancel(EchoRepairPayload check) {
        pending = null;
        ClientPlayNetworking.send(new EchoRepairSubmitPayload(check.matchId(), check.checkId(), -1));
    }

    public static EchoRepairPayload pending() { return pending; }

    /** 换局或离开模式时清掉未完成的界面。 */
    public static void clear() {
        pending = null;
        var client = Minecraft.getInstance();
        if (client.screen instanceof EchoRepairScreen) client.setScreen(null);
    }
}
