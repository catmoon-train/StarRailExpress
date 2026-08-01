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

package io.wifi;

import com.bawnorton.mixinsquared.api.MixinCanceller;

import io.wifi.starrailexpress.SRE;

import java.util.List;

public class SodiumExtraLightOptionMixinBlocker implements MixinCanceller {
    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        List<String> bannedMixins = List.of(
                "me.flashyreese.mods.sodiumextra.mixin.light_updates.MixinLevelLightEngine" // 光照
        );
        // ItemFrameRenderer
        if (bannedMixins.contains(mixinClassName)
                || mixinClassName.contains("me.flashyreese.mods.sodiumextra.mixin.fog")
                || mixinClassName.contains("me.flashyreese.mods.sodiumextra.mixin.render.entity")) {
            SRE.LOGGER.info("Blocked sodium-extra mixin: [" + mixinClassName + "]");
            return true;
        }
        return false;
    }
}
