package org.agmas.noellesroles.role;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.OnPlayerKilledPlayer;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.GameInitializeEvent;
import org.agmas.harpymodloader.events.ModifierAssigned;
import org.agmas.harpymodloader.events.ModifierRemoved;
import org.agmas.harpymodloader.events.ResetPlayerEvent;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.packet.BroadcastMessageS2CPacket;
import org.agmas.noellesroles.utils.RoleUtils;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 叛徒职业和所有新修饰符的注册与处理
 */
public class TraitorAndModifiers {

    // ==================== 叛徒职业 ID ====================
    public static final ResourceLocation TRAITOR_ID = Noellesroles.id("traitor");

    // ==================== 叛徒职业定义 ====================
    public static SRERole TRAITOR = TMMRoles.registerRole(new SRERole(
            TRAITOR_ID,
            new Color(139, 0, 0).getRGB(), // 深红色 - 代表背叛
            false, // isInnocent = false, 非平民阵营
            true,  // canUseKiller = true, 有杀手能力
            SRERole.MoodType.FAKE, // 假心情
            Integer.MAX_VALUE, // 无限体力
            true  // 隐藏计分板
    ) {
        @Override
        public List<io.wifi.starrailexpress.util.ShopEntry> getShopEntries() {
            return new ArrayList<>();
        }
    }).setOccupiedRoleCount(0).setCanBeRandomedByOtherRoles(false);

    // ==================== 修饰符定义 ====================
    
    // 鬼祟 - 当距离8格内有玩家时，杀手无法透视看到你
    public static SREModifier SNEAKY = HMLModifiers.registerModifier(new SREModifier(
            Noellesroles.id("sneaky"),
            new Color(75, 0, 130).getRGB(), // 深紫色
            null, null, false, true));
    
    // 黄油手 - 手枪冷却随机变化
    public static SREModifier BUTTER_FINGERS = HMLModifiers.registerModifier(new SREModifier(
            Noellesroles.id("butter_fingers"),
            new Color(255, 215, 0).getRGB(), // 金色
            null, null, false, false));
    
    // 强壮 - 35%击退抗性
    private static final AttributeModifier STRONG_KNOCKBACK_RESIST_MODIFIER = new AttributeModifier(
            Noellesroles.id("strong_knockback_resist_modifier"), 
            0.35, AttributeModifier.Operation.ADD_VALUE);
    public static SREModifier STRONG = HMLModifiers.registerModifier(new SREModifier(
            Noellesroles.id("strong"),
            new Color(139, 69, 19).getRGB(), // 棕色
            null, null, false, false));
    
    // 夜猫子 - 免疫黑暗效果
    public static SREModifier NIGHT_OWL = HMLModifiers.registerModifier(new SREModifier(
            Noellesroles.id("night_owl"),
            new Color(25, 25, 112).getRGB(), // 暗蓝色
            null, null, false, true));
    
    // 慷慨 - 每1.5分钟给予最近玩家25金币
    public static SREModifier GENEROUS = HMLModifiers.registerModifier(new SREModifier(
            Noellesroles.id("generous"),
            new Color(255, 182, 193).getRGB(), // 粉色
            null, null, false, false));
    
    // 勇敢 - 关灯时恢复50%理智
    public static SREModifier BRAVE = HMLModifiers.registerModifier(new SREModifier(
            Noellesroles.id("brave"),
            new Color(255, 165, 0).getRGB(), // 橙色
            null, null, false, true));
    
    // 工作狂 - 任务刷新快50%
    public static SREModifier WORKAHOLIC = HMLModifiers.registerModifier(new SREModifier(
            Noellesroles.id("workaholic"),
            new Color(128, 128, 128).getRGB(), // 灰色
            null, null, false, false));
    
    // 大胃王 - 每1.5分钟获得苹果，吃食物任务恢复75% san和25金币
    public static SREModifier BIG_EATER = HMLModifiers.registerModifier(new SREModifier(
            Noellesroles.id("big_eater"),
            new Color(255, 99, 71).getRGB(), // 番茄红
            null, null, false, true));
    
