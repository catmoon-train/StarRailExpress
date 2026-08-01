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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.init.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 里世界·同界描边。
 *
 * <p>当本地玩家自身处于里世界（持有 {@link ModEffects#BACKWORLD_OUTLINE}）时，
 * 同样处于里世界的其他玩家会被绘制发光轮廓；不在里世界的人则完全看不到该轮廓。
 * 效果类似原版发光，但可见性被限制在里世界内部。</p>
 *
 * <p>实现方式：客户端侧拦截 {@code Entity#isCurrentlyGlowing()}，让原版的实体描边
 * 后处理（entity outline framebuffer）自动接管渲染，性能开销与原版发光一致。
 * 描边颜色通过 {@code Entity#getTeamColor()} 指定为青白色。</p>
 */
@Mixin(Entity.class)
public abstract class BackworldOutlineGlowMixin {

    /** 里世界描边颜色（青白色）。 */
    private static final int NR$BACKWORLD_OUTLINE_COLOR = 0x7FE7E0;

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void nr$backworldOutlineGlow(CallbackInfoReturnable<Boolean> cir) {
        if (nr$shouldOutline((Entity) (Object) this)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void nr$backworldOutlineColor(CallbackInfoReturnable<Integer> cir) {
        if (nr$shouldOutline((Entity) (Object) this)) {
            cir.setReturnValue(NR$BACKWORLD_OUTLINE_COLOR);
        }
    }

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
        // 只有观察者自己也在里世界时，才能看见里世界里其他人的描边。
        return viewer.hasEffect(ModEffects.BACKWORLD_OUTLINE)
                && target.hasEffect(ModEffects.BACKWORLD_OUTLINE);
    }
}
