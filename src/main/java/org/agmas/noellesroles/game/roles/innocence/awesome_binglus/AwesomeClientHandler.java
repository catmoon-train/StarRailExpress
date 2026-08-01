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

package org.agmas.noellesroles.game.roles.innocence.awesome_binglus;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

public class AwesomeClientHandler {

    public static void renderParticleOfPlayer(Minecraft client, Player p, AwesomePlayerComponent aweC) {
        // Noellesroles.LOGGER.info(p.getScoreboardName() + ":" + aweC.nearByDeathTime);
        if (aweC.nearByDeathTime <= 0) {
            return;
        }
        DustParticleOptions greenDust = new DustParticleOptions(
                new Vector3f(1.0f
                        * ((float) aweC.nearByDeathTime
                                / (float) AwesomePlayerComponent.nearByDeathTimeRecordTime),
                        0.0f,
                        0.0f),
                (2.0f * ((float) aweC.nearByDeathTime
                        / (float) AwesomePlayerComponent.nearByDeathTimeRecordTime)) + 0.2f);
        client.level.addParticle(
                greenDust, true,
                p.getX(), p.getY() + 2.0, p.getZ(), // 在玩家头上稍上方
                0, 0, 0 // 速度为0
        );
    }

}