    // 狂躁症 - 任务乱码，无法完成，附近完成任务恢复san和金币
    public static SREModifier MANIC = HMLModifiers.registerModifier(new SREModifier(
            Noellesroles.id("manic"),
            new Color(220, 20, 60).getRGB(), // 深红色
            null, null, false, true));
    
    // 回光返照 - 被击杀时获得3秒特殊效果后死亡
    public static SREModifier LAST_GASP = HMLModifiers.registerModifier(new SREModifier(
            Noellesroles.id("last_gasp"),
            new Color(192, 192, 192).getRGB(), // 银色
            null, null, false, false));
    
    // 起义军 - 被同阵营误杀时变为叛徒
    public static SREModifier REBEL = HMLModifiers.registerModifier(new SREModifier(
            Noellesroles.id("rebel"),
            new Color(0, 100, 0).getRGB(), // 暗绿色
            null, null, false, true));
    
    // 晕血症 - 看到死亡获得缓慢和反胃
    public static SREModifier HEMOPHOBIA = HMLModifiers.registerModifier(new SREModifier(
            Noellesroles.id("hemophobia"),
            new Color(139, 0, 0).getRGB(), // 暗红色
            null, null, false, true));
    
    // 敛财 - 死后扣除击杀者40%金币
    public static SREModifier MONEY_GRUBBER = HMLModifiers.registerModifier(new SREModifier(
            Noellesroles.id("money_grubber"),
            new Color(184, 134, 11).getRGB(), // 暗金色
            null, null, false, false));
    
    // 素食主义者 - 肉类获得负面效果，其他食物获得正面效果
    public static SREModifier VEGETARIAN = HMLModifiers.registerModifier(new SREModifier(
            Noellesroles.id("vegetarian"),
            new Color(34, 139, 34).getRGB(), // 森林绿
            null, null, false, false));
    
