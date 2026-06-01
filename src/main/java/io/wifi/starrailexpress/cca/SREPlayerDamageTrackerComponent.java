package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 追踪玩家受到伤害事件的组件
 * 用于实体交互方块的条件触发
 */
public class SREPlayerDamageTrackerComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<SREPlayerDamageTrackerComponent> KEY =
            ComponentRegistry.getOrCreate(
                    SRE.id("player_damage_tracker"),
                    SREPlayerDamageTrackerComponent.class
            );

    private final Player player;

    // 玩家UUID -> 伤害记录
    private final ConcurrentHashMap<UUID, DamageRecord> recentDamages = new ConcurrentHashMap<>();

    // 伤害记录保存的时间窗口（tick），默认10秒
    private static final int DAMAGE_TRACKING_WINDOW = 200; // 10秒 * 20tick

    public SREPlayerDamageTrackerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        recentDamages.clear();
    }

    @Override
    public void clear() {
        init();
    }

    /**
     * 记录玩家受到伤害
     * @param hurtPlayer 受伤的玩家
     * @param isPlayerDamage 是否来自其他玩家的伤害
     * @param attackerUuid 攻击者UUID（如果是玩家伤害）
     */
    public static void recordDamage(Player hurtPlayer, boolean isPlayerDamage, UUID attackerUuid) {
        SREPlayerDamageTrackerComponent component = KEY.get(hurtPlayer);
        if (component != null) {
            component.addDamageRecord(isPlayerDamage, attackerUuid);
        }
    }

    private void addDamageRecord(boolean isPlayerDamage, UUID attackerUuid) {
        DamageRecord record = new DamageRecord(
                System.currentTimeMillis(),
                isPlayerDamage,
                attackerUuid,
                player.level().getGameTime()
        );
        recentDamages.put(player.getUUID(), record);
    }

    /**
     * 检查玩家是否在指定范围内受到过玩家伤害
     * @param checkPlayer 要检查的玩家
     * @param currentGameTime 当前游戏时间
     * @return true 如果玩家在时间窗口内受到过玩家伤害
     */
    public static boolean hasPlayerDamage(Player checkPlayer, long currentGameTime) {
        SREPlayerDamageTrackerComponent component = KEY.get(checkPlayer);
        if (component == null) return false;
        return component.checkPlayerDamage(checkPlayer.getUUID(), currentGameTime);
    }

    private boolean checkPlayerDamage(UUID playerUuid, long currentGameTime) {
        DamageRecord record = recentDamages.get(playerUuid);
        if (record == null) return false;
        if (currentGameTime - record.gameTime > DAMAGE_TRACKING_WINDOW) {
            recentDamages.remove(playerUuid);
            return false;
        }
        return record.isPlayerDamage;
    }

    /**
     * 检查玩家是否在指定范围内受到过非玩家伤害
     * @param checkPlayer 要检查的玩家
     * @param currentGameTime 当前游戏时间
     * @return true 如果玩家在时间窗口内受到过非玩家伤害
     */
    public static boolean hasNonPlayerDamage(Player checkPlayer, long currentGameTime) {
        SREPlayerDamageTrackerComponent component = KEY.get(checkPlayer);
        if (component == null) return false;
        return component.checkNonPlayerDamage(checkPlayer.getUUID(), currentGameTime);
    }

    private boolean checkNonPlayerDamage(UUID playerUuid, long currentGameTime) {
        DamageRecord record = recentDamages.get(playerUuid);
        if (record == null) return false;
        if (currentGameTime - record.gameTime > DAMAGE_TRACKING_WINDOW) {
            recentDamages.remove(playerUuid);
            return false;
        }
        return !record.isPlayerDamage;
    }

    @Override
    public void serverTick() {
        // 清理过期的记录
        long currentGameTime = player.level().getGameTime();

        recentDamages.entrySet().removeIf(entry -> {
            DamageRecord record = entry.getValue();
            return currentGameTime - record.gameTime > DAMAGE_TRACKING_WINDOW;
        });
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registryLookup) {
        // 不需要同步伤害记录
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registryLookup) {
        // 不需要同步伤害记录
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registryLookup) {
        // 不需要持久化伤害记录，重生时清空
        recentDamages.clear();
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registryLookup) {
        // 不需要持久化伤害记录
    }

    /**
     * 伤害记录
     */
    private record DamageRecord(
            long timestamp,      // 时间戳
            boolean isPlayerDamage, // 是否是玩家伤害
            UUID attackerUuid,    // 攻击者UUID
            long gameTime         // 游戏时间
    ) {}
}
