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

package io.wifi.starrailexpress.mixin.compat.sodium_extra;

import me.flashyreese.mods.sodiumextra.client.config.SodiumExtraGameOptions;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 全局及逐类型粒子开关不得隐藏技能线索；不对 Extra 早加载主入口进行注入。 */
@Mixin(value = SodiumExtraGameOptions.ParticleSettings.class, remap = false)
public abstract class SodiumExtraLoadedMixin {
    @Inject(method = "isParticleEnabled", at = @At("HEAD"), cancellable = true)
    private void sre$keepGameplayParticles(ResourceLocation particle, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }
}
