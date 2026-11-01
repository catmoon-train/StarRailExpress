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

import io.wifi.starrailexpress.client.SodiumExtraOptionPolicy;
import me.flashyreese.mods.sodiumextra.client.config.SodiumExtraGameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 载入/保存时先沿用 Extra 的空值修复，再强制安全配置；不再覆写整张设置页。 */
@Mixin(value = SodiumExtraGameOptions.class, remap = false)
public abstract class SodiumExtraDisableOptionMixin {
    @Inject(method = "sanitize", at = @At("RETURN"))
    private void sre$restoreFairOptions(CallbackInfo ci) {
        SodiumExtraOptionPolicy.enforce((SodiumExtraGameOptions) (Object) this);
    }
}
