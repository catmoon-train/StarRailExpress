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

package io.wifi.starrailexpress.client.ponder;

import io.wifi.starrailexpress.api.SRERole;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.Nullable;

/**
 * 按职业 id 查询 {@link SRERole}，供思索扩展步骤使用。
 *
 * <p>职业注册表在客户端也可查询，因此思索里可以用真实职业数据
 * （商店条目、皮肤路径、本能透视颜色）来驱动场景。
 */
public final class PonderRoles {

    private PonderRoles() {
    }

    /** 按完整 id（如 {@code noellesroles:jester}）查询职业；找不到返回 null。 */
    @Nullable
    public static SRERole byId(@Nullable ResourceLocation id) {
        if (id == null) {
            return null;
        }
        try {
            return RoleUtils.getRole(id);
        } catch (Throwable error) {
            return null;
        }
    }

    /** 按字符串 id 查询职业；{@code null} 或非法 id 返回 null。 */
    @Nullable
    public static SRERole byId(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return byId(ResourceLocation.tryParse(raw.trim()));
    }
}
