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

package pro.fazeclan.river.stupid_express.role.initiate;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.agmas.noellesroles.init.ModEffects;
import pro.fazeclan.river.stupid_express.constants.SEItems;
import pro.fazeclan.river.stupid_express.constants.SERoles;
import pro.fazeclan.river.stupid_express.utils.StupidRoleUtils;

import java.util.List;
import java.util.stream.Collectors;

public class InitiateUtils {

    private static final int FIVE_SECONDS_TICKS = GameConstants.getInTicks(0, 5);

    private static void clearModItems(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var item = player.getInventory().getItem(i);
            // 清除模组物品：刀、汽油桶、打火机
            if (item.is(TMMItems.KNIFE) ||
                    item.is(SEItems.JERRY_CAN) ||
                    item.is(SEItems.LIGHTER)) {
                player.getInventory().setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
            }
        }
    }

    public static void InitiateChange() {
        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
            boolean isGameRuning = false;
            var gameWorldComponent = SREGameWorldComponent.KEY.get(server.overworld());
            if (gameWorldComponent != null) {
                if (gameWorldComponent.isRunning()) {
                    isGameRuning = true;
                }
            }
            if (!gameWorldComponent.isSkillAvailable) {
                // 技能不可用
                return;
            }
            if (!isGameRuning)
                return;
            if (server.getPlayerList().getPlayers().isEmpty()) {
                return;
            }

            var playerList = server.getPlayerList().getPlayers();
            var level = playerList.getFirst().level();
            var gameTimeComponent = SREGameTimeComponent.KEY.get(level);

            // 检查是否有初学者
            List<ServerPlayer> initiates = playerList.stream()
                    .filter(p -> GameUtils.isPlayerAliveAndSurvival(p)
                            && (gameWorldComponent.isRole(p, SERoles.INITIATE)))
                    .collect(Collectors.toList());
            int initiateCount = initiates.size();

            // 如果没有初学者，不做任何修改
            if (initiateCount == 0) {
                return;
            }
            // 如果有2个或更多初学者，不做任何修改
            if (initiateCount >= 2) {
                return;
            }

            // 如果只有1个初学者，每隔5秒检查一次
            if (initiateCount == 1) {
                long gameTime = gameTimeComponent.time;

                // 检查是否是5秒的整倍数时刻
                if (gameTime % FIVE_SECONDS_TICKS == 0) {

                    // 安全时间
                    if (initiates.stream().anyMatch(p -> p.hasEffect(ModEffects.SAFE_TIME))) {
                        return;
                    }

                    ServerPlayer initiate = initiates.get(0);
                    // 领袖追随者初学者：不再因考核失败而转职（死亡）
                    if (org.agmas.noellesroles.game.roles.neutral.leader.LeaderFollowerEffects
                            .isFollowerOfLeader(initiate)) {
                        return;
                    }
                    clearModItems(initiate);
                    StupidRoleUtils.changeRole(initiate, SERoles.AMNESIAC);

                    // 播放全场音效
                    initiate.level().playSound(null, initiate.blockPosition(),
                            SoundEvents.CONDUIT_ATTACK_TARGET, SoundSource.MASTER, 5.0F, 1.0F);

                    StupidRoleUtils.sendWelcomeAnnouncement(initiate);
                }
            }
        });
    }

}
