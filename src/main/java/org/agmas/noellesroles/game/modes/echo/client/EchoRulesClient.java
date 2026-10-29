package org.agmas.noellesroles.game.modes.echo.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.agmas.noellesroles.game.modes.echo.rules.EchoRulesPayload;

/** 允许首次规则包先于RoleData抵达，只对同维度同match的本人投影生效。 */
public final class EchoRulesClient {
    private static EchoRulesPayload pending;
    private EchoRulesClient() { }
    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(EchoRulesPayload.ID,(payload,context) -> context.client().execute(() -> pending=payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client) -> pending=null);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if(pending==null || client.level==null || client.player==null) return;
            if(!pending.dimension().equals(client.level.dimension().location())) { pending=null; return; }
            var data=EchoBagClient.data();
            if(data!=null && pending.match().equals(data.matchId()) && !data.rulesReady()) data.projectRules(pending.rules());
        });
    }
}
