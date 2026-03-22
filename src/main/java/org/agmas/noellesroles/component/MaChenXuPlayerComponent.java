package org.agmas.noellesroles.component;

import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.init.ModEffects;

/**
 * 布袋鬼·诡舍·缚灵 组件
 *
 * 管理布袋鬼的四段成长机制：
 * - 阶段1（初现鬼影）：基础恐惧12格，-2SAN/3s
 * - 阶段2（索命鬼魅）：恐惧15格，-3SAN/3s，+10%移速，1鬼术
 * - 阶段3（厉鬼领域）：恐惧18格，-5SAN/3s，解锁大招，+1鬼术
 * - 阶段4（恐怖化身）：恐惧25格，-7SAN/3s，+1鬼术，永久护盾
 */
public class MaChenXuPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    /** 组件键 */
    public static final ComponentKey<MaChenXuPlayerComponent> KEY = ModComponents.MA_CHEN_XU;

    // ==================== 常量定义 ====================

    /** 恐惧范围（格） */
    public static final double FEAR_RANGE_STAGE_1 = 30.0;
    public static final double FEAR_RANGE_STAGE_2 = 40.0;
    public static final double FEAR_RANGE_STAGE_3 = 50.0;
    public static final double FEAR_RANGE_STAGE_4 = 50.0;

    /** 恐惧SAN掉落间隔（tick） - 5秒 */
    public static final int FEAR_INTERVAL = 100;

    /** 恐惧SAN掉落量 */
    public static final int FEAR_SAN_LOSS_STAGE_1 = 2;
    public static final int FEAR_SAN_LOSS_STAGE_2 = 3;
    public static final int FEAR_SAN_LOSS_STAGE_3 = 5;
    public static final int FEAR_SAN_LOSS_STAGE_4 = 7;

    /** 低SAN增强阈值（mood < 0.3 即 SAN < 30） */
    public static final float LOW_SAN_THRESHOLD = 0.3f;

    /** 低SAN增强范围加成 */
    public static final double LOW_SAN_RANGE_BONUS = 4.0;

    /** 进化阈值（根据人数而改变） */
    public int STAGE_2_THRESHOLD = 100;
    public int STAGE_3_THRESHOLD = 250;
    public int STAGE_4_THRESHOLD = 500;
    public int STAGE_4_AUTO_ULT_THRESHOLD = 800;

    /** 里世界SAN掉落间隔（tick） - 2秒 */
    public static final int OTHERWORLD_INTERVAL = 40;

    /** 里世界SAN掉落量 */
    public static final int OTHERWORLD_SAN_LOSS = 2;

    /** 大招费用 */
    public static final int ULTIMATE_COST = 200;

    /** 大招持续时间（tick） */
    public static final int ULTIMATE_DURATION_STAGE_3 = 600; // 30秒
    public static final int ULTIMATE_DURATION_STAGE_4 = 900; // 45秒

    /** 初始金币 */
    public static final int INITIAL_GOLD = 50;

    /** 鬼术冷却时间（tick） */
    public static final int GHOST_SKILL_COOLDOWN_GHOST_WALL = 600; // 30秒
    public static final int GHOST_SKILL_COOLDOWN_ECHO = 900; // 45秒
    public static final int GHOST_SKILL_COOLDOWN_TRAP = 400; // 20秒
    public static final int GHOST_SKILL_COOLDOWN_PARASITE = 1800; // 90秒
    public static final int GHOST_SKILL_COOLDOWN_VANISH = 900; // 45秒

    /** 隐匿参数 */
    public static final int VANISH_DURATION = 160; // 8秒
    public static final int VANISH_DURATION_OTHERWORLD = 240; // 12秒

    /** 鬼打墙参数 */
    public static final int GHOST_WALL_DURATION = 100; // 5秒
    public static final int GHOST_WALL_DURATION_OTHERWORLD = 160; // 8秒
    public static final int GHOST_WALL_SAN_LOSS = 15;
    public static final int GHOST_WALL_SAN_LOSS_OTHERWORLD = 25; // 15 + 10
    public static final double GHOST_WALL_KNOCKBACK = 5.0;

    /** 回响录制时间（tick） */
    public static final int ECHO_RECORD_DURATION = 100; // 5秒

    /** 诱捕参数 */
    public static final int TRAP_ROOT_DURATION = 60; // 3秒
    public static final int TRAP_ROOT_DURATION_OTHERWORLD = 100; // 5秒
    public static final int TRAP_SAN_LOSS = 25;
    public static final int TRAP_SAN_LOSS_OTHERWORLD = 40;
    public static final double TRAP_TRIGGER_RANGE = 1.5;
    public static final int MAX_TRAPS = 2;

    /** 寄生参数 */
    public static final int PARASITE_DEATH_TICKS = 1200; // 60秒
    public static final int PARASITE_DEATH_TICKS_OTHERWORLD = 600; // 30秒

    /** 寄生静止判断阈值（水平速度，容忍网络延迟和重力波动） */
    public static final double PARASITE_STATIONARY_THRESHOLD = 0.05;

    /** 浊雨参数 */
    public static final int TURBID_RAIN_DURATION = 600; // 30秒
    public static final int TURBID_RAIN_SAN_INTERVAL = 100; // 5秒
    public static final int TURBID_RAIN_SAN_LOSS = 3;

    /** 镇魂铃参数 */
    public static final double SOUL_BELL_RANGE = 20.0;
    public static final int SOUL_BELL_DURATION = 200; // 10秒

    /** 掠风参数（阶段4里世界专属） */
    public static final int SWIFT_WIND_DURATION = 200; // 10秒

    /** 里世界全体发光间隔（tick） - 15秒 */
    public static final int OTHERWORLD_GLOW_INTERVAL = 300;

    /** 里世界全体发光持续时间（tick） - 5秒 */
    public static final int OTHERWORLD_GLOW_DURATION = 100;

    /** 里世界玩家接近警告距离（格） */
    public static final double OTHERWORLD_WARN_RANGE = 20.0;

    /** 里世界黑雾粒子间隔（tick） */
    public static final int BLACK_FOG_PARTICLE_INTERVAL = 5;

    // ==================== 状态变量 ====================

    private final Player player;

    /** 当前阶段（1-4） */
    public int stage = 1;

    /** 累计造成的SAN掉落 */
    public int totalSanLoss = 0;

    /** 恐惧计时器 */
    public int fearTimer = 0;

    /** 里世界是否激活 */
    public boolean otherworldActive = false;

    /** 里世界计时器 */
    public int otherworldTimer = 0;

    /** 里世界剩余时间 */
    public int otherworldDuration = 0;

    /** 大招冷却 */
    public int ultimateCooldown = 0;

    /** 阶段4是否已用免费大招 */
    public boolean stage4FreeUltUsed = false;

    /** 里世界中被标记的玩家 */
    public List<UUID> markedPlayers = new ArrayList<>();

    /** 永久护盾（阶段4，吸收一次致命伤害后消耗） */
    public boolean permanentShield = false;

    /** 永久移速加成（百分比，最大30） */
    public int permanentSpeedBonus = 0;

    /** 已获得的鬼术列表 */
    public List<String> ghostSkills = new ArrayList<>();

    /** 当前选中技能索引 */
    public int nowSelectedSkill = 0;

    /** 鬼术冷却 */
    public int ghostWallCooldown = 0;
    public int echoCooldown = 0;
    public int trapCooldown = 0;
    public int parasiteCooldown = 0;
    public int vanishCooldown = 0;

    /** 鬼打墙状态 */
    public boolean ghostWallActive = false;
    public int ghostWallRemainingTicks = 0;
    public Vec3 ghostWallPos = null;
    public Vec3 ghostWallDirection = null;
    /** 已被鬼打墙命中的玩家（每次施放只触发一次） */
    private final java.util.Set<UUID> ghostWallHitPlayers = new java.util.HashSet<>();

    /** 回响状态 */
    public boolean echoRecording = false;
    public int echoRecordTicks = 0;
    public Vec3 echoRecordPos = null;
    public boolean echoCanTeleport = false;

    /** 诱捕陷阱位置 */
    public List<Vec3> trapPositions = new ArrayList<>();

    /** 寄生目标UUID（用于透视） */
    public UUID parasiteTargetUUID = null;

    /** 浊雨状态 */
    public boolean turbidRainActive = false;
    public int turbidRainDuration = 0;
    public int turbidRainTimer = 0;

    /** 掠风技能状态（阶段4里世界专属） */
    public boolean swiftWindActive = false;
    public int swiftWindDuration = 0;

    /** 里世界发光计时器 */
    public int otherworldGlowTimer = 0;

    /** 里世界黑雾粒子计时器 */
    public int blackFogTimer = 0;

    private final Random random = new Random();

    /**
     * 构造函数
     */
    public MaChenXuPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    /**
     * 初始化组件状态
     */
    @Override
    public void init() {
        this.nowSelectedSkill = 0;
        this.stage = 1;
        this.totalSanLoss = 0;
        this.fearTimer = 0;
        this.ghostWallHitPlayers.clear();
        this.otherworldActive = false;
        this.otherworldTimer = 0;
        this.otherworldDuration = 0;
        this.ultimateCooldown = 0;
        this.stage4FreeUltUsed = false;
        this.markedPlayers.clear();
        this.permanentShield = false;
        this.permanentSpeedBonus = 0;
        this.ghostSkills.clear();
        this.ghostWallCooldown = 0;
        this.echoCooldown = 0;
        this.trapCooldown = 0;
        this.parasiteCooldown = 0;
        this.vanishCooldown = 0;
        this.ghostWallActive = false;
        this.ghostWallRemainingTicks = 0;
        this.ghostWallPos = null;
        this.ghostWallDirection = null;
        this.echoRecording = false;
        this.echoRecordTicks = 0;
        this.echoRecordPos = null;
        this.echoCanTeleport = false;
        this.trapPositions.clear();
        this.parasiteTargetUUID = null;
        this.turbidRainActive = false;
        this.turbidRainDuration = 0;
        this.turbidRainTimer = 0;
        this.swiftWindActive = false;
        this.swiftWindDuration = 0;
        this.otherworldGlowTimer = 0;
        this.blackFogTimer = 0;
        // 给予初始金币
        if (player instanceof ServerPlayer serverPlayer) {
            SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(serverPlayer);
            shopComponent.setBalance(INITIAL_GOLD);
            shopComponent.sync();
            updateStageNeeds(serverPlayer.level().players().size());
        }
        this.sync();
    }

    public void updateStageNeeds(int playerCount) {
        if (playerCount <= 12) {
            STAGE_2_THRESHOLD = 100;
            STAGE_3_THRESHOLD = 400;
            STAGE_4_THRESHOLD = 800;
            STAGE_4_AUTO_ULT_THRESHOLD = 1500;
        } else if (playerCount <= 24) {
            STAGE_2_THRESHOLD = 150;
            STAGE_3_THRESHOLD = 500;
            STAGE_4_THRESHOLD = 1000;
            STAGE_4_AUTO_ULT_THRESHOLD = 2000;
        } else {
            STAGE_2_THRESHOLD = 200;
            STAGE_3_THRESHOLD = 800;
            STAGE_4_THRESHOLD = 1200;
            STAGE_4_AUTO_ULT_THRESHOLD = 2000;
        }
    }

    @Override
    public void clear() {
        clearAll();
    }

    /**
     * 完全清除组件状态
     */
    public void clearAll() {
        this.stage = 0;
        this.totalSanLoss = 0;
        this.fearTimer = 0;
        this.ghostWallHitPlayers.clear();
        this.otherworldActive = false;
        this.otherworldTimer = 0;
        this.otherworldDuration = 0;
        this.ultimateCooldown = 0;
        this.stage4FreeUltUsed = false;
        this.markedPlayers.clear();
        this.permanentShield = false;
        this.permanentSpeedBonus = 0;
        this.ghostSkills.clear();
        this.ghostWallCooldown = 0;
        this.echoCooldown = 0;
        this.trapCooldown = 0;
        this.parasiteCooldown = 0;
        this.vanishCooldown = 0;
        this.ghostWallActive = false;
        this.ghostWallRemainingTicks = 0;
        this.ghostWallPos = null;
        this.ghostWallDirection = null;
        this.echoRecording = false;
        this.echoRecordTicks = 0;
        this.echoRecordPos = null;
        this.echoCanTeleport = false;
        this.trapPositions.clear();
        this.parasiteTargetUUID = null;
        this.turbidRainActive = false;
        this.turbidRainDuration = 0;
        this.turbidRainTimer = 0;
        this.swiftWindActive = false;
        this.swiftWindDuration = 0;
        this.otherworldGlowTimer = 0;
        this.blackFogTimer = 0;
        // 取消无敌状态
        if (player instanceof ServerPlayer sp) {
            sp.setInvulnerable(false);
        }
        this.sync();
    }

    /**
     * 检查是否是活跃的布袋鬼
     */
    public boolean isActiveMaChenXu() {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorldComponent == null)
            return false;
        if (!gameWorldComponent.isRole(player, ModRoles.MA_CHEN_XU))
            return false;
        return stage > 0;
    }

    /**
     * 检查玩家是否是杀手
     */
    private boolean isKiller(Player target) {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(target.level());
        return gameWorldComponent.isKillerTeam(target);
    }

    // ==================== 阶段进阶 ====================

    /**
     * 增加SAN掉落并检查阶段进阶
     */
    public void addSanLoss(int amount) {
        this.totalSanLoss += amount;
        checkStageAdvance();
        this.sync();
    }

    /**
     * 检查阶段4自动触发大招（放在serverTick末尾，确保其他tick逻辑优先处理）
     */
    private void checkAutoUltimate() {
        if (stage == 4 && !stage4FreeUltUsed && totalSanLoss >= STAGE_4_AUTO_ULT_THRESHOLD && !otherworldActive) {
            stage4FreeUltUsed = true;
            activateOtherworld(ULTIMATE_DURATION_STAGE_4);
            if (player instanceof ServerPlayer sp) {
                sp.displayClientMessage(
                        Component.translatable("message.noellesroles.ma_chen_xu.auto_ultimate")
                                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                        true);
            }
        }
    }

    /**
     * 检查阶段进阶
     */
    public void checkStageAdvance() {
        int oldStage = stage;
        if (stage == 1 && totalSanLoss >= STAGE_2_THRESHOLD) {
            advanceToStage2();
        } else if (stage == 2 && totalSanLoss >= STAGE_3_THRESHOLD) {
            advanceToStage3();
        } else if (stage == 3 && totalSanLoss >= STAGE_4_THRESHOLD) {
            advanceToStage4();
        }
        if (stage != oldStage) {
            this.sync();
        }
    }

    /**
     * 进入阶段2（索命鬼魅）：+10%移速，获得1个鬼术
     */
    public void advanceToStage2() {
        this.stage = 2;
        addRandomGhostSkill();
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.ma_chen_xu.stage_advance",
                            Component.translatable("hud.noellesroles.ma_chen_xu.phase2"))
                            .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD),
                    true);
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 1.0F, 0.8F);
        }
    }

    /**
     * 进入阶段3（厉鬼领域）：获得1个鬼术，解锁大招
     */
    public void advanceToStage3() {
        this.stage = 3;
        addRandomGhostSkill();
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.ma_chen_xu.stage_advance",
                            Component.translatable("hud.noellesroles.ma_chen_xu.phase3"))
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                    true);
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1.0F, 0.6F);
        }
    }

    /**
     * 进入阶段4（恐怖化身）：获得1个鬼术，获得永久护盾
     */
    public void advanceToStage4() {
        this.stage = 4;
        addRandomGhostSkill();
        // 获得永久护盾（布尔值，吸收一次致命伤害）
        this.permanentShield = true;
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.ma_chen_xu.stage_advance",
                            Component.translatable("hud.noellesroles.ma_chen_xu.phase4"))
                            .withStyle(ChatFormatting.BLACK, ChatFormatting.BOLD),
                    true);
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.0F, 0.4F);
        }
    }

    /**
     * 随机添加一个尚未获得的鬼术（鬼打墙/回响/诱捕/寄生）
     */
    private void addRandomGhostSkill() {
        List<String> availableSkills = new ArrayList<>();
        String[] allSkills = { "ghost_wall", "echo", "trap", "parasite", "vanish" };
        for (String skill : allSkills) {
            if (!ghostSkills.contains(skill)) {
                availableSkills.add(skill);
            }
        }
        if (!availableSkills.isEmpty()) {
            String skill = availableSkills.get(random.nextInt(availableSkills.size()));
            ghostSkills.add(skill);
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.ma_chen_xu.ghost_skill_acquired",
                                Component.translatable("hud.noellesroles.ma_chen_xu.skill." + skill))
                                .withStyle(ChatFormatting.GOLD),
                        true);
            }
        }
    }

    // ==================== 恐惧与里世界 ====================

    /** 获取当前阶段恐惧范围 */
    private double getFearRange() {
        return switch (stage) {
            case 1 -> FEAR_RANGE_STAGE_1;
            case 2 -> FEAR_RANGE_STAGE_2;
            case 3 -> FEAR_RANGE_STAGE_3;
            case 4 -> FEAR_RANGE_STAGE_4;
            default -> 0;
        };
    }

    /** 获取当前阶段恐惧SAN掉落量 */
    private int getFearSanLoss() {
        return switch (stage) {
            case 1 -> FEAR_SAN_LOSS_STAGE_1;
            case 2 -> FEAR_SAN_LOSS_STAGE_2;
            case 3 -> FEAR_SAN_LOSS_STAGE_3;
            case 4 -> FEAR_SAN_LOSS_STAGE_4;
            default -> 0;
        };
    }

    /**
     * 处理恐惧机制
     * 每3秒对范围内好人造成SAN掉落；低SAN目标范围+4、损失x2
     * 里世界期间恐惧变为全图
     */
    private void processFearMechanism() {
        if (!isActiveMaChenXu())
            return;

        fearTimer++;
        if (fearTimer >= FEAR_INTERVAL) {
            fearTimer = 0;
            double baseRange = getFearRange();
            int baseSanLoss = getFearSanLoss();
            boolean fullMap = otherworldActive;

            if (baseRange > 0 && baseSanLoss > 0) {
                Level world = player.level();
                Vec3 playerPos = player.position();
                for (Player target : world.players()) {
                    if (target.equals(player))
                        continue;
                    if (!GameUtils.isPlayerAliveAndSurvival(target))
                        continue;
                    if (isKiller(target))
                        continue;

                    double distance = playerPos.distanceTo(target.position());
                    float targetMood = SREPlayerMoodComponent.KEY.get(target).getMood();

                    // 低SAN增强：范围+4，SAN损失x2
                    double effectiveRange = baseRange;
                    int effectiveSanLoss = baseSanLoss;
                    if (targetMood < LOW_SAN_THRESHOLD) {
                        effectiveRange += LOW_SAN_RANGE_BONUS;
                        effectiveSanLoss *= 2;
                    }

                    if (fullMap || distance <= effectiveRange) {
                        SREPlayerMoodComponent.KEY.get(target).addMood(-((float) effectiveSanLoss / 100));
                        addSanLoss(effectiveSanLoss);
                    }
                }
            }
        }
    }

    /**
     * 处理里世界机制
     * 每2秒全图好人失去5SAN
     * 每15秒全体好人获得短暂发光
     * 被标记的玩家永久发光
     * 好人接近时发出警告（Title形式）
     * 布袋鬼释放黑雾粒子（增强版）
     */
    private void processOtherworldMechanism() {
        if (!otherworldActive)
            return;

        otherworldTimer++;
        otherworldDuration--;
        otherworldGlowTimer++;
        blackFogTimer++;

        if (otherworldTimer >= OTHERWORLD_INTERVAL) {
            otherworldTimer = 0;
            Level world = player.level();
            for (Player target : world.players()) {
                if (!GameUtils.isPlayerAliveAndSurvival(target))
                    continue;
                if (isKiller(target))
                    continue;
                SREPlayerMoodComponent.KEY.get(target).addMood(-((float) OTHERWORLD_SAN_LOSS / 100));
                addSanLoss(OTHERWORLD_SAN_LOSS);
            }
        }

        // 被标记的玩家永久发光（每3秒刷新，确保持续）
        if (player.level().getGameTime() % 60 == 0) {
            Level world = player.level();
            for (UUID uuid : markedPlayers) {
                Player marked = world.getPlayerByUUID(uuid);
                if (marked != null && GameUtils.isPlayerAliveAndSurvival(marked)) {
                    marked.addEffect(new MobEffectInstance(
                            MobEffects.GLOWING, 80, 0, false, false, true));
                }
            }
        }

        // 每15秒给所有人短暂发光（暴露所有人位置，包括杀手队友）
        if (otherworldGlowTimer >= OTHERWORLD_GLOW_INTERVAL) {
            otherworldGlowTimer = 0;
            Level world = player.level();
            for (Player target : world.players()) {
                if (target.equals(player))
                    continue; // 布袋鬼自己不发光
                if (!GameUtils.isPlayerAliveAndSurvival(target))
                    continue;
                target.addEffect(new MobEffectInstance(
                        MobEffects.GLOWING, OTHERWORLD_GLOW_DURATION, 0, false, false, true));
                if (target instanceof ServerPlayer targetSp) {
                    // 使用Title显示发光脉冲提示
                    targetSp.connection.send(new ClientboundSetTitlesAnimationPacket(5, 30, 10));
                    targetSp.connection.send(new ClientboundSetTitleTextPacket(
                            Component.translatable("message.noellesroles.ma_chen_xu.glow_pulse")
                                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
                    world.playSound(null, target.blockPosition(),
                            SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 0.6F, 0.8F);
                }
            }
        }

        // 布袋鬼附近有好人时发出警告（使用Title）
        if (player instanceof ServerPlayer && player.level().getGameTime() % 40 == 0) {
            Level world = player.level();
            Vec3 playerPos = player.position();
            for (Player target : world.players()) {
                if (target.equals(player))
                    continue;
                if (!GameUtils.isPlayerAliveAndSurvival(target))
                    continue;
                if (isKiller(target))
                    continue;
                double distance = playerPos.distanceTo(target.position());
                if (distance <= OTHERWORLD_WARN_RANGE) {
                    if (target instanceof ServerPlayer targetSp) {
                        if (distance <= 8.0) {
                            // 危险警告：Title
                            targetSp.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
                            targetSp.connection.send(new ClientboundSetTitleTextPacket(
                                    Component.translatable("message.noellesroles.ma_chen_xu.proximity_danger")
                                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
                            world.playSound(null, target.blockPosition(),
                                    SoundEvents.WARDEN_NEARBY_CLOSEST, SoundSource.HOSTILE, 0.8F, 1.0F);
                        } else {
                            // 普通警告：actionbar
                            targetSp.displayClientMessage(
                                    Component.translatable("message.noellesroles.ma_chen_xu.proximity_warning")
                                            .withStyle(ChatFormatting.RED),
                                    true);
                            world.playSound(null, target.blockPosition(),
                                    SoundEvents.WARDEN_NEARBY_CLOSE, SoundSource.HOSTILE, 0.4F, 1.0F);
                        }
                    }
                }
            }
        }

        // 黑雾粒子效果（布袋鬼周围持续释放 - 增强版，更加明显）
        if (blackFogTimer >= BLACK_FOG_PARTICLE_INTERVAL) {
            blackFogTimer = 0;
            if (player.level() instanceof ServerLevel sl) {
                Vec3 pos = player.position();
                // 主体黑雾：大量烟雾
                sl.sendParticles(ParticleTypes.LARGE_SMOKE,
                        pos.x, pos.y + 0.5, pos.z, 15, 1.2, 0.8, 1.2, 0.02);
                // 灵魂粒子
                sl.sendParticles(ParticleTypes.SOUL,
                        pos.x, pos.y + 1.0, pos.z, 5, 0.8, 0.5, 0.8, 0.02);
                sl.sendParticles(ParticleTypes.SCULK_SOUL,
                        pos.x, pos.y + 0.2, pos.z, 4, 1.5, 0.2, 1.5, 0.01);
                // 灵魂火焰粒子（更明显的视觉标识）
                sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        pos.x, pos.y + 0.8, pos.z, 6, 1.0, 0.4, 1.0, 0.01);
                // 幽匿粒子
                sl.sendParticles(ParticleTypes.SCULK_CHARGE_POP,
                        pos.x, pos.y + 1.5, pos.z, 3, 0.6, 0.3, 0.6, 0.005);
                // 烟雾粒子（扩大范围）
                sl.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                        pos.x, pos.y + 0.3, pos.z, 2, 2.0, 0.1, 2.0, 0.001);
            }
        }

        // 里世界结束
        if (otherworldDuration <= 0) {
            endOtherworld();
        }
    }

    /**
     * 激活里世界·百鬼夜行
     */
    public void activateOtherworld(int duration) {
        if (!(player instanceof ServerPlayer sp))
            return;

        this.otherworldActive = true;
        this.otherworldDuration = duration;
        this.otherworldTimer = 0;
        this.otherworldGlowTimer = 0;
        this.blackFogTimer = 0;
        this.markedPlayers.clear();

        // 布袋鬼无敌 + 速度III + 隐身
        sp.setInvulnerable(true);
        sp.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SPEED, duration, 2, false, false, false));
        sp.addEffect(new MobEffectInstance(
                MobEffects.INVISIBILITY, duration, 0, false, false, false));

        Level world = player.level();

        // 过渡动画：先给所有人短暂黑屏 + 失明效果作为过渡
        for (Player target : world.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(target))
                continue;
            if (target instanceof ServerPlayer targetSp) {
                // 短暂失明作为过渡动画（1.5秒）
                targetSp.addEffect(new MobEffectInstance(
                        MobEffects.DARKNESS, 60, 0, false, false, false));
            }
        }

        // 布袋鬼位置释放大量入场粒子
        if (world instanceof ServerLevel sl) {
            Vec3 pos = sp.position();
            // 爆发性灵魂粒子
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    pos.x, pos.y + 1.0, pos.z, 50, 3.0, 2.0, 3.0, 0.05);
            sl.sendParticles(ParticleTypes.SCULK_SOUL,
                    pos.x, pos.y + 0.5, pos.z, 30, 4.0, 1.0, 4.0, 0.03);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE,
                    pos.x, pos.y + 0.3, pos.z, 40, 5.0, 1.0, 5.0, 0.02);
            sl.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                    pos.x, pos.y + 0.5, pos.z, 15, 2.0, 3.0, 2.0, 0.005);
        }

        // 好人获得速度II + 里世界侵蚀效果（用于客户端检测）+ 使用Title发送里世界降临提醒
        for (Player target : world.players()) {
            if (GameUtils.isPlayerAliveAndSurvival(target) && !isKiller(target)) {
                target.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SPEED, duration, 2, false, false, true));
                target.addEffect(new MobEffectInstance(
                        ModEffects.OTHERWORLD_AURA, duration, 0, false, false, false));
                target.addEffect(new MobEffectInstance(
                        ModEffects.INFINITE_STAMINA, duration, 5, false, false, false));
                target.addEffect(new MobEffectInstance(
                        ModEffects.LOW_SAN_SHADER_RESISTANCE, duration, 10, false, false, false));
                target.addEffect(new MobEffectInstance(
                        ModEffects.MOOD_DRAIN_REDUCTION, duration, 1, false, false, false));

                if (target instanceof ServerPlayer targetSp) {
                    // 使用原生Title指令显示
                    targetSp.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
                    targetSp.connection.send(new ClientboundSetTitleTextPacket(
                            Component.translatable("message.noellesroles.ma_chen_xu.otherworld_warning_title")
                                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD,
                                            ChatFormatting.OBFUSCATED)));
                    targetSp.connection.send(new ClientboundSetSubtitleTextPacket(
                            Component.translatable("message.noellesroles.ma_chen_xu.otherworld_warning_subtitle")
                                    .withStyle(ChatFormatting.RED)));
                    world.playSound(null, target.blockPosition(),
                            SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.0F, 0.5F);
                }
            }
        }

        // 布袋鬼自己的里世界进场音效 + Title
        sp.connection.send(new ClientboundSetTitlesAnimationPacket(10, 40, 10));
        sp.connection.send(new ClientboundSetTitleTextPacket(
                Component.translatable("message.noellesroles.ma_chen_xu.li_shi_jie_activated")
                        .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)));
        world.playSound(null, sp.blockPosition(),
                SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 1.5F, 0.6F);

        this.sync();
    }

    /**
     * 结束里世界，处理标记玩家和奖励
     */
    private void endOtherworld() {
        otherworldActive = false;
        otherworldTimer = 0;
        otherworldDuration = 0;
        otherworldGlowTimer = 0;
        blackFogTimer = 0;

        if (!(player instanceof ServerPlayer sp))
            return;

        // 取消无敌并移除里世界专属效果
        sp.setInvulnerable(false);
        sp.removeEffect(MobEffects.INVISIBILITY);

        Level world = player.level();

        // 被标记的玩家死亡（无法复活）
        int markCount = 0;
        for (UUID uuid : markedPlayers) {
            Player markedPlayer = world.getPlayerByUUID(uuid);
            if (markedPlayer != null && GameUtils.isPlayerAliveAndSurvival(markedPlayer)) {
                GameUtils.killPlayer(markedPlayer, true, player, Noellesroles.id("machenxu"));
                markCount++;
            }
        }

        // 未被标记的存活好人恢复20SAN
        for (Player target : world.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(target))
                continue;
            if (isKiller(target))
                continue;
            if (!markedPlayers.contains(target.getUUID())) {
                SREPlayerMoodComponent.KEY.get(target).addMood(0.2f);
            }
        }

        // 根据标记数给予奖励
        SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(sp);
        if (markCount >= 3) {
            // 3+标记：+300金，+10%永久移速（最大30%）
            shopComponent.setBalance(shopComponent.balance + 300);
            permanentSpeedBonus = Math.min(30, permanentSpeedBonus + 10);
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.ma_chen_xu.ult_reward_3")
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    true);
        } else if (markCount >= 2) {
            // 2标记：+200金，进入下一阶段
            shopComponent.setBalance(shopComponent.balance + 200);
            if (stage == 1)
                advanceToStage2();
            else if (stage == 2)
                advanceToStage3();
            else if (stage == 3)
                advanceToStage4();
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.ma_chen_xu.ult_reward_2")
                            .withStyle(ChatFormatting.GOLD),
                    true);
        } else if (markCount >= 1) {
            // 1标记：+100金，大招CD减30s
            shopComponent.setBalance(shopComponent.balance + 100);
            ultimateCooldown = Math.max(0, ultimateCooldown - 600);
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.ma_chen_xu.ult_reward_1")
                            .withStyle(ChatFormatting.YELLOW),
                    true);
        }
        shopComponent.sync();
        markedPlayers.clear();

        // 移除好人的速度效果 + 使用Title发送里世界结束提醒
        for (Player target : world.players()) {
            if (GameUtils.isPlayerAliveAndSurvival(target) && !isKiller(target)) {
                target.removeEffect(MobEffects.MOVEMENT_SPEED);
                target.removeEffect(MobEffects.GLOWING);
                target.removeEffect(ModEffects.OTHERWORLD_AURA);
                if (target instanceof ServerPlayer targetSp) {
                    targetSp.connection.send(new ClientboundSetTitlesAnimationPacket(10, 40, 20));
                    targetSp.connection.send(new ClientboundSetTitleTextPacket(
                            Component.translatable("message.noellesroles.ma_chen_xu.otherworld_end_notice")
                                    .withStyle(ChatFormatting.GREEN)));
                }
            }
        }

        // 里世界结束的恢复粒子爆发
        if (world instanceof ServerLevel sl) {
            Vec3 pos = sp.position();
            sl.sendParticles(ParticleTypes.END_ROD,
                    pos.x, pos.y + 1.0, pos.z, 30, 3.0, 2.0, 3.0, 0.05);
            sl.sendParticles(ParticleTypes.FLASH,
                    pos.x, pos.y + 1.0, pos.z, 1, 0, 0, 0, 0);
        }

        // 掠风状态结束
        swiftWindActive = false;
        swiftWindDuration = 0;

        this.sync();
    }

    // ==================== 核心技能 ====================

    /**
     * 魂噬击杀（里世界外使用）
     * 目标SAN <= 10时可用，击杀后无法复活，清空物品，+50金，范围内好人-10SAN
     */
    public boolean soulDevour(Player target) {
        if (!(player instanceof ServerPlayer))
            return false;
        if (SREGameWorldComponent.KEY.get(player.level()).canUseKillerFeatures(target))return false;
        // 检查目标SAN <= 10（mood <= 0.1）
        float mood = SREPlayerMoodComponent.KEY.get(target).getMood();
        if (mood > 0.1f) {
            //Noellesroles.LOGGER.info("San {} over 0.1", mood);
            return false;
        }

        // 击杀目标（无法复活）
        GameUtils.forceKillPlayer(target, true, player, Noellesroles.id("machenxu"));

        // 移除受害者所有物品
        if (target instanceof ServerPlayer targetSp) {
            targetSp.getInventory().clearContent();
        }

        // 给布袋鬼50金币
        if (player instanceof ServerPlayer sp) {
            SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(sp);
            shopComponent.setBalance(shopComponent.balance + 50);
            shopComponent.sync();
        }

        // 恐惧范围内所有好人失去10SAN
        double range = getFearRange();
        Level world = player.level();
        Vec3 playerPos = player.position();
        for (Player nearby : world.players()) {
            if (nearby.equals(player) || nearby.equals(target))
                continue;
            if (!GameUtils.isPlayerAliveAndSurvival(nearby))
                continue;
            if (isKiller(nearby))
                continue;
            if (playerPos.distanceTo(nearby.position()) <= range) {
                SREPlayerMoodComponent.KEY.get(nearby).addMood(-0.1f);
            }
        }

        // 播放音效
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.WITHER_DEATH, SoundSource.PLAYERS, 1.0F, 1.2F);

        this.sync();
        return true;
    }

    /**
     * 里世界中标记玩家（左键调用，无SAN要求）
     * 标记的玩家在里世界结束时死亡（无法复活）
     */
    public boolean markPlayer(Player target) {
        if (!(player instanceof ServerPlayer))
            return false;
        if (!otherworldActive)
            return false;
        if (!GameUtils.isPlayerAliveAndSurvival(target))
            return false;

        UUID targetUUID = target.getUUID();
        if (markedPlayers.contains(targetUUID))
            return false;

        markedPlayers.add(targetUUID);

        // 被标记者永久发光
        target.addEffect(new MobEffectInstance(
                MobEffects.GLOWING, otherworldDuration + 200, 0, false, false, true));
        target.addEffect(new MobEffectInstance(
                MobEffects.INVISIBILITY, otherworldDuration + 200, 0, false, false, true));
        target.addEffect(new MobEffectInstance(
                ModEffects.GHOST_CURSE, otherworldDuration + 200, 0, false, false, true));

        if (target instanceof ServerPlayer targetSp) {
            // 使用Title显示被标记警告
            targetSp.connection.send(new ClientboundSetTitlesAnimationPacket(5, 60, 10));
            targetSp.connection.send(new ClientboundSetTitleTextPacket(
                    Component.translatable("message.noellesroles.ma_chen_xu.marked")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));

            // 标记粒子效果
            if (target.level() instanceof ServerLevel sl) {

                Vec3 pos = target.position();
                    // 灵魂火焰爆发
                    sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            pos.x, pos.y + 1.0, pos.z, 15, 0.3, 0.5, 0.3, 0.05);
                    // 暗色烟雾
                    sl.sendParticles(ParticleTypes.LARGE_SMOKE,
                            pos.x, pos.y + 0.5, pos.z, 10, 0.4, 0.3, 0.4, 0.02);
                    // 幽匿粒子
                    sl.sendParticles(ParticleTypes.SCULK_SOUL,
                            pos.x, pos.y + 0.8, pos.z, 5, 0.3, 0.3, 0.3, 0.01);


                // 音效
                sl.playSound(null, target.blockPosition(),
                        SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 0.8F, 0.7F);

                sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        pos.x, pos.y + 1.0, pos.z, 20, 0.5, 1.0, 0.5, 0.03);
                sl.sendParticles(ParticleTypes.SCULK_SOUL,
                        pos.x, pos.y + 0.5, pos.z, 10, 0.5, 0.5, 0.5, 0.02);
            }
        }

        player.level().playSound(null, target.blockPosition(),
                SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 1.0F, 1.5F);

        this.sync();
        return true;
    }

    /**
     * 使用大招 - 里世界·百鬼夜行
     * 阶段3+可用，消耗200金币（阶段4首次免费）
     */
    public void usePrayerRain() {
        if (otherworldActive)
            return;
        if (stage < 3) {
            player.displayClientMessage(
                    Component.translatable("tip.noellesroles.not_enough_energy")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }
        if (ultimateCooldown > 0) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.ma_chen_xu.prayer_rain_cooldown",
                            ultimateCooldown / 20)
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        // 检查金币（阶段4免费一次）
        boolean isFree = (stage == 4 && !stage4FreeUltUsed);
        SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(serverPlayer);
        if (!isFree) {
            if (shopComponent.balance < ULTIMATE_COST) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.insufficient_funds")
                                .withStyle(ChatFormatting.RED),
                        true);
                return;
            }
            shopComponent.setBalance(shopComponent.balance - ULTIMATE_COST);
            shopComponent.sync();
        } else {
            stage4FreeUltUsed = true;
        }

        int duration = (stage >= 4) ? ULTIMATE_DURATION_STAGE_4 : ULTIMATE_DURATION_STAGE_3;
        activateOtherworld(duration);
        ultimateCooldown = duration * 2;

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.ma_chen_xu.prayer_rain_activated")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                false);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    // ==================== 商店物品方法 ====================

    /**
     * 使用浊雨（商店物品）
     * 30秒下雨，恐惧范围外的好人每5秒失去3SAN，可与大招叠加
     */
    public boolean useTurbidRain() {
        if (!(player instanceof ServerPlayer sp))
            return false;
        if (turbidRainActive)
            return false;

        this.turbidRainActive = true;
        this.turbidRainDuration = TURBID_RAIN_DURATION;
        this.turbidRainTimer = 0;

        sp.serverLevel().setWeatherParameters(0, TURBID_RAIN_DURATION + 20, true, false);

        sp.displayClientMessage(
                Component.translatable("message.noellesroles.ma_chen_xu.turbid_rain_activated")
                        .withStyle(ChatFormatting.DARK_AQUA),
                false);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8F, 1.0F);

        this.sync();
        return true;
    }

    /**
     * 使用镇魂铃（商店物品）
     * 20格范围AoE，好人获得10秒耳鸣效果（挖掘疲劳+反胃）
     */
    public boolean useSoulBell() {
        if (!(player instanceof ServerPlayer))
            return false;

        Level world = player.level();
        Vec3 playerPos = player.position();
        boolean hitAny = false;

        for (Player target : world.players()) {
            if (target.equals(player))
                continue;
            if (!GameUtils.isPlayerAliveAndSurvival(target))
                continue;
            if (isKiller(target))
                continue;
            if (playerPos.distanceTo(target.position()) > SOUL_BELL_RANGE)
                continue;
            PlayerVolumeComponent.KEY.get(target).setVolume(10 * 20, 5);
            // 耳鸣效果：挖掘疲劳 + 反胃
            target.addEffect(new MobEffectInstance(
                    MobEffects.DIG_SLOWDOWN, SOUL_BELL_DURATION, 2, false, false, true));
            target.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, SOUL_BELL_DURATION, 0, false, false, true));
            target.addEffect(new MobEffectInstance(
                    MobEffects.CONFUSION, SOUL_BELL_DURATION, 0, false, false, true));
            target.addEffect(new MobEffectInstance(
                    MobEffects.LUCK, SOUL_BELL_DURATION, 0, false, false, true));
            hitAny = true;
        }

        if (hitAny) {
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.5F, 0.5F);
        }

        this.sync();
        return hitAny;
    }

    // ==================== 鬼术使用方法 ====================

    /**
     * 使用鬼打墙
     * 30秒CD，视线方向3格处放置隐形墙5秒（里世界8秒）。
     * 穿过的玩家被击退5格并失去15SAN（里世界额外-10SAN）
     */
    public void useGhostWall() {
        if (!(player instanceof ServerPlayer sp))
            return;
        if (ghostWallCooldown > 0) {
            sp.displayClientMessage(
                    Component.translatable("tip.noellesroles.ability_cooldown", ghostWallCooldown / 20)
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        Vec3 lookVec = sp.getViewVector(1.0F);
        Vec3 wallPos = sp.position().add(lookVec.scale(3.0));

        this.ghostWallActive = true;
        this.ghostWallPos = wallPos;
        this.ghostWallDirection = lookVec.normalize();
        this.ghostWallRemainingTicks = otherworldActive ? GHOST_WALL_DURATION_OTHERWORLD : GHOST_WALL_DURATION;
        this.ghostWallCooldown = GHOST_SKILL_COOLDOWN_GHOST_WALL;

        sp.displayClientMessage(
                Component.translatable("tip.noellesroles.activated.with_name",
                        Component.translatable("hud.noellesroles.ma_chen_xu.skill.ghost_wall"))
                        .withStyle(ChatFormatting.DARK_PURPLE),
                true);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 0.6F);
        sync();
    }

    /**
     * 处理鬼打墙tick逻辑：检查是否有玩家穿过墙平面
     */
    private void processGhostWall() {
        if (!ghostWallActive)
            return;

        ghostWallRemainingTicks--;
        if (ghostWallRemainingTicks <= 0) {
            ghostWallActive = false;
            ghostWallPos = null;
            ghostWallDirection = null;
            ghostWallHitPlayers.clear();
            return;
        }
        if (ghostWallPos == null || ghostWallDirection == null)
            return;

        Level world = player.level();
        for (Player target : world.players()) {
            if (target.equals(player)) continue;
            if (!GameUtils.isPlayerAliveAndSurvival(target)) continue;
            if (isKiller(target)) continue;
            // 每个玩家只触发一次
            if (ghostWallHitPlayers.contains(target.getUUID())) continue;

            Vec3 toTarget = target.position().subtract(ghostWallPos);
            double dot = toTarget.dot(ghostWallDirection);
            if (Math.abs(dot) < 1.5 && toTarget.horizontalDistance() < 3.0) {
                // 记录已命中
                ghostWallHitPlayers.add(target.getUUID());

                // 击退5格
                Vec3 knockback = ghostWallDirection.scale(GHOST_WALL_KNOCKBACK);
                if (dot < 0)
                    knockback = knockback.scale(-1);
                target.setDeltaMovement(knockback);
                if (target instanceof ServerPlayer targetSp) {
                    targetSp.hurtMarked = true;
                }

                // SAN损失
                int sanLoss = otherworldActive ? GHOST_WALL_SAN_LOSS_OTHERWORLD : GHOST_WALL_SAN_LOSS;
                SREPlayerMoodComponent.KEY.get(target).addMood(-((float) sanLoss / 100));
                addSanLoss(sanLoss);

                if (player.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.SOUL,
                            target.getX(), target.getY() + 1.0, target.getZ(),
                            15, 0.5, 0.5, 0.5, 0.02);
                }
            }
        }
    }

    /**
     * 使用回响
     * 45秒CD（里世界减半）。第一次使用录制位置5秒，之后可传送回录制位置。
     */
    public void useEcho() {
        if (!(player instanceof ServerPlayer sp))
            return;
        if (echoCooldown > 0) {
            sp.displayClientMessage(
                    Component.translatable("tip.noellesroles.ability_cooldown", echoCooldown / 20)
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        if (echoCanTeleport && echoRecordPos != null) {
            // 传送回录制位置
            sp.teleportTo(echoRecordPos.x, echoRecordPos.y, echoRecordPos.z);
            echoCanTeleport = false;
            echoRecordPos = null;
            echoCooldown = otherworldActive ? GHOST_SKILL_COOLDOWN_ECHO / 2 : GHOST_SKILL_COOLDOWN_ECHO;

            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.ma_chen_xu.echo.teleported")
                            .withStyle(ChatFormatting.AQUA),
                    true);
            player.level().playSound(null, sp.blockPosition(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.2F);
        } else if (!echoRecording) {
            // 开始录制位置
            echoRecording = true;
            echoRecordTicks = 0;
            echoRecordPos = sp.position();

            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.ma_chen_xu.echo.recording")
                            .withStyle(ChatFormatting.YELLOW),
                    true);
            player.level().playSound(null, sp.blockPosition(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 0.6F);
        }
        sync();
    }

    /**
     * 处理回响tick逻辑：录制倒计时
     */
    private void processEcho() {
        if (echoRecording) {
            echoRecordTicks++;
            if (echoRecordTicks >= ECHO_RECORD_DURATION) {
                echoRecording = false;
                echoCanTeleport = true;
                if (player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(
                            Component.translatable("message.noellesroles.ma_chen_xu.echo.ready")
                                    .withStyle(ChatFormatting.GREEN),
                            true);
                }
                sync();
            }
        }
    }

    /**
     * 使用诱捕
     * 20秒CD，放置隐形陷阱。触发：3秒定身 + 25SAN（里世界5秒 + 40SAN）。最多2个。
     */
    public void useTrap() {
        if (!(player instanceof ServerPlayer sp))
            return;
        if (trapCooldown > 0) {
            sp.displayClientMessage(
                    Component.translatable("tip.noellesroles.ability_cooldown", trapCooldown / 20)
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 超过上限则移除最早的陷阱
        if (trapPositions.size() >= MAX_TRAPS) {
            trapPositions.remove(0);
        }

        trapPositions.add(sp.position());
        trapCooldown = GHOST_SKILL_COOLDOWN_TRAP;

        sp.displayClientMessage(
                Component.translatable("tip.noellesroles.activated.with_name",
                        Component.translatable("hud.noellesroles.ma_chen_xu.skill.trap"))
                        .withStyle(ChatFormatting.DARK_GREEN),
                true);
        player.level().playSound(null, sp.blockPosition(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8F, 0.8F);
        sync();
    }

    /**
     * 处理陷阱tick逻辑：检查非杀手玩家是否踩到陷阱（1.5格内）
     */
    private void processTraps() {
        if (trapPositions.isEmpty())
            return;

        Level world = player.level();
        List<Vec3> triggeredTraps = new ArrayList<>();

        for (Vec3 trapPos : trapPositions) {
            for (Player target : world.players()) {
                if (target.equals(player))
                    continue;
                if (!GameUtils.isPlayerAliveAndSurvival(target))
                    continue;
                if (isKiller(target))
                    continue;

                if (target.position().distanceToSqr(trapPos) <= TRAP_TRIGGER_RANGE * TRAP_TRIGGER_RANGE) {
                    // 触发陷阱
                    int rootDuration = otherworldActive ? TRAP_ROOT_DURATION_OTHERWORLD : TRAP_ROOT_DURATION;
                    int sanLoss = otherworldActive ? TRAP_SAN_LOSS_OTHERWORLD : TRAP_SAN_LOSS;

                    // 定身效果
                    target.addEffect(new MobEffectInstance(
                            MobEffects.MOVEMENT_SLOWDOWN, rootDuration, 255, false, true, true));
                    // SAN损失
                    SREPlayerMoodComponent.KEY.get(target).addMood(-((float) sanLoss / 100));
                    addSanLoss(sanLoss);

                    if (target instanceof ServerPlayer targetSp) {
                        targetSp.displayClientMessage(
                                Component.translatable("message.noellesroles.ma_chen_xu.trap.triggered")
                                        .withStyle(ChatFormatting.DARK_RED),
                                true);
                    }

                    if (player.level() instanceof ServerLevel sl) {
                        sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                                trapPos.x, trapPos.y + 0.5, trapPos.z,
                                20, 0.5, 0.3, 0.5, 0.02);
                    }

                    triggeredTraps.add(trapPos);
                    break; // 一个陷阱只触发一次
                }
            }
        }

        trapPositions.removeAll(triggeredTraps);
        if (!triggeredTraps.isEmpty()) {
            sync();
        }
    }

    /**
     * 使用寄生
     * 90秒CD，对静止目标使用（速度接近0）。目标获得"鬼种"，60秒后死亡（里世界30秒）。
     * 布袋鬼可透视目标。
     */
    public void useParasite() {
        if (!(player instanceof ServerPlayer sp))
            return;
        if (parasiteCooldown > 0) {
            sp.displayClientMessage(
                    Component.translatable("tip.noellesroles.ability_cooldown", parasiteCooldown / 20)
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 搜索视线方向的目标
        Vec3 eyePos = sp.getEyePosition();
        Vec3 lookVec = sp.getViewVector(1.0F);
        Vec3 endPos = eyePos.add(lookVec.scale(10.0));
        AABB searchArea = new AABB(eyePos, endPos).inflate(2.0);
        List<Player> nearbyPlayers = sp.level().getEntitiesOfClass(Player.class, searchArea);

        Player target = null;
        double closestDistance = Double.MAX_VALUE;
        for (Player nearbyPlayer : nearbyPlayers) {
            if (nearbyPlayer.equals(sp) || !GameUtils.isPlayerAliveAndSurvival(nearbyPlayer))
                continue;
            if (isKiller(nearbyPlayer))
                continue;
            double distance = eyePos.distanceTo(nearbyPlayer.getEyePosition());
            if (distance < closestDistance) {
                closestDistance = distance;
                target = nearbyPlayer;
            }
        }

        if (target == null) {
            sp.displayClientMessage(
                    Component.translatable("tip.noellesroles.no_target")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 检查目标是否静止（速度接近0，容忍网络延迟和重力波动）
        Vec3 velocity = target.getDeltaMovement();
        if (velocity.horizontalDistance() > PARASITE_STATIONARY_THRESHOLD) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.ma_chen_xu.parasite.target_moving")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        parasiteCooldown = GHOST_SKILL_COOLDOWN_PARASITE;

        // 设置中毒（里世界30秒，普通60秒）
        int deathTicks = otherworldActive ? PARASITE_DEATH_TICKS_OTHERWORLD : PARASITE_DEATH_TICKS;
        SREPlayerPoisonComponent poisonComponent = SREPlayerPoisonComponent.KEY.get(target);
        poisonComponent.setPoisonTicks(deathTicks, sp.getUUID());
        poisonComponent.sync();

        // 记录寄生目标（用于透视）
        parasiteTargetUUID = target.getUUID();

        sp.level().playSound(null, target.blockPosition(),
                SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.8F, 1.2F);

        if (sp.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                    target.getX(), target.getY() + 1.0, target.getZ(),
                    30, 0.5, 0.8, 0.5, 0.02);
        }

        sp.displayClientMessage(
                Component.translatable("tip.noellesroles.activated.with_name_and_target",
                        target.getDisplayName(),
                        Component.translatable("hud.noellesroles.ma_chen_xu.skill.parasite"))
                        .withStyle(ChatFormatting.DARK_GREEN),
                true);

        if (target instanceof ServerPlayer targetSp) {
            targetSp.displayClientMessage(
                    Component.translatable("message.noellesroles.ma_chen_xu.parasite.infected")
                            .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.ITALIC),
                    true);
        }

        sync();
    }

    /**
     * 使用隐匿
     * 45秒CD，获得8秒隐身（里世界12秒），释放烟雾粒子迷惑
     */
    public void useVanish() {
        if (!(player instanceof ServerPlayer sp))
            return;
        if (vanishCooldown > 0) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.ma_chen_xu.cooldown",
                            Component.translatable("hud.noellesroles.ma_chen_xu.skill.vanish"),
                            vanishCooldown / 20)
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        int duration = otherworldActive ? VANISH_DURATION_OTHERWORLD : VANISH_DURATION;
        this.vanishCooldown = GHOST_SKILL_COOLDOWN_VANISH;

        // 隐身效果
        sp.addEffect(new MobEffectInstance(
                MobEffects.INVISIBILITY, duration, 0, false, false, false));

        // 释放烟雾粒子（迷惑对手）
        if (player.level() instanceof ServerLevel sl) {
            Vec3 pos = sp.position();
            sl.sendParticles(ParticleTypes.LARGE_SMOKE,
                    pos.x, pos.y + 1.0, pos.z, 30, 2.0, 1.0, 2.0, 0.05);
            sl.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                    pos.x, pos.y + 0.5, pos.z, 10, 1.5, 2.0, 1.5, 0.01);
            sl.sendParticles(ParticleTypes.SOUL,
                    pos.x, pos.y + 1.0, pos.z, 15, 1.0, 0.5, 1.0, 0.03);
        }

        sp.displayClientMessage(
                Component.translatable("tip.noellesroles.activated.with_name",
                        Component.translatable("hud.noellesroles.ma_chen_xu.skill.vanish"))
                        .withStyle(ChatFormatting.DARK_PURPLE),
                true);
        player.level().playSound(null, sp.blockPosition(),
                SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 0.8F);
        sync();
    }

    /**
     * 使用掠风（阶段4里世界专属）
     * 50%移速加成，持续10秒
     */
    public void useSwiftWind() {
        if (!(player instanceof ServerPlayer sp))
            return;
        if (stage < 4 || !otherworldActive) {
            sp.displayClientMessage(
                    Component.translatable("tip.noellesroles.not_enough_energy")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }
        if (swiftWindActive)
            return;

        swiftWindActive = true;
        swiftWindDuration = SWIFT_WIND_DURATION;

        sp.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SPEED, SWIFT_WIND_DURATION, 2, false, false, true));

        sp.displayClientMessage(
                Component.translatable("tip.noellesroles.activated.with_name",
                        Component.translatable("hud.noellesroles.ma_chen_xu.skill.swift_wind"))
                        .withStyle(ChatFormatting.AQUA),
                true);
        player.level().playSound(null, sp.blockPosition(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.2F);
        sync();
    }

    // ==================== 技能管理 ====================

    public void changeSkill() {
        if (ghostSkills.isEmpty())
            return;
        this.nowSelectedSkill++;
        if (this.nowSelectedSkill >= this.ghostSkills.size()) {
            this.nowSelectedSkill = 0;
        }
        Component text = Component.translatable("message.noellesroles.ma_chen_xu.now_sel_skill",
                Component.translatable("hud.noellesroles.ma_chen_xu.skill." + ghostSkills.get(nowSelectedSkill))
                        .withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.GOLD);
        this.player.displayClientMessage(text, true);
        this.sync();
    }

    public void tryActiveAbility() {
        if (player.isShiftKeyDown()) {
            this.changeSkill();
        } else {
            if (ghostSkills.isEmpty())
                return;
            if (this.nowSelectedSkill >= ghostSkills.size())
                return;
            var skillId = ghostSkills.get(this.nowSelectedSkill);
            switch (skillId) {
                case "ghost_wall" -> useGhostWall();
                case "echo" -> useEcho();
                case "trap" -> useTrap();
                case "parasite" -> useParasite();
                case "vanish" -> useVanish();
                default -> {
                }
            }
        }
    }

    public Component getNowCooldownText() {
        if (ghostSkills.isEmpty()) return null;
        if (this.nowSelectedSkill >= ghostSkills.size()) return null;

        var skillId = ghostSkills.get(this.nowSelectedSkill);
        return switch (skillId) {
            case "ghost_wall" -> {
                if (ghostWallCooldown <= 0)
                    yield Component.translatable("message.noellesroles.ma_chen_xu.available",
                            NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
                            .withStyle(ChatFormatting.GREEN);
                yield Component.translatable("message.noellesroles.ma_chen_xu.cooldown",
                        Component.translatable("hud.noellesroles.ma_chen_xu.skill." + skillId),
                        ghostWallCooldown / 20)
                        .withStyle(ChatFormatting.YELLOW);
            }
            case "echo" -> {
                if (echoCanTeleport)
                    yield Component.translatable("message.noellesroles.ma_chen_xu.echo.can_teleport",
                            NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
                            .withStyle(ChatFormatting.AQUA);
                if (echoRecording)
                    yield Component.translatable("message.noellesroles.ma_chen_xu.echo.recording_status",
                            (ECHO_RECORD_DURATION - echoRecordTicks) / 20)
                            .withStyle(ChatFormatting.YELLOW);
                if (echoCooldown <= 0)
                    yield Component.translatable("message.noellesroles.ma_chen_xu.available",
                            NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
                            .withStyle(ChatFormatting.GREEN);
                yield Component.translatable("message.noellesroles.ma_chen_xu.cooldown",
                        Component.translatable("hud.noellesroles.ma_chen_xu.skill." + skillId),
                        echoCooldown / 20)
                        .withStyle(ChatFormatting.YELLOW);
            }
            case "trap" -> {
                if (trapCooldown <= 0)
                    yield Component.translatable("message.noellesroles.ma_chen_xu.available",
                            NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
                            .withStyle(ChatFormatting.GREEN);
                yield Component.translatable("message.noellesroles.ma_chen_xu.cooldown",
                        Component.translatable("hud.noellesroles.ma_chen_xu.skill." + skillId),
                        trapCooldown / 20)
                        .withStyle(ChatFormatting.YELLOW);
            }
            case "parasite" -> {
                if (parasiteCooldown <= 0)
                    yield Component.translatable("message.noellesroles.ma_chen_xu.available",
                            NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
                            .withStyle(ChatFormatting.GREEN);
                yield Component.translatable("message.noellesroles.ma_chen_xu.cooldown",
                        Component.translatable("hud.noellesroles.ma_chen_xu.skill." + skillId),
                        parasiteCooldown / 20)
                        .withStyle(ChatFormatting.YELLOW);
            }
            case "vanish" -> {
                if (vanishCooldown <= 0)
                    yield Component.translatable("message.noellesroles.ma_chen_xu.available",
                            NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
                            .withStyle(ChatFormatting.GREEN);
                yield Component.translatable("message.noellesroles.ma_chen_xu.cooldown",
                        Component.translatable("hud.noellesroles.ma_chen_xu.skill." + skillId),
                        vanishCooldown / 20)
                        .withStyle(ChatFormatting.YELLOW);
            }
            default -> null;
        };
    }

    // ==================== 同步 ====================

    public void sync() {
        ModComponents.MA_CHEN_XU.sync(this.player);
    }

    // ==================== Tick 处理 ====================

    @Override
    public void serverTick() {
        if (!isActiveMaChenXu())
            return;
        if (!GameUtils.isPlayerAliveAndSurvival(player))
            return;
        if (!(player instanceof ServerPlayer))
            return;
        // 技能禁止
        if (this.player.hasEffect(ModEffects.SKILL_BANED))
            return;
        // 大招冷却
        if (ultimateCooldown > 0)
            ultimateCooldown--;

        // 处理恐惧机制
        processFearMechanism();

        // 处理里世界机制
        processOtherworldMechanism();

        // 处理浊雨机制
        processTurbidRain();

        // 处理鬼术效果
        processGhostWall();
        processEcho();
        processTraps();

        // 处理掠风效果
        if (swiftWindActive) {
            swiftWindDuration--;
            if (swiftWindDuration <= 0) {
                swiftWindActive = false;
            }
        }

        // 鬼术冷却递减
        if (ghostWallCooldown > 0)
            ghostWallCooldown--;
        if (echoCooldown > 0)
            echoCooldown--;
        if (trapCooldown > 0)
            trapCooldown--;
        if (parasiteCooldown > 0)
            parasiteCooldown--;
        if (vanishCooldown > 0)
            vanishCooldown--;

        // 阶段2+的移速加成（10%）
        if (stage >= 2) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SPEED, 3, 0, false, false, false));
        }

        // 永久移速加成（大招3+标记奖励）
        if (permanentSpeedBonus >= 10 && player.level().getGameTime() % 20 == 0) {
            int amplifier = Math.max(0, (permanentSpeedBonus / 10) - 1); // 10%=0, 20%=1, 30%=2
            player.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SPEED, 25, amplifier, false, false, false));
        }

        // 阶段4自动大招检查（延迟到tick末尾，避免mid-tick状态变更）
        checkAutoUltimate();
    }

    /**
     * 处理浊雨机制：恐惧范围外好人每5秒-3SAN
     */
    private void processTurbidRain() {
        if (!turbidRainActive)
            return;

        turbidRainDuration--;
        turbidRainTimer++;

        if (turbidRainTimer >= TURBID_RAIN_SAN_INTERVAL) {
            turbidRainTimer = 0;
            double fearRange = getFearRange();
            Level world = player.level();
            Vec3 playerPos = player.position();

            for (Player target : world.players()) {
                if (target.equals(player))
                    continue;
                if (!GameUtils.isPlayerAliveAndSurvival(target))
                    continue;
                if (isKiller(target))
                    continue;
                // 只对恐惧范围外的好人生效（浊雨覆盖恐惧盲区，与恐惧互补）
                if (playerPos.distanceTo(target.position()) > fearRange) {
                    SREPlayerMoodComponent.KEY.get(target).addMood(-((float) TURBID_RAIN_SAN_LOSS / 100));
                    addSanLoss(TURBID_RAIN_SAN_LOSS);
                }
            }
        }

        if (turbidRainDuration <= 0) {
            turbidRainActive = false;
            if (player instanceof ServerPlayer sp) {
                sp.serverLevel().setWeatherParameters(6000, 0, false, false);
            }
            this.sync();
        }
    }

    @Override
    public void clientTick() {
        // 技能禁止
        if (this.player.hasEffect(ModEffects.SKILL_BANED))
            return;
        if (otherworldActive && otherworldDuration > 1)
            otherworldDuration--;
        if (ultimateCooldown > 1)
            ultimateCooldown--;
        if (ghostWallCooldown > 1)
            ghostWallCooldown--;
        if (echoCooldown > 1)
            echoCooldown--;
        if (trapCooldown > 1)
            trapCooldown--;
        if (parasiteCooldown > 1)
            parasiteCooldown--;
        if (vanishCooldown > 1)
            vanishCooldown--;
        if (turbidRainActive && turbidRainDuration > 1)
            turbidRainDuration--;
        if (echoRecording) {
            echoRecordTicks++;
            if (echoRecordTicks >= ECHO_RECORD_DURATION) {
                echoRecording = false;
                echoCanTeleport = true;
            }
        }
        if (swiftWindActive && swiftWindDuration > 1)
            swiftWindDuration--;
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (this.stage <= 0)
            return;

        tag.putInt("STAGE_2_THRESHOLD", this.STAGE_2_THRESHOLD);
        tag.putInt("STAGE_3_THRESHOLD", this.STAGE_3_THRESHOLD);
        tag.putInt("STAGE_4_THRESHOLD", this.STAGE_4_THRESHOLD);

        tag.putInt("stage", this.stage);
        tag.putInt("totalSanLoss", this.totalSanLoss);
        tag.putInt("fearTimer", this.fearTimer);
        tag.putBoolean("otherworldActive", this.otherworldActive);
        tag.putInt("otherworldTimer", this.otherworldTimer);
        tag.putInt("otherworldDuration", this.otherworldDuration);
        tag.putInt("ultimateCooldown", this.ultimateCooldown);
        tag.putBoolean("stage4FreeUltUsed", this.stage4FreeUltUsed);
        tag.putBoolean("permanentShield", this.permanentShield);
        tag.putInt("permanentSpeedBonus", this.permanentSpeedBonus);
        tag.putInt("nowSelectedSkill", this.nowSelectedSkill);

        // 鬼术冷却
        tag.putInt("ghostWallCooldown", this.ghostWallCooldown);
        tag.putInt("echoCooldown", this.echoCooldown);
        tag.putInt("trapCooldown", this.trapCooldown);
        tag.putInt("parasiteCooldown", this.parasiteCooldown);
        tag.putInt("vanishCooldown", this.vanishCooldown);

        // 回响状态
        tag.putBoolean("echoRecording", this.echoRecording);
        tag.putInt("echoRecordTicks", this.echoRecordTicks);
        tag.putBoolean("echoCanTeleport", this.echoCanTeleport);

        // 浊雨状态
        tag.putBoolean("turbidRainActive", this.turbidRainActive);
        tag.putInt("turbidRainDuration", this.turbidRainDuration);

        // 掠风状态
        tag.putBoolean("swiftWindActive", this.swiftWindActive);
        tag.putInt("swiftWindDuration", this.swiftWindDuration);

        // 鬼术列表
        CompoundTag skillsTag = new CompoundTag();
        for (int i = 0; i < ghostSkills.size(); i++) {
            skillsTag.putString("skill_" + i, ghostSkills.get(i));
        }
        skillsTag.putInt("size", ghostSkills.size());
        tag.put("ghostSkills", skillsTag);

        // 标记玩家列表
        CompoundTag markedTag = new CompoundTag();
        for (int i = 0; i < markedPlayers.size(); i++) {
            markedTag.putUUID("player_" + i, markedPlayers.get(i));
        }
        markedTag.putInt("size", markedPlayers.size());
        tag.put("markedPlayers", markedTag);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.STAGE_2_THRESHOLD = tag.contains("STAGE_2_THRESHOLD") ? tag.getInt("STAGE_2_THRESHOLD") : 100000;
        this.STAGE_3_THRESHOLD = tag.contains("STAGE_3_THRESHOLD") ? tag.getInt("STAGE_3_THRESHOLD") : 100000;
        this.STAGE_4_THRESHOLD = tag.contains("STAGE_4_THRESHOLD") ? tag.getInt("STAGE_4_THRESHOLD") : 100000;
        this.stage = tag.contains("stage") ? tag.getInt("stage") : 1;
        this.totalSanLoss = tag.contains("totalSanLoss") ? tag.getInt("totalSanLoss") : 0;
        this.fearTimer = tag.contains("fearTimer") ? tag.getInt("fearTimer") : 0;
        this.otherworldActive = tag.contains("otherworldActive") && tag.getBoolean("otherworldActive");
        this.otherworldTimer = tag.contains("otherworldTimer") ? tag.getInt("otherworldTimer") : 0;
        this.otherworldDuration = tag.contains("otherworldDuration") ? tag.getInt("otherworldDuration") : 0;
        this.ultimateCooldown = tag.contains("ultimateCooldown") ? tag.getInt("ultimateCooldown") : 0;
        this.stage4FreeUltUsed = tag.contains("stage4FreeUltUsed") && tag.getBoolean("stage4FreeUltUsed");
        this.permanentShield = tag.contains("permanentShield") && tag.getBoolean("permanentShield");
        this.permanentSpeedBonus = tag.contains("permanentSpeedBonus") ? tag.getInt("permanentSpeedBonus") : 0;
        this.nowSelectedSkill = tag.contains("nowSelectedSkill") ? tag.getInt("nowSelectedSkill") : 0;

        // 鬼术冷却
        this.ghostWallCooldown = tag.contains("ghostWallCooldown") ? tag.getInt("ghostWallCooldown") : 0;
        this.echoCooldown = tag.contains("echoCooldown") ? tag.getInt("echoCooldown") : 0;
        this.trapCooldown = tag.contains("trapCooldown") ? tag.getInt("trapCooldown") : 0;
        this.parasiteCooldown = tag.contains("parasiteCooldown") ? tag.getInt("parasiteCooldown") : 0;
        this.vanishCooldown = tag.contains("vanishCooldown") ? tag.getInt("vanishCooldown") : 0;

        // 回响状态
        this.echoRecording = tag.contains("echoRecording") && tag.getBoolean("echoRecording");
        this.echoRecordTicks = tag.contains("echoRecordTicks") ? tag.getInt("echoRecordTicks") : 0;
        this.echoCanTeleport = tag.contains("echoCanTeleport") && tag.getBoolean("echoCanTeleport");

        // 浊雨状态
        this.turbidRainActive = tag.contains("turbidRainActive") && tag.getBoolean("turbidRainActive");
        this.turbidRainDuration = tag.contains("turbidRainDuration") ? tag.getInt("turbidRainDuration") : 0;

        // 掠风状态
        this.swiftWindActive = tag.contains("swiftWindActive") && tag.getBoolean("swiftWindActive");
        this.swiftWindDuration = tag.contains("swiftWindDuration") ? tag.getInt("swiftWindDuration") : 0;

        // 鬼术列表
        this.ghostSkills.clear();
        if (tag.contains("ghostSkills")) {
            CompoundTag skillsTag = tag.getCompound("ghostSkills");
            int size = skillsTag.getInt("size");
            for (int i = 0; i < size; i++) {
                String skill = skillsTag.getString("skill_" + i);
                if (!skill.isEmpty()) {
                    this.ghostSkills.add(skill);
                }
            }
        }

        // 标记玩家列表
        this.markedPlayers.clear();
        if (tag.contains("markedPlayers")) {
            CompoundTag markedTag = tag.getCompound("markedPlayers");
            int size = markedTag.getInt("size");
            for (int i = 0; i < size; i++) {
                if (markedTag.contains("player_" + i)) {
                    this.markedPlayers.add(markedTag.getUUID("player_" + i));
                }
            }
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
