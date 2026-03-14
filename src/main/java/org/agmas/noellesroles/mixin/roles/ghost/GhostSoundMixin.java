package org.agmas.noellesroles.mixin.roles.ghost;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public class GhostSoundMixin {
    @Inject(method = "playSeededSound*", at = @At("HEAD"), cancellable = true)
    private void onPlaySound(Player source, double x, double y, double z, Holder<SoundEvent> sound, SoundSource category, float volume, float pitch, long seed, CallbackInfo ci) {
        if (source != null) {
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(source.level());
            if (gameWorldComponent.isRole(source, ModRoles.GHOST)) {
                // 如果发出声音的玩家是“小透明”，则取消该声音事件，阻止其被其他玩家听到。
                ci.cancel();
            }
        }
    }
}