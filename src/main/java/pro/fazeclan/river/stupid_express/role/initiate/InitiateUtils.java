package pro.fazeclan.river.stupid_express.role.initiate;

import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import net.minecraft.server.level.ServerPlayer;
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
                    ServerPlayer initiate = initiates.get(0);
                    clearModItems(initiate);
                    StupidRoleUtils.changeRole(initiate, SERoles.AMNESIAC);
                    StupidRoleUtils.sendWelcomeAnnouncement(initiate);
                }
            }
        });
    }

}
