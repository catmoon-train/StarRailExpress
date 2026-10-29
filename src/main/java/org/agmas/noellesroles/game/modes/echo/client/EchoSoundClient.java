package org.agmas.noellesroles.game.modes.echo.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.sounds.*;
import net.minecraft.util.RandomSource;
import org.agmas.noellesroles.game.modes.echo.objective.EchoSoundPayload;

/** 保留真实源方向，显式关闭距离衰减；声音不改变任何目标状态。 */
public final class EchoSoundClient {
    private EchoSoundClient() { }
    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(EchoSoundPayload.ID,(payload,context) -> context.client().execute(() -> {
            var client=context.client(); var data=EchoBagClient.data();
            if(client.level==null || client.player==null || client.player.isSpectator() || data==null || !payload.match().equals(data.matchId())
                    || !payload.dimension().equals(client.level.dimension().location()) || !data.life().potentiallyEscapable() || !data.phase().acceptsActions()) return;
            client.getSoundManager().play(new SimpleSoundInstance(SoundEvents.AMETHYST_BLOCK_RESONATE.getLocation(),SoundSource.PLAYERS,1,1,
                    RandomSource.create(payload.seed()),false,0,SoundInstance.Attenuation.NONE,payload.origin().x,payload.origin().y,payload.origin().z,false));
        }));
    }
}