    // 侏儒 - 尺寸缩小33%（固定缩放为0.67）
    public static final AttributeModifier DWARF_MODIFIER = new AttributeModifier(
            Noellesroles.id("dwarf_modifier"), 
            -0.33, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    public static SREModifier DWARF = HMLModifiers.registerModifier(new SREModifier(
            Noellesroles.id("dwarf"),
            new Color(205, 133, 63).getRGB(), // 秘鲁色
            null, null, false, false));
    
    // 绝境信徒 - 唯一杀手时获得金币和药水效果
    public static SREModifier DESPERATE_FAITH = HMLModifiers.registerModifier(new SREModifier(
            Noellesroles.id("desperate_faith"),
            new Color(128, 0, 128).getRGB(), // 紫色
            null, null, true, false));
    
    // 吝啬 - 商店购买返还20%金币
    public static SREModifier STINGY = HMLModifiers.registerModifier(new SREModifier(
            Noellesroles.id("stingy"),
            new Color(85, 107, 47).getRGB(), // 暗橄榄绿
            null, null, false, false));
    
    // 腐化 - 死亡后尸体直接变骷髅
    public static SREModifier CORRUPTED = HMLModifiers.registerModifier(new SREModifier(
            Noellesroles.id("corrupted"),
            new Color(47, 79, 79).getRGB(), // 深石板灰
            null, null, false, false));
    
    // 柔韧 - 潜行速度提升40%
    private static final AttributeModifier FLEXIBLE_SNEAK_MODIFIER = new AttributeModifier(
            Noellesroles.id("flexible_sneak_modifier"), 
            0.4, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    public static SREModifier FLEXIBLE = HMLModifiers.registerModifier(new SREModifier(
            Noellesroles.id("flexible"),
            new Color(0, 255, 255).getRGB(), // 青色
            null, null, false, false));
    
    // 反牛顿 - 重力减少20%
    private static final AttributeModifier ANTI_NEWTON_GRAVITY_MODIFIER = new AttributeModifier(
            Noellesroles.id("anti_newton_gravity_modifier"), 
            -0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    public static SREModifier ANTI_NEWTON = HMLModifiers.registerModifier(new SREModifier(
            Noellesroles.id("anti_newton"),
            new Color(135, 206, 250).getRGB(), // 天蓝色
            null, null, false, false));

    // ==================== 运行时数据存储 ====================
    
    // 回光返照 - 被触发的玩家集合
    public static final Set<UUID> LAST_GASP_TRIGGERED = ConcurrentHashMap.newKeySet();
    
    // 回光返照 - 触发时的游戏时间（用于3秒游戏时间计时器）
    public static final Map<UUID, Long> LAST_GASP_TRIGGER_GAME_TIME = new ConcurrentHashMap<>();
    
    // 回光返照 - 保存击杀者UUID
    public static final Map<UUID, UUID> LAST_GASP_KILLER = new ConcurrentHashMap<>();
    
    // 回光返照 - 保存死亡原因
    public static final Map<UUID, ResourceLocation> LAST_GASP_DEATH_REASON = new ConcurrentHashMap<>();
    
    // 起义军 - 被触发的玩家集合（每游戏一次）
    public static final Set<UUID> REBEL_TRIGGERED = ConcurrentHashMap.newKeySet();
    
    // 绝境信徒 - 被触发的玩家集合（每游戏一次）
    public static final Set<UUID> DESPERATE_FAITH_ACTIVATED = ConcurrentHashMap.newKeySet();
    
    // 玩家最后给予金币的时间
    public static final Map<UUID, Long> LAST_GIVE_COIN_TIME = new ConcurrentHashMap<>();
    
    // 玩家最后获得苹果的时间
    public static final Map<UUID, Long> LAST_APPLE_TIME = new ConcurrentHashMap<>();
    
    // 腐化 - 标记需要快速下沉的尸体
    public static final Set<UUID> CORRUPTED_BODIES = ConcurrentHashMap.newKeySet();
    
    // 吝啬 - 记录上次购买返还金币的玩家
    public static final Map<UUID, Long> LAST_STINGY_REFUND_TIME = new ConcurrentHashMap<>();
    
    // 狂躁症 - 记录最后触发时间
    public static final Map<UUID, Long> LAST_MANIC_TRIGGER_TIME = new ConcurrentHashMap<>();

    // ==================== 初始化方法 ====================
    public static void init() {
        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
        
        // 设置修饰符概率和最大生成数（从配置文件读取）
        SNEAKY.setEnableChance(config.chanceOfSneaky).setMax(1);
        BUTTER_FINGERS.setEnableChance(config.chanceOfButterFingers).setMax(1);
        STRONG.setEnableChance(config.chanceOfStrong).setMax(1);
        NIGHT_OWL.setEnableChance(config.chanceOfNightOwl).setMax(1);
        GENEROUS.setEnableChance(config.chanceOfGenerous).setMax(1);
        BRAVE.setEnableChance(config.chanceOfBrave).setMax(1);
        WORKAHOLIC.setEnableChance(config.chanceOfWorkaholic).setMax(1);
        BIG_EATER.setEnableChance(config.chanceOfBigEater).setMax(1);
        MANIC.setEnableChance(config.chanceOfManic).setMax(1);
        LAST_GASP.setEnableChance(config.chanceOfLastGasp).setMax(1);
        REBEL.setEnableChance(config.chanceOfRebel).setMax(1);
        HEMOPHOBIA.setEnableChance(config.chanceOfHemophobia).setMax(1);
        MONEY_GRUBBER.setEnableChance(config.chanceOfMoneyGrubber).setMax(1);
        VEGETARIAN.setEnableChance(config.chanceOfVegetarian).setMax(1);
        DWARF.setEnableChance(config.chanceOfDwarf).setMax(1);
        DESPERATE_FAITH.setEnableChance(config.chanceOfDesperateFaith).setMax(1);
        STINGY.setEnableChance(config.chanceOfStingy).setMax(1);
        CORRUPTED.setEnableChance(config.chanceOfCorrupted).setMax(1);
        FLEXIBLE.setEnableChance(config.chanceOfFlexible).setMax(1);
        ANTI_NEWTON.setEnableChance(config.chanceOfAntiNewton).setMax(1);
        
        registerModifierEvents();
        registerDeathEvents();
        registerGameEvents();
        registerTaskEvents();
    }
    
    private static void registerModifierEvents() {
        // 修饰符分配事件
        ModifierAssigned.EVENT.register((player, modifier) -> {
            if (!(player instanceof ServerPlayer sp)) return;
            
            WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(player.level());
            
            // === 狂躁症互斥 - 与任务大师和工作狂互斥 ===
            if (modifier.equals(MANIC)) {
                // 移除任务大师
                if (worldModifierComponent.isModifier(player.getUUID(), SEModifiers.TASKMASTER)) {
                    worldModifierComponent.removeModifier(player.getUUID(), SEModifiers.TASKMASTER);
                }
                // 移除工作狂
                if (worldModifierComponent.isModifier(player.getUUID(), WORKAHOLIC)) {
                    worldModifierComponent.removeModifier(player.getUUID(), WORKAHOLIC);
                }
            }
            
            // === 任务大师和工作狂与狂躁症互斥 ===
            if (modifier.equals(SEModifiers.TASKMASTER) || modifier.equals(WORKAHOLIC)) {
                if (worldModifierComponent.isModifier(player.getUUID(), MANIC)) {
                    worldModifierComponent.removeModifier(player.getUUID(), MANIC);
                }
            }
            
            // 强壮 - 添加击退抗性
            if (modifier.equals(STRONG)) {
                player.getAttribute(Attributes.KNOCKBACK_RESISTANCE).removeModifier(STRONG_KNOCKBACK_RESIST_MODIFIER);
                player.getAttribute(Attributes.KNOCKBACK_RESISTANCE).addPermanentModifier(STRONG_KNOCKBACK_RESIST_MODIFIER);
            }
            
            // 侏儒 - 缩小50%（同时移除高大/矮小修饰符）
            if (modifier.equals(DWARF)) {
                // 移除高大修饰符（如果存在）
                if (worldModifierComponent.isModifier(player.getUUID(), SEModifiers.TALL)) {
                    worldModifierComponent.removeModifier(player.getUUID(), SEModifiers.TALL);
                    player.getAttribute(Attributes.SCALE).removeModifier(SEModifiers.TALL_MODIFIER);
                }
                // 移除矮小修饰符（如果之前有）
                if (worldModifierComponent.isModifier(player.getUUID(), SEModifiers.TINY)) {
                    worldModifierComponent.removeModifier(player.getUUID(), SEModifiers.TINY);
                    player.getAttribute(Attributes.SCALE).removeModifier(SEModifiers.TINY_MODIFIER);
                }
                player.getAttribute(Attributes.SCALE).removeModifier(DWARF_MODIFIER);
                player.getAttribute(Attributes.SCALE).addPermanentModifier(DWARF_MODIFIER);
            }
            
            // 柔韧 - 潜行速度+40%
            if (modifier.equals(FLEXIBLE)) {
                player.getAttribute(Attributes.SNEAKING_SPEED).removeModifier(FLEXIBLE_SNEAK_MODIFIER);
                player.getAttribute(Attributes.SNEAKING_SPEED).addPermanentModifier(FLEXIBLE_SNEAK_MODIFIER);
            }
            
            // 反牛顿 - 重力-20%
            if (modifier.equals(ANTI_NEWTON)) {
                player.getAttribute(Attributes.GRAVITY).removeModifier(ANTI_NEWTON_GRAVITY_MODIFIER);
                player.getAttribute(Attributes.GRAVITY).addPermanentModifier(ANTI_NEWTON_GRAVITY_MODIFIER);
            }
            
            // 初始化给予金币/苹果计时器
            LAST_GIVE_COIN_TIME.put(player.getUUID(), System.currentTimeMillis());
            LAST_APPLE_TIME.put(player.getUUID(), System.currentTimeMillis());
        });
        
        // 修饰符移除事件 - 清理属性修改
        ModifierRemoved.EVENT.register((player, modifier) -> {
            if (modifier.equals(STRONG)) {
                player.getAttribute(Attributes.KNOCKBACK_RESISTANCE).removeModifier(STRONG_KNOCKBACK_RESIST_MODIFIER);
            }
            if (modifier.equals(DWARF)) {
                player.getAttribute(Attributes.SCALE).removeModifier(DWARF_MODIFIER);
            }
            if (modifier.equals(FLEXIBLE)) {
                player.getAttribute(Attributes.SNEAKING_SPEED).removeModifier(FLEXIBLE_SNEAK_MODIFIER);
            }
            if (modifier.equals(ANTI_NEWTON)) {
                player.getAttribute(Attributes.GRAVITY).removeModifier(ANTI_NEWTON_GRAVITY_MODIFIER);
            }
        });
        
        // 重置玩家事件
        ResetPlayerEvent.EVENT.register(player -> {
            LAST_GASP_TRIGGERED.remove(player.getUUID());
            LAST_GASP_TRIGGER_GAME_TIME.remove(player.getUUID());
            LAST_GASP_KILLER.remove(player.getUUID());
            LAST_GASP_DEATH_REASON.remove(player.getUUID());
            REBEL_TRIGGERED.remove(player.getUUID());
            DESPERATE_FAITH_ACTIVATED.remove(player.getUUID());
            LAST_GIVE_COIN_TIME.remove(player.getUUID());
            LAST_APPLE_TIME.remove(player.getUUID());
            CORRUPTED_BODIES.remove(player.getUUID());
            LAST_STINGY_REFUND_TIME.remove(player.getUUID());
            LAST_MANIC_TRIGGER_TIME.remove(player.getUUID());
        });
    }
    
    private static void registerDeathEvents() {
        // 回光返照 - 被击杀时延后3秒死亡（不阻挡列车碾压和挂机死亡）
        AllowPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> {
            if (!(player instanceof ServerPlayer sp)) return true;
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
            if (gameWorld == null || !gameWorld.isRunning()) return true;
            
            // 检查死亡原因，排除列车碾压和挂机死亡
            ResourceLocation deathReasonId = deathReason != null ? deathReason : GameConstants.DeathReasons.GENERIC;
            ResourceLocation trainDeath = GameConstants.DeathReasons.FELL_OUT_OF_TRAIN;
            ResourceLocation afkDeath = io.wifi.starrailexpress.SRE.id("death_afk");
            if (deathReasonId.equals(trainDeath) || deathReasonId.equals(afkDeath)) {
                return true; // 这些死亡原因不触发回光返照
            }
            
            WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(player.level());
            if (modifiers.isModifier(player.getUUID(), LAST_GASP) && !LAST_GASP_TRIGGERED.contains(player.getUUID())) {
                LAST_GASP_TRIGGERED.add(player.getUUID());
                
                // 记录触发时的游戏时间（用于3秒游戏时间计时器）
                long gameTime = player.level().getGameTime();
                LAST_GASP_TRIGGER_GAME_TIME.put(player.getUUID(), gameTime);
                
                // 保存击杀者和死亡原因
                if (killer != null) {
                    LAST_GASP_KILLER.put(player.getUUID(), killer.getUUID());
                }
                LAST_GASP_DEATH_REASON.put(player.getUUID(), deathReasonId);
                
                // 使用模组已有的效果：禁止移动、无敌、禁止技能、禁止转向、禁止物品、禁止背包、黑暗
                sp.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, 100, 0, false, false, false)); // 禁止移动
                sp.addEffect(new MobEffectInstance(ModEffects.SAFE_TIME, 100, 0, false, false, false)); // 无敌（安全时间）
                sp.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, 100, 0, false, false, false)); // 禁止使用技能
                sp.addEffect(new MobEffectInstance(ModEffects.TURN_BANED, 100, 0, false, false, false)); // 禁止转向
                sp.addEffect(new MobEffectInstance(ModEffects.USED_BANED, 100, 0, false, false, false)); // 禁止使用物品
                sp.addEffect(new MobEffectInstance(ModEffects.INVENTORY_BANED, 100, 0, false, false, false)); // 禁止打开背包
                sp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, false, false, false)); // 黑暗
                
