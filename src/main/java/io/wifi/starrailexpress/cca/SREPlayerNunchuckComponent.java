package io.wifi.starrailexpress.cca;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家双节棍组件
 * 用于追踪玩家被双节棍击打的记录
 */
public class SREPlayerNunchuckComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<SREPlayerNunchuckComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("player_nunchuck"), SREPlayerNunchuckComponent.class);

    public final Player player;
    private final Map<UUID, HitRecord> hitRecords = new HashMap<>(); // 记录被该玩家击打的记录
    private AttackRecord attackRecord = null; // 记录该玩家使用双节棍的记录
    private final Map<Integer, Long> moveCooldowns = new HashMap<>(); // 记录每个招式的使用时间 (direction -> last use time)

    public SREPlayerNunchuckComponent(Player player) {
        this.player = player;
    }

    /**
     * 记录玩家被击打
     * @param attackerId 攻击者的UUID
     * @param damageType 伤害类型(0: 左, 1: 右, 2: 后)
     * @param nearBlock 是否在方块侧面被击打
     */
    public void recordHit(UUID attackerId, int damageType, boolean nearBlock) {
        HitRecord record = new HitRecord();
        record.attackerId = attackerId;
        record.damageType = damageType;
        record.nearBlock = nearBlock;
        record.lastHitTime = ((net.minecraft.server.level.ServerPlayer) player).serverLevel().getGameTime();
        record.hitCount = 1;

        // 移除旧记录
        hitRecords.remove(attackerId);
        hitRecords.put(attackerId, record);

        KEY.sync(this.player);
    }

    /**
     * 增加击打次数
     */
    public void incrementHitCount(UUID attackerId) {
        HitRecord record = hitRecords.get(attackerId);
        if (record != null) {
            record.hitCount++;
            record.lastHitTime = ((net.minecraft.server.level.ServerPlayer) player).serverLevel().getGameTime();
            KEY.sync(this.player);
        }
    }

    /**
     * 获取击打记录
     */
    public HitRecord getHitRecord(UUID attackerId) {
        return hitRecords.get(attackerId);
    }

    /**
     * 清除击打记录
     */
    public void clearHitRecord(UUID attackerId) {
        hitRecords.remove(attackerId);
        KEY.sync(this.player);
    }

    /**
     * 清除所有击打记录
     */
    public void clearAllRecords() {
        hitRecords.clear();
        KEY.sync(this.player);
    }

    /**
     * 记录该玩家使用双节棍
     */
    public void recordAttack() {
        long currentTime = ((net.minecraft.server.level.ServerPlayer) player).serverLevel().getGameTime();
        attackRecord = new AttackRecord();
        attackRecord.lastAttackTime = currentTime;
        attackRecord.attackCount = 1;
        KEY.sync(this.player);
    }

    /**
     * 增加攻击次数
     */
    public void incrementAttackCount() {
        if (attackRecord != null) {
            attackRecord.attackCount++;
            attackRecord.lastAttackTime = ((net.minecraft.server.level.ServerPlayer) player).serverLevel().getGameTime();
            KEY.sync(this.player);
        }
    }

    /**
     * 获取攻击记录
     */
    public AttackRecord getAttackRecord() {
        return attackRecord;
    }

    /**
     * 检查是否需要在攻击后重置攻击记录（因为连续3次了）
     */
    public void resetAttackRecord() {
        attackRecord = null;
        KEY.sync(this.player);
    }

    /**
     * 检查某个招式是否在冷却中
     * @param direction 招式方向 (0: 向左, 1: 向右, 2: 向后, 3: 向前)
     * @param cooldownTicks 冷却时间（ticks）
     * @return true 如果在冷却中
     */
    public boolean isMoveOnCooldown(int direction, long cooldownTicks) {
        Long lastUseTime = moveCooldowns.get(direction);
        if (lastUseTime == null) {
            return false;
        }
        long currentTime = ((net.minecraft.server.level.ServerPlayer) player).serverLevel().getGameTime();
        return currentTime - lastUseTime < cooldownTicks;
    }

    /**
     * 记录某个招式的使用时间
     * @param direction 招式方向
     */
    public void recordMoveUse(int direction) {
        long currentTime = ((net.minecraft.server.level.ServerPlayer) player).serverLevel().getGameTime();
        moveCooldowns.put(direction, currentTime);
        KEY.sync(this.player);
    }

    @Override
    public void serverTick() {
        // 清理超过7秒的记录 (7秒 = 140 ticks)
        long currentTime = ((net.minecraft.server.level.ServerPlayer) player).serverLevel().getGameTime();
        long timeout = 140; // 7 seconds in ticks

        hitRecords.entrySet().removeIf(entry -> {
            HitRecord record = entry.getValue();
            if (currentTime - record.lastHitTime > timeout) {
                return true;
            }
            return false;
        });

        // 清理攻击者的攻击记录
        if (attackRecord != null) {
            if (currentTime - attackRecord.lastAttackTime > timeout) {
                attackRecord = null;
            }
        }

        // 清理招式冷却记录
        moveCooldowns.entrySet().removeIf(entry -> {
            return currentTime - entry.getValue() > timeout;
        });

        if (!hitRecords.isEmpty() && currentTime % 20 == 0) {
            KEY.sync(this.player);
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
        CompoundTag recordsTag = new CompoundTag();
        for (Map.Entry<UUID, HitRecord> entry : hitRecords.entrySet()) {
            CompoundTag recordTag = new CompoundTag();
            recordTag.putUUID("attacker_id", entry.getValue().attackerId);
            recordTag.putInt("damage_type", entry.getValue().damageType);
            recordTag.putBoolean("near_block", entry.getValue().nearBlock);
            recordTag.putLong("last_hit_time", entry.getValue().lastHitTime);
            recordTag.putInt("hit_count", entry.getValue().hitCount);
            recordsTag.put(entry.getKey().toString(), recordTag);
        }
        tag.put("hit_records", recordsTag);

        // 保存攻击记录
        if (attackRecord != null) {
            CompoundTag attackTag = new CompoundTag();
            attackTag.putLong("last_attack_time", attackRecord.lastAttackTime);
            attackTag.putInt("attack_count", attackRecord.attackCount);
            tag.put("attack_record", attackTag);
        }

        // 保存招式冷却记录
        CompoundTag moveCooldownsTag = new CompoundTag();
        for (Map.Entry<Integer, Long> entry : moveCooldowns.entrySet()) {
            moveCooldownsTag.putLong(String.valueOf(entry.getKey()), entry.getValue());
        }
        tag.put("move_cooldowns", moveCooldownsTag);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
        hitRecords.clear();
        if (tag.contains("hit_records", Tag.TAG_COMPOUND)) {
            CompoundTag recordsTag = tag.getCompound("hit_records");
            for (String key : recordsTag.getAllKeys()) {
                CompoundTag recordTag = recordsTag.getCompound(key);
                HitRecord record = new HitRecord();
                record.attackerId = recordTag.getUUID("attacker_id");
                record.damageType = recordTag.getInt("damage_type");
                record.nearBlock = recordTag.getBoolean("near_block");
                record.lastHitTime = recordTag.getLong("last_hit_time");
                record.hitCount = recordTag.getInt("hit_count");
                hitRecords.put(UUID.fromString(key), record);
            }
        }

        // 读取攻击记录
        if (tag.contains("attack_record", Tag.TAG_COMPOUND)) {
            CompoundTag attackTag = tag.getCompound("attack_record");
            attackRecord = new AttackRecord();
            attackRecord.lastAttackTime = attackTag.getLong("last_attack_time");
            attackRecord.attackCount = attackTag.getInt("attack_count");
        }

        // 读取招式冷却记录
        if (tag.contains("move_cooldowns", Tag.TAG_COMPOUND)) {
            CompoundTag moveCooldownsTag = tag.getCompound("move_cooldowns");
            for (String key : moveCooldownsTag.getAllKeys()) {
                moveCooldowns.put(Integer.parseInt(key), moveCooldownsTag.getLong(key));
            }
        }
    }

    public static class HitRecord {
        public UUID attackerId;
        public int damageType; // 0: 左,1: 右, 2: 后
        public boolean nearBlock; // 是否在方块侧面
        public long lastHitTime;
        public int hitCount;
    }

    public static class AttackRecord {
        public long lastAttackTime;
        public int attackCount;
    }
    
    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public Player getPlayer() {
        return this.player;
    }

    @Override
    public void init() {
        this.clearAllRecords();
    }

    @Override
    public void clear() {
        this.init();
    }
}
