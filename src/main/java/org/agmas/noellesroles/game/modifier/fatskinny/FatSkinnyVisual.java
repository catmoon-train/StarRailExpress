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

package org.agmas.noellesroles.game.modifier.fatskinny;

import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.game.modifier.NRModifiers;

import java.util.UUID;

/**
 * 胖子/瘦子第三人称模型的水平缩放倍率。
 */
public final class FatSkinnyVisual {
    private FatSkinnyVisual() {
    }

    public static float horizontalScale(LivingEntity entity) {
        if (entity instanceof Player player) {
            return horizontalScale(player.level(), player.getUUID());
        }
        if (entity instanceof PlayerBodyEntity body) {
            UUID uuid = body.getPlayerUuid();
            if (uuid == null) {
                return 1.0F;
            }
            return horizontalScale(body.level(), uuid);
        }
        return 1.0F;
    }

    public static float horizontalScale(Level level, UUID uuid) {
        if (level == null || uuid == null) {
            return 1.0F;
        }
        WorldModifierComponent cca = WorldModifierComponent.KEY.maybeGet(level).orElse(null);
        if (cca == null) {
            return 1.0F;
        }
        boolean fat = cca.isModifier(uuid, NRModifiers.FAT);
        boolean skinny = cca.isModifier(uuid, NRModifiers.SKINNY);
        return FatSkinnyPushLogic.horizontalScale(fat, skinny);
    }
}
