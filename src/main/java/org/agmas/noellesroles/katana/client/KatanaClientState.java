package org.agmas.noellesroles.katana.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.agmas.noellesroles.katana.KatanaState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 武士刀的客户端动画状态（仅客户端）。
 *
 * <p>服务端通过实体事件（{@link KatanaState#EVENT_MOVE_USED_BASE} 等）广播
 * 「本次使用的招式」「下一招式预测」「格挡内置冷却」，本类按实体 id 记录并
 * 提供给第一人称 / 第三人称渲染 Mixin 读取。
 *
 * <p>本地玩家攻击瞬间会先做一次乐观预测（见 {@code MinecraftKatanaAttackMixin}），
 * 服务端事件随后到达时若为同一招式则不重启动画（去重）。
 */
public final class KatanaClientState {

    private KatanaClientState() {
    }

    /** 招式动画时长（tick）。 */
    public static final int MOVE_ANIM_DURATION_TICKS = 10;
    /** 与服务端 {@code KatanaState.BLOCK_INTERNAL_COOLDOWN_TICKS} 一致的格挡内置冷却。 */
    private static final int BLOCK_INTERNAL_COOLDOWN_TICKS = 100;

    private static class Entry {
        /** 当前正在播放动画的招式（1~3）。 */
        int animMove = KatanaState.MOVE_SWEEP;
        /** 动画开始的游戏时间（tick）。 */
        long animStartGameTime;
        /** 乐观动画的起始游戏时间（用于去重，避免服务端事件重启动画）。 */
        long optimisticAnimGameTime = Long.MIN_VALUE;
        /** 下一招式预测（服务端权威）。 */
        int currentMove = KatanaState.MOVE_SWEEP;
        /** 格挡内置冷却截止（游戏时间 tick）。 */
        long blockCooldownUntil;
        /** 本次格挡是否为「衔接格挡」（0 前摇 → 抬臂瞬间到位）。 */
        boolean blockLinked;
    }

    private static final Map<Integer, Entry> BY_ENTITY_ID = new ConcurrentHashMap<>();

    private static Entry get(LivingEntity entity) {
        return BY_ENTITY_ID.computeIfAbsent(entity.getId(), id -> new Entry());
    }

    // ── 实体事件入口（由 LivingEntityKatanaEventMixin 调用） ──

    /** 收到「本次使用的招式」事件：同一招式且刚做过乐观预测时不重启动画。 */
    public static void onMoveUsed(LivingEntity entity, int move) {
        Entry entry = get(entity);
        long now = currentGameTime();
        if (entry.animMove == move && entry.optimisticAnimGameTime != Long.MIN_VALUE
                && now - entry.optimisticAnimGameTime < MOVE_ANIM_DURATION_TICKS) {
            return; // 保留乐观动画的起始时间
        }
        entry.animMove = move;
        entry.animStartGameTime = now;
        entry.optimisticAnimGameTime = Long.MIN_VALUE;
    }

    /** 收到「下一招式预测」事件（服务端权威）。 */
    public static void onNextMove(LivingEntity entity, int move) {
        get(entity).currentMove = move;
    }

    /** 收到格挡内置冷却事件。 */
    public static void onBlockCooldown(LivingEntity entity, boolean clear) {
        Entry entry = get(entity);
        entry.blockCooldownUntil = clear ? 0 : currentGameTime() + BLOCK_INTERNAL_COOLDOWN_TICKS;
    }

    /** 收到「格挡开始」事件：记录本次是否为无前摇的衔接格挡。 */
    public static void onBlockStart(LivingEntity entity, boolean linked) {
        get(entity).blockLinked = linked;
    }

    /**
     * 本次格挡是否为「衔接格挡」。
     *
     * <p>衔接格挡没有前摇，抬臂进度直接给满（瞬间架刀）；普通格挡则按前摇渐进。
     */
    public static boolean isLinkedBlock(LivingEntity entity) {
        return get(entity).blockLinked;
    }

    // ── 乐观预测（本地玩家攻击瞬间） ──

    /** 本地玩家左键攻击瞬间：以当前预测的招式立即开始播放动画（零延迟）。 */
    public static void startOptimisticAnim(LivingEntity entity) {
        Entry entry = get(entity);
        entry.animMove = entry.currentMove;
        entry.animStartGameTime = currentGameTime();
        entry.optimisticAnimGameTime = entry.animStartGameTime;
    }

    // ── 渲染查询 ──

    /** 当前正在播放的招式（没有新动画时返回上一招式，供第三人称兜底）。 */
    public static int animatingMove(LivingEntity entity) {
        return get(entity).animMove;
    }

    /** 下一招式预测（本地玩家左键攻击前读取）。 */
    public static int currentMove(LivingEntity entity) {
        return get(entity).currentMove;
    }

    /**
     * 当前招式动画进度。
     *
     * @return 0~1 的进度；超过动画时长返回 -1（不在动画中）
     */
    public static float moveProgress(LivingEntity entity) {
        Entry entry = get(entity);
        float elapsed = currentGameTime() - entry.animStartGameTime + currentPartialTick();
        if (elapsed >= MOVE_ANIM_DURATION_TICKS) {
            return -1.0F;
        }
        return Mth.clamp(elapsed / MOVE_ANIM_DURATION_TICKS, 0.0F, 1.0F);
    }

    /** 该实体是否处于格挡内置冷却中。 */
    public static boolean isOnBlockCooldown(LivingEntity entity) {
        return currentGameTime() < get(entity).blockCooldownUntil;
    }

    private static long currentGameTime() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null ? minecraft.level.getGameTime() : 0L;
    }

    private static float currentPartialTick() {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
    }
}
