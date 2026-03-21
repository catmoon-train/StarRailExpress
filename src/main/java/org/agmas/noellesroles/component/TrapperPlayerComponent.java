package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.game.GameUtils;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.entity.CalamityMarkEntity;
import org.agmas.noellesroles.entity.TripwireTrapEntity;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;

import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 设陷者组件
 *
 * 管理设陷者的陷阱机制：
 * - 技能对准地面设置隐形灾厄印记陷阱
 * - 其他玩家踩中触发，发出巨响暴露位置并发光
 * - 施加"标记"，被标记者被囚禁
 * - 囚禁时间递增：3秒 -> 10秒 -> 25秒
 * - 可切换为绊索陷阱（可见、可拆除、根据疾跑状态不同效果）
 */
public class TrapperPlayerComponent implements RoleComponent, ServerTickingComponent {
    @Override
    public Player getPlayer() {
        return player;
    }

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<TrapperPlayerComponent> KEY = ModComponents.TRAPPER;

    // ==================== 常量定义 ====================

    /** 陷阱恢复时间（30秒 = 600 tick） */
    public static final int TRAP_RECHARGE_TIME = 600;

    /** 最大陷阱储存数量 */
    public static final int MAX_TRAP_CHARGES = 3;

    /** 最大陷阱放置距离（格） */
    public static final double MAX_PLACE_DISTANCE = 8.0;

    /** 第一次触发囚禁时间（3秒 = 60 tick） */
    public static final int PRISON_TIME_1 = 60;

    /** 第二次触发囚禁时间（10秒 = 200 tick） */
    public static final int PRISON_TIME_2 = 200;

    /** 第三次及以上触发囚禁时间（25秒 = 500 tick） */
    public static final int PRISON_TIME_3 = 500;

    /** 发光效果持续时间（5秒 = 100 tick） */
    public static final int GLOWING_DURATION = 100;

    // ==================== 陷阱类型 ====================

    /** 灾厄陷阱类型 */
    public static final int TRAP_TYPE_CALAMITY = 0;

    /** 绊索陷阱类型 */
    public static final int TRAP_TYPE_TRIPWIRE = 1;

    /** 陷阱类型总数 */
    public static final int TRAP_TYPE_COUNT = 2;

    // ==================== 状态变量 ====================

    private final Player player;

    /** 当前陷阱储存数量 */
    public int trapCharges = MAX_TRAP_CHARGES;

    /** 陷阱恢复计时器（tick） */
    public int rechargeTimer = 0;

    /** 是否已标记为设陷者 */
    public boolean isTrapperMarked = false;

    /** 是否正在恢复陷阱（用于防止重置计时器） */
    private boolean isRecharging = false;

    /** 记录每个玩家被触发陷阱的次数（用于增加囚禁时间） */
    private Map<UUID, Integer> triggerCounts = new HashMap<>();

    /** 当前被囚禁玩家的剩余时间 */
    private Map<UUID, Integer> prisonTimers = new HashMap<>();

    /** 被囚禁玩家的位置（用于锁定） */
    private Map<UUID, Vec3> prisonPositions = new HashMap<>();

    /** 当前选择的陷阱类型 */
    public int selectedTrapType = TRAP_TYPE_CALAMITY;

    /**
     * 构造函数
     */
    public TrapperPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    /**
     * 重置组件状态
     * 在游戏开始时或角色分配时调用
     */
    @Override
    public void init() {
        this.trapCharges = MAX_TRAP_CHARGES;
        this.rechargeTimer = 0;
        this.isTrapperMarked = true;
        this.isRecharging = false;
        this.triggerCounts.clear();
        this.prisonTimers.clear();
        this.prisonPositions.clear();
        this.selectedTrapType = TRAP_TYPE_CALAMITY;
        this.sync();
    }

    @Override
    public void clear() {
        this.trapCharges = MAX_TRAP_CHARGES;
        this.rechargeTimer = 0;
        this.isTrapperMarked = false;
        this.isRecharging = false;
        this.triggerCounts.clear();
        this.prisonTimers.clear();
        this.prisonPositions.clear();
        this.selectedTrapType = TRAP_TYPE_CALAMITY;
        this.sync();
    }

    /**
     * 完全清除组件状态（游戏结束时调用）
     */
    public void clearAll() {
        this.trapCharges = MAX_TRAP_CHARGES;
        this.rechargeTimer = 0;
        this.isTrapperMarked = false;
        this.isRecharging = false;
        this.triggerCounts.clear();
        this.prisonTimers.clear();
        this.prisonPositions.clear();
        this.selectedTrapType = TRAP_TYPE_CALAMITY;
        this.sync();
    }

