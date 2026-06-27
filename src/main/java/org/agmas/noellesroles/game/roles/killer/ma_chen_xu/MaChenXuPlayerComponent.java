package org.agmas.noellesroles.game.roles.killer.ma_chen_xu;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent.GameStatus;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.component.PlayerVolumeComponent;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.agmas.noellesroles.game.roles.killer.ma_chen_xu.MaChenXuEventHandler.HIT_SELF_LOCK_TICKS;

/**
 * 布袋鬼·诡舍·缚灵 组件（惊吓·精神压制套路）
 *
 * 核心玩法：
 * - 恐惧侵蚀：以布袋鬼为中心的「距离梯度 + 视线」持续掉 SAN，越近、被正视掉得越快，
 *   范围边缘几乎为 0，血条平滑下滑而非突兀跳变。
 * - 四阶段成长，依次解锁四种惊吓向诡术：鬼遮眼 / 替身草人 / 怨声 / 夺魄。
 * - 里世界·百鬼夜行（大招，机制保持）：无敌领域 + 标记秒杀 + 全图侵蚀。
 *
 * 输入：V 切换诡术，G 释放选中诡术，Sneak+G 开里世界大招，左键 魂噬 / 标记。
 */
public class MaChenXuPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    /** 组件键 */
    public static final ComponentKey<MaChenXuPlayerComponent> KEY = ModComponents.MA_CHEN_XU;

    /** 诡术槽位顺序（与 RoleSkill 注册顺序一致，HUD/选中索引据此映射） */
    public static final String[] ART_ORDER = { "veil", "effigy", "wail", "seize" };

    // ==================== 同步分组掩码 ====================

    public static final int SYNC_CORE = 0x01;
    public static final int SYNC_SKILLS = 0x02;
    public static final int SYNC_COOLDOWNS = 0x04;
    public static final int SYNC_OTHERWORLD = 0x08;
    public static final int SYNC_TURBID = 0x10;
    public static final int SYNC_ALL = 0x1F;

    // ==================== 恐惧侵蚀常量 ====================

    public static final double[] STAGE_RANGE = { 0, 30.0, 40.0, 50.0, 50.0 };
    /** 各阶段点对点（prox=1）基础侵蚀（mood/tick）：约 0.64/1.04/1.44/1.84 SAN/s（-60%） */
    public static final double[] STAGE_EROSION = { 0, 0.00032, 0.00052, 0.00072, 0.00092 };
    public static final double GAZE_MULT = 1.8;
    public static final float LOW_SAN_THRESHOLD = 0.3f;
    public static final double LOW_SAN_MAX_BONUS = 0.5;
    public static final double OTHERWORLD_PROX_FLOOR = 0.5;

    public int STAGE_2_THRESHOLD = 100;
    public int STAGE_3_THRESHOLD = 250;
    public int STAGE_4_THRESHOLD = 500;

    // ==================== 大招 / 里世界常量 ====================

    public static final int ULTIMATE_COST = 150;
    public static final int ULTIMATE_DURATION_STAGE_3 = 600; // 30s
    public static final int ULTIMATE_DURATION_STAGE_4 = 900; // 45s
    public static final int INITIAL_GOLD = 50;

    public static final int OTHERWORLD_GLOW_INTERVAL = 300;
    public static final int OTHERWORLD_GLOW_DURATION = 100;
    public static final double OTHERWORLD_WARN_RANGE = 20.0;
    public static final int BLACK_FOG_PARTICLE_INTERVAL = 5;
    public static final int OTHERWORLD_DESCENT_DURATION = 100;
    public static final int OTHERWORLD_INTRO_FREEZE_DURATION = 40;

    // ==================== 诡术常量 ====================

    /** 鬼遮眼：朝准星区域致盲+反胃+扭曲 */
    public static final int VEIL_COOLDOWN = 320; // 16s
    public static final double VEIL_AIM_DISTANCE = 9.0;
    public static final double VEIL_RADIUS = 4.0;
    public static final int VEIL_DEBUFF_TICKS = 100; // 5s
    public static final int VEIL_SAN_LOSS = 15;

    /** 替身草人：伪装成布袋鬼的诱饵，好人靠近受惊 */
    public static final int EFFIGY_COOLDOWN = 500; // 25s
    public static final int EFFIGY_LIFETIME = 600; // 30s
    public static final double EFFIGY_TRIGGER_RANGE = 3.0;
    public static final int EFFIGY_SAN_LOSS = 20;
    public static final int EFFIGY_GLOW_TICKS = 120;

    /** 怨声：以自身为中心的恐惧脉冲 */
    public static final int WAIL_COOLDOWN = 400; // 20s
    public static final double WAIL_RANGE = 12.0;
    public static final int WAIL_SAN_MAX = 18;
    public static final int WAIL_SAN_MIN = 8;
    public static final int WAIL_SLOW_TICKS = 50;

    /** 夺魄：拉拽并定身低 SAN 目标（处决前摇） */
    public static final int SEIZE_COOLDOWN = 600; // 30s
    public static final double SEIZE_RANGE = 12.0;
    public static final float SEIZE_SAN_THRESHOLD = 0.35f;
    public static final int SEIZE_ROOT_TICKS = 50;
    public static final int SEIZE_SAN_LOSS = 20;

    // ==================== 小诡术常量 ====================

    /** 小诡术池（随机获取，不重复） */
    public static final String[] MINOR_TRICK_POOL = { "parasite", "push", "echo" };
    public static final int MAX_MINOR_TRICKS = 4;

    /** 寄生：目标20秒后死亡，自身隐身 */
    public static final int PARASITE_COOLDOWN = 480; // 24s
    public static final double PARASITE_RANGE = 8.0;
    public static final int PARASITE_DEATH_DELAY = 400; // 20s
    public static final int PARASITE_INVIS_DURATION = 160; // 8s
    public static final int PARASITE_SAN_LOSS = 20;

    /** 推走：推开周围非杀手玩家 */
    public static final int PUSH_COOLDOWN = 280; // 14s
    public static final double PUSH_RANGE = 6.0;
    public static final double PUSH_FORCE = 2.8;

    /** 录制回响：记录位置→前瞬移→再释放返回原位 */
    public static final int ECHO_COOLDOWN = 360; // 18s
    public static final double ECHO_TELEPORT_DISTANCE = 10.0;
    public static final int ECHO_RETURN_WINDOW = 160; // 8s
    public static final int ECHO_SAN_LOSS = 12;

    /** 魂噬：低 SAN 处决 */
    public static final float SOUL_DEVOUR_THRESHOLD = 0.15f;

    /** 浊雨（商店） */
    public static final int TURBID_RAIN_DURATION = 600;
    public static final int TURBID_RAIN_SAN_INTERVAL = 100;
    public static final int TURBID_RAIN_SAN_LOSS = 3;

    /** 镇魂铃（商店） */
    public static final double SOUL_BELL_RANGE = 20.0;
    public static final int SOUL_BELL_DURATION = 200;

    // ==================== 状态变量 ====================

    private final Player player;

    public int stage = 1;
    public int totalSanLoss = 0;
    private float sanLossAccumulator = 0f;

    public boolean otherworldActive = false;
    public int otherworldTimer = 0;
    public int otherworldDuration = 0;

    public int ultimateCooldown = 0;
    public boolean stage4FreeUltUsed = false;
    public List<UUID> markedPlayers = new ArrayList<>();

    public boolean permanentShield = false;
    public int permanentSpeedBonus = 0;

    public List<String> ghostSkills = new ArrayList<>();
    /** 已解锁的小诡术（随机获取，不放V键循环；主技能冷却时按G自动尝试释放） */
    public final List<String> minorTricks = new ArrayList<>();
    public int nowSelectedSkill = 0;

    /** 诡术冷却 */
    public int veilCooldown = 0;
    public int effigyCooldown = 0;
    public int wailCooldown = 0;
    public int seizeCooldown = 0;

    /** 小诡术冷却 */
    public int parasiteCooldown = 0;
    public int pushCooldown = 0;
    public int echoCooldown = 0;

    /** 寄生追踪：目标UUID → 剩余tick */
    private final Map<UUID, Integer> parasiteTargets = new HashMap<>();

    /** 录制回响状态 */
    private Vec3 echoRecordedPos = null;
    private boolean echoActive = false;
    private int echoReturnTimer = 0;

    /** 替身草人状态（服务端，不同步） */
    private Vec3 effigyPos = null;
    private int effigyTicks = 0;
    private final Set<UUID> effigyTriggered = new HashSet<>();

    /** 浊雨 */
    public boolean turbidRainActive = false;
    public int turbidRainDuration = 0;
    public int turbidRainTimer = 0;
    public int turbidRainUseCount = 0;

    private int otherworldGlowTimer = 0;
    private int blackFogTimer = 0;

    private final Random random = new Random();

    private transient int pendingSyncMask = SYNC_ALL;

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

    @Override
    public void init() {
        this.stage = 1;
        this.totalSanLoss = 0;
        this.sanLossAccumulator = 0f;
        this.otherworldActive = false;
        this.otherworldTimer = 0;
        this.otherworldDuration = 0;
        this.ultimateCooldown = 0;
        this.stage4FreeUltUsed = false;
        this.markedPlayers.clear();
        this.permanentShield = false;
        this.permanentSpeedBonus = 0;
        this.ghostSkills.clear();
        this.ghostSkills.add("veil"); // 阶段1初始诡术
        this.minorTricks.clear();
        this.nowSelectedSkill = 0;
        this.veilCooldown = 0;
        this.effigyCooldown = 0;
        this.wailCooldown = 0;
        this.seizeCooldown = 0;
        this.parasiteCooldown = 0;
        this.pushCooldown = 0;
        this.echoCooldown = 0;
        this.parasiteTargets.clear();
        this.echoRecordedPos = null;
        this.echoActive = false;
        this.echoReturnTimer = 0;
        resetEffigy();
        this.turbidRainActive = false;
        this.turbidRainDuration = 0;
        this.turbidRainTimer = 0;
        this.turbidRainUseCount = 0;
        this.otherworldGlowTimer = 0;
        this.blackFogTimer = 0;
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
            STAGE_2_THRESHOLD = 500;
            STAGE_3_THRESHOLD = 800;
            STAGE_4_THRESHOLD = 1600;
        } else if (playerCount <= 24) {
            STAGE_2_THRESHOLD = 700;
            STAGE_3_THRESHOLD = 1200;
            STAGE_4_THRESHOLD = 1800;
        } else {
            STAGE_2_THRESHOLD = 1000;
            STAGE_3_THRESHOLD = 1500;
            STAGE_4_THRESHOLD = 2400;
        }
    }

    @Override
    public void clear() {
        this.stage = 0;
        this.totalSanLoss = 0;
        this.sanLossAccumulator = 0f;
        this.otherworldActive = false;
        this.otherworldTimer = 0;
        this.otherworldDuration = 0;
        this.ultimateCooldown = 0;
        this.stage4FreeUltUsed = false;
        this.markedPlayers.clear();
        this.permanentShield = false;
        this.permanentSpeedBonus = 0;
        this.ghostSkills.clear();
        this.minorTricks.clear();
        this.nowSelectedSkill = 0;
        this.veilCooldown = 0;
        this.effigyCooldown = 0;
        this.wailCooldown = 0;
        this.seizeCooldown = 0;
        this.parasiteCooldown = 0;
        this.pushCooldown = 0;
        this.echoCooldown = 0;
        this.parasiteTargets.clear();
        this.echoRecordedPos = null;
        this.echoActive = false;
        this.echoReturnTimer = 0;
        resetEffigy();
        this.turbidRainActive = false;
        this.turbidRainDuration = 0;
        this.turbidRainTimer = 0;
        this.turbidRainUseCount = 0;
        this.otherworldGlowTimer = 0;
        this.blackFogTimer = 0;
        if (player instanceof ServerPlayer sp) {
            sp.setInvulnerable(false);
        }
        this.sync();
    }

    private void resetEffigy() {
        this.effigyPos = null;
        this.effigyTicks = 0;
        this.effigyTriggered.clear();
    }

    public boolean isActiveMaChenXu() {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorldComponent == null)
            return false;
        if (!gameWorldComponent.isRole(player, ModRoles.MA_CHEN_XU))
            return false;
        return stage > 0;
    }

    private boolean isKiller(Player target) {
        return SREGameWorldComponent.KEY.get(target.level()).isKillerTeam(target);
    }

    // ==================== 阶段进阶 ====================

    /** 进阶提速倍率：所有恐惧值累计来源都汇入 accrueSanLoss，统一对进阶 +40% 提速 */
    public static final double ADVANCE_SPEED_MULT = 1.4;

    /** 累计恐惧值并检查进阶，不触发同步（高频侵蚀使用） */
    private void accrueSanLoss(int amount) {
        if (amount <= 0)
            return;
        this.totalSanLoss += (int) Math.round(amount * ADVANCE_SPEED_MULT);
        checkStageAdvance();
    }

    /** 低频来源使用：累计 + 立即同步 */
    public void addSanLoss(int amount) {
        if (amount <= 0)
            return;
        accrueSanLoss(amount);
        this.sync(SYNC_CORE | SYNC_SKILLS);
    }

    public void checkStageAdvance() {
        if (stage == 1 && totalSanLoss >= STAGE_2_THRESHOLD) {
            advanceToStage2();
        } else if (stage == 2 && totalSanLoss >= STAGE_3_THRESHOLD) {
            advanceToStage3();
        } else if (stage == 3 && totalSanLoss >= STAGE_4_THRESHOLD) {
            advanceToStage4();
        }
    }

    private void unlockArt(String artId) {
        if (!ghostSkills.contains(artId)) {
            ghostSkills.add(artId);
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.ma_chen_xu.ghost_skill_acquired",
                                Component.translatable("hud.noellesroles.ma_chen_xu.skill." + artId))
                                .withStyle(ChatFormatting.GOLD),
                        true);
            }
        }
    }

    /** 从池中随机获取一个未拥有的小诡术。小诡术存在独立的 minorTricks 列表中，
     *  不影响 V 键的 4 个主技能槽位。 */
    private void unlockRandomMinorTrick() {
        if (minorTricks.size() >= MAX_MINOR_TRICKS)
            return;

        List<String> pool = new ArrayList<>();
        for (String id : MINOR_TRICK_POOL) {
            if (!minorTricks.contains(id))
                pool.add(id);
        }
        if (pool.isEmpty())
            return;

        Collections.shuffle(pool, random);
        String picked = pool.get(0);
        minorTricks.add(picked);

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.ma_chen_xu.minor_trick_acquired",
                            Component.translatable("hud.noellesroles.ma_chen_xu.skill." + picked))
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC),
                    true);
            // 提示使用方式
            serverPlayer.displayClientMessage(
                    Component.translatable("tip.noellesroles.ma_chen_xu.minor_trick_usage")
                            .withStyle(ChatFormatting.GRAY),
                    false);
        }
    }

    private void announceStage(String phaseKey, ChatFormatting... styles) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.ma_chen_xu.stage_advance",
                            Component.translatable(phaseKey)).withStyle(styles),
                    true);
        }
    }

    public void advanceToStage2() {
        this.stage = 2;
        unlockArt("effigy");
        unlockRandomMinorTrick();
        announceStage("hud.noellesroles.ma_chen_xu.phase2", ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 1.0F, 0.8F);
        sync(SYNC_CORE | SYNC_SKILLS);
    }

    public void advanceToStage3() {
        this.stage = 3;
        unlockArt("wail");
        unlockRandomMinorTrick();
        announceStage("hud.noellesroles.ma_chen_xu.phase3", ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.ma_chen_xu.ghost_skill_acquired",
                            Component.translatable("hud.noellesroles.ma_chen_xu.skill.prayer_rain"))
                            .withStyle(ChatFormatting.GOLD),
                    false);
        }
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1.0F, 0.6F);
        sync(SYNC_CORE | SYNC_SKILLS);
    }

    public void advanceToStage4() {
        this.stage = 4;
        unlockArt("seize");
        unlockRandomMinorTrick();
        announceStage("hud.noellesroles.ma_chen_xu.phase4", ChatFormatting.BLACK, ChatFormatting.BOLD);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.0F, 0.4F);
        sync(SYNC_CORE | SYNC_SKILLS);
    }

    // ==================== 恐惧侵蚀 ====================

    private double getFearRange() {
        return (stage >= 1 && stage <= 4) ? STAGE_RANGE[stage] : 0;
    }

    private double getBaseErosion() {
        return (stage >= 1 && stage <= 4) ? STAGE_EROSION[stage] : 0;
    }

    private void processErosion() {
        double range = getFearRange();
        double base = getBaseErosion();
        if (range <= 0 || base <= 0)
            return;

        Level world = player.level();
        Vec3 playerPos = player.position();
        float drainedTotal = 0f;

        for (Player target : world.players()) {
            if (target.equals(player))
                continue;
            if (!GameUtils.isPlayerAliveAndSurvival(target))
                continue;
            if (isKiller(target))
                continue;

            double distance = playerPos.distanceTo(target.position());
            double prox;
            if (otherworldActive) {
                double t = Math.min(1.0, distance / range);
                prox = Math.max(OTHERWORLD_PROX_FLOOR, Math.pow(1.0 - t, 1.5));
            } else {
                if (distance > range)
                    continue;
                double t = distance / range;
                prox = Math.pow(1.0 - t, 1.5);
            }

            double gaze = target.hasLineOfSight(player) ? GAZE_MULT : 1.0;
            float mood = SREPlayerMoodComponent.KEY.get(target).getMood();
            double low = 1.0;
            if (mood < LOW_SAN_THRESHOLD) {
                low = 1.0 + LOW_SAN_MAX_BONUS * ((LOW_SAN_THRESHOLD - mood) / LOW_SAN_THRESHOLD);
            }

            float rate = (float) (base * prox * gaze * low);
            if (rate <= 0f)
                continue;
            SREPlayerMoodComponent.KEY.get(target).addMood(-rate);
            drainedTotal += rate;
        }

        if (drainedTotal > 0f) {
            sanLossAccumulator += drainedTotal * 100f;
            if (sanLossAccumulator >= 1f) {
                int whole = (int) sanLossAccumulator;
                sanLossAccumulator -= whole;
                accrueSanLoss(whole);
            }
        }
    }

    // ==================== 里世界·百鬼夜行 ====================

    private void processOtherworldMechanism() {
        if (!otherworldActive)
            return;

        otherworldTimer++;
        otherworldDuration--;
        otherworldGlowTimer++;
        blackFogTimer++;

        Level world = player.level();

        if (world.getGameTime() % 60 == 0) {
            for (UUID uuid : markedPlayers) {
                Player marked = world.getPlayerByUUID(uuid);
                if (marked != null && GameUtils.isPlayerAliveAndSurvival(marked)) {
                    marked.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0, false, false, true));
                }
            }
        }

        if (otherworldGlowTimer >= OTHERWORLD_GLOW_INTERVAL) {
            otherworldGlowTimer = 0;
            for (Player target : world.players()) {
                if (target.equals(player) || !GameUtils.isPlayerAliveAndSurvival(target))
                    continue;
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, OTHERWORLD_GLOW_DURATION, 0, false, false, true));
                if (target instanceof ServerPlayer targetSp) {
                    targetSp.connection.send(new ClientboundSetTitlesAnimationPacket(5, 30, 10));
                    targetSp.connection.send(new ClientboundSetTitleTextPacket(
                            Component.translatable("message.noellesroles.ma_chen_xu.glow_pulse")
                                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
                    world.playSound(null, target.blockPosition(),
                            SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 0.6F, 0.8F);
                }
            }
        }

        if (world.getGameTime() % 40 == 0) {
            Vec3 playerPos = player.position();
            for (Player target : world.players()) {
                if (target.equals(player) || !GameUtils.isPlayerAliveAndSurvival(target) || isKiller(target))
                    continue;
                if (!(target instanceof ServerPlayer targetSp))
                    continue;
                double distance = playerPos.distanceTo(target.position());
                if (distance > OTHERWORLD_WARN_RANGE)
                    continue;
                if (distance <= 8.0) {
                    targetSp.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
                    targetSp.connection.send(new ClientboundSetTitleTextPacket(
                            Component.translatable("message.noellesroles.ma_chen_xu.proximity_danger")
                                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
                    world.playSound(null, target.blockPosition(),
                            SoundEvents.WARDEN_NEARBY_CLOSEST, SoundSource.HOSTILE, 0.8F, 1.0F);
                } else {
                    targetSp.displayClientMessage(
                            Component.translatable("message.noellesroles.ma_chen_xu.proximity_warning")
                                    .withStyle(ChatFormatting.RED),
                            true);
                }
            }
        }

        if (otherworldTimer <= OTHERWORLD_DESCENT_DURATION && player instanceof ServerPlayer sp
                && world instanceof ServerLevel sl) {
            playOtherworldDescentEffects(sl, sp, otherworldTimer);
        }

        if (blackFogTimer >= BLACK_FOG_PARTICLE_INTERVAL && world instanceof ServerLevel sl) {
            blackFogTimer = 0;
            Vec3 pos = player.position();
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 0.5, pos.z, 10, 1.2, 0.8, 1.2, 0.02);
            sl.sendParticles(ParticleTypes.SCULK_SOUL, pos.x, pos.y + 0.2, pos.z, 3, 1.4, 0.2, 1.4, 0.01);
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + 0.8, pos.z, 4, 1.0, 0.4, 1.0, 0.01);
        }

        if (otherworldDuration <= 0) {
            endOtherworld();
        }
    }

    private void playOtherworldDescentEffects(ServerLevel sl, ServerPlayer sp, int timer) {
        Vec3 pos = sp.position();
        if (timer % 2 == 0) {
            double radius = 4.0 + (12.0 * Math.min(timer, OTHERWORLD_DESCENT_DURATION) / OTHERWORLD_DESCENT_DURATION);
            for (int i = 0; i < 32; i++) {
                double yaw = random.nextDouble() * Math.PI * 2.0;
                double pitch = (random.nextDouble() - 0.5) * Math.PI;
                double px = pos.x + Math.cos(yaw) * Math.cos(pitch) * radius;
                double py = pos.y + 1.2 + Math.sin(pitch) * radius * 0.6;
                double pz = pos.z + Math.sin(yaw) * Math.cos(pitch) * radius;
                sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, 1, 0, 0, 0, 0.01);
            }
        }
        if (timer == 1) {
            sl.playSound(null, sp.blockPosition(), SoundEvents.WARDEN_NEARBY_CLOSE, SoundSource.HOSTILE, 1.3F, 0.55F);
        } else if (timer == 35) {
            sl.playSound(null, sp.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.2F, 0.75F);
        } else if (timer == 70) {
            sl.playSound(null, sp.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0F, 0.9F);
        } else if (timer == OTHERWORLD_DESCENT_DURATION) {
            sl.playSound(null, sp.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.4F, 0.65F);
        }
    }

    public void activateOtherworld(int duration) {
        if (!(player instanceof ServerPlayer sp))
            return;

        this.otherworldActive = true;
        this.otherworldDuration = duration;
        this.otherworldTimer = 0;
        this.otherworldGlowTimer = 0;
        this.blackFogTimer = 0;
        this.markedPlayers.clear();

        sp.setInvulnerable(true);
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, duration, 2, false, false, false));
        sp.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, duration, 0, false, false, false));

        Level world = player.level();

        for (Player target : world.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(target))
                continue;
            if (target instanceof ServerPlayer targetSp) {
                targetSp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false, false));
            }
            if (!target.equals(sp)) {
                target.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, OTHERWORLD_INTRO_FREEZE_DURATION, 0, false, false, true));
                target.addEffect(new MobEffectInstance(ModEffects.USED_BANED, OTHERWORLD_INTRO_FREEZE_DURATION, 0, false, false, true));
                target.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, OTHERWORLD_INTRO_FREEZE_DURATION, 0, false, false, true));
            }
        }

        if (world instanceof ServerLevel sl) {
            Vec3 pos = sp.position();
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + 1.0, pos.z, 40, 3.0, 2.0, 3.0, 0.05);
            sl.sendParticles(ParticleTypes.SCULK_SOUL, pos.x, pos.y + 0.5, pos.z, 25, 4.0, 1.0, 4.0, 0.03);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 0.3, pos.z, 30, 5.0, 1.0, 5.0, 0.02);
            playOtherworldDescentEffects(sl, sp, 1);
        }

        for (Player target : world.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(target) || isKiller(target))
                continue;
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, duration, 2, false, false, true));
            target.addEffect(new MobEffectInstance(ModEffects.OTHERWORLD_AURA, duration, 0, false, false, false));
            target.addEffect(new MobEffectInstance(ModEffects.INFINITE_STAMINA, duration, 5, false, false, false));
            target.addEffect(new MobEffectInstance(ModEffects.LOW_SAN_SHADER_RESISTANCE, duration, 10, false, false, false));
            target.addEffect(new MobEffectInstance(ModEffects.MOOD_DRAIN_REDUCTION, duration, 1, false, false, false));
            if (target instanceof ServerPlayer targetSp) {
                targetSp.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
                targetSp.connection.send(new ClientboundSetTitleTextPacket(
                        Component.translatable("message.noellesroles.ma_chen_xu.otherworld_warning_title")
                                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD, ChatFormatting.OBFUSCATED)));
                targetSp.connection.send(new ClientboundSetSubtitleTextPacket(
                        Component.translatable("message.noellesroles.ma_chen_xu.otherworld_warning_subtitle")
                                .withStyle(ChatFormatting.RED)));
                world.playSound(null, target.blockPosition(),
                        SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.0F, 0.5F);
            }
        }

        sp.connection.send(new ClientboundSetTitlesAnimationPacket(10, 40, 10));
        sp.connection.send(new ClientboundSetTitleTextPacket(
                Component.translatable("message.noellesroles.ma_chen_xu.li_shi_jie_activated")
                        .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)));
        world.playSound(null, sp.blockPosition(), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 1.5F, 0.6F);

        this.sync(SYNC_OTHERWORLD);
    }

    private void endOtherworld() {
        otherworldActive = false;
        otherworldTimer = 0;
        otherworldDuration = 0;
        otherworldGlowTimer = 0;
        blackFogTimer = 0;

        if (!(player instanceof ServerPlayer sp))
            return;

        sp.setInvulnerable(false);
        sp.removeEffect(MobEffects.INVISIBILITY);

        Level world = player.level();

        int markCount = 0;
        for (UUID uuid : markedPlayers) {
            Player markedPlayer = world.getPlayerByUUID(uuid);
            if (markedPlayer != null && GameUtils.isPlayerAliveAndSurvival(markedPlayer)) {
                GameUtils.killPlayer(markedPlayer, true, player, Noellesroles.id("machenxu"));
                markCount++;
            }
        }

        for (Player target : world.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(target) || isKiller(target))
                continue;
            if (!markedPlayers.contains(target.getUUID())) {
                SREPlayerMoodComponent.KEY.get(target).addMood(0.2f);
            }
        }

        SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(sp);

        // 动态倍率：玩家越少收益越高（击杀更难），基准8人 = 1.0x
        long aliveInnocents = world.players().stream()
                .filter(p -> GameUtils.isPlayerAliveAndSurvival(p) && !isKiller(p) && !markedPlayers.contains(p.getUUID()))
                .count();
        double dynamicMultiplier = Math.clamp(8.0 / Math.max(1.0, (double) aliveInnocents), 0.5, 2.0);

        if (markCount >= 3) {
            int reward = (int) Math.round(150 * dynamicMultiplier);
            shopComponent.setBalance(shopComponent.balance + reward);
            permanentSpeedBonus = Math.min(30, permanentSpeedBonus + 10);
            permanentShield = true;
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.ma_chen_xu.ult_reward_3", reward)
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    true);
        } else if (markCount >= 2) {
            int reward = (int) Math.round(100 * dynamicMultiplier);
            shopComponent.setBalance(shopComponent.balance + reward);
            permanentShield = true;
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.ma_chen_xu.ult_reward_2", reward)
                            .withStyle(ChatFormatting.GOLD),
                    true);
        } else if (markCount >= 1) {
            int reward = (int) Math.round(50 * dynamicMultiplier);
            shopComponent.setBalance(shopComponent.balance + reward);
            ultimateCooldown = Math.max(0, ultimateCooldown - 600);
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.ma_chen_xu.ult_reward_1", reward)
                            .withStyle(ChatFormatting.YELLOW),
                    true);
        }
        shopComponent.sync();
        markedPlayers.clear();

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

        if (world instanceof ServerLevel sl) {
            Vec3 pos = sp.position();
            sl.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 1.0, pos.z, 30, 3.0, 2.0, 3.0, 0.05);
            sl.sendParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z, 1, 0, 0, 0, 0);
        }

        this.sync(SYNC_OTHERWORLD | SYNC_CORE | SYNC_SKILLS | SYNC_COOLDOWNS);
    }

    // ==================== 魂噬 / 标记（左键） ====================

    public boolean soulDevour(Player target) {
        if (!(player instanceof ServerPlayer sp))
            return false;
        if (SREGameWorldComponent.KEY.get(player.level()).canUseKillerFeatures(target))
            return false;
        float mood = SREPlayerMoodComponent.KEY.get(target).getMood();
        if (mood > SOUL_DEVOUR_THRESHOLD)
            return false;
        if (!sp.hasLineOfSight(target))
            return false;

        if (player.level() instanceof ServerLevel sl) {
            playSoulDevourExecutionEffects(sl, player, target);
        }
        GameUtils.forceKillPlayer(target, true, player, Noellesroles.id("machenxu"));
        if (target instanceof ServerPlayer targetSp) {
            targetSp.getInventory().clearContent();
        }
        SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(sp);
        shopComponent.setBalance(shopComponent.balance + 50);
        shopComponent.sync();

        double range = getFearRange();
        Vec3 playerPos = player.position();
        for (Player nearby : player.level().players()) {
            if (nearby.equals(player) || nearby.equals(target))
                continue;
            if (!GameUtils.isPlayerAliveAndSurvival(nearby) || isKiller(nearby))
                continue;
            if (playerPos.distanceTo(nearby.position()) <= range) {
                SREPlayerMoodComponent.KEY.get(nearby).addMood(-0.1f);
            }
        }
        return true;
    }

    public boolean markPlayer(Player target) {
        if (!(player instanceof ServerPlayer sp))
            return false;
        if (!otherworldActive)
            return false;
        if (!GameUtils.isPlayerAliveAndSurvival(target) || isKiller(target))
            return false;
        if (markedPlayers.contains(target.getUUID()))
            return false;
        if (!sp.hasLineOfSight(target))
            return false;

        if (player.level() instanceof ServerLevel sl) {
            playSoulDevourExecutionEffects(sl, player, target);
        }
        markedPlayers.add(target.getUUID());

        player.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, HIT_SELF_LOCK_TICKS, 1, false, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.USED_BANED, HIT_SELF_LOCK_TICKS, 1, false, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, HIT_SELF_LOCK_TICKS, 1, false, false, false));

        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, otherworldDuration + 200, 0, false, false, true));
        target.addEffect(new MobEffectInstance(ModEffects.GHOST_CURSE, otherworldDuration + 200, 0, false, false, true));

        if (target instanceof ServerPlayer targetSp) {
            targetSp.connection.send(new ClientboundSetTitlesAnimationPacket(5, 60, 10));
            targetSp.connection.send(new ClientboundSetTitleTextPacket(
                    Component.translatable("message.noellesroles.ma_chen_xu.marked")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
        }
        player.level().playSound(null, target.blockPosition(),
                SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 1.0F, 1.5F);

        this.sync(SYNC_OTHERWORLD);
        return true;
    }

    private void playSoulDevourExecutionEffects(ServerLevel sl, Player killer, Player target) {
        Vec3 tp = target.position();
        Vec3 kp = killer.position();
        for (int ring = 0; ring < 3; ring++) {
            double y = tp.y + 0.35 + ring * 0.65;
            for (int i = 0; i < 20; i++) {
                double angle = (Math.PI * 2.0 * i) / 20.0;
                double px = tp.x + Math.cos(angle) * (2.0 + ring * 0.35);
                double pz = tp.z + Math.sin(angle) * (2.0 + ring * 0.35);
                sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, px, y, pz, 1, 0, 0, 0, 0.01);
            }
        }
        sl.sendParticles(ParticleTypes.FLASH, tp.x, tp.y + 1.0, tp.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.SWEEP_ATTACK, tp.x, tp.y + 0.9, tp.z, 4, 0.8, 0.5, 0.8, 0.0);
        Vec3 dir = tp.subtract(kp).normalize();
        for (int i = 1; i <= 8; i++) {
            Vec3 p = kp.add(dir.scale(i * 0.6)).add(0, 1.0, 0);
            sl.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.02);
        }
        sl.playSound(null, target.blockPosition(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 1.25F, 0.7F);
    }

    // ==================== 大招：里世界 ====================

    public boolean usePrayerRain() {
        if (otherworldActive)
            return false;
        if (stage < 3) {
            player.displayClientMessage(
                    Component.translatable("tip.noellesroles.not_enough_energy").withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (ultimateCooldown > 0) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.ma_chen_xu.prayer_rain_cooldown", ultimateCooldown / 20)
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        if (!(player instanceof ServerPlayer serverPlayer))
            return false;

        boolean isFree = (stage == 4 && !stage4FreeUltUsed);
        SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(serverPlayer);
        if (!isFree) {
            if (shopComponent.balance < ULTIMATE_COST) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.insufficient_funds").withStyle(ChatFormatting.RED),
                        true);
                return false;
            }
            shopComponent.setBalance(shopComponent.balance - ULTIMATE_COST);
            shopComponent.sync();
        } else {
            stage4FreeUltUsed = true;
        }

        int duration = (stage >= 4) ? ULTIMATE_DURATION_STAGE_4 : ULTIMATE_DURATION_STAGE_3;
        activateOtherworld(duration);
        ultimateCooldown = duration * 2;
        sync(SYNC_COOLDOWNS | SYNC_CORE);

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.ma_chen_xu.prayer_rain_activated")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                false);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0F, 0.8F);
        return true;
    }

    // ==================== 商店物品 ====================

    public boolean useTurbidRain() {
        if (!(player instanceof ServerPlayer sp))
            return false;
        if (turbidRainActive)
            return false;

        this.turbidRainActive = true;
        this.turbidRainDuration = TURBID_RAIN_DURATION;
        this.turbidRainTimer = 0;
        this.turbidRainUseCount++;

        sp.serverLevel().setWeatherParameters(0, TURBID_RAIN_DURATION + 20, true, false);
        sp.displayClientMessage(
                Component.translatable("message.noellesroles.ma_chen_xu.turbid_rain_activated")
                        .withStyle(ChatFormatting.DARK_AQUA),
                false);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8F, 1.0F);
        this.sync(SYNC_TURBID);
        return true;
    }

    public boolean useSoulBell() {
        if (!(player instanceof ServerPlayer))
            return false;
        Level world = player.level();
        Vec3 playerPos = player.position();
        boolean hitAny = false;
        for (Player target : world.players()) {
            if (target.equals(player) || !GameUtils.isPlayerAliveAndSurvival(target) || isKiller(target))
                continue;
            if (playerPos.distanceTo(target.position()) > SOUL_BELL_RANGE)
                continue;
            PlayerVolumeComponent.KEY.get(target).setVolume(10 * 20, 5);
            target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, SOUL_BELL_DURATION, 2, false, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SOUL_BELL_DURATION, 0, false, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, SOUL_BELL_DURATION, 0, false, false, true));
            hitAny = true;
        }
        if (hitAny) {
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.5F, 0.5F);
        }
        return hitAny;
    }

    private void processTurbidRain() {
        if (!turbidRainActive)
            return;
        turbidRainDuration--;
        turbidRainTimer++;
        if (turbidRainTimer >= TURBID_RAIN_SAN_INTERVAL) {
            turbidRainTimer = 0;
            double fearRange = getFearRange();
            Vec3 playerPos = player.position();
            for (Player target : player.level().players()) {
                if (target.equals(player) || !GameUtils.isPlayerAliveAndSurvival(target) || isKiller(target))
                    continue;
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
            this.sync(SYNC_TURBID);
        }
    }

    // ==================== 诡术：输入分发 ====================

    /**
     * 诡术按键入口（由各 RoleSkill 定义调用）。
     * Sneak+G → 开里世界大招（任意槽位均可）。
     */
    public boolean onGhostArt(String artId) {
        if (this.player.hasEffect(ModEffects.SKILL_BANED))
            return false;
        if (player.isShiftKeyDown()) {
            return usePrayerRain();
        }
        if (!ghostSkills.contains(artId)) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.ma_chen_xu.skill_locked",
                            Component.translatable("hud.noellesroles.ma_chen_xu.skill." + artId))
                            .withStyle(ChatFormatting.GRAY),
                    true);
            return false;
        }
        // 主技能冷却时自动尝试小诡术
        boolean mainOnCd = switch (artId) {
            case "veil" -> veilCooldown > 0;
            case "effigy" -> effigyCooldown > 0;
            case "wail" -> wailCooldown > 0;
            case "seize" -> seizeCooldown > 0;
            default -> true;
        };
        if (mainOnCd) {
            if (tryUseMinorTrick())
                return true;
            sendCooldownTip(artId, getArtCooldown(artId));
            return false;
        }
        switch (artId) {
            case "veil" -> useVeil();
            case "effigy" -> useEffigy();
            case "wail" -> useWail();
            case "seize" -> useSeize();
            default -> { return false; }
        }
        return true;
    }

    /** 从已解锁的小诡术中随机选一个冷却就绪的释放。返回 true 表示成功释放。 */
    private boolean tryUseMinorTrick() {
        if (minorTricks.isEmpty())
            return false;
        List<String> ready = new ArrayList<>();
        for (String t : minorTricks) {
            int cd = getArtCooldown(t);
            if (cd <= 0)
                ready.add(t);
        }
        if (ready.isEmpty())
            return false;
        Collections.shuffle(ready, random);
        String picked = ready.get(0);
        switch (picked) {
            case "parasite" -> useParasite();
            case "push" -> usePush();
            case "echo" -> useEcho();
            default -> { return false; }
        }
        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(
                    Component.translatable("tip.noellesroles.ma_chen_xu.minor_trick_cast",
                            Component.translatable("hud.noellesroles.ma_chen_xu.skill." + picked))
                            .withStyle(ChatFormatting.LIGHT_PURPLE),
                    true);
        }
        return true;
    }

    // ==================== 鬼遮眼 ====================

    public void useVeil() {
        if (!(player instanceof ServerPlayer sp))
            return;
        if (veilCooldown > 0) {
            sendCooldownTip("veil", veilCooldown);
            return;
        }
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getViewVector(1.0F);
        Vec3 aim = eye.add(look.scale(VEIL_AIM_DISTANCE));

        List<Player> hits = new ArrayList<>();
        for (Player target : sp.level().players()) {
            if (target.equals(sp) || !GameUtils.isPlayerAliveAndSurvival(target) || isKiller(target))
                continue;
            if (target.position().add(0, 1.0, 0).distanceTo(aim) <= VEIL_RADIUS) {
                hits.add(target);
            }
        }
        if (hits.isEmpty()) {
            sp.displayClientMessage(Component.translatable("tip.noellesroles.no_target").withStyle(ChatFormatting.RED), true);
            return;
        }

        for (Player target : hits) {
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, VEIL_DEBUFF_TICKS, 0, false, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, VEIL_DEBUFF_TICKS, 0, false, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, VEIL_DEBUFF_TICKS, 0, false, false, false));
            SREPlayerMoodComponent.KEY.get(target).addMood(-((float) VEIL_SAN_LOSS / 100));
            addSanLoss(VEIL_SAN_LOSS);
            if (target instanceof ServerPlayer targetSp) {
                targetSp.connection.send(new ClientboundSetTitlesAnimationPacket(3, 40, 10));
                targetSp.connection.send(new ClientboundSetTitleTextPacket(
                        Component.translatable("message.noellesroles.ma_chen_xu.veil.hit")
                                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)));
            }
            if (sp.level() instanceof ServerLevel sl) {
                Vec3 hp = target.position();
                sl.sendParticles(ParticleTypes.SQUID_INK, hp.x, hp.y + 1.5, hp.z, 30, 0.4, 0.4, 0.4, 0.02);
                sl.sendParticles(ParticleTypes.SCULK_SOUL, hp.x, hp.y + 1.5, hp.z, 12, 0.3, 0.3, 0.3, 0.01);
            }
        }
        veilCooldown = VEIL_COOLDOWN;
        if (sp.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, aim.x, aim.y, aim.z, 25, 1.0, 1.0, 1.0, 0.03);
        }
        sendActivatedTip("veil", ChatFormatting.DARK_PURPLE);
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.7F, 1.4F);
        sync(SYNC_COOLDOWNS);
    }

    // ==================== 替身草人 ====================

    public void useEffigy() {
        if (!(player instanceof ServerPlayer sp))
            return;
        if (effigyCooldown > 0) {
            sendCooldownTip("effigy", effigyCooldown);
            return;
        }
        effigyPos = sp.position();
        effigyTicks = EFFIGY_LIFETIME;
        effigyTriggered.clear();
        effigyCooldown = EFFIGY_COOLDOWN;
        if (sp.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, effigyPos.x, effigyPos.y + 0.5, effigyPos.z, 20, 0.6, 0.8, 0.6, 0.02);
        }
        sendActivatedTip("effigy", ChatFormatting.DARK_GREEN);
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.8F, 0.7F);
        sync(SYNC_COOLDOWNS);
    }

    private void processEffigy() {
        if (effigyTicks <= 0 || effigyPos == null)
            return;
        effigyTicks--;
        Level world = player.level();

        // 模拟布袋鬼的黑雾，迷惑好人
        if (world instanceof ServerLevel sl && effigyTicks % BLACK_FOG_PARTICLE_INTERVAL == 0) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, effigyPos.x, effigyPos.y + 0.5, effigyPos.z, 6, 0.5, 0.7, 0.5, 0.02);
            sl.sendParticles(ParticleTypes.SCULK_SOUL, effigyPos.x, effigyPos.y + 0.2, effigyPos.z, 2, 0.4, 0.2, 0.4, 0.01);
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, effigyPos.x, effigyPos.y + 0.8, effigyPos.z, 2, 0.4, 0.4, 0.4, 0.01);
        }

        for (Player target : world.players()) {
            if (target.equals(player) || !GameUtils.isPlayerAliveAndSurvival(target) || isKiller(target))
                continue;
            if (effigyTriggered.contains(target.getUUID()))
                continue;
            if (target.position().distanceTo(effigyPos) > EFFIGY_TRIGGER_RANGE)
                continue;
            effigyTriggered.add(target.getUUID());

            SREPlayerMoodComponent.KEY.get(target).addMood(-((float) EFFIGY_SAN_LOSS / 100));
            addSanLoss(EFFIGY_SAN_LOSS);
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, EFFIGY_GLOW_TICKS, 0, false, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60, 0, false, false, true));

            if (target instanceof ServerPlayer targetSp) {
                targetSp.connection.send(new ClientboundSetTitlesAnimationPacket(3, 35, 8));
                targetSp.connection.send(new ClientboundSetTitleTextPacket(
                        Component.translatable("message.noellesroles.ma_chen_xu.effigy.scared")
                                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
            }
            if (player instanceof ServerPlayer self) {
                self.displayClientMessage(
                        Component.translatable("message.noellesroles.ma_chen_xu.effigy.triggered", target.getName())
                                .withStyle(ChatFormatting.GOLD),
                        true);
            }
            if (world instanceof ServerLevel sl) {
                Vec3 tp = target.position();
                sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, tp.x, tp.y + 1.0, tp.z, 20, 0.4, 0.6, 0.4, 0.05);
                sl.playSound(null, target.blockPosition(), SoundEvents.WARDEN_NEARBY_CLOSEST, SoundSource.HOSTILE, 1.0F, 1.3F);
            }
        }

        if (effigyTicks <= 0) {
            resetEffigy();
        }
    }

    /** 寄生：每 tick 递减死亡倒计时，到 0 时处决目标 */
    private void processParasite() {
        if (parasiteTargets.isEmpty())
            return;
        Level world = player.level();
        List<UUID> expired = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : parasiteTargets.entrySet()) {
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                expired.add(entry.getKey());
            } else {
                entry.setValue(remaining);
            }
        }
        for (UUID uuid : expired) {
            parasiteTargets.remove(uuid);
            Player target = world.getPlayerByUUID(uuid);
            if (target != null && GameUtils.isPlayerAliveAndSurvival(target)) {
                if (world instanceof ServerLevel sl) {
                    Vec3 tp = target.position();
                    for (int i = 0; i < 25; i++) {
                        sl.sendParticles(ParticleTypes.SCULK_CHARGE_POP,
                                tp.x + (random.nextDouble() - 0.5) * 2.0,
                                tp.y + 1.0 + random.nextDouble() * 2.0,
                                tp.z + (random.nextDouble() - 0.5) * 2.0,
                                2, 0.1, 0.1, 0.1, 0.04);
                    }
                    sl.playSound(null, target.blockPosition(), SoundEvents.WARDEN_DEATH, SoundSource.HOSTILE, 1.4F, 0.5F);
                }
                GameUtils.forceKillPlayer(target, true, player, Noellesroles.id("machenxu"));
                if (player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(
                            Component.translatable("message.noellesroles.ma_chen_xu.parasite.killed", target.getName())
                                    .withStyle(ChatFormatting.DARK_RED),
                            true);
                }
            }
        }
    }

    /** 录制回响：归位窗口倒计时，超时自动取消 */
    private void processEchoTimer() {
        if (!echoActive)
            return;
        echoReturnTimer--;
        if (echoReturnTimer <= 0) {
            echoActive = false;
            echoCooldown = ECHO_COOLDOWN / 2; // 未归位给予半冷却
            echoRecordedPos = null;
            if (player instanceof ServerPlayer sp) {
                sp.displayClientMessage(
                        Component.translatable("message.noellesroles.ma_chen_xu.echo.expired")
                                .withStyle(ChatFormatting.GRAY),
                        true);
            }
            sync(SYNC_COOLDOWNS);
        }
    }

    // ==================== 怨声 ====================

    public void useWail() {
        if (!(player instanceof ServerPlayer sp))
            return;
        if (wailCooldown > 0) {
            sendCooldownTip("wail", wailCooldown);
            return;
        }
        Vec3 center = sp.position();
        for (Player target : sp.level().players()) {
            if (target.equals(sp) || !GameUtils.isPlayerAliveAndSurvival(target) || isKiller(target))
                continue;
            double dist = center.distanceTo(target.position());
            if (dist > WAIL_RANGE)
                continue;
            double t = dist / WAIL_RANGE;
            int san = (int) Math.round(WAIL_SAN_MIN + (WAIL_SAN_MAX - WAIL_SAN_MIN) * (1.0 - t));
            SREPlayerMoodComponent.KEY.get(target).addMood(-((float) san / 100));
            addSanLoss(san);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, WAIL_SLOW_TICKS, 1, false, false, true));
            if (target instanceof ServerPlayer targetSp) {
                targetSp.displayClientMessage(
                        Component.translatable("message.noellesroles.ma_chen_xu.wail.hit").withStyle(ChatFormatting.DARK_RED),
                        true);
            }
        }
        wailCooldown = WAIL_COOLDOWN;
        if (sp.level() instanceof ServerLevel sl) {
            Vec3 c = sp.position();
            for (int ring = 1; ring <= 3; ring++) {
                double r = ring * 3.0;
                int count = (int) (r * 6);
                for (int i = 0; i < count; i++) {
                    double a = (Math.PI * 2.0 * i) / count;
                    sl.sendParticles(ParticleTypes.SONIC_BOOM, c.x + Math.cos(a) * r, c.y + 1.0, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0.0);
                }
            }
        }
        sendActivatedTip("wail", ChatFormatting.DARK_RED);
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 1.2F, 0.7F);
        sync(SYNC_COOLDOWNS);
    }

    // ==================== 夺魄 ====================

    public void useSeize() {
        if (!(player instanceof ServerPlayer sp))
            return;
        if (seizeCooldown > 0) {
            sendCooldownTip("seize", seizeCooldown);
            return;
        }
        Vec3 eyePos = sp.getEyePosition();
        Vec3 lookVec = sp.getViewVector(1.0F);
        Vec3 endPos = eyePos.add(lookVec.scale(SEIZE_RANGE));
        AABB searchArea = new AABB(eyePos, endPos).inflate(2.0);
        Player target = null;
        double closest = Double.MAX_VALUE;
        for (Player p : sp.level().getEntitiesOfClass(Player.class, searchArea)) {
            if (p.equals(sp) || !GameUtils.isPlayerAliveAndSurvival(p) || isKiller(p))
                continue;
            double d = eyePos.distanceTo(p.getEyePosition());
            if (d < closest) {
                closest = d;
                target = p;
            }
        }
        if (target == null) {
            sp.displayClientMessage(Component.translatable("tip.noellesroles.no_target").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (SREPlayerMoodComponent.KEY.get(target).getMood() > SEIZE_SAN_THRESHOLD) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.ma_chen_xu.seize.too_high").withStyle(ChatFormatting.RED), true);
            return;
        }

        // 拉到面前并定身
        Vec3 flat = new Vec3(lookVec.x, 0, lookVec.z).normalize();
        Vec3 front = sp.position().add(flat.scale(1.6));
        target.teleportTo(front.x, sp.getY(), front.z);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SEIZE_ROOT_TICKS, 255, false, true, true));
        target.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, SEIZE_ROOT_TICKS, 0, false, false, true));
        SREPlayerMoodComponent.KEY.get(target).addMood(-((float) SEIZE_SAN_LOSS / 100));
        addSanLoss(SEIZE_SAN_LOSS);
        seizeCooldown = SEIZE_COOLDOWN;

        if (target instanceof ServerPlayer targetSp) {
            targetSp.connection.send(new ClientboundSetTitlesAnimationPacket(3, 40, 10));
            targetSp.connection.send(new ClientboundSetTitleTextPacket(
                    Component.translatable("message.noellesroles.ma_chen_xu.seize.pulled")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
        }
        if (sp.level() instanceof ServerLevel sl) {
            playSoulDevourExecutionEffects(sl, sp, target);
        }
        sendActivatedTip("seize", ChatFormatting.DARK_RED);
        sp.level().playSound(null, target.blockPosition(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 1.0F, 0.8F);
        sync(SYNC_COOLDOWNS);
    }

    // ==================== 小诡术：寄生 ====================

    /** 寄生：对目标施加死亡倒计时，自身隐身。目标20秒后死亡。 */
    public void useParasite() {
        if (!(player instanceof ServerPlayer sp))
            return;
        if (parasiteCooldown > 0) {
            sendCooldownTip("parasite", parasiteCooldown);
            return;
        }

        Vec3 eyePos = sp.getEyePosition();
        Vec3 lookVec = sp.getViewVector(1.0F);
        Vec3 endPos = eyePos.add(lookVec.scale(PARASITE_RANGE));
        AABB searchArea = new AABB(eyePos, endPos).inflate(1.5);

        Player target = null;
        double closest = Double.MAX_VALUE;
        for (Player p : sp.level().getEntitiesOfClass(Player.class, searchArea)) {
            if (p.equals(sp) || !GameUtils.isPlayerAliveAndSurvival(p) || isKiller(p))
                continue;
            if (parasiteTargets.containsKey(p.getUUID()))
                continue;
            double d = eyePos.distanceTo(p.getEyePosition());
            if (d < closest) {
                closest = d;
                target = p;
            }
        }
        if (target == null) {
            sp.displayClientMessage(Component.translatable("tip.noellesroles.no_target").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!sp.hasLineOfSight(target)) {
            sp.displayClientMessage(Component.translatable("tip.noellesroles.no_target").withStyle(ChatFormatting.RED), true);
            return;
        }

        parasiteTargets.put(target.getUUID(), PARASITE_DEATH_DELAY);
        sp.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, PARASITE_INVIS_DURATION, 0, false, false, false));
        SREPlayerMoodComponent.KEY.get(target).addMood(-((float) PARASITE_SAN_LOSS / 100));
        addSanLoss(PARASITE_SAN_LOSS);
        parasiteCooldown = PARASITE_COOLDOWN;

        if (target instanceof ServerPlayer targetSp) {
            targetSp.connection.send(new ClientboundSetTitlesAnimationPacket(5, 60, 10));
            targetSp.connection.send(new ClientboundSetTitleTextPacket(
                    Component.translatable("message.noellesroles.ma_chen_xu.parasite.infected")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
        }
        if (sp.level() instanceof ServerLevel sl) {
            Vec3 tp = target.position();
            for (int i = 0; i < 20; i++) {
                sl.sendParticles(ParticleTypes.SCULK_CHARGE_POP,
                        tp.x + (random.nextDouble() - 0.5) * 2.0,
                        tp.y + 1.0 + random.nextDouble() * 1.5,
                        tp.z + (random.nextDouble() - 0.5) * 2.0,
                        2, 0.1, 0.1, 0.1, 0.02);
            }
            sl.playSound(null, target.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.0F, 0.6F);
        }
        sendActivatedTip("parasite", ChatFormatting.DARK_RED);
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.6F, 1.8F);
        sync(SYNC_COOLDOWNS);
    }

    // ==================== 小诡术：推走 ====================

    /** 推走：将周围非杀手玩家推飞。 */
    public void usePush() {
        if (!(player instanceof ServerPlayer sp))
            return;
        if (pushCooldown > 0) {
            sendCooldownTip("push", pushCooldown);
            return;
        }

        Vec3 selfPos = sp.position();
        boolean hitAny = false;

        for (Player target : sp.level().players()) {
            if (target.equals(sp) || !GameUtils.isPlayerAliveAndSurvival(target) || isKiller(target))
                continue;
            double dist = selfPos.distanceTo(target.position());
            if (dist > PUSH_RANGE)
                continue;

            Vec3 dir = target.position().subtract(selfPos);
            double hDist = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
            if (hDist < 0.01)
                hDist = 0.01;
            Vec3 knockback = new Vec3(dir.x / hDist * PUSH_FORCE, 0.6, dir.z / hDist * PUSH_FORCE);
            target.setDeltaMovement(target.getDeltaMovement().add(knockback));
            target.hurtMarked = true;

            if (target instanceof ServerPlayer targetSp) {
                targetSp.displayClientMessage(
                        Component.translatable("message.noellesroles.ma_chen_xu.push.pushed")
                                .withStyle(ChatFormatting.DARK_PURPLE),
                        true);
            }
            hitAny = true;
        }

        if (!hitAny) {
            sp.displayClientMessage(Component.translatable("tip.noellesroles.no_target").withStyle(ChatFormatting.RED), true);
            return;
        }

        pushCooldown = PUSH_COOLDOWN;

        if (sp.level() instanceof ServerLevel sl) {
            for (int ring = 0; ring < 3; ring++) {
                double r = 1.5 + ring * 1.8;
                int count = (int) (r * 8);
                for (int i = 0; i < count; i++) {
                    double a = (Math.PI * 2.0 * i) / count;
                    sl.sendParticles(ParticleTypes.POOF,
                            selfPos.x + Math.cos(a) * r,
                            selfPos.y + 1.0,
                            selfPos.z + Math.sin(a) * r,
                            2, 0.2, 0.3, 0.2, 0.05);
                }
            }
        }
        sendActivatedTip("push", ChatFormatting.DARK_PURPLE);
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.8F, 1.6F);
        sync(SYNC_COOLDOWNS);
    }

    // ==================== 小诡术：录制回响 ====================

    /** 录制回响：首次释放录制位置并前瞬移，再次释放返回原位。 */
    public void useEcho() {
        if (!(player instanceof ServerPlayer sp))
            return;

        if (!echoActive) {
            // 首次释放：录制 → 前瞬移
            if (echoCooldown > 0) {
                sendCooldownTip("echo", echoCooldown);
                return;
            }

            echoRecordedPos = sp.position();
            Vec3 lookVec = sp.getViewVector(1.0F);
            Vec3 forward = sp.position().add(lookVec.x * ECHO_TELEPORT_DISTANCE, 0, lookVec.z * ECHO_TELEPORT_DISTANCE);

            // 墙体检测：沿路径找可落脚点
            Level world = sp.level();
            Vec3 safePos = forward;
            for (int step = (int) ECHO_TELEPORT_DISTANCE; step >= 2; step--) {
                Vec3 probe = sp.position().add(lookVec.x * step, 0, lookVec.z * step);
                // 找地面
                int floorY = (int) probe.y;
                while (floorY > world.getMinBuildHeight() && world.getBlockState(
                        new BlockPos((int) probe.x, floorY, (int) probe.z)).isAir()) {
                    floorY--;
                }
                if (floorY > world.getMinBuildHeight()) {
                    safePos = new Vec3(probe.x, floorY + 1.0, probe.z);
                    break;
                }
            }

            sp.teleportTo(safePos.x, safePos.y, safePos.z);
            echoActive = true;
            echoReturnTimer = ECHO_RETURN_WINDOW;

            if (sp.level() instanceof ServerLevel sl) {
                Vec3 pos = sp.position();
                sl.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y + 1.0, pos.z, 30, 0.6, 0.8, 0.6, 0.03);
                sl.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 0.5, pos.z, 8, 0.3, 0.4, 0.3, 0.02);
            }
            sendActivatedTip("echo", ChatFormatting.AQUA);
            sp.level().playSound(null, sp.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
            sync(SYNC_COOLDOWNS);
        } else {
            // 再次释放：返回原位
            Vec3 returnPos = echoRecordedPos;
            echoActive = false;
            echoReturnTimer = 0;
            echoCooldown = ECHO_COOLDOWN;

            sp.teleportTo(returnPos.x, returnPos.y, returnPos.z);

            if (sp.level() instanceof ServerLevel sl) {
                Vec3 pos = sp.position();
                sl.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y + 1.0, pos.z, 30, 0.6, 0.8, 0.6, 0.03);
                sl.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 0.5, pos.z, 8, 0.3, 0.4, 0.3, 0.02);
                // 返回时对周围好人造成 SAN 损失
                for (Player target : sl.players()) {
                    if (target.equals(sp) || !GameUtils.isPlayerAliveAndSurvival(target) || isKiller(target))
                        continue;
                    if (pos.distanceTo(target.position()) <= 6.0) {
                        SREPlayerMoodComponent.KEY.get(target).addMood(-((float) ECHO_SAN_LOSS / 100));
                        addSanLoss(ECHO_SAN_LOSS);
                    }
                }
            }
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.ma_chen_xu.echo.returned")
                            .withStyle(ChatFormatting.AQUA),
                    true);
            sp.level().playSound(null, sp.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.6F);
            echoRecordedPos = null;
            sync(SYNC_COOLDOWNS);
        }
    }

    // ==================== 提示工具 ====================

    private void sendActivatedTip(String artId, ChatFormatting color) {
        player.displayClientMessage(
                Component.translatable("tip.noellesroles.activated.with_name",
                        Component.translatable("hud.noellesroles.ma_chen_xu.skill." + artId)).withStyle(color),
                true);
    }

    private void sendCooldownTip(String artId, int cooldown) {
        player.displayClientMessage(
                Component.translatable("message.noellesroles.ma_chen_xu.cooldown",
                        Component.translatable("hud.noellesroles.ma_chen_xu.skill." + artId), cooldown / 20)
                        .withStyle(ChatFormatting.RED),
                true);
    }

    // ==================== HUD 辅助 ====================

    public String getSelectedArtId() {
        int slot = SREAbilityPlayerComponent.KEY.get(player).getSelectedSkill();
        if (slot < 0 || slot >= ART_ORDER.length)
            slot = 0;
        return ART_ORDER[slot];
    }

    public int getArtCooldown(String artId) {
        return switch (artId) {
            case "veil" -> veilCooldown;
            case "effigy" -> effigyCooldown;
            case "wail" -> wailCooldown;
            case "seize" -> seizeCooldown;
            case "parasite" -> parasiteCooldown;
            case "push" -> pushCooldown;
            case "echo" -> echoCooldown;
            default -> 0;
        };
    }

    /** 当前选中诡术名（HUD 始终显示） */
    public Component getSelectedArtName() {
        return Component.translatable("hud.noellesroles.ma_chen_xu.skill." + getSelectedArtId());
    }

    /** 选中诡术状态文本（就绪也带名字） */
    public Component getNowCooldownText() {
        String artId = getSelectedArtId();
        Component name = Component.translatable("hud.noellesroles.ma_chen_xu.skill." + artId);
        if (!ghostSkills.contains(artId)) {
            return Component.translatable("message.noellesroles.ma_chen_xu.skill_locked", name).withStyle(ChatFormatting.GRAY);
        }
        int cd = getArtCooldown(artId);
        if (cd <= 0) {
            return Component.translatable("message.noellesroles.ma_chen_xu.ready_named",
                    name, NoellesrolesClient.abilityBind.getTranslatedKeyMessage()).withStyle(ChatFormatting.GREEN);
        }
        return Component.translatable("message.noellesroles.ma_chen_xu.cooldown", name, cd / 20)
                .withStyle(ChatFormatting.YELLOW);
    }

    // ==================== 同步 ====================

    public void sync(int mask) {
        this.pendingSyncMask = mask;
        ModComponents.MA_CHEN_XU.sync(this.player);
        this.pendingSyncMask = SYNC_ALL;
    }

    public void sync() {
        sync(SYNC_ALL);
    }

    // ==================== Tick ====================

    @Override
    public void serverTick() {
        if (!isActiveMaChenXu())
            return;
        if (!GameUtils.isPlayerAliveAndSurvival(player))
            return;
        if (!(player instanceof ServerPlayer))
            return;
        if (this.player.hasEffect(ModEffects.SKILL_BANED))
            return;

        if (ultimateCooldown > 0)
            ultimateCooldown--;

        processErosion();
        processOtherworldMechanism();
        processTurbidRain();
        processEffigy();
        processParasite();
        processEchoTimer();

        if (veilCooldown > 0)
            veilCooldown--;
        if (effigyCooldown > 0)
            effigyCooldown--;
        if (wailCooldown > 0)
            wailCooldown--;
        if (seizeCooldown > 0)
            seizeCooldown--;
        if (parasiteCooldown > 0)
            parasiteCooldown--;
        if (pushCooldown > 0)
            pushCooldown--;
        if (echoCooldown > 0)
            echoCooldown--;

        if (stage >= 2) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 3, 0, false, false, false));
        }
        if (permanentSpeedBonus >= 10 && player.level().getGameTime() % 20 == 0) {
            int amplifier = Math.max(0, (permanentSpeedBonus / 10) - 1);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 25, amplifier, false, false, false));
        }

        // 周期性同步累计恐惧值 / 冷却，刷新 HUD
        if (player.level().getGameTime() % 20 == 0) {
            sync(SYNC_CORE | SYNC_COOLDOWNS);
        }
    }

    @Override
    public void clientTick() {
        if (this.player.hasEffect(ModEffects.SKILL_BANED))
            return;
        if (otherworldActive && otherworldDuration > 1)
            otherworldDuration--;
        if (ultimateCooldown > 1)
            ultimateCooldown--;
        if (veilCooldown > 1)
            veilCooldown--;
        if (effigyCooldown > 1)
            effigyCooldown--;
        if (wailCooldown > 1)
            wailCooldown--;
        if (seizeCooldown > 1)
            seizeCooldown--;
        if (parasiteCooldown > 1)
            parasiteCooldown--;
        if (pushCooldown > 1)
            pushCooldown--;
        if (echoCooldown > 1)
            echoCooldown--;
        if (turbidRainActive && turbidRainDuration > 1)
            turbidRainDuration--;
    }

    // ==================== NBT 同步序列化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (this.stage <= 0)
            return;
        if (SREGameWorldComponent.KEY.get(this.player.level()).getGameStatus() != GameStatus.ACTIVE) {
            return;
        }
        int mask = this.pendingSyncMask;
        tag.putInt("_mask", mask);

        if ((mask & SYNC_CORE) != 0) {
            tag.putInt("STAGE_2_THRESHOLD", this.STAGE_2_THRESHOLD);
            tag.putInt("STAGE_3_THRESHOLD", this.STAGE_3_THRESHOLD);
            tag.putInt("STAGE_4_THRESHOLD", this.STAGE_4_THRESHOLD);
            tag.putInt("stage", this.stage);
            tag.putInt("totalSanLoss", this.totalSanLoss);
            tag.putBoolean("stage4FreeUltUsed", this.stage4FreeUltUsed);
            tag.putBoolean("permanentShield", this.permanentShield);
            tag.putInt("permanentSpeedBonus", this.permanentSpeedBonus);
        }
        if ((mask & SYNC_SKILLS) != 0) {
            tag.putInt("nowSelectedSkill", this.nowSelectedSkill);
            CompoundTag skillsTag = new CompoundTag();
            for (int i = 0; i < ghostSkills.size(); i++) {
                skillsTag.putString("skill_" + i, ghostSkills.get(i));
            }
            skillsTag.putInt("size", ghostSkills.size());
            tag.put("ghostSkills", skillsTag);
            // 小诡术同步
            CompoundTag minorTag = new CompoundTag();
            for (int i = 0; i < minorTricks.size(); i++) {
                minorTag.putString("mt_" + i, minorTricks.get(i));
            }
            minorTag.putInt("size", minorTricks.size());
            tag.put("minorTricks", minorTag);
        }
        if ((mask & SYNC_COOLDOWNS) != 0) {
            tag.putInt("ultimateCooldown", this.ultimateCooldown);
            tag.putInt("veilCooldown", this.veilCooldown);
            tag.putInt("effigyCooldown", this.effigyCooldown);
            tag.putInt("wailCooldown", this.wailCooldown);
            tag.putInt("seizeCooldown", this.seizeCooldown);
            tag.putInt("parasiteCooldown", this.parasiteCooldown);
            tag.putInt("pushCooldown", this.pushCooldown);
            tag.putInt("echoCooldown", this.echoCooldown);
            tag.putBoolean("echoActive", this.echoActive);
            tag.putInt("echoReturnTimer", this.echoReturnTimer);
        }
        if ((mask & SYNC_OTHERWORLD) != 0) {
            tag.putBoolean("otherworldActive", this.otherworldActive);
            tag.putInt("otherworldTimer", this.otherworldTimer);
            tag.putInt("otherworldDuration", this.otherworldDuration);
            CompoundTag markedTag = new CompoundTag();
            for (int i = 0; i < markedPlayers.size(); i++) {
                markedTag.putUUID("player_" + i, markedPlayers.get(i));
            }
            markedTag.putInt("size", markedPlayers.size());
            tag.put("markedPlayers", markedTag);
        }
        if ((mask & SYNC_TURBID) != 0) {
            tag.putBoolean("turbidRainActive", this.turbidRainActive);
            tag.putInt("turbidRainDuration", this.turbidRainDuration);
            tag.putInt("turbidRainUseCount", this.turbidRainUseCount);
        }
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        int mask = tag.contains("_mask") ? tag.getInt("_mask") : SYNC_ALL;

        if ((mask & SYNC_CORE) != 0) {
            this.STAGE_2_THRESHOLD = tag.contains("STAGE_2_THRESHOLD") ? tag.getInt("STAGE_2_THRESHOLD") : 100000;
            this.STAGE_3_THRESHOLD = tag.contains("STAGE_3_THRESHOLD") ? tag.getInt("STAGE_3_THRESHOLD") : 100000;
            this.STAGE_4_THRESHOLD = tag.contains("STAGE_4_THRESHOLD") ? tag.getInt("STAGE_4_THRESHOLD") : 100000;
            this.stage = tag.contains("stage") ? tag.getInt("stage") : 1;
            this.totalSanLoss = tag.contains("totalSanLoss") ? tag.getInt("totalSanLoss") : 0;
            this.stage4FreeUltUsed = tag.contains("stage4FreeUltUsed") && tag.getBoolean("stage4FreeUltUsed");
            this.permanentShield = tag.contains("permanentShield") && tag.getBoolean("permanentShield");
            this.permanentSpeedBonus = tag.contains("permanentSpeedBonus") ? tag.getInt("permanentSpeedBonus") : 0;
        }
        if ((mask & SYNC_SKILLS) != 0) {
            this.nowSelectedSkill = tag.contains("nowSelectedSkill") ? tag.getInt("nowSelectedSkill") : 0;
            this.ghostSkills.clear();
            if (tag.contains("ghostSkills")) {
                CompoundTag skillsTag = tag.getCompound("ghostSkills");
                int size = skillsTag.getInt("size");
                for (int i = 0; i < size; i++) {
                    String skill = skillsTag.getString("skill_" + i);
                    if (!skill.isEmpty())
                        this.ghostSkills.add(skill);
                }
            }
            this.minorTricks.clear();
            if (tag.contains("minorTricks")) {
                CompoundTag minorTag = tag.getCompound("minorTricks");
                int size = minorTag.getInt("size");
                for (int i = 0; i < size; i++) {
                    String mt = minorTag.getString("mt_" + i);
                    if (!mt.isEmpty())
                        this.minorTricks.add(mt);
                }
            }
        }
        if ((mask & SYNC_COOLDOWNS) != 0) {
            this.ultimateCooldown = tag.contains("ultimateCooldown") ? tag.getInt("ultimateCooldown") : 0;
            this.veilCooldown = tag.contains("veilCooldown") ? tag.getInt("veilCooldown") : 0;
            this.effigyCooldown = tag.contains("effigyCooldown") ? tag.getInt("effigyCooldown") : 0;
            this.wailCooldown = tag.contains("wailCooldown") ? tag.getInt("wailCooldown") : 0;
            this.seizeCooldown = tag.contains("seizeCooldown") ? tag.getInt("seizeCooldown") : 0;
            this.parasiteCooldown = tag.contains("parasiteCooldown") ? tag.getInt("parasiteCooldown") : 0;
            this.pushCooldown = tag.contains("pushCooldown") ? tag.getInt("pushCooldown") : 0;
            this.echoCooldown = tag.contains("echoCooldown") ? tag.getInt("echoCooldown") : 0;
            this.echoActive = tag.contains("echoActive") && tag.getBoolean("echoActive");
            this.echoReturnTimer = tag.contains("echoReturnTimer") ? tag.getInt("echoReturnTimer") : 0;
        }
        if ((mask & SYNC_OTHERWORLD) != 0) {
            this.otherworldActive = tag.contains("otherworldActive") && tag.getBoolean("otherworldActive");
            this.otherworldTimer = tag.contains("otherworldTimer") ? tag.getInt("otherworldTimer") : 0;
            this.otherworldDuration = tag.contains("otherworldDuration") ? tag.getInt("otherworldDuration") : 0;
            this.markedPlayers.clear();
            if (tag.contains("markedPlayers")) {
                CompoundTag markedTag = tag.getCompound("markedPlayers");
                int size = markedTag.getInt("size");
                for (int i = 0; i < size; i++) {
                    if (markedTag.contains("player_" + i))
                        this.markedPlayers.add(markedTag.getUUID("player_" + i));
                }
            }
        }
        if ((mask & SYNC_TURBID) != 0) {
            this.turbidRainActive = tag.contains("turbidRainActive") && tag.getBoolean("turbidRainActive");
            this.turbidRainDuration = tag.contains("turbidRainDuration") ? tag.getInt("turbidRainDuration") : 0;
            this.turbidRainUseCount = tag.contains("turbidRainUseCount") ? tag.getInt("turbidRainUseCount") : 0;
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
