package org.agmas.noellesroles.game.roles.innocence.diviner;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.SRENetworkMessageUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.content.item.CrystalBallItem;
import org.agmas.noellesroles.game.roles.killer.insane_killer.InsaneKillerPlayerComponent;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 占卜家（Diviner，乘客阵营）组件。
 *
 * <p>开局获得【晶球】，右键对准尸体进入占卜：需静止不动持续 {@value #CHANNEL_TICKS} tick（10 秒）。
 * 完成后得知死者死亡时间 + 随机一项凶手线索；50% 概率晶球破碎；冷却 60 秒。
 *
 * <p>凶手线索三选一：
 * <ol>
 *   <li>凶手存活/死亡 + 凶手名字（远距离显示 ???，靠近后揭示）</li>
 *   <li>凶手具体职业</li>
 *   <li>凶手全局高亮 1 秒</li>
 * </ol>
 *
 * <p>特殊交互：若目标是【亡语杀手】伪装尸体，视为其用刀刺死自己。
 */
public class DivinerPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<DivinerPlayerComponent> KEY = ModComponents.DIVINER;

    // ==================== 常量 ====================
    private static final int CHANNEL_TICKS = 10 * 20;          // 施法 10 秒
    private static final double MOVE_THRESHOLD = 0.15;          // 移动判定阈值
    private static final int KILLER_GLOW_TICKS = 1 * 20;       // 凶手高亮 1 秒
    private static final float BREAK_CHANCE = 0.5f;             // 晶球破碎概率
    private static final double REVEAL_RANGE = 15.0;            // 线索 a：靠近揭示范围
    private static final int REVEAL_CHECK_INTERVAL = 20;        // 线索 a：每 20 tick 检查一次

    // ==================== 状态字段 ====================
    private final Player player;

    /** 已占卜过的尸体 UUID。 */
    private final Set<UUID> divinedCorpses = new HashSet<>();

    /** 是否已发放开局晶球。 */
    private boolean gaveItem = false;

    // --- 施法状态 ---
    private boolean isChanneling = false;
    private int channelTicks = 0;
    private Vec3 channelStartPos;           // 施法起始位置（用于检测移动）
    private UUID channelBodyId;              // 正在占卜的尸体 UUID

    // --- 线索 a：延迟揭示 ---
    /** 待揭示的凶手 UUID（线索类型 a 使用）。 */
    private UUID pendingRevealKiller;
    /** 凶手是否存活（线索类型 a 使用）。 */
    private boolean pendingRevealKillerAlive;
    /** 凶手名字是否已揭示。 */
    private boolean killerRevealed;

    public DivinerPlayerComponent(Player player) {
        this.player = player;
    }

    // ==================== 生命周期 ====================

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) {
        return p == this.player;
    }

    @Override
    public void init() {
        this.divinedCorpses.clear();
        this.gaveItem = false;
        cancelChannel();
        this.pendingRevealKiller = null;
        this.killerRevealed = false;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    // ==================== 施法入口（由 CrystalBallItem 调用） ====================

    /**
     * 开始占卜施法。若已经在施法中或冷却中，给出提示并返回 false。
     */
    public boolean startChannel(ServerPlayer sp, Entity target) {
        NoellesRolesConfig cfg = NoellesRolesConfig.HANDLER.instance();
        // 冷却检查
        if (sp.getCooldowns().isOnCooldown(ModItems.CRYSTAL_BALL)) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.cooldown",
                    (getCooldownSec(sp) + 1)).withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (isChanneling) {
            return false;
        }

        // —— 亡语杀手伪装尸体 ——
        if (target instanceof ServerPlayer tp) {
            SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
            if (gw.isRole(tp, ModRoles.INSANE_KILLER) && InsaneKillerPlayerComponent.KEY.get(tp).isActive) {
                // 揭穿伪装：亡语杀手用刀刺死自己
                GameUtils.killPlayer(tp, true, tp, GameConstants.DeathReasons.KNIFE, true);
                setCooldown(sp, cfg);
                playCompleteFx(sp);
                sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.insane_killer")
                        .withStyle(ChatFormatting.DARK_RED), false);
                SRENetworkMessageUtils.sendTitleTime(sp, 8, 60, 20);
                SRENetworkMessageUtils.sendTitle(sp,
                        Component.translatable("message.noellesroles.diviner.insane_killer.title")
                                .withStyle(ChatFormatting.DARK_RED));
                // 50% 概率碎晶球
                breakCrystalBall(sp);
                return true;
            }
            sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.not_corpse")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }

        // —— 尸体占卜 ——
        if (target instanceof PlayerBodyEntity body) {
            if (org.agmas.noellesroles.content.entity.DoomedSinnerBodyEntity.isDoomedSinnerBody(body)) {
                sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.not_corpse")
                        .withStyle(ChatFormatting.GRAY), true);
                return false;
            }
            UUID bodyId = body.getUUID();
            if (divinedCorpses.contains(bodyId)) {
                sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.already")
                        .withStyle(ChatFormatting.GRAY), true);
                return false;
            }

            // 开始施法
            this.isChanneling = true;
            this.channelTicks = 0;
            this.channelStartPos = sp.position();
            this.channelBodyId = bodyId;

            playChannelStartFx(sp);
            sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.channel_start")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return true;
        }

        sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.no_corpse")
                .withStyle(ChatFormatting.GRAY), true);
        return false;
    }

    // ==================== 施法 Tick 逻辑 ====================

    /** 在 serverTick 中调用：推进施法进度；若完成则执行占卜结果。 */
    private void tickChannel(ServerPlayer sp, NoellesRolesConfig cfg) {
        if (!isChanneling) return;

        // 检查玩家是否移动
        double moved = sp.position().distanceTo(channelStartPos);
        if (moved > MOVE_THRESHOLD) {
            cancelChannel();
            sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.channel_moved")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        // 推进进度
        channelTicks++;
        // 每 2 秒播放一次施法粒子
        if (channelTicks % 40 == 0) {
            playChannelTickFx(sp);
        }

        if (channelTicks >= CHANNEL_TICKS) {
            completeDivination(sp, cfg);
        }
    }

    /** 完成占卜。 */
    private void completeDivination(ServerPlayer sp, NoellesRolesConfig cfg) {
        isChanneling = false;

        // 重新获取尸体——可能在这 10 秒内被清除
        Entity target = findBodyById(sp, channelBodyId);
        if (!(target instanceof PlayerBodyEntity body)) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.corpse_gone")
                    .withStyle(ChatFormatting.RED), true);
            channelBodyId = null;
            return;
        }

        divinedCorpses.add(channelBodyId);
        setCooldown(sp, cfg);
        playCompleteFx(sp);

        UUID killerUuid = body.getKillerUuid();
        int deathSeconds = body.tickCount / 20;
        Component deadName = resolveDeadName(sp, body);

        // 死亡时间
        MutableComponent deathTimeMsg = Component.translatable("message.noellesroles.diviner.death_time",
                deadName, formatTime(deathSeconds));

        // 随机凶手线索（三选一）
        int clueType = sp.level().random.nextInt(3);
        switch (clueType) {
            case 0 -> revealClueA(sp, body, killerUuid, deathTimeMsg);
            case 1 -> revealClueB(sp, body, killerUuid, deathTimeMsg);
            case 2 -> revealClueC(sp, body, killerUuid, deathTimeMsg);
        }

        // 50% 概率晶球破碎
        breakCrystalBall(sp);

        channelBodyId = null;
    }

    // ==================== 三种凶手线索 ====================

    /** 线索 A：凶手存活/死亡 + 凶手名字（远距 ???，靠近揭示）。 */
    private void revealClueA(ServerPlayer sp, PlayerBodyEntity body, UUID killerUuid, MutableComponent deathTimeMsg) {
        sp.displayClientMessage(deathTimeMsg, false);

        boolean killerAlive;
        Component killerName;
        if (killerUuid != null && sp.getServer() != null) {
            ServerPlayer killer = sp.getServer().getPlayerList().getPlayer(killerUuid);
            killerAlive = (killer != null && GameUtils.isPlayerAliveAndSurvival(killer));
        } else {
            killerAlive = false;
        }

        // 存入待揭示状态
        this.pendingRevealKiller = killerUuid != null ? killerUuid : null;
        this.pendingRevealKillerAlive = killerAlive;
        this.killerRevealed = false;

        MutableComponent aliveMsg = killerAlive
                ? Component.translatable("message.noellesroles.diviner.killer_alive").withStyle(ChatFormatting.GREEN)
                : Component.translatable("message.noellesroles.diviner.killer_dead").withStyle(ChatFormatting.GRAY);

        MutableComponent nameMsg = Component.translatable("message.noellesroles.diviner.killer_name_hidden")
                .withStyle(ChatFormatting.GOLD);

        sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.clue_a",
                aliveMsg, nameMsg).withStyle(ChatFormatting.DARK_PURPLE), false);

        SRENetworkMessageUtils.sendTitleTime(sp, 8, 70, 20);
        SRENetworkMessageUtils.sendTitle(sp, Component.translatable("message.noellesroles.diviner.clue_a_title")
                .withStyle(ChatFormatting.DARK_PURPLE));
        SRENetworkMessageUtils.sendSubtitle(sp, aliveMsg);
    }

    /** 线索 B：凶手具体职业。 */
    private void revealClueB(ServerPlayer sp, PlayerBodyEntity body, UUID killerUuid, MutableComponent deathTimeMsg) {
        sp.displayClientMessage(deathTimeMsg, false);

        Component killerRole;
        if (killerUuid != null && sp.getServer() != null) {
            ServerPlayer killer = sp.getServer().getPlayerList().getPlayer(killerUuid);
            if (killer != null) {
                SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
                ResourceLocation roleId = gw.getRole(killer).identifier();
                killerRole = RoleUtils.getRoleName(roleId);
            } else {
                // 凶手已离线：从尸体 NBT 查
                killerRole = Component.translatable("message.noellesroles.diviner.unknown");
            }
        } else {
            // 无凶手（自然死亡等）
            killerRole = Component.translatable("message.noellesroles.diviner.no_killer");
        }

        sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.clue_b",
                killerRole).withStyle(ChatFormatting.RED), false);

        SRENetworkMessageUtils.sendTitleTime(sp, 8, 70, 20);
        SRENetworkMessageUtils.sendTitle(sp, Component.translatable("message.noellesroles.diviner.clue_b_title")
                .withStyle(ChatFormatting.RED));
        SRENetworkMessageUtils.sendSubtitle(sp, killerRole);
    }

    /** 线索 C：凶手全局高亮 1 秒。 */
    private void revealClueC(ServerPlayer sp, PlayerBodyEntity body, UUID killerUuid, MutableComponent deathTimeMsg) {
        sp.displayClientMessage(deathTimeMsg, false);

        if (killerUuid != null && sp.getServer() != null) {
            ServerPlayer killer = sp.getServer().getPlayerList().getPlayer(killerUuid);
            if (killer != null && GameUtils.isPlayerAliveAndSurvival(killer)) {
                killer.addEffect(new MobEffectInstance(MobEffects.GLOWING, KILLER_GLOW_TICKS, 0,
                        false, false, true));
                sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.clue_c")
                        .withStyle(ChatFormatting.GOLD), false);
                SRENetworkMessageUtils.sendTitleTime(sp, 8, 40, 20);
                SRENetworkMessageUtils.sendTitle(sp, Component.translatable("message.noellesroles.diviner.clue_c_title")
                        .withStyle(ChatFormatting.GOLD));
            } else {
                sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.clue_c_gone")
                        .withStyle(ChatFormatting.GRAY), false);
                SRENetworkMessageUtils.sendTitleTime(sp, 8, 40, 20);
                SRENetworkMessageUtils.sendTitle(sp, Component.translatable("message.noellesroles.diviner.clue_c_title")
                        .withStyle(ChatFormatting.GOLD));
                SRENetworkMessageUtils.sendSubtitle(sp,
                        Component.translatable("message.noellesroles.diviner.clue_c_gone")
                                .withStyle(ChatFormatting.GRAY));
            }
        } else {
            sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.clue_c_gone")
                    .withStyle(ChatFormatting.GRAY), false);
            SRENetworkMessageUtils.sendTitleTime(sp, 8, 40, 20);
            SRENetworkMessageUtils.sendTitle(sp, Component.translatable("message.noellesroles.diviner.clue_c_title")
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    // ==================== 延迟揭示（线索 a）Tick ====================

    private int revealCheckTimer = 0;

    private void tickReveal(ServerPlayer sp) {
        if (pendingRevealKiller == null || killerRevealed) return;

        revealCheckTimer++;
        if (revealCheckTimer < REVEAL_CHECK_INTERVAL) return;
        revealCheckTimer = 0;

        if (sp.getServer() == null) return;
        ServerPlayer killer = sp.getServer().getPlayerList().getPlayer(pendingRevealKiller);
        if (killer == null || !GameUtils.isPlayerAliveAndSurvival(killer)) {
            // 凶手已死/离线：直接揭示
            this.killerRevealed = true;
            sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.killer_revealed_dead")
                    .withStyle(ChatFormatting.GRAY), false);
            return;
        }

        double dist = sp.position().distanceTo(killer.position());
        if (dist <= REVEAL_RANGE) {
            this.killerRevealed = true;
            Component killerDisplayName = killer.getDisplayName();
            sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.killer_revealed",
                    killerDisplayName).withStyle(ChatFormatting.GOLD), false);
            SRENetworkMessageUtils.sendTitleTime(sp, 8, 40, 20);
            SRENetworkMessageUtils.sendTitle(sp, killerDisplayName);
        }
    }

    // ==================== serverTick ====================

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) return;

        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
        if (!gw.isRunning() || !gw.isRole(sp, ModRoles.DIVINER)) return;

        // 开局发放晶球
        if (!gaveItem && GameUtils.isPlayerAliveAndSurvival(sp)) {
            sp.addItem(ModItems.CRYSTAL_BALL.getDefaultInstance().copy());
            gaveItem = true;
            NoellesRolesConfig cfg = NoellesRolesConfig.HANDLER.instance();
            sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.intro",
                    cfg.divinerCooldown, cfg.divinerCrystalBallPrice), false);
        }

        // 施法进度
        tickChannel(sp, NoellesRolesConfig.HANDLER.instance());

        // 线索 a 延迟揭示
        tickReveal(sp);
    }

    // ==================== 辅助方法 ====================

    /** 中断施法。 */
    private void cancelChannel() {
        this.isChanneling = false;
        this.channelTicks = 0;
        this.channelStartPos = null;
        this.channelBodyId = null;
    }

    /** 根据 UUID 查找尸体。 */
    private Entity findBodyById(ServerPlayer sp, UUID bodyId) {
        if (bodyId != null && sp.level() instanceof ServerLevel sl) {
            return sl.getEntity(bodyId);
        }
        return null;
    }

    /** 50% 概率消耗晶球。 */
    private void breakCrystalBall(ServerPlayer sp) {
        if (sp.level().random.nextFloat() < BREAK_CHANCE) {
            ItemStack held = sp.getMainHandItem();
            if (held.is(ModItems.CRYSTAL_BALL)) {
                held.shrink(1);
                sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.break")
                        .withStyle(ChatFormatting.GRAY), true);
            }
        }
    }

    /** 设置冷却。 */
    private void setCooldown(ServerPlayer sp, NoellesRolesConfig cfg) {
        sp.getCooldowns().addCooldown(ModItems.CRYSTAL_BALL, GameConstants.getInTicks(0, cfg.divinerCooldown));
    }

    /** 获取冷却剩余秒数。 */
    private int getCooldownSec(ServerPlayer sp) {
        ItemCooldowns cooldowns = sp.getCooldowns();
        ItemCooldowns.CooldownInstance cd = cooldowns.cooldowns.get(ModItems.CRYSTAL_BALL);
        if (cd == null) return 0;
        return Math.max(0, (cd.endTime - cooldowns.tickCount + 19) / 20);
    }

    /** 解析死者名称。 */
    private Component resolveDeadName(ServerPlayer sp, PlayerBodyEntity body) {
        UUID id = body.getPlayerUuid();
        if (id != null && sp.getServer() != null) {
            ServerPlayer dead = sp.getServer().getPlayerList().getPlayer(id);
            if (dead != null) return dead.getDisplayName();
        }
        if (body.getCustomName() != null) return body.getCustomName();
        return Component.translatable("message.noellesroles.diviner.unknown");
    }

    /** 格式化秒数为 "X分Y秒"。 */
    private String formatTime(int totalSeconds) {
        int mins = totalSeconds / 60;
        int secs = totalSeconds % 60;
        if (mins > 0) {
            return mins + "分" + secs + "秒";
        }
        return secs + "秒";
    }

    // ==================== 特效 ====================

    private void playChannelStartFx(ServerPlayer sp) {
        if (sp.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, sp.getX(), sp.getY() + 1.5, sp.getZ(),
                    8, 0.3, 0.3, 0.3, 0.02);
            sl.playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.PLAYERS, 0.8f, 0.9f);
        }
    }

    private void playChannelTickFx(ServerPlayer sp) {
        if (sp.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.ENCHANT, sp.getX(), sp.getY() + 1.5, sp.getZ(),
                    12, 0.3, 0.6, 0.3, 0.1);
        }
    }

    private void playCompleteFx(ServerPlayer sp) {
        if (sp.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.ENCHANT, sp.getX(), sp.getY() + 1.2, sp.getZ(),
                    24, 0.4, 0.6, 0.4, 0.2);
            sl.playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 1.0f, 1.4f);
        }
    }

    // ==================== NBT 持久化 ====================

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (pendingRevealKiller != null) {
            tag.putUUID("pendingRevealKiller", pendingRevealKiller);
            tag.putBoolean("pendingRevealKillerAlive", pendingRevealKillerAlive);
            tag.putBoolean("killerRevealed", killerRevealed);
        }
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (tag.hasUUID("pendingRevealKiller")) {
            pendingRevealKiller = tag.getUUID("pendingRevealKiller");
            pendingRevealKillerAlive = tag.getBoolean("pendingRevealKillerAlive");
            killerRevealed = tag.getBoolean("killerRevealed");
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
