package org.agmas.noellesroles.mixin;

import io.wifi.starrailexpress.SRE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.init.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * “脚步消失”药水效果（{@link ModEffects#FOOTSTEP_VANISH}）的行为实现。
 *
 * <p>拥有该效果的玩家：脚步声不会播放（{@code playStepSound} 取消）、疾跑粒子不会生成
 * （{@code canSpawnSprintParticle} 返回 false）。该效果由 {@code FootstepVanishEffectSync}
 * 广播给所有客户端，因此其它玩家侧运行的本拦截也能查到该效果，实现“别人也听不到脚步”。</p>
 */
@Mixin(Entity.class)
public class FootstepVanishMixin {

    @Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
    private void noe$silenceStepSound(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (SRE.isLobby)
            return;
        if ((Entity) (Object) this instanceof Player player && player.hasEffect(ModEffects.FOOTSTEP_VANISH)) {
            ci.cancel();
        }
    }

    @Inject(method = "canSpawnSprintParticle", at = @At("HEAD"), cancellable = true)
    private void noe$hideSprintParticle(CallbackInfoReturnable<Boolean> cir) {
        if (SRE.isLobby)
            return;
        if ((Entity) (Object) this instanceof Player player && player.hasEffect(ModEffects.FOOTSTEP_VANISH)) {
            cir.setReturnValue(false);
        }
    }
}
