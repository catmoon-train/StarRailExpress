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

/**
 * 武器命中类型。客户端 {@link SREHitManager#getTarget} 与服务端
 * {@link SREHitManager#tryHit} 共用，避免各物品各自写 instanceof。
 */
public enum HitType {
    /** 左轮 / 德林加等手枪 */
    GUN(30.0),
    /** 杀手刀 */
    KNIFE(4.0),
    /** 狙击枪 */
    SNIPER(200.0);

    /** 该武器默认最大有效距离（格）。实体可通过 {@link IsTargetObject#getMaxHitRange} 覆盖。 */
    public final double defaultRange;

    HitType(double defaultRange) {
        this.defaultRange = defaultRange;
    }

    public boolean isRanged() {
        return this != KNIFE;
    }
}
