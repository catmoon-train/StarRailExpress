package org.agmas.noellesroles.game.modes.echo.client;

import io.wifi.starrailexpress.api.data.RoleData;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.agmas.noellesroles.game.modes.echo.action.EchoCancelActionPayload;
import org.agmas.noellesroles.game.modes.echo.state.EchoLifeState;
import org.agmas.noellesroles.game.modes.echo.state.EchoRoleData;

import java.util.UUID;

/** 禁键仅负责表现；服务器仍独立约束位置、机会数与动作权限。 */
public final class EchoInputClient {
    private static UUID lastCancelled;
    private EchoInputClient() { }
    public static void initialize() {
        EchoBagClient.initialize();
        EchoCardClient.initialize();
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if(client.player!=null) {
                var own=RoleData.getNullable(EchoRoleData.class,client.player);
                if(own!=null && own.draft()!=null && own.draft().state().equals("CHANNELING")) client.options.keyJump.setDown(false);
                // 九格均可选择；Q/F 不进行本地预测，服务器另有独立门控。
                if(EchoHotbar.active()) {
                    client.options.keyDrop.setDown(false); client.options.keySwapOffhand.setDown(false);
                    while(client.options.keyDrop.consumeClick()) { }
                    while(client.options.keySwapOffhand.consumeClick()) { }
                }
            }
            if(client.player==null) return;
            var data=RoleData.getNullable(EchoRoleData.class,client.player);
            if(data==null || data.matchId()==null || (data.life()!=EchoLifeState.DOWNED && !data.controlled())) return;
            var options=client.options;
            options.keyUp.setDown(false); options.keyDown.setDown(false);
            options.keyLeft.setDown(false); options.keyRight.setDown(false);
            options.keyJump.setDown(false); options.keySprint.setDown(false);
            options.keyAttack.setDown(false); options.keyUse.setDown(false);
            client.player.input.forwardImpulse=0; client.player.input.leftImpulse=0;
            client.player.input.jumping=false; client.player.setSprinting(false);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if(client.player==null || client.getConnection()==null) { lastCancelled=null; return; }
            var data=RoleData.getNullable(EchoRoleData.class,client.player);
            if(data==null || data.matchId()==null || data.actionId()==null) { lastCancelled=null; return; }
            if(client.screen==null && client.options.keyUse.isDown()) { lastCancelled=null; return; }
            if(data.actionId().equals(lastCancelled)) return;
            lastCancelled=data.actionId();
            ClientPlayNetworking.send(new EchoCancelActionPayload(data.matchId(),data.actionId()));
        });
    }
}
