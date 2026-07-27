package org.agmas.noellesroles.game.roles.killer.delayer;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * 滞时鬼（Delayer）玩家组件
 * - 击杀玩家时为游戏增加20秒（被动）
 * - 当存在滞时鬼且游戏时间为1分25秒（85秒）时，增加30秒（仅触发一次，被动）
 * - 主动技能【时间锚点】：按技能键消耗 75 金币锚定当前状态，锚点持续 15 秒后自动回溯（仅自己）：
 *   回溯位置、金钱、药水效果；<b>不回溯物品栏与物品冷却</b>；若已死亡则不会复活。
 *   回溯不是瞬移，而是<b>沿原路平滑返回</b>：锚定期间每 2 秒记录一次位置，回溯时倒序平滑传送回锚点。
 *   回溯期间回溯者本人屏幕呈现<b>时停同款灰白滤镜</b>（{@link ModEffects#TIME_STOP_FILTER}），
 *   其它人短暂恍惚（{@link ModEffects#TIME_REWIND_DAZE} + 反胃）并播放玻璃破碎声。冷却 120 秒。
 * - 若锚点仍在（15 秒内）时被击中（受到伤害），则<b>强制立即回溯</b>，且回溯后进入“趴下”且
 *   无法移动 30 秒的虚弱状态。本游戏中刀/枪等攻击走击杀管线而非扣血，因此“被击中”通过
 *   {@link AllowPlayerDeathWithKiller} 在护盾判定前拦截：锚点被击碎、取消这次死亡并强制回溯；
 *   普通掉血（摔落、中毒等）仍由血量轮询兜底检测。
 */
public class DelayerPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final org.ladysnake.cca.api.v3.component.ComponentKey<DelayerPlayerComponent> KEY = ModComponents.DELAYER;

    private final Player player;

    // world-level 一次性触发标志（每轮仅触发一次）
    public static volatile boolean timeBoostTriggered = false;

    // ===== 时间锚点 / 回溯 状态 =====
    // 锚定中（自由活动，等待自动回溯或被击中触发回溯）
    private boolean anchored = false;
    private int rewindTicksLeft = 0;
    private double anchorX, anchorY, anchorZ;
    private int anchorBalance;
    private final List<MobEffectInstance> effectSnapshot = new ArrayList<>();

    // 路径记录（锚定期间每 delayerPathSampleSeconds 秒记录一次当前位置；第 0 个为锚点）
    private final List<Vec3> pathPoints = new ArrayList<>();
    private int sampleTicks = 0;
    private float prevHealth = -1f;

    // 平滑回溯动画状态
    private boolean rewinding = false;
    private List<Vec3> returnPath = null;
    private int returnIndex = 0;
    private int segTick = 0;
    private boolean penaltyPending = false; // 本次回溯是否因被击中而触发（触发则回溯后趴下）

    // 趴下 + 无法移动 惩罚状态
    private int downedTicksLeft = 0;

    public DelayerPlayerComponent(Player player) {
        this.player = player;
    }

    public static void registerEvents() {
        // 击杀触发：当被击杀的玩家死亡且有击杀者时，为游戏增加20秒
        AfterShieldAllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (victim == null || killer == null) return true;
            var world = victim.level();
            if (world == null || world.isClientSide()) return true;
            var gameWorld = SREGameWorldComponent.KEY.get(world);
            if (gameWorld.isRole(killer, ModRoles.DELAYER)) {
                SREGameTimeComponent.KEY.get(world).addTime(20 * 20);
            }
            return true;
        });

        // 锚定期间被击中（刀/枪等致命攻击走击杀管线，不扣血）：
        // 在护盾判定前拦截这次死亡——锚点被击碎，强制沿原路回溯并趴下。
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (victim == null || killer == null || victim == killer) return true;
            if (!(victim instanceof ServerPlayer sp)) return true;
            var world = victim.level();
            if (world == null || world.isClientSide()) return true;
            var gameWorld = SREGameWorldComponent.KEY.get(world);
            if (!gameWorld.isRunning() || !gameWorld.isRole(victim, ModRoles.DELAYER)) return true;
            DelayerPlayerComponent comp = KEY.get(victim);
            if (comp == null || !comp.isAnchored()) return true;
            if (!GameUtils.isPlayerAliveAndSurvival(sp)) return true;
            comp.forceRewindByHit(sp);
            return false; // 锚点抵消了这次击杀
        });
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    public boolean isAnchored() {
        return anchored;
    }

    /** 锚定期间被击中：锚点被击碎，强制立即沿原路回溯，回溯后进入趴下惩罚。 */
    public void forceRewindByHit(ServerPlayer sp) {
        if (!anchored || rewinding) return;
        penaltyPending = true;
        beginReturn(sp);
    }

    /** 锚定当前状态并安排持续 {@code delayerRewindDelaySeconds} 秒后自动回溯。冷却/扣费由 {@link org.agmas.noellesroles.AbilityHandler} 处理。 */
    public void anchor() {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        NoellesRolesConfig cfg = NoellesRolesConfig.HANDLER.instance();
        this.anchorX = sp.getX();
        this.anchorY = sp.getY();
        this.anchorZ = sp.getZ();
        this.anchorBalance = SREPlayerShopComponent.KEY.get(sp).balance;
        this.effectSnapshot.clear();
        for (MobEffectInstance e : sp.getActiveEffects()) {
            this.effectSnapshot.add(new MobEffectInstance(e));
        }
        // 重置回溯/路径状态
        this.rewinding = false;
        this.returnPath = null;
        this.returnIndex = 0;
        this.segTick = 0;
        this.penaltyPending = false;
        this.downedTicksLeft = 0;
        this.pathPoints.clear();
        this.pathPoints.add(new Vec3(anchorX, anchorY, anchorZ)); // 第 0 个路径点 = 锚点
        this.sampleTicks = 0;
        this.prevHealth = sp.getHealth();

        this.rewindTicksLeft = GameConstants.getInTicks(0, cfg.delayerRewindDelaySeconds);
        this.anchored = true;

        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 1.0f, 1.5f);
        if (sp.level() instanceof ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                    sp.getX(), sp.getY() + 1.0, sp.getZ(), 30, 0.4, 0.8, 0.4, 0.2);
        }
        sp.displayClientMessage(Component.translatable("message.noellesroles.delayer.anchored",
                cfg.delayerRewindDelaySeconds).withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    /** 锚定期间每 tick：检测被击中、采样路径、倒计时。 */
    private void tickAnchored(ServerPlayer sp) {
        NoellesRolesConfig cfg = NoellesRolesConfig.HANDLER.instance();

        // 普通掉血（摔落、中毒等）兜底：受到伤害即强制回溯并附带趴下惩罚
        float hp = sp.getHealth();
        if (prevHealth >= 0f && hp < prevHealth - 0.001f) {
            prevHealth = hp;
            forceRewindByHit(sp);
            return;
        }
        prevHealth = hp;

        // 路径采样
        int sampleInterval = GameConstants.getInTicks(0, cfg.delayerPathSampleSeconds);
        if (sampleInterval < 1) sampleInterval = 1;
        sampleTicks++;
        if (sampleTicks >= sampleInterval) {
            sampleTicks = 0;
            addPathSample(sp.position(), cfg, sampleInterval);
        }

        // 自动回溯倒计时
        if (rewindTicksLeft > 0) {
            rewindTicksLeft--;
            if (rewindTicksLeft % 20 == 0) {
                sp.displayClientMessage(Component.translatable("message.noellesroles.delayer.rewind_countdown",
                        rewindTicksLeft / 20).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
        }
        if (rewindTicksLeft <= 0) {
            beginReturn(sp); // 正常回溯，无惩罚
        }
    }

    private void addPathSample(Vec3 pos, NoellesRolesConfig cfg, int sampleInterval) {
        pathPoints.add(pos);
        // 保留最近 delayerPathRecordSeconds 秒的路径；始终保留第 0 个锚点
        int maxSamples = Math.max(2, GameConstants.getInTicks(0, cfg.delayerPathRecordSeconds) / sampleInterval + 1);
        while (pathPoints.size() > maxSamples) {
            pathPoints.remove(1); // 移除锚点之后最早的一个，保住锚点
        }
    }

    /** 开始平滑回溯：构建倒序返回路径，施加限制与视觉滤镜。 */
    private void beginReturn(ServerPlayer sp) {
        NoellesRolesConfig cfg = NoellesRolesConfig.HANDLER.instance();
        anchored = false;
        rewinding = true;
        rewindTicksLeft = 0;

        // 返回路径：当前位置 → 倒序经过各采样点 → 锚点
        returnPath = new ArrayList<>();
        returnPath.add(sp.position());
        for (int i = pathPoints.size() - 1; i >= 0; i--) {
            returnPath.add(pathPoints.get(i));
        }
        returnIndex = 0;
        segTick = 0;

        int segTicks = Math.max(1, cfg.delayerReturnSegmentTicks);
        int segments = Math.max(1, returnPath.size() - 1);
        // 回溯者本人套上时停同款灰白滤镜，覆盖整个返回动画（+ 少量余量用于淡出）
        int filterTicks = segments * segTicks + 10;
        sp.addEffect(new MobEffectInstance(ModEffects.TIME_STOP_FILTER, filterTicks, 0, false, false, false));

        // 其它人短暂恍惚（时空滤镜 + 反胃）并播放玻璃破碎声
        int daze = GameConstants.getInTicks(0, cfg.delayerDazeSeconds);
        if (sp.level() instanceof ServerLevel level) {
            for (ServerPlayer p : level.players()) {
                if (p == sp) continue;
                p.addEffect(new MobEffectInstance(ModEffects.TIME_REWIND_DAZE, daze, 0, false, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, daze, 0, false, true, true));
                p.playNotifySound(SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2f, 0.8f);
            }
        }
        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.GLASS_BREAK,
                SoundSource.PLAYERS, 1.2f, 0.8f);

        sp.displayClientMessage(Component.translatable(penaltyPending
                        ? "message.noellesroles.delayer.forced_return"
                        : "message.noellesroles.delayer.returning")
                .withStyle(ChatFormatting.AQUA), true);
    }

    /** 平滑回溯动画每 tick：限制移动、沿路径插值传送。 */
    private void tickReturn(ServerPlayer sp) {
        // 回溯期间锁定移动与视角，避免玩家与强制传送抗衡
        applyRestraints(sp, false);

        if (returnPath == null || returnIndex >= returnPath.size() - 1) {
            finishReturn(sp);
            return;
        }

        int segTicks = Math.max(1, NoellesRolesConfig.HANDLER.instance().delayerReturnSegmentTicks);
        Vec3 a = returnPath.get(returnIndex);
        Vec3 b = returnPath.get(returnIndex + 1);
        segTick++;
        double frac = Math.min(1.0, (double) segTick / segTicks);
        double x = a.x + (b.x - a.x) * frac;
        double y = a.y + (b.y - a.y) * frac;
        double z = a.z + (b.z - a.z) * frac;
        sp.setDeltaMovement(Vec3.ZERO);
        sp.teleportTo(x, y, z);
        sp.fallDistance = 0f;

        if (segTick >= segTicks) {
            returnIndex++;
            segTick = 0;
        }
    }

    /** 抵达锚点，完成回溯：回溯金钱与药水效果（不回溯物品栏），必要时进入趴下惩罚。 */
    private void finishReturn(ServerPlayer sp) {
        rewinding = false;
        returnPath = null;
        returnIndex = 0;
        segTick = 0;
        pathPoints.clear();

        // 精确落回锚点
        sp.setDeltaMovement(Vec3.ZERO);
        sp.teleportTo(anchorX, anchorY, anchorZ);

        // 回溯金钱
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(sp);
        shop.balance = anchorBalance;
        shop.sync();

        // 回溯药水效果（不回溯物品栏 / 物品冷却）；同时清掉回溯期间的限制/滤镜
        sp.removeAllEffects();
        for (MobEffectInstance e : effectSnapshot) {
            sp.addEffect(new MobEffectInstance(e));
        }
        effectSnapshot.clear();

        if (penaltyPending) {
            penaltyPending = false;
            startDowned(sp);
        } else {
            sp.displayClientMessage(Component.translatable("message.noellesroles.delayer.rewound")
                    .withStyle(ChatFormatting.AQUA), true);
        }
    }

    /** 进入趴下 + 无法移动状态。 */
    private void startDowned(ServerPlayer sp) {
        NoellesRolesConfig cfg = NoellesRolesConfig.HANDLER.instance();
        downedTicksLeft = GameConstants.getInTicks(0, cfg.delayerDownedSeconds);
        sp.setSwimming(true);
        applyRestraints(sp, true);
        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 1.0f, 0.6f);
        sp.displayClientMessage(Component.translatable("message.noellesroles.delayer.downed",
                cfg.delayerDownedSeconds).withStyle(ChatFormatting.RED), true);
    }

    /** 趴下状态每 tick：持续保持趴下姿态与移动限制。 */
    private void tickDowned(ServerPlayer sp) {
        sp.setSwimming(true);
        applyRestraints(sp, true);
        sp.setDeltaMovement(new Vec3(0.0, sp.getDeltaMovement().y, 0.0));
        downedTicksLeft--;
        if (downedTicksLeft % 20 == 0) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.delayer.downed",
                    Math.max(0, downedTicksLeft / 20)).withStyle(ChatFormatting.RED), true);
        }
        if (downedTicksLeft <= 0) {
            downedTicksLeft = 0;
            sp.setSwimming(false);
            sp.displayClientMessage(Component.translatable("message.noellesroles.delayer.downed_end")
                    .withStyle(ChatFormatting.GREEN), true);
        }
    }

    /**
     * 施加移动限制。
     * @param prone true = 趴下惩罚（禁止移动/使用 + 强力缓慢，允许转头）；false = 回溯动画（禁止移动/转头）。
     */
    private void applyRestraints(ServerPlayer sp, boolean prone) {
        sp.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, 10, 0, false, false, false));
        if (prone) {
            sp.addEffect(new MobEffectInstance(ModEffects.USED_BANED, 10, 0, false, false, false));
            sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, 255, false, false, false));
        } else {
            sp.addEffect(new MobEffectInstance(ModEffects.TURN_BANED, 10, 0, false, false, false));
        }
    }

    /** 死亡/中止时清理所有主动状态（不回溯任何东西）。 */
    private void abortActive(ServerPlayer sp) {
        anchored = false;
        rewinding = false;
        returnPath = null;
        returnIndex = 0;
        segTick = 0;
        rewindTicksLeft = 0;
        penaltyPending = false;
        downedTicksLeft = 0;
        pathPoints.clear();
        effectSnapshot.clear();
        sp.setSwimming(false);
    }

    @Override
    public void init() {
        anchored = false;
        rewinding = false;
        returnPath = null;
        returnIndex = 0;
        segTick = 0;
        rewindTicksLeft = 0;
        penaltyPending = false;
        downedTicksLeft = 0;
        sampleTicks = 0;
        prevHealth = -1f;
        pathPoints.clear();
        effectSnapshot.clear();
        if (player instanceof ServerPlayer sp) {
            sp.setSwimming(false);
        }
    }

    @Override
    public void clear() {
        init();
    }

    @Override
    public void clientTick() {
        // client-side nothing
    }

    @Override
    public void serverTick() {
        var world = player.level();
        if (world.isClientSide()) return;
        var gameWorld = SREGameWorldComponent.KEY.get(world);
        if (!gameWorld.isRunning()) return;
        if (!gameWorld.isRole(player, ModRoles.DELAYER)) return;

        // 主动技能状态机（仅存活生存态有意义）
        if (player instanceof ServerPlayer sp) {
            if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
                if (anchored || rewinding || downedTicksLeft > 0) {
                    abortActive(sp);
                }
            } else if (downedTicksLeft > 0) {
                tickDowned(sp);
            } else if (rewinding) {
                tickReturn(sp);
            } else if (anchored) {
                tickAnchored(sp);
            }
        }

        // 仅在角色存在且尚未触发时检查全局计时器
        if (!timeBoostTriggered) {
            var timeComp = SREGameTimeComponent.KEY.get(world);
            int timeLeft = timeComp.getTime();
            // 85 秒 = 85 * 20 ticks
            if (timeLeft == 85 * 20) {
                timeComp.addTime(30 * 20);
                timeBoostTriggered = true;
            }
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