                // 只发送给玩家自己
                sp.displayClientMessage(Component.translatable("modifier.noellesroles.last_gasp.trigger"), true);
                
                // 播放死亡来临的音效
                sp.playSound(net.minecraft.sounds.SoundEvents.WITHER_DEATH, 0.8f, 0.5f);
                
                // 播放粒子效果（女巫死亡粒子）
                if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH, 
                            player.getX(), player.getY() + 1.0, player.getZ(), 
                            30, 0.8, 1.0, 0.8, 0.05);
                }
                
                return false; // 取消当前死亡
            }
            return true;
        });
        
        // 起义军 - 被同阵营误杀时变为叛徒（只能触发一次）
        AllowPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
            if (gameWorld == null || !gameWorld.isRunning()) return true;
            
            if (killer == null) return true;
            
            // 检查是否已经触发过
            if (REBEL_TRIGGERED.contains(player.getUUID())) return true;
            
            WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(player.level());
            if (modifiers.isModifier(player.getUUID(), REBEL)) {
                // 检查杀手是否是平民阵营（误杀）
                if (gameWorld.isInnocent(killer)) {
                    // 标记为已触发
                    REBEL_TRIGGERED.add(player.getUUID());
                    
                    // 确保是 ServerPlayer 和 ServerLevel
                    if (!(player instanceof ServerPlayer sp)) return true;
                    if (!(player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return true;
                    
                    // 保存触发位置，用于播放音效
                    double triggerX = player.getX();
                    double triggerY = player.getY();
                    double triggerZ = player.getZ();
                    
                    // 传送回房间
                    Vec3 pos = GameUtils.getSpawnPos(AreasWorldComponent.KEY.get(player.level()),
                            GameUtils.roomToPlayer.get(player.getUUID()));
                    if (pos != null) {
                        player.teleportTo(pos.x(), pos.y() + 1, pos.z());
                    }
                    
                    // 变为叛徒
                    RoleUtils.changeRole(player, TraitorAndModifiers.TRAITOR);
                    RoleUtils.sendWelcomeAnnouncement(sp, TraitorAndModifiers.TRAITOR);
                    
                    // 移除起义军修饰符
                    modifiers.removeModifier(player.getUUID(), REBEL);
                    
                    // 向所有玩家发送广播消息
                    for (ServerPlayer targetPlayer : serverLevel.getServer().getPlayerList().getPlayers()) {
                        BroadcastMessageS2CPacket packet = new BroadcastMessageS2CPacket(
                                Component.translatable("modifier.noellesroles.rebel.trigger")
                                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                        ServerPlayNetworking.send(targetPlayer, packet);
                    }
                    
                    // 播放不祥号角：鸣响音效（使用之前的触发位置）
                    serverLevel.playSound(null, triggerX, triggerY, triggerZ,
                            SoundEvents.WARDEN_AGITATED, SoundSource.MASTER, 1.0f, 1.0f);
                    
                    // 给予初始物品
                    sp.addItem(new ItemStack(org.agmas.noellesroles.init.ModItems.SHORT_SHOTGUN));
                    sp.addItem(new ItemStack(TMMItems.GRENADE));
                    
                    return false; // 取消死亡
                }
            }
            return true;
        });
        

        
        // 敛财 - 死后扣除击杀者40%金币，给击杀拥有敛财修饰符的玩家提示
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (victim.level().isClientSide) return;
            
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());
            if (gameWorld == null || !gameWorld.isRunning()) return;
            
            WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(victim.level());
            // 只有当被害者拥有敛财修饰符时才触发效果
            if (modifiers != null && modifiers.isModifier(victim.getUUID(), MONEY_GRUBBER)) {
                
                if (killer != null) {
                    // 击杀者被扣除40%金币
                    SREPlayerShopComponent killerShop = SREPlayerShopComponent.KEY.get(killer);
                    int killerCurrentCoins = killerShop.balance;
                    int coinsToTake = (int) (killerCurrentCoins * 0.4);
                    
                    if (coinsToTake > 0) {
                        killerShop.setBalance(killerCurrentCoins - coinsToTake);
                        killerShop.sync();

                        if (killer instanceof ServerPlayer) {
                            ((ServerPlayer) killer).displayClientMessage(Component.translatable("modifier.noellesroles.money_grubber.taken",
                                    coinsToTake), true);
                        }
                    }
                }
            }
        });
        
        // 腐化 - 尸体直接下沉到完全阶段
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(victim.level());
            if (modifiers.isModifier(victim.getUUID(), CORRUPTED)) {
                CORRUPTED_BODIES.add(victim.getUUID());
            }
        });
        
        // 起义军效果 - 当平民玩家击杀起义军玩家时，平民因误杀平民而死亡
        OnPlayerKilledPlayer.EVENT.register((victim, killer, reason) -> {
            if (victim.level().isClientSide) return;
            
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());
            if (gameWorld == null || !gameWorld.isRunning()) return;
            
            // 检查被击杀者是否是起义军玩家
            WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(victim.level());
            if (modifiers != null && modifiers.isModifier(victim.getUUID(), REBEL)) {
                // 检查击杀者是否是平民阵营
                if (gameWorld.isInnocent(killer)) {
                    // 使用误杀平民的死亡原因
                    GameUtils.killPlayer(killer, true, victim, Noellesroles.id("shot_innocent"));
                }
            }
        });
    }
    
    private static void registerGameEvents() {
        // 游戏初始化时重置
        GameInitializeEvent.EVENT.register((level, gameWorldComponent, readyPlayerList) -> {
            LAST_GASP_TRIGGERED.clear();
            LAST_GASP_TRIGGER_GAME_TIME.clear();
            LAST_GASP_KILLER.clear();
            LAST_GASP_DEATH_REASON.clear();
            REBEL_TRIGGERED.clear();
            DESPERATE_FAITH_ACTIVATED.clear();
            LAST_GIVE_COIN_TIME.clear();
            LAST_APPLE_TIME.clear();
            CORRUPTED_BODIES.clear();
            LAST_STINGY_REFUND_TIME.clear();
        });
        
        // 游戏结束时重置
        OnGameEnd.EVENT.register((level, gameWorldComponent) -> {
            LAST_GASP_TRIGGERED.clear();
            DESPERATE_FAITH_ACTIVATED.clear();
        });
    }
    
    private static void registerTaskEvents() {
        // 注册工作狂修饰符的任务刷新加速效果
        // 工作狂的效果将在 ModifierEffects 中通过修改 refreshInterval 实现
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 检查食物是否为肉类
     */
    public static boolean isMeat(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var item = stack.getItem();
        // 生肉
        if (item == net.minecraft.world.item.Items.BEEF || item == net.minecraft.world.item.Items.PORKCHOP || 
            item == net.minecraft.world.item.Items.CHICKEN || item == net.minecraft.world.item.Items.RABBIT || 
            item == net.minecraft.world.item.Items.MUTTON || item == net.minecraft.world.item.Items.COD || 
            item == net.minecraft.world.item.Items.SALMON || item == net.minecraft.world.item.Items.TROPICAL_FISH || 
            item == net.minecraft.world.item.Items.PUFFERFISH) {
            return true;
        }
        // 熟肉
        if (item == net.minecraft.world.item.Items.COOKED_BEEF || item == net.minecraft.world.item.Items.COOKED_PORKCHOP || 
            item == net.minecraft.world.item.Items.COOKED_CHICKEN || item == net.minecraft.world.item.Items.COOKED_RABBIT || 
            item == net.minecraft.world.item.Items.COOKED_MUTTON || item == net.minecraft.world.item.Items.COOKED_COD || 
            item == net.minecraft.world.item.Items.COOKED_SALMON) {
            return true;
        }
        // 腐肉和蜘蛛眼
        if (item == net.minecraft.world.item.Items.ROTTEN_FLESH || item == net.minecraft.world.item.Items.SPIDER_EYE) {
            return true;
        }
        // 兔肉煲
        if (item == net.minecraft.world.item.Items.RABBIT_STEW) {
            return true;
        }
        return false;
    }
    
    /**
     * 检查尸体是否应该快速下沉（腐化修饰符）
     */
    public static boolean isBodyCorrupted(UUID victimUUID) {
        return CORRUPTED_BODIES.contains(victimUUID);
    }
    
    /**
     * 获取吝啬返还的金币数量
     */
    public static int calculateStingyRefund(int originalPrice) {
        return (int) (originalPrice * 0.2);
    }
}