    /**
     * 检查是否可以放置陷阱
     */
    public boolean canPlaceTrap() {
        return trapCharges > 0;
    }

    /**
     * 获取当前陷阱储存数量
     */
    public int getTrapCharges() {
        return trapCharges;
    }

    /**
     * 获取恢复计时器秒数
     */
    public float getRechargeSeconds() {
        return rechargeTimer / 20.0f;
    }

    /**
     * 获取恢复进度百分比（0.0 - 1.0）
     */
    public float getRechargeProgress() {
        if (trapCharges >= MAX_TRAP_CHARGES)
            return 1.0f;
        return 1.0f - ((float) rechargeTimer / TRAP_RECHARGE_TIME);
    }

    /**
     * 获取当前选择的陷阱类型
     */
    public int getSelectedTrapType() {
        return selectedTrapType;
    }

    /**
     * 切换陷阱类型
     */
    public void switchTrapType() {
        this.selectedTrapType = (this.selectedTrapType + 1) % TRAP_TYPE_COUNT;
        this.sync();
    }

    /**
     * 获取当前陷阱类型名称的翻译键
     */
    public String getTrapTypeName() {
        return switch (selectedTrapType) {
            case TRAP_TYPE_TRIPWIRE -> "hud.noellesroles.trapper.type.tripwire";
            default -> "hud.noellesroles.trapper.type.calamity";
        };
    }

    /**
     * 尝试放置陷阱
     * 
     * @return 是否成功放置
     */
    public boolean tryPlaceTrap() {
        if (!canPlaceTrap())
            return false;
        if (!(player instanceof ServerPlayer serverPlayer))
            return false;

        Level world = player.level();

        // 射线检测找到地面
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookDir = player.getViewVector(1.0f);
        Vec3 endPos = eyePos.add(lookDir.scale(MAX_PLACE_DISTANCE));

        ClipContext context = new ClipContext(
                eyePos, endPos,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player);
        BlockHitResult hit = world.clip(context);

        if (hit.getType() == HitResult.Type.MISS) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.trapper.no_ground")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        // 获取放置位置（方块上方）
        BlockPos hitPos = hit.getBlockPos();
        Vec3 spawnPos = new Vec3(hitPos.getX() + 0.5, hitPos.getY() + 1.0, hitPos.getZ() + 0.5);

        // 根据当前选择的陷阱类型创建实体
        if (selectedTrapType == TRAP_TYPE_TRIPWIRE) {
            // 创建绊索陷阱实体
            TripwireTrapEntity trap = new TripwireTrapEntity(ModEntities.TRIPWIRE_TRAP, world);
            trap.setPos(spawnPos.add(0,0.3,0));
            trap.setOwner(player);
            world.addFreshEntity(trap);
        } else {
            // 创建灾厄印记实体
            CalamityMarkEntity mark = new CalamityMarkEntity(ModEntities.CALAMITY_MARK, world);
            mark.setPos(spawnPos.add(0,0.3,0));
            mark.setOwner(player);
            world.addFreshEntity(mark);
        }

        // 消耗一个陷阱储存
        this.trapCharges--;

        // 如果陷阱未满，且恢复计时器未开始，则开始计时
        if (trapCharges < MAX_TRAP_CHARGES && rechargeTimer == 0) {
            this.rechargeTimer = TRAP_RECHARGE_TIME;
        }

        // 播放放置音效（只有设陷者能听到）
        serverPlayer.playSound(SoundEvents.SCULK_CLICKING, 0.5f, 1.5f);

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.trapper.trap_placed")
                        .withStyle(ChatFormatting.GREEN),
                true);

