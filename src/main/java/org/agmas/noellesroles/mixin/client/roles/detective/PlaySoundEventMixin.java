package org.agmas.noellesroles.mixin.client.roles.detective;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.client.Listen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.agmas.noellesroles.client.Listen.*;

@Mixin(SoundEngine.class)
public class PlaySoundEventMixin {

//    private static Sound soundc;
//    @ModifyVariable(
//            method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V",
//            at = @At(
//                    value = "STORE",
//                    ordinal = 0
//            )
//    )
//    private Sound onGetSound(Sound value) {
//
//        if (value != null) {
//            soundc = value;
//        }
//        return value;
//    }

    @Inject(method = "play", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenAccept(Ljava/util/function/Consumer;)Ljava/util/concurrent/CompletableFuture;",ordinal = 0,shift = At.Shift.AFTER))
    public void onPlaySound(SoundInstance sound, CallbackInfo ci) {
        if (!inListen){
            return;
        }

        if (soundInfos.size() >= 30){
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null){
            return;
        }

        Vec3 playerPos = mc.player.getPosition(mc.getTimer().getGameTimeDeltaPartialTick(true));



        double x = sound.getX();
        double y = sound.getY();
        double z = sound.getZ();

        double dis = 25d;
        if (playerPos.distanceToSqr(sound.getX(), sound.getY(), sound.getZ()) >= dis * dis) {
            return;
        }

        SoundSource source = sound.getSource();
        if (!(source == SoundSource.HOSTILE || source == SoundSource.NEUTRAL || source == SoundSource.PLAYERS)) {
            return;
        }

        long currentTime = mc.level.getGameTime();
        for (Listen.SoundInfo existing : soundInfos) {
            if (existing.pos.distanceToSqr(x, y ,z) < 1.0d && currentTime - existing.time < 10) {
                return;
            }
        }

        Listen.SoundInfo soundInfo;
        if (!soundInfoPool.empty()) {
            soundInfo = soundInfoPool.pop();
        }
        else {
            soundInfo = new SoundInfo();
        }

        soundInfo.pos = new Vec3(x, y, z);
        soundInfo.time = mc.level.getGameTime();
        soundInfos.add(soundInfo);
    }
}
