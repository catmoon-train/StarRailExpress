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

package org.agmas.noellesroles.utils;

import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import net.minecraft.world.entity.Entity;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.game.roles.innocence.fool.TarotAssemblyManager;

/**
 * 尸体自动清除区判定。
 *
 * <p>{@link PlayerBodyEntity#tick()} 会在塔罗会会议区与游记放逐区内无条件 discard 尸体。
 * 跟随本体移动的假尸体（咸鱼晒咸鱼、亡语杀手的伪装尸体）一旦被同步进这些区域就会被清除，
 * 且清除后不会再生成（回到原地时伪装已经消失）。所以假尸体在同步位置前必须先用本判定挡住，
 * 让尸体留在区域外。
 *
 * <p>判定条件需与 {@link PlayerBodyEntity#tick()} 中的两处检查保持一致，
 * 改动其一必须同步改动另一处。
 */
public final class BodyCleanupZones {
    private BodyCleanupZones() {
    }

    public static boolean isInside(double x, double z) {
        if (x > TarotAssemblyManager.MEETING_X - 100 && x < TarotAssemblyManager.MEETING_X + 100
                && z > TarotAssemblyManager.MEETING_Z - 100 && z < TarotAssemblyManager.MEETING_Z + 100) {
            return true;
        }
        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
        return x > config.grosellTravelogBanishX - 100 && x < config.grosellTravelogBanishX + 100
                && z > config.grosellTravelogBanishZ - 100 && z < config.grosellTravelogBanishZ + 100;
    }

    public static boolean isInside(Entity entity) {
        return isInside(entity.getX(), entity.getZ());
    }
}
