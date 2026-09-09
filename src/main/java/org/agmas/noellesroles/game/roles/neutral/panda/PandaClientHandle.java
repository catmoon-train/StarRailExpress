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

package org.agmas.noellesroles.game.roles.neutral.panda;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.event.AllowOtherCameraType;
import io.wifi.starrailexpress.event.client.OnGameFinishedClient;
import io.wifi.starrailexpress.event.client.OnGameStartedClient;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.client.PandaDisguiseRenderer;

/**
 * 黑白熊猫形态的客户端状态维护。
 *
 * <p>熊猫外观本身由 {@link PandaDisguiseRenderer} 纯渲染，这里只负责在形态结束时丢掉缓存的熊猫，
 * 以及熊猫形态下强制第三人称视角。
 */
public class PandaClientHandle {

    public static void tickVisual(Player player, boolean isPanda) {
        if (SREClient.gameComponent == null || !SREClient.gameComponent.isRunning()) {
            PandaDisguiseRenderer.clear();
            return;
        }
        if (!isPanda || player.isSpectator()) {
            PandaDisguiseRenderer.discard(player.getUUID());
        }
    }

    static {
        AllowOtherCameraType.EVENT.register((original, localplayer) -> {
            if (PandaDisguiseRenderer.shouldDisguise(localplayer)) {
                return AllowOtherCameraType.ReturnCameraType.THIRD_PERSON_BACK;
            }
            return AllowOtherCameraType.ReturnCameraType.NO_CHANGE;
        });
        OnGameStartedClient.EVENT.register(PandaDisguiseRenderer::clear);
        OnGameFinishedClient.EVENT.register(PandaDisguiseRenderer::clear);
    }
}
