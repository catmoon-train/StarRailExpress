package org.agmas.noellesroles.game.modes.echo.objective;

import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.*;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoMatch;

/** 有限半径、活人接收、真实位置的普通声音；全图提交另走白名单专用包。 */
public final class EchoAudio {
    private EchoAudio() { }
    public static void nearby(EchoMatch match,Vec3 point,SoundEvent sound,double range,float volume) {
        long seed=java.util.concurrent.ThreadLocalRandom.current().nextLong();
        for(var receiver:match.onlinePlayers()) if(match.present(receiver) && match.life(receiver.getUUID()).potentiallyEscapable() && receiver.distanceToSqr(point)<=range*range)
            try { receiver.connection.send(new ClientboundSoundPacket(Holder.direct(sound),SoundSource.PLAYERS,point.x,point.y,point.z,volume,1,seed)); }
            catch(RuntimeException failure) { org.agmas.noellesroles.Noellesroles.LOGGER.debug("回声近距音效投递失败 {}",receiver.getUUID(),failure); }
    }
    public static void submission(EchoMatch match,Vec3 point) {
        long seed=java.util.concurrent.ThreadLocalRandom.current().nextLong();
        for(var receiver:match.onlinePlayers()) if(match.present(receiver) && match.life(receiver.getUUID()).potentiallyEscapable())
            try { net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(receiver,new EchoSoundPayload(match.id(),receiver.level().dimension().location(),point,seed)); }
            catch(RuntimeException failure) { org.agmas.noellesroles.Noellesroles.LOGGER.debug("回声提交音效投递失败 {}",receiver.getUUID(),failure); }
    }
}
