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

import java.util.List;

public class WatheMixinBlocker implements MixinCanceller {
    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        if (mixinClassName.startsWith("dev.doctor4t.wathe"))
            return true;
        // 阻止 ratatouille 的卡顿渲染 mixin
        if (mixinClassName.startsWith("dev.doctor4t.ratatouille.mixin")) {
            // BlockRenderManagerAccessor
            if (mixinClassName.startsWith("dev.doctor4t.ratatouille.mixin.client.BlockRenderManagerAccessor"))
                return false;
            return true;
        }
        return false;
    }
}