        this.sync();
        return true;
    }

    /**
     * 当陷阱被触发时调用
     * 
     * @param victim  触发陷阱的玩家
     * @param trapPos 陷阱位置
     */
    public void onTrapTriggered(Player victim, Vec3 trapPos) {
        if (victim == null || victim.level().isClientSide())
            return;
        victim.stopRiding();
        // 获取该玩家的触发次数
        UUID victimUuid = victim.getUUID();
        int count = triggerCounts.getOrDefault(victimUuid, 0) + 1;
        triggerCounts.put(victimUuid, count);

        // 计算囚禁时间
        int prisonTime;
        if (count == 1) {
            prisonTime = PRISON_TIME_1;
        } else if (count == 2) {
            prisonTime = PRISON_TIME_2;
        } else {
            prisonTime = PRISON_TIME_3;
        }

        // 设置囚禁状态
        prisonTimers.put(victimUuid, prisonTime);
        prisonPositions.put(victimUuid, victim.position());

        // 播放巨响音效（仅受害者和狼人能听到）
        Level world = victim.level();
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(world);
        world.playSound(victim, trapPos.x, trapPos.y, trapPos.z,
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 2.0f, 0.5f);
        world.playSound(victim, trapPos.x, trapPos.y, trapPos.z,
                SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 3.0f, 0.3f);
        for (var p : world.players()) {
            SRERole role = gameWorldComponent.getRole(p);
            if (role != null) {
                if (role.canUseKiller()) {
                    world.playSound(p, trapPos.x, trapPos.y, trapPos.z,
                            SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 2.0f, 0.5f);
                    world.playSound(p, trapPos.x, trapPos.y, trapPos.z,
                            SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 3.0f, 0.3f);
                } else if (role.isNeutralForKiller()) {
                    world.playSound(p, trapPos.x, trapPos.y, trapPos.z,
                            SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 2.0f, 0.5f);
                    world.playSound(p, trapPos.x, trapPos.y, trapPos.z,
                            SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 3.0f, 0.3f);
                }
            }
        }
        // // 给受害者发光效果
        // victim.addEffect(new MobEffectInstance(
        // MobEffects.GLOWING, GLOWING_DURATION, 0, false, false, true
        // ));

        // 给受害者缓慢和挖掘疲劳（防止移动和破坏方块）
        victim.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN, prisonTime, 255, false, false, false));
        victim.addEffect(new MobEffectInstance(
                ModEffects.TURN_BANED, prisonTime, 255, false, false, false));
        victim.addEffect(new MobEffectInstance(
                ModEffects.MOVE_BANED, prisonTime, 255, false, false, false));
        victim.addEffect(new MobEffectInstance(
                ModEffects.USED_BANED, prisonTime, 255, false, false, false));
        victim.addEffect(new MobEffectInstance(
                MobEffects.DIG_SLOWDOWN, prisonTime, 255, false, false, false));
        victim.addEffect(new MobEffectInstance(
                MobEffects.JUMP, prisonTime, 128, false, false, false)); // 负面跳跃（防止跳跃）

        // 发送消息给受害者
        if (victim instanceof ServerPlayer serverVictim) {
            String timeStr = String.format("%.1f", prisonTime / 20.0f);
            serverVictim.displayClientMessage(
                    Component.translatable("message.noellesroles.trapper.trap_triggered", timeStr)
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                    true);

            if (count > 1) {
                serverVictim.displayClientMessage(
                        Component.translatable("message.noellesroles.trapper.mark_count", count)
                                .withStyle(ChatFormatting.DARK_RED),
                        true);
            }
        }

        // 发送消息给设陷者
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.trapper.trap_triggered_notify", victim.getName())
                            .withStyle(ChatFormatting.GREEN),
                    true);
        }

        this.sync();
    }

    /**
     * 检查是否是活跃的设陷者
     */
    public boolean isActiveTrapper() {
        return isTrapperMarked;
    }

    /**
     * 同步到客户端
     */
    public void sync() {
        ModComponents.TRAPPER.sync(this.player);
    }

    // ==================== Tick 处理 ====================
    static int tickR = 0;

    @Override
    public void serverTick() {
        // 只在设陷者角色时处理
        if (!isActiveTrapper())
            return;

        ++tickR;
        if (tickR % 20 == 0) {
            sync();
        }
        // 处理陷阱恢复计时
        if (trapCharges < MAX_TRAP_CHARGES) {
            if (isRecharging) {
                if (rechargeTimer > 0) {
                    rechargeTimer--;
                    if (rechargeTimer == 0) {
                        // 恢复一个陷阱
                        trapCharges++;
                        this.sync();
                        // 如果还没满，继续计时
                        if (trapCharges < MAX_TRAP_CHARGES) {
                            rechargeTimer = TRAP_RECHARGE_TIME;
                        } else {
                            // 已满，停止恢复
                            isRecharging = false;
                        }
                    }
                }
            } else {
                // 开始恢复计时
                isRecharging = true;
                rechargeTimer = TRAP_RECHARGE_TIME;
            }
        } else {
            // 陷阱已满，停止恢复
            isRecharging = false;
            rechargeTimer = 0;
        }

        // 处理所有被囚禁玩家
        Level world = player.level();
        for (Map.Entry<UUID, Integer> entry : new HashMap<>(prisonTimers).entrySet()) {
            UUID victimUuid = entry.getKey();
            int remaining = entry.getValue();

            if (remaining > 0) {
                // 找到受害者
                Player victim = world.getPlayerByUUID(victimUuid);
                if (victim != null && GameUtils.isPlayerAliveAndSurvival(victim)) {
                    // 锁定位置
                    Vec3 prisonPos = prisonPositions.get(victimUuid);
                    if (prisonPos != null) {
                        // 如果玩家移动了，拉回原位
                        double dist = victim.position().distanceToSqr(prisonPos);
                        if (dist > 0.1) {
                            if (victim instanceof ServerPlayer serverVictim) {
                                serverVictim.teleportTo(
                                        serverVictim.serverLevel(),
                                        prisonPos.x, prisonPos.y, prisonPos.z,
                                        victim.getYRot(), victim.getXRot());
                            }
                        }
                    }
                }

                // 减少剩余时间
                prisonTimers.put(victimUuid, remaining - 1);
            } else {
                // 囚禁结束
                prisonTimers.remove(victimUuid);
                prisonPositions.remove(victimUuid);

                Player victim = world.getPlayerByUUID(victimUuid);
                if (victim instanceof ServerPlayer serverVictim) {
                    serverVictim.displayClientMessage(
                            Component.translatable("message.noellesroles.trapper.prison_ended")
                                    .withStyle(ChatFormatting.GREEN),
                            true);
                }
            }
        }
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("trapCharges", this.trapCharges);
        tag.putInt("rechargeTimer", this.rechargeTimer);
        tag.putBoolean("isTrapperMarked", this.isTrapperMarked);
        tag.putBoolean("isRecharging", this.isRecharging);
        tag.putInt("selectedTrapType", this.selectedTrapType);

        // 保存触发次数
        CompoundTag countsTag = new CompoundTag();
        for (Map.Entry<UUID, Integer> entry : triggerCounts.entrySet()) {
            countsTag.putInt(entry.getKey().toString(), entry.getValue());
        }
        tag.put("triggerCounts", countsTag);

        // 保存囚禁计时器
        CompoundTag timersTag = new CompoundTag();
        for (Map.Entry<UUID, Integer> entry : prisonTimers.entrySet()) {
            timersTag.putInt(entry.getKey().toString(), entry.getValue());
        }
        tag.put("prisonTimers", timersTag);

        // 保存囚禁位置
        CompoundTag positionsTag = new CompoundTag();
        for (Map.Entry<UUID, Vec3> entry : prisonPositions.entrySet()) {
            CompoundTag posTag = new CompoundTag();
            posTag.putDouble("x", entry.getValue().x);
            posTag.putDouble("y", entry.getValue().y);
            posTag.putDouble("z", entry.getValue().z);
            positionsTag.put(entry.getKey().toString(), posTag);
        }
        tag.put("prisonPositions", positionsTag);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.trapCharges = tag.contains("trapCharges") ? tag.getInt("trapCharges") : MAX_TRAP_CHARGES;
        this.rechargeTimer = tag.contains("rechargeTimer") ? tag.getInt("rechargeTimer") : 0;
        this.isTrapperMarked = tag.contains("isTrapperMarked") && tag.getBoolean("isTrapperMarked");
        this.isRecharging = tag.contains("isRecharging") && tag.getBoolean("isRecharging");
        this.selectedTrapType = tag.contains("selectedTrapType") ? tag.getInt("selectedTrapType") : TRAP_TYPE_CALAMITY;

        // 读取触发次数
        this.triggerCounts.clear();
        if (tag.contains("triggerCounts")) {
            CompoundTag countsTag = tag.getCompound("triggerCounts");
            for (String key : countsTag.getAllKeys()) {
                try {
                    UUID uuid = UUID.fromString(key);
                    this.triggerCounts.put(uuid, countsTag.getInt(key));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        // 读取囚禁计时器
        this.prisonTimers.clear();
        if (tag.contains("prisonTimers")) {
            CompoundTag timersTag = tag.getCompound("prisonTimers");
            for (String key : timersTag.getAllKeys()) {
                try {
                    UUID uuid = UUID.fromString(key);
                    this.prisonTimers.put(uuid, timersTag.getInt(key));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        // 读取囚禁位置
        this.prisonPositions.clear();
        if (tag.contains("prisonPositions")) {
            CompoundTag positionsTag = tag.getCompound("prisonPositions");
            for (String key : positionsTag.getAllKeys()) {
                try {
                    UUID uuid = UUID.fromString(key);
                    CompoundTag posTag = positionsTag.getCompound(key);
                    Vec3 pos = new Vec3(
                            posTag.getDouble("x"),
                            posTag.getDouble("y"),
                            posTag.getDouble("z"));
                    this.prisonPositions.put(uuid, pos);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    
    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("selectedTrapType", this.selectedTrapType);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.selectedTrapType = tag.contains("selectedTrapType") ? tag.getInt("selectedTrapType") : TRAP_TYPE_CALAMITY;
    }
}