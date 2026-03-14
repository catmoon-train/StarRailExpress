package pro.fazeclan.river.stupid_express.role.avaricious;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.OnGameTrueStarted;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import pro.fazeclan.river.stupid_express.constants.SERoles;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AvariciousGoldHandler {

    public static long gameStartTime = -1; // 添加游戏开始时间字段
    public static int TIMER_TICKS = GameConstants.getInTicks(0, 60); // 改为45秒一次，频率加倍
    public static double MAX_DISTANCE = 8.0; // 扩大距离范围
    public static int STARTING_BALANCE = 100; // 初始金币翻倍
    public static int BASE_PAYOUT_PER_PLAYER = 25; // 基础金币提高
    public static double DISTANCE_MULTIPLIER = 1.5; // 距离奖励系数

    private static final Map<UUID, Integer> playerBonusMap = new HashMap<>();

    public static int calculatePayout(int totalPlayerCount, int nearbyPlayers, double avgDistance) {
        double originalResult = 0;
        if (totalPlayerCount <= 6) {
            originalResult = (50 * (1 + (nearbyPlayers * 0.1))); // 基础值提高，且根据附近玩家数加成
        } else if (totalPlayerCount >= 20) {
            originalResult = (20 * (1 + (nearbyPlayers * 0.15))); // 提高下限和加成系数
        } else {
            // 优化后的公式：提高基础值和衰减系数，增加附近玩家加成
            double base = 120.0 * Math.exp(-0.09 * totalPlayerCount) + 15; // 提高基础值和降低衰减
            double nearbyBonus = 1 + (nearbyPlayers * 0.12); // 附近玩家加成
            double distanceBonus = 1 + ((MAX_DISTANCE - avgDistance) / MAX_DISTANCE) * 0.3; // 距离奖励

            originalResult = (base * nearbyBonus * distanceBonus);
        }
        return (int) Math.round((originalResult) * 0.75);
    }

    public static void onGameStart() {
        OnGameTrueStarted.EVENT.register((ServerLevel) -> {
            AvariciousGoldHandler.gameStartTime = -1;
        });
        ModdedRoleAssigned.EVENT.register(((player, role) -> {
            if (role.equals(SERoles.AVARICIOUS)) {
                SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
                shop.setBalance(STARTING_BALANCE);
                playerBonusMap.put(player.getUUID(), 0); // 初始化连续奖励计数
            }
        }));
    }

    public static void playerServerTick(ServerPlayer player, SREGameWorldComponent gameWorldComponent) {
        var serverWorld = player.serverLevel();
        long time = serverWorld.getGameTime();

        if (AvariciousGoldHandler.gameStartTime == -1) {
            AvariciousGoldHandler.gameStartTime = time;
            return;
        }

        long elapsed = time - AvariciousGoldHandler.gameStartTime;
        long timeinterval = elapsed % AvariciousGoldHandler.TIMER_TICKS;

        if (elapsed % AvariciousGoldHandler.TIMER_TICKS != 0) {
            if (elapsed % 20 == 0) {
                player.sendSystemMessage(
                        Component.translatable(
                                "hud.stupid_express.avaricious.payout_timer",
                                ((AvariciousGoldHandler.TIMER_TICKS - timeinterval) / 20))
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                        true);
            }
            return;
        }

        int nearbyPlayers = 0;
        for (ServerPlayer other : serverWorld.players()) {
            if (GameUtils.isPlayerEliminated(other))
                continue;
            if (other == player)
                continue;
            if (other.distanceTo(player) <= AvariciousGoldHandler.MAX_DISTANCE)
                nearbyPlayers++;
        }

        if (nearbyPlayers > 0) {
            int totalPlayers = serverWorld.players().size();
            // 计算平均距离
            double totalDistance = 0.0;
            for (ServerPlayer other : serverWorld.players()) {
                if (GameUtils.isPlayerEliminated(other))
                    continue;
                if (other == player)
                    continue;
                if (other.distanceTo(player) <= AvariciousGoldHandler.MAX_DISTANCE) {
                    totalDistance += other.distanceTo(player);
                }
            }
            double avgDistance = totalDistance / nearbyPlayers;

            int payoutPerPlayer = AvariciousGoldHandler.calculatePayout(totalPlayers, nearbyPlayers, avgDistance);
            int totalPayout = nearbyPlayers * payoutPerPlayer;
            // 确保不超过150金币上限
            totalPayout = Math.min(totalPayout, 150);
            SREPlayerShopComponent.KEY.get(player).addToBalance(totalPayout);
            player.playNotifySound(TMMSounds.UI_SHOP_BUY, SoundSource.PLAYERS, 10.0f, 0.5f);
        }
    }

    // public static void payout() {
    // ModdedRoleAssigned.EVENT.register(((player, role) -> {
    // if (role.equals(SERoles.AVARICIOUS)) {
    // GameTimeComponent timeComponent = GameTimeComponent.KEY.get(player.level());
    // boolean payoutTime = timeComponent.time % TIMER_TICKS == 0;

    // PlayerShopComponent shop = PlayerShopComponent.KEY.get(player);

    // if (payoutTime) {
    // int nearbyPlayerCount = 0;
    // double totalDistance = 0.0;

    // for (Player playerInWorld : player.level().players()) {
    // if (playerInWorld != player) {
    // double distance = playerInWorld.distanceTo(player);
    // if (distance <= MAX_DISTANCE) {
    // nearbyPlayerCount++;
    // totalDistance += distance;
    // }
    // }
    // }

    // if (nearbyPlayerCount > 0) {
    // double avgDistance = totalDistance / nearbyPlayerCount;
    // int totalPlayers = player.level().players().size();
    // int basePayout = calculatePayout(totalPlayers, nearbyPlayerCount,
    // avgDistance);

    // // 应用距离奖励（越近奖励越高）
    // double distanceMultiplier = 1 + (MAX_DISTANCE - avgDistance) / MAX_DISTANCE *
    // (DISTANCE_MULTIPLIER - 1);
    // int distanceAdjustedPayout = (int)(basePayout * distanceMultiplier);

    // // 连续触发奖励
    // int consecutiveCount = playerBonusMap.getOrDefault(player.getUUID(), 0) + 1;
    // int finalPayout = distanceAdjustedPayout;

    // if (consecutiveCount >= BONUS_THRESHOLD) {
    // finalPayout *= BONUS_MULTIPLIER;
    // consecutiveCount = 0; // 重置计数
    // }

    // playerBonusMap.put(player.getUUID(), consecutiveCount);

    // // 额外奖励：当附近玩家达到一定数量时
    // if (nearbyPlayerCount >= 5) {
    // finalPayout += (int)(BASE_PAYOUT_PER_PLAYER * nearbyPlayerCount * 0.5);
    // }

    // // 确保不超过150金币上限
    // finalPayout = Math.min(finalPayout, 150);
    // shop.addToBalance(finalPayout);

    // // 调试信息（可移除）
    // System.out.println("[Avaricious] Payout: " + finalPayout +
    // " | Nearby: " + nearbyPlayerCount +
    // " | AvgDist: " + String.format("%.1f", avgDistance));
    // }
    // }
    // }
    // }));
    // }
}