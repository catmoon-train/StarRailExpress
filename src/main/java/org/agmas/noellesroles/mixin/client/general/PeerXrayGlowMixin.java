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

package org.agmas.noellesroles.mixin.client.general;

import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.init.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 同级透视（{@link ModEffects#PEER_XRAY}）：白框互相透视。
 *
 * <p>效果期间，<b>同样持有该效果、且药水等级相同</b>的两名（或多名）玩家可以互相透视：
 * 无论中间隔着什么方块，双方都能看到对方身上那圈纯白色的描边。等级不同（I / II / III …）
 * 则<b>互相看不见</b>，避免高等级单方面窥视低等级。
 *
 * <p>实现方式与 {@code BackworldOutlineGlowMixin} 完全一致：客户端拦截
 * {@code Entity#isCurrentlyGlowing()} 让原版的实体描边后处理接管，性能开销与原版发光相同；
 * 描边颜色通过 {@code Entity#getTeamColor()} 指定为纯白。</p>
 */
@Mixin(Entity.class)
public abstract class PeerXrayGlowMixin {

    /** 同级透视的描边颜色（纯白）。 */
    private static final int NR$PEER_XRAY_COLOR = 0xFFFFFF;

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void nr$peerXrayGlow(CallbackInfoReturnable<Boolean> cir) {
        if (nr$shouldOutline((Entity) (Object) this)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void nr$peerXrayColor(CallbackInfoReturnable<Integer> cir) {
        if (nr$shouldOutline((Entity) (Object) this)) {
            cir.setReturnValue(NR$PEER_XRAY_COLOR);
        }
    }

    /**
     * 目标是否应当对本地玩家显示同级透视白框。
     *
     * <p>需要同时满足：都是玩家、都处于客户端、双方都持有 {@link ModEffects#PEER_XRAY}，
     * 且双方该效果的<b>等级相同</b>（{@code MobEffectInstance#getAmplifier}，I 级为 0）。
     */
    private static boolean nr$shouldOutline(Entity self) {
        if (!(self instanceof Player target)) {
            return false;
        }
        if (!self.level().isClientSide()) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        Player viewer = client.player;
        if (viewer == null || viewer == target) {
            return false;
        }
        MobEffectInstance viewerEffect = viewer.getEffect(ModEffects.PEER_XRAY);
        if (viewerEffect == null) {
            return false;
        }
        MobEffectInstance targetEffect = target.getEffect(ModEffects.PEER_XRAY);
        // 「同级」：等级必须完全一致，等级不同则互相不可见
        return targetEffect != null && viewerEffect.getAmplifier() == targetEffect.getAmplifier();
    }
}