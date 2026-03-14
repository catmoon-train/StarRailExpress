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
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.entity.KuiXiPuppetEntity;
import org.agmas.noellesroles.init.ModEntities;

/**
 * 马晨絮组件
 *
 * 管理马晨絮的四段成长机制：
 * - 阶段1（初级鬼）：基础恐惧机制
 * - 阶段2（中级鬼）：增强恐惧+移速+鬼术
 * - 阶段3（高级鬼）：更强恐惧+鬼术+祈雨大招
 * - 阶段4（极致鬼）：最强形态+护盾
 */
public class MaChenXuPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<MaChenXuPlayerComponent> KEY = ModComponents.MA_CHEN_XU;

    // ==================== 常量定义 ====================

    /** 恐惧范围（格） */
    public static final double FEAR_RANGE_STAGE_1 = 10.0;
    public static final double FEAR_RANGE_STAGE_2 = 10.0;
    public static final double FEAR_RANGE_STAGE_3 = 15.0;

    /** 恐惧SAN掉落间隔（tick） */
    public static final int FEAR_INTERVAL = 200; // 10秒

    /** 恐惧SAN掉落量 */
    public static final int FEAR_SAN_LOSS_STAGE_1 = 5;
    public static final int FEAR_SAN_LOSS_STAGE_2 = 5;
    public static final int FEAR_SAN_LOSS_STAGE_3 = 7;

    /** 里世界SAN掉落间隔（tick） */
    public static final int OTHERWORLD_INTERVAL = 100; // 5秒

    /** 里世界SAN掉落量 */
    public static final int OTHERWORLD_SAN_LOSS = 5;

    /** 魂噬减少里世界时间（tick） */
    public static final int SOUL_DEVOUR_REDUCTION = 200; // 10秒

    /** 祈雨持续时间（tick） */
    public static final int PRAYER_RAIN_DURATION = 600; // 30秒

    /** 祈雨费用 */
    public static final int PRAYER_RAIN_COST = 250;

    /** 下雨[狂热]持续时间（tick） */
    public static final int FRENZY_RAIN_DURATION = 400; // 20秒

    /** 下雨[狂热]费用 */
    public static final int FRENZY_RAIN_COST = 250;

    /** 下雨[狂热]冷却时间（tick） */
    public static final int FRENZY_RAIN_COOLDOWN = 3000; // 150秒

    /** 鬼术冷却时间 */
    public static final int GHOST_SKILL_COOLDOWN_SWIFT_WIND = 400; // 20秒
    public static final int GHOST_SKILL_COOLDOWN_SPIRIT_WALK = 700; // 35秒
    public static final int GHOST_SKILL_COOLDOWN_PUPPET_SHOW = 1200; // 60秒

    /** 新增鬼术冷却时间 */
    public static final int GHOST_SKILL_COOLDOWN_INSTANT_SILENCE = 600; // 30秒
    public static final int GHOST_SKILL_COOLDOWN_BLINK = 400; // 20秒
    public static final int GHOST_SKILL_COOLDOWN_PARASITE = 1800; // 90秒

    // ==================== 状态变量 ====================
    private final Player player;

    /** 当前阶段（1、2、3、4） */
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

    /** 祈雨是否激活 */
    public boolean prayerRainActive = false;

    /** 祈雨剩余时间 */
    public int prayerRainDuration = 0;

    /** 下雨[狂热]是否激活 */
    public boolean frenzyRainActive = false;

    /** 下雨[狂热]剩余时间 */
    public int frenzyRainDuration = 0;

    /** 下雨[狂热]冷却时间 */
    public int frenzyRainCooldown = 0;

    /** 护盾是否激活（阶段4） */
    public boolean shieldActive = false;

    /** 已获得的鬼术列表 */
    public List<String> ghostSkills = new ArrayList<>();

    /** 鬼术冷却时间 */
    public int swiftWindCooldown = 0;
    public int spiritWalkCooldown = 0;
    public int puppetShowCooldown = 0;

    /** 新增鬼术冷却时间 */
    public int instantSilenceCooldown = 0;
    public int blinkCooldown = 0;
    public int parasiteCooldown = 0;

    /** 掠风技能状态 */
    public boolean swiftWindActive = false;
    public int swiftWindDuration = 0;
    public int swiftWindChargeTime = 0;

    /** 傩面游魂技能状态 */
    public boolean spiritWalkActive = false;
    public int spiritWalkDuration = 0;

    /** 傀戏技能状态 */
    public boolean puppetShowActive = false;
    public int puppetShowDuration = 0;

    /** 伪摹技能是否已使用 */
    public boolean falseMimicryUsed = false;

    public int nowSelectedSkill = 0;
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
     * 重置组件状态
     */
    @Override
    public void reset() {
        this.nowSelectedSkill = 0;
        this.stage = 1;
        this.totalSanLoss = 0;
        this.fearTimer = 0;
        this.otherworldActive = false;
        this.otherworldTimer = 0;
        this.otherworldDuration = 0;
        this.prayerRainActive = false;
        this.prayerRainDuration = 0;
        this.frenzyRainActive = false;
        this.frenzyRainDuration = 0;
        this.frenzyRainCooldown = 0;
        this.shieldActive = false;
        this.ghostSkills.clear();
        this.swiftWindCooldown = 0;
        this.spiritWalkCooldown = 0;
        this.puppetShowCooldown = 0;
        this.instantSilenceCooldown = 0;
        this.blinkCooldown = 0;
        this.parasiteCooldown = 0;
        this.swiftWindActive = false;
        this.swiftWindDuration = 0;
        this.swiftWindChargeTime = 0;
        this.spiritWalkActive = false;
        this.spiritWalkDuration = 0;
        this.puppetShowActive = false;
        this.puppetShowDuration = 0;
        this.falseMimicryUsed = false;

        // 给予初始金币80
        if (player instanceof ServerPlayer serverPlayer) {
            SREPlayerShopComponent playerShopComponent = SREPlayerShopComponent.KEY.get(serverPlayer);
            playerShopComponent.setBalance(playerShopComponent.balance + 80);
            playerShopComponent.sync();
        }

        this.sync();
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
        this.otherworldActive = false;
        this.otherworldTimer = 0;
        this.otherworldDuration = 0;
        this.prayerRainActive = false;
        this.prayerRainDuration = 0;
        this.frenzyRainActive = false;
        this.frenzyRainDuration = 0;
        this.frenzyRainCooldown = 0;
        this.shieldActive = false;
        this.ghostSkills.clear();
        this.swiftWindCooldown = 0;
        this.spiritWalkCooldown = 0;
        this.puppetShowCooldown = 0;
        this.instantSilenceCooldown = 0;
        this.blinkCooldown = 0;
        this.parasiteCooldown = 0;
        this.swiftWindActive = false;
        this.swiftWindDuration = 0;
        this.swiftWindChargeTime = 0;
        this.spiritWalkActive = false;
        this.spiritWalkDuration = 0;
        this.puppetShowActive = false;
        this.puppetShowDuration = 0;
        this.falseMimicryUsed = false;
        this.sync();
    }

    /**
     * 检查是否是活跃的马晨絮
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
     * 增加SAN掉落并检查阶段进阶
     */
    public void addSanLoss(int amount) {
        this.totalSanLoss += amount;
        checkStageAdvance();
        this.sync();
    }

    /**
     * 检查阶段进阶
     */
    public void checkStageAdvance() {
        int oldStage = stage;

        if (stage == 1 && totalSanLoss >= 50) {
            advanceToStage2();
        } else if (stage == 2 && totalSanLoss >= 120) {
            advanceToStage3();
        } else if (stage == 3 && totalSanLoss >= 200) {
            advanceToStage4();
        }

        if (stage != oldStage) {
            this.sync();
        }
    }

    /**
     * 进入阶段2（中级鬼）
     */
    public void advanceToStage2() {
        this.stage = 2;

        // 随机获得一个鬼术
        addRandomGhostSkill();

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.ma_chen_xu.stage_advance",
                            Component.translatable("hud.noellesroles.ma_chen_xu.phase2"))
                            .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD),
                    false);

            // 播放音效
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 1.0F, 0.8F);
        }
    }

    /**
     * 进入阶段3（高级鬼）
     */
    public void advanceToStage3() {
        this.stage = 3;

        // 随机获得一个鬼术
        addRandomGhostSkill();

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.ma_chen_xu.stage_advance",
                            Component.translatable("hud.noellesroles.ma_chen_xu.phase3"))
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                    false);

            // 播放音效
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1.0F, 0.6F);
        }
    }

    /**
     * 进入阶段4（极致鬼）
     */
    public void advanceToStage4() {
        this.stage = 4;

        // 随机获得一个鬼术
        addRandomGhostSkill();

        // 获得护盾
        this.shieldActive = true;

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component
                            .translatable("message.noellesroles.ma_chen_xu.stage_advance",
                                    Component.translatable("hud.noellesroles.ma_chen_xu.phase4"))
                            .withStyle(ChatFormatting.BLACK, ChatFormatting.BOLD),
                    false);

            // 播放音效
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.0F, 0.4F);
        }
    }

    /**
     * 随机添加鬼术
     */
    private void addRandomGhostSkill() {
        List<String> availableSkills = new ArrayList<>();

        if (!ghostSkills.contains("swift_wind")) {
            availableSkills.add("swift_wind");
        }
        if (!ghostSkills.contains("spirit_walk")) {
            availableSkills.add("spirit_walk");
        }
        if (!ghostSkills.contains("puppet_show")) {
            availableSkills.add("puppet_show");
        }
        if (!ghostSkills.contains("instant_silence")) {
            availableSkills.add("instant_silence");
        }
        if (!ghostSkills.contains("blink")) {
            availableSkills.add("blink");
        }
        if (!ghostSkills.contains("parasite")) {
            availableSkills.add("parasite");
        }
        if (!ghostSkills.contains("false_mimicry")) {
            availableSkills.add("false_mimicry");
        }

        if (!availableSkills.isEmpty()) {
            String skill = availableSkills.get(random.nextInt(availableSkills.size()));
            ghostSkills.add(skill);

            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.ma_chen_xu.ghost_skill_acquired", skill)
                                .withStyle(ChatFormatting.GOLD),
                        true);
            }
        }
    }

    /**
     * 激活里世界
     */
    public void activateOtherworld(int duration) {
        this.otherworldActive = true;
        this.otherworldDuration = duration;
        this.otherworldTimer = 0;

        // 给所有好人添加失明效果
        Level world = player.level();
        for (Player target : world.players()) {
            if (GameUtils.isPlayerAliveAndSurvival(target) && !isKiller(target)) {
                target.addEffect(new MobEffectInstance(
                        MobEffects.BLINDNESS, duration, 0, false, false, false));
            }
        }

        this.sync();
    }

    /**
     * 检查玩家是否是杀手
     */
    private boolean isKiller(Player target) {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(target.level());
        return gameWorldComponent.isKillerTeam(target);
    }

    /**
     * 魂噬击杀
     */
    public void soulDevour(Player target) {
        if (!(player instanceof ServerPlayer))
            return;

        // 检查目标SAN值是否 <= 10
        // 这里需要实现SAN值检查逻辑

        // 击杀目标
        GameUtils.killPlayer(target, true, player, Noellesroles.id("machenxu"));

        // 减少里世界时间
        if (otherworldActive) {
            otherworldDuration = Math.max(0, otherworldDuration - SOUL_DEVOUR_REDUCTION);
        }

        // 播放音效
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.WITHER_DEATH, SoundSource.PLAYERS, 1.0F, 1.2F);

        this.sync();
    }

    /**
     * 使用祈雨大招
     */
    public void usePrayerRain() {
        if (stage < 3)
            return;
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        // 检查金币
        SREPlayerShopComponent playerShopComponent = SREPlayerShopComponent.KEY.get(serverPlayer);
        if (playerShopComponent.balance < PRAYER_RAIN_COST) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.insufficient_money")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 扣除金币
        playerShopComponent.setBalance(playerShopComponent.balance - PRAYER_RAIN_COST);
        playerShopComponent.sync();

        // 激活里世界30秒
        activateOtherworld(PRAYER_RAIN_DURATION);

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.ma_chen_xu.prayer_rain_activated")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                false);

        // 播放音效
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    /**
     * 使用下雨[狂热]技能
     * 使用后地图进入小雨状态，全图好人SAN掉落速度翻倍，获得一层护盾，持续20秒，鬼术冷却刷新，CD 150秒
     */
    public void useFrenzyRain() {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        // 检查冷却时间
        if (frenzyRainCooldown > 0) {
            serverPlayer.displayClientMessage(
                    Component.translatable("gui.noellesroles.ma_chen_xu.frenzy_rain_cooldown", frenzyRainCooldown / 20)
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 检查金币
        SREPlayerShopComponent playerShopComponent = SREPlayerShopComponent.KEY.get(serverPlayer);
        if (playerShopComponent.balance < FRENZY_RAIN_COST) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.insufficient_money")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 扣除金币
        playerShopComponent.setBalance(playerShopComponent.balance - FRENZY_RAIN_COST);
        playerShopComponent.sync();

        // 激活下雨[狂热]
        this.frenzyRainActive = true;
        this.frenzyRainDuration = FRENZY_RAIN_DURATION;

        // 设置冷却时间
        this.frenzyRainCooldown = FRENZY_RAIN_COOLDOWN;

        // 获得一层护盾
        this.shieldActive = true;

        // 刷新所有鬼术冷却
        this.swiftWindCooldown = 0;
        this.spiritWalkCooldown = 0;
        this.puppetShowCooldown = 0;

        // 激活里世界效果（SAN掉落翻倍）
        activateOtherworld(FRENZY_RAIN_DURATION);

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.ma_chen_xu.frenzy_rain_activated")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                false);

        // 播放音效
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0F, 1.2F);

        // 给玩家添加速度效果（狂热状态）
        player.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SPEED, FRENZY_RAIN_DURATION, 1, false, false, true));

        this.sync();
    }

    /**
     * 处理恐惧机制
     */
    private void processFearMechanism() {
        if (!isActiveMaChenXu())
            return;

        fearTimer++;
        if (fearTimer >= FEAR_INTERVAL) {
            fearTimer = 0;

            double range = switch (stage) {
                case 1 -> FEAR_RANGE_STAGE_1;
                case 2 -> FEAR_RANGE_STAGE_2;
                case 3, 4 -> FEAR_RANGE_STAGE_3;
                default -> 0;
            };

            int sanLoss = switch (stage) {
                case 1 -> FEAR_SAN_LOSS_STAGE_1;
                case 2 -> FEAR_SAN_LOSS_STAGE_2;
                case 3, 4 -> FEAR_SAN_LOSS_STAGE_3;
                default -> 0;
            };

            if (range > 0 && sanLoss > 0) {
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
                    if (distance <= range) {
                        // 这里需要实现SAN值减少逻辑
                        SREPlayerMoodComponent.KEY.get(target).addMood(sanLoss / 100);
                        addSanLoss(sanLoss);
                    }
                }
            }
        }
    }

    /**
     * 处理里世界机制
     */
    private void processOtherworldMechanism() {
        if (otherworldActive) {
            otherworldTimer++;
            otherworldDuration--;

            // 每5秒对所有好人造成SAN掉落
            if (otherworldTimer >= OTHERWORLD_INTERVAL) {
                otherworldTimer = 0;

                Level world = player.level();
                for (Player target : world.players()) {
                    if (!GameUtils.isPlayerAliveAndSurvival(target))
                        continue;
                    if (isKiller(target))
                        continue;

                    // 这里需要实现SAN值减少逻辑
                    addSanLoss(OTHERWORLD_SAN_LOSS);
                }
            }

            // 检查里世界是否结束
            if (otherworldDuration <= 0) {
                otherworldActive = false;
                otherworldTimer = 0;
                otherworldDuration = 0;

                // 移除所有好人的失明效果
                Level world = player.level();
                for (Player target : world.players()) {
                    if (GameUtils.isPlayerAliveAndSurvival(target) && !isKiller(target)) {
                        target.removeEffect(MobEffects.BLINDNESS);
                    }
                }

                this.sync();
            }
        }
    }

    /**
     * 同步到客户端
     */
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
        if (this.spiritWalkActive) {
            if (this.player.level().getGameTime() % 30 == 0) {
                this.player.addEffect(new MobEffectInstance(
                        MobEffects.INVISIBILITY,
                        2 * 20, // 持续时间 60s（tick）
                        1, // 等级（0 = 速度 I）
                        true, // ambient（环境效果，如信标）
                        false, // showParticles（显示粒子）
                        true // showIcon（显示图标）
                ));
            }
        }
        if (this.player.isSprinting()) {
            this.chargeSwiftWind();
        }
        // 处理恐惧机制
        processFearMechanism();

        // 处理里世界机制
        processOtherworldMechanism();

        // 处理下雨[狂热]机制
        if (frenzyRainActive) {
            frenzyRainDuration--;
            if (frenzyRainDuration <= 0) {
                frenzyRainActive = false;
                this.sync();
            }
            // 处理下雨[狂热]冷却
            if (frenzyRainCooldown > 0) {
                frenzyRainCooldown--;
                if (frenzyRainCooldown % 200 == 0)
                    this.sync();
            }

        }

        // 处理鬼术冷却
        if (swiftWindCooldown > 0) {
            swiftWindCooldown--;
        }
        if (spiritWalkCooldown > 0) {
            spiritWalkCooldown--;
        }

        // 处理新增鬼术冷却
        if (instantSilenceCooldown > 0) {
            instantSilenceCooldown--;
        }
        if (blinkCooldown > 0) {
            blinkCooldown--;
        }
        if (parasiteCooldown > 0) {
            parasiteCooldown--;
        }
        if (puppetShowCooldown > 0) {
            puppetShowCooldown--;
        }

        // 处理鬼术效果
        if (swiftWindActive) {
            swiftWindDuration--;
            if (swiftWindDuration <= 0) {
                swiftWindActive = false;
                // 移除速度效果
                player.removeEffect(MobEffects.MOVEMENT_SPEED);
            }
        }

        if (spiritWalkActive) {
            spiritWalkDuration--;
            if (spiritWalkDuration <= 0) {
                spiritWalkActive = false;
                // 移除隐身效果
                player.removeEffect(MobEffects.INVISIBILITY);
            }
        }

        if (puppetShowActive) {
            puppetShowDuration--;
            if (puppetShowDuration <= 0) {
                puppetShowActive = false;
                // 移除傀儡
            }
        }

        // 阶段2和4的移速加成
        if (stage == 2 || stage == 4) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SPEED, 3, 0, false, false, false));
        }
    }

    @Override
    public void clientTick() {
        // 客户端tick处理
        if (frenzyRainCooldown > 1) {
            frenzyRainCooldown--;
        }
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (this.stage <= 1) // 神秘优化
            return;
        tag.putInt("stage", this.stage);
        tag.putInt("totalSanLoss", this.totalSanLoss);
        tag.putInt("fearTimer", this.fearTimer);
        tag.putBoolean("otherworldActive", this.otherworldActive);
        tag.putInt("otherworldTimer", this.otherworldTimer);
        tag.putInt("otherworldDuration", this.otherworldDuration);
        tag.putBoolean("prayerRainActive", this.prayerRainActive);
        tag.putInt("prayerRainDuration", this.prayerRainDuration);
        tag.putBoolean("frenzyRainActive", this.frenzyRainActive);
        tag.putInt("frenzyRainDuration", this.frenzyRainDuration);
        tag.putInt("instantSilenceCooldown", this.instantSilenceCooldown);
        tag.putInt("blinkCooldown", this.blinkCooldown);
        tag.putInt("parasiteCooldown", this.parasiteCooldown);
        tag.putInt("frenzyRainCooldown", this.frenzyRainCooldown);
        tag.putBoolean("shieldActive", this.shieldActive);

        // 保存鬼术列表
        CompoundTag skillsTag = new CompoundTag();
        for (int i = 0; i < ghostSkills.size(); i++) {
            skillsTag.putString("skill_" + i, ghostSkills.get(i));
        }
        skillsTag.putInt("size", ghostSkills.size());
        tag.put("ghostSkills", skillsTag);

        tag.putInt("swiftWindCooldown", this.swiftWindCooldown);
        tag.putInt("spiritWalkCooldown", this.spiritWalkCooldown);
        tag.putInt("puppetShowCooldown", this.puppetShowCooldown);
        tag.putBoolean("swiftWindActive", this.swiftWindActive);
        tag.putInt("swiftWindDuration", this.swiftWindDuration);
        tag.putInt("swiftWindChargeTime", this.swiftWindChargeTime);
        tag.putBoolean("spiritWalkActive", this.spiritWalkActive);
        tag.putInt("spiritWalkDuration", this.spiritWalkDuration);
        tag.putBoolean("puppetShowActive", this.puppetShowActive);
        tag.putInt("puppetShowDuration", this.puppetShowDuration);
        tag.putBoolean("falseMimicryUsed", this.falseMimicryUsed);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.stage = tag.contains("stage") ? tag.getInt("stage") : 1;
        this.totalSanLoss = tag.contains("totalSanLoss") ? tag.getInt("totalSanLoss") : 0;
        this.fearTimer = tag.contains("fearTimer") ? tag.getInt("fearTimer") : 0;
        this.otherworldActive = tag.contains("otherworldActive") && tag.getBoolean("otherworldActive");
        this.otherworldTimer = tag.contains("otherworldTimer") ? tag.getInt("otherworldTimer") : 0;
        this.otherworldDuration = tag.contains("otherworldDuration") ? tag.getInt("otherworldDuration") : 0;
        this.prayerRainActive = tag.contains("prayerRainActive") && tag.getBoolean("prayerRainActive");
        this.prayerRainDuration = tag.contains("prayerRainDuration") ? tag.getInt("prayerRainDuration") : 0;
        this.frenzyRainActive = tag.contains("frenzyRainActive") && tag.getBoolean("frenzyRainActive");
        this.frenzyRainDuration = tag.contains("frenzyRainDuration") ? tag.getInt("frenzyRainDuration") : 0;
        this.frenzyRainCooldown = tag.contains("frenzyRainCooldown") ? tag.getInt("frenzyRainCooldown") : 0;
        this.shieldActive = tag.contains("shieldActive") && tag.getBoolean("shieldActive");

        // 读取鬼术列表
        this.ghostSkills.clear();
        this.instantSilenceCooldown = tag.contains("instantSilenceCooldown") ? tag.getInt("instantSilenceCooldown") : 0;
        this.blinkCooldown = tag.contains("blinkCooldown") ? tag.getInt("blinkCooldown") : 0;
        this.parasiteCooldown = tag.contains("parasiteCooldown") ? tag.getInt("parasiteCooldown") : 0;
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

        this.swiftWindCooldown = tag.contains("swiftWindCooldown") ? tag.getInt("swiftWindCooldown") : 0;
        this.spiritWalkCooldown = tag.contains("spiritWalkCooldown") ? tag.getInt("spiritWalkCooldown") : 0;
        this.puppetShowCooldown = tag.contains("puppetShowCooldown") ? tag.getInt("puppetShowCooldown") : 0;
        this.swiftWindActive = tag.contains("swiftWindActive") && tag.getBoolean("swiftWindActive");
        this.swiftWindDuration = tag.contains("swiftWindDuration") ? tag.getInt("swiftWindDuration") : 0;
        this.swiftWindChargeTime = tag.contains("swiftWindChargeTime") ? tag.getInt("swiftWindChargeTime") : 0;
        this.spiritWalkActive = tag.contains("spiritWalkActive") && tag.getBoolean("spiritWalkActive");
        this.spiritWalkDuration = tag.contains("spiritWalkDuration") ? tag.getInt("spiritWalkDuration") : 0;
        this.puppetShowActive = tag.contains("puppetShowActive") && tag.getBoolean("puppetShowActive");
        this.puppetShowDuration = tag.contains("puppetShowDuration") ? tag.getInt("puppetShowDuration") : 0;
        this.falseMimicryUsed = tag.contains("falseMimicryUsed") && tag.getBoolean("falseMimicryUsed");
    }

    // ==================== 鬼术使用方法 ====================

    /**
     * 使用掠风鬼术
     * 奔跑15秒积攒风能，使用后移速大幅提升50%，持续10秒
     */
    public void useSwiftWind() {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;
        if (swiftWindCooldown > 0) {
            serverPlayer.displayClientMessage(
                    Component
                            .translatable("message.noellesroles.ma_chen_xu.swift_wind.cooldown", swiftWindCooldown / 20)
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 检查风能是否充足（需要奔跑15秒 = 300 tick）
        if (swiftWindChargeTime < 300) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.ma_chen_xu.swift_wind.not_enough_energy")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 消耗风能
        swiftWindChargeTime = 0;
        swiftWindCooldown = GHOST_SKILL_COOLDOWN_SWIFT_WIND;

        // 给予移速效果
        MobEffectInstance speedEffect = new MobEffectInstance(
                MobEffects.MOVEMENT_SPEED,
                200, // 10秒
                1, // 50%加成 (等级2)
                false,
                false,
                true);
        serverPlayer.addEffect(speedEffect);

        swiftWindActive = true;
        swiftWindDuration = 200;

        // 播放音效
        serverPlayer.level().playSound(null, serverPlayer.blockPosition(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
                1.0F, 1.2F);

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.ma_chen_xu.swift_wind.activated")
                        .withStyle(ChatFormatting.AQUA),
                true);

        sync();
    }

    /**
     * 使用傩面游魂鬼术
     * 进入灵界6秒，灵界中无法攻击、无法被看见、无碰撞
     */
    public void useSpiritWalk() {
        if (!(player instanceof ServerPlayer))
            return;

        if (spiritWalkActive) {
            // 提前退出灵界
            exitSpiritWalk();
        } else {
            // 进入灵界
            enterSpiritWalk();
        }
    }

    private void enterSpiritWalk() {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;
        if (spiritWalkCooldown > 0) {
            serverPlayer.displayClientMessage(
                    Component
                            .translatable("message.noellesroles.ma_chen_xu.spirit_walk.cooldown",
                                    spiritWalkCooldown / 20)
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        spiritWalkActive = true;
        spiritWalkDuration = 20 * 6; // 6秒
        spiritWalkCooldown = GHOST_SKILL_COOLDOWN_SPIRIT_WALK;

        // 给予隐身效果
        MobEffectInstance invisibilityEffect = new MobEffectInstance(
                MobEffects.INVISIBILITY,
                140, // 7秒（比技能时间稍长）
                0,
                false,
                false,
                false);
        serverPlayer.addEffect(invisibilityEffect);

        // 设置无敌状态
        serverPlayer.setInvulnerable(true);

        // 播放音效
        serverPlayer.level().playSound(null, serverPlayer.blockPosition(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                0.8F, 0.6F);

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.ma_chen_xu.spirit_walk.enter")
                        .withStyle(ChatFormatting.LIGHT_PURPLE),
                true);

        sync();
    }

    private void exitSpiritWalk() {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        spiritWalkActive = false;
        spiritWalkDuration = 0;

        // 移除无敌状态
        serverPlayer.setInvulnerable(false);

        // 播放音效
        serverPlayer.level().playSound(null, serverPlayer.blockPosition(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                0.8F, 1.4F);

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.ma_chen_xu.spirit_walk.exit")
                        .withStyle(ChatFormatting.GRAY),
                true);
        sync();
    }

    /**
     * 使用傀戏鬼术
     * 召唤一个完全相同的傀儡（外观、手持物品一致）
     */
    public void usePuppetShow() {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;
        if (puppetShowCooldown > 0) {
            serverPlayer.displayClientMessage(
                    Component
                            .translatable("message.noellesroles.ma_chen_xu.puppet_show.cooldown",
                                    puppetShowCooldown / 20)
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 在玩家附近生成傀儡
        Vec3 playerPos = serverPlayer.position();
        KuiXiPuppetEntity puppet = new KuiXiPuppetEntity(ModEntities.KUIXI_PUPPET, serverPlayer.level());
        puppet.setPos(playerPos.x + 2, playerPos.y, playerPos.z);
        puppet.setOwner(serverPlayer);

        // 生成傀儡
        serverPlayer.level().addFreshEntity(puppet);

        puppetShowCooldown = GHOST_SKILL_COOLDOWN_PUPPET_SHOW;
        puppetShowActive = true;
        puppetShowDuration = 400; // 20秒

        // 播放音效
        serverPlayer.level().playSound(null, serverPlayer.blockPosition(),
                SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.PLAYERS,
                1.0F, 0.8F);

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.ma_chen_xu.puppet_show.summoned")
                        .withStyle(ChatFormatting.DARK_PURPLE),
                true);

        sync();
    }

    /**
     * 使用伪摹鬼术
     * 一局仅限1次，复制当前手持的任意可消耗/手持物品
     */
    public void useFalseMimicry() {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        if (falseMimicryUsed) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.ma_chen_xu.false_mimicry.already_used")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        ItemStack heldItem = serverPlayer.getMainHandItem();
        if (heldItem.isEmpty()) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.ma_chen_xu.false_mimicry.no_item")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 复制物品
        ItemStack copiedItem = heldItem.copy();

        // 给予复制的物品
        if (!serverPlayer.getInventory().add(copiedItem)) {
            // 背包满了，掉落到地上
            serverPlayer.drop(copiedItem, false);
        }

        falseMimicryUsed = true;

        // 播放音效
        serverPlayer.level().playSound(null, serverPlayer.blockPosition(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS,
                1.0F, 1.5F);

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.ma_chen_xu.false_mimicry.copied",
                        copiedItem.getDisplayName())
                        .withStyle(ChatFormatting.GOLD),
                true);
        sync();
    }

    /**
     * 处理掠风充能（玩家奔跑时调用）
     */
    public void chargeSwiftWind() {
        if (ghostSkills.contains("swift_wind") && swiftWindChargeTime < 300) {
            swiftWindChargeTime++;
            if (swiftWindChargeTime >= 300 && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.ma_chen_xu.swift_wind.charged")
                                .withStyle(ChatFormatting.GREEN),
                        true);
            }
        }
    }

    // ==================== 新增鬼术使用方法 ====================

    /**
     * 使用瞬寂鬼术
     * 使瞄准指定目标定立在原地3秒，CD：30秒
     */
    public void useInstantSilence() {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;
        if (instantSilenceCooldown > 0) {
            serverPlayer.displayClientMessage(
                    Component
                            .translatable("message.noellesroles.ma_chen_xu.instant_silence.cooldown",
                                    instantSilenceCooldown / 20)
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 获取玩家视线方向的目标
        HitResult hitResult = serverPlayer.pick(10.0, 1.0F, false);
        Player target = null;

        if (hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof Player) {
            target = (Player) entityHit.getEntity();
        } else {
            // 如果没有直接瞄准，寻找视线方向最近的玩家
            Vec3 eyePos = serverPlayer.getEyePosition();
            Vec3 lookVec = serverPlayer.getViewVector(1.0F);
            Vec3 endPos = eyePos.add(lookVec.scale(10.0));

            AABB searchArea = new AABB(eyePos, endPos).inflate(2.0);
            List<Player> nearbyPlayers = serverPlayer.level().getEntitiesOfClass(Player.class, searchArea);

            double closestDistance = Double.MAX_VALUE;
            for (Player nearbyPlayer : nearbyPlayers) {
                if (nearbyPlayer.equals(serverPlayer) || !GameUtils.isPlayerAliveAndSurvival(nearbyPlayer))
                    continue;

                double distance = eyePos.distanceTo(nearbyPlayer.getEyePosition());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    target = nearbyPlayer;
                }
            }
        }

        if (target == null || !GameUtils.isPlayerAliveAndSurvival(target)) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.ma_chen_xu.instant_silence.no_target")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 设置冷却时间
        instantSilenceCooldown = GHOST_SKILL_COOLDOWN_INSTANT_SILENCE;

        // 给目标添加缓慢效果（定身3秒）
        target.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                60, // 3秒
                255, // 最高等级，完全无法移动
                false,
                true,
                true));

        // 同时添加挖掘疲劳防止攻击
        target.addEffect(new MobEffectInstance(
                MobEffects.DIG_SLOWDOWN,
                60, // 3秒
                255,
                false,
                false,
                true));

        // 播放音效
        serverPlayer.level().playSound(null, target.blockPosition(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS,
                1.0F, 0.5F);

        // 生成粒子效果
        if (serverPlayer.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SOUL,
                    target.getX(), target.getY() + 1.0, target.getZ(),
                    20, 0.5, 0.5, 0.5, 0.02);
        }

        serverPlayer.displayClientMessage(
                Component
                        .translatable("message.noellesroles.ma_chen_xu.instant_silence.activated",
                                target.getDisplayName())
                        .withStyle(ChatFormatting.DARK_PURPLE),
                true);

        sync();
    }

    /**
     * 使用闪现鬼术
     * 向鼠标指向的方向飞速前进，获得五秒钟缓慢效果，CD：30秒
     */
    public void useBlink() {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;
        if (blinkCooldown > 0) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.ma_chen_xu.blink.cooldown", blinkCooldown / 20)
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 设置冷却时间
        blinkCooldown = GHOST_SKILL_COOLDOWN_BLINK;

        // 获取玩家视线方向
        Vec3 lookVec = serverPlayer.getViewVector(1.0F);

        // 向视线方向飞速前进（传送约10格距离）
        double blinkDistance = 10.0;
        Vec3 currentPos = serverPlayer.position();
        Vec3 targetPos = currentPos.add(lookVec.scale(blinkDistance));

        // 播放起始位置音效和粒子
        if (serverPlayer.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    currentPos.x, currentPos.y + 1.0, currentPos.z,
                    30, 0.3, 0.5, 0.3, 0.05);
        }

        // 传送玩家
        serverPlayer.teleportTo(targetPos.x, targetPos.y, targetPos.z);

        // 播放目标位置音效和粒子
        serverPlayer.level().playSound(null, serverPlayer.blockPosition(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                1.0F, 1.5F);

        if (serverPlayer.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    targetPos.x, targetPos.y + 1.0, targetPos.z,
                    30, 0.3, 0.5, 0.3, 0.05);
        }

        // 给自己添加缓慢效果5秒
        serverPlayer.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                100, // 5秒
                1, // 缓慢II
                false,
                true,
                true));

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.ma_chen_xu.blink.activated")
                        .withStyle(ChatFormatting.DARK_AQUA),
                true);

        sync();
    }

    /**
     * 使用寄生鬼术
     * 瞄准目标，让对方中毒，中毒一分钟后死去，CD：90秒
     * 参考 ChlorineBombEntity 的中毒实现
     */
    public void useParasite() {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;
        if (parasiteCooldown > 0) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.ma_chen_xu.parasite.cooldown", parasiteCooldown / 20)
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 获取玩家视线方向的目标
        HitResult hitResult = serverPlayer.pick(10.0, 1.0F, false);
        Player target = null;

        if (hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof Player) {
            target = (Player) entityHit.getEntity();
        } else {
            // 如果没有直接瞄准，寻找视线方向最近的玩家
            Vec3 eyePos = serverPlayer.getEyePosition();
            Vec3 lookVec = serverPlayer.getViewVector(1.0F);
            Vec3 endPos = eyePos.add(lookVec.scale(10.0));

            AABB searchArea = new AABB(eyePos, endPos).inflate(2.0);
            List<Player> nearbyPlayers = serverPlayer.level().getEntitiesOfClass(Player.class, searchArea);

            double closestDistance = Double.MAX_VALUE;
            for (Player nearbyPlayer : nearbyPlayers) {
                if (nearbyPlayer.equals(serverPlayer) || !GameUtils.isPlayerAliveAndSurvival(nearbyPlayer))
                    continue;

                double distance = eyePos.distanceTo(nearbyPlayer.getEyePosition());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    target = nearbyPlayer;
                }
            }
        }

        if (target == null || !GameUtils.isPlayerAliveAndSurvival(target)) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.ma_chen_xu.parasite.no_target")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 设置冷却时间
        parasiteCooldown = GHOST_SKILL_COOLDOWN_PARASITE;

        // 参考 ChlorineBombEntity 的中毒实现，使用 SREPlayerPoisonComponent
        // 设置中毒时间为60秒（1200 ticks），60秒后死亡
        SREPlayerPoisonComponent poisonComponent = SREPlayerPoisonComponent.KEY.get(target);
        poisonComponent.setPoisonTicks(1200, serverPlayer.getUUID());
        poisonComponent.sync();

        // 播放音效
        serverPlayer.level().playSound(null, target.blockPosition(),
                SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS,
                0.8F, 1.2F);

        // 生成寄生粒子效果
        if (serverPlayer.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                    target.getX(), target.getY() + 1.0, target.getZ(),
                    30, 0.5, 0.8, 0.5, 0.02);
        }

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.ma_chen_xu.parasite.activated", target.getDisplayName())
                        .withStyle(ChatFormatting.DARK_GREEN),
                true);

        // 给目标一个提示
        if (target instanceof ServerPlayer targetPlayer) {
            targetPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.ma_chen_xu.parasite.infected")
                            .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.ITALIC),
                    false);
        }

        sync();
    }

    public void changeSkill() {
        this.nowSelectedSkill++;
        if (this.nowSelectedSkill >= this.ghostSkills.size()) {
            this.nowSelectedSkill = 0;
        }
    }

    public void tryActiveAbility() {
        if (player.isShiftKeyDown()) {
            // 切换技能
            this.changeSkill();
        } else {
            // 触发技能
            if (ghostSkills.isEmpty())
                return;
            if (this.nowSelectedSkill >= ghostSkills.size()) {
                return;
            }
            var skillId = ghostSkills.get(this.nowSelectedSkill);
            switch (skillId) {
                case "swift_wind":
                    // 掠风
                    useSwiftWind();
                    break;
                case "spirit_walk":
                    useSpiritWalk();
                    break;
                case "puppet_show":
                    usePuppetShow();
                    break;
                case "false_mimicry":
                    useFalseMimicry();
                    break;
                default:
                    break;
            }
        }
    }
}