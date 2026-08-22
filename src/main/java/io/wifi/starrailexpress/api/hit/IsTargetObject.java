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

package io.wifi.starrailexpress.api.hit;

import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * 可被枪 / 刀等武器瞄准并击中的非玩家实体。
 *
 * <p>绊线、亡灵、傀儡本体等实现此接口后，会自动进入
 * {@link SREHitManager#getTarget} 射线拾取，并在服务端由
 * {@link SREHitManager#tryHit} 回调 {@link #onWeaponHit}，无需再为每种实体
 * 单独写 {@code instanceof} 或 mixin。
 *
 * <p>若无法修改实体类，可改用 {@link SREHitManager#registerTargetFilter}
 * + {@link io.wifi.starrailexpress.event.OnEntityWeaponHit}。
 */
public interface IsTargetObject {

    /**
     * 是否可作为当前武器的瞄准 / 命中目标。默认仅远程武器（枪 / 狙击）。
     */
    default boolean isValidTarget(Player attacker, HitType type) {
        return type.isRanged();
    }

    /**
     * 瞄准优先级。默认 {@link HitPriority#PRIMARY}（与玩家同级）。
     * 绊线一类薄体积障碍应返回 {@link HitPriority#FALLBACK}。
     */
    default HitPriority getTargetPriority(HitType type) {
        return HitPriority.PRIMARY;
    }

    /**
     * 该武器的最大有效距离（格）。返回 {@code <= 0} 时使用 {@link HitType#defaultRange}。
     */
    default double getMaxHitRange(HitType type) {
        return -1;
    }

    /**
     * 命中此实体等价于命中的玩家（如傀儡本体 → 傀儡师）。非代理实体返回 {@code null}。
     * 返回非 null 时，枪击会走玩家击杀流程（反伤 / 掉枪等），然后再回调 {@link #onWeaponHit}。
     */
    @Nullable
    default Player getProxyPlayer() {
        return null;
    }

    /**
     * 服务端命中结算：摧毁绊线、击杀亡灵、伤害傀儡等。
     *
     * @return {@code true} 表示已处理，调用方应跳过默认的「击杀玩家」逻辑
     */
    boolean onWeaponHit(Player attacker, HitType type);
}
