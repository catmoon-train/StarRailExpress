/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.mixin.client.roles.detective;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.client.AgentListenStepHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.agmas.noellesroles.client.AgentListenStepHandler.*;

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
        for (AgentListenStepHandler.SoundInfo existing : soundInfos) {
            if (existing.pos.distanceToSqr(x, y ,z) < 1.0d && currentTime - existing.time < 10) {
                return;
            }
        }

        AgentListenStepHandler.SoundInfo soundInfo;
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
