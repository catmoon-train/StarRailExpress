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

package org.agmas.noellesroles.game.roles.killer.pooler;

import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 祈月者技能用到的自定义形状粒子 id。
 * <p>
 * 形状在客户端生成（见 {@code PoolerClientParticles}）：服务端只发一个包，
 * 带 id + 原点 + 时长 + 参数，不随人数放大包量。
 */
public final class PoolerFx {

    /** 海潮环：圆环向外扩散（参数：半径、是否反向）。 */
    public static final ResourceLocation TIDE_RING =
            Noellesroles.id("pooler_tide_ring");
    /** 月光光柱：从地面升起的螺旋月光（参数：半径、高度）。 */
    public static final ResourceLocation MOON_PILLAR =
            Noellesroles.id("pooler_moon_pillar");
    /** 月光爆散：以玩家为中心向外爆散的月尘（参数：半径、颗粒密度）。 */
    public static final ResourceLocation MOON_BURST =
            Noellesroles.id("pooler_moon_burst");
    /** 领域展开·升月：地面圆环 + 上引光柱（参数：半径、高度）。 */
    public static final ResourceLocation MOON_ASCEND =
            Noellesroles.id("pooler_moon_ascend");
    /** 领域展开·水缸：水面涟漪 + 气泡柱（参数：半径、水面高度）。 */
    public static final ResourceLocation AQUARIUM_OPEN =
            Noellesroles.id("pooler_aquarium_open");
    /** 星之权杖锁定：指向目标的光线（参数：目标 X/Y/Z 差值）。 */
    public static final ResourceLocation STAR_LOCK =
            Noellesroles.id("star_gatherer_lock");
    /** 星辰坠落轨迹（参数：半径、高度）。 */
    public static final ResourceLocation STAR_TRAIL =
            Noellesroles.id("star_gatherer_trail");

    private PoolerFx() {
    }
}
