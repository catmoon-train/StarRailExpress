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
import io.wifi.starrailexpress.api.TMMRoles;
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

    /**
     * 按职业路径（{@code identifier} 的 path 部分）查询职业。
     *
     * <p>场景 JSON 里通常只写路径（例如 {@code jester}），因为不同来源注册的
     * 职业命名空间并不统一（{@code noellesroles} / {@code starrailexpress} / {@code wifi}），
     * 写完整 id 容易写错。
     */
    @Nullable
    public static SRERole byPath(@Nullable String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            return TMMRoles.getRoleByPath(path.trim());
        } catch (Throwable error) {
            return null;
        }
    }

    /** 先按完整 id 查，查不到再按路径查。 */
    @Nullable
    public static SRERole byIdOrPath(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.indexOf(':') >= 0) {
            SRERole role = byId(value);
            if (role != null) {
                return role;
            }
        }
        return byPath(value);
    }
}
