package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.SixtySecondsPhase;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 末日60秒的健康/倒地/死亡模型：
 * <ul>
 *   <li>所有受伤统一造成 {@link #INJURY_DAMAGE} 点健康值伤害（拦截模组击杀路径与原版伤害）。</li>
 *   <li>受伤致健康归零 → 首次倒地；同日第二次倒地 → 直接死亡；对倒地者再受伤 → 处决。</li>
 *   <li>非受伤致健康归零（饥渴等）→ 直接死亡（不进倒地）。见 {@link #onHealthZero}。</li>
 *   <li>倒地 {@link #BLEED_OUT_TICKS} 后流血而死；队友近身累计 {@link #REVIVE_TICKS} 救起。</li>
 * </ul>
 * 参照 {@code repair} 的倒地/救援与 {@code GameMode.killPlayer} 事件门。
 */
public final class SixtySecondsHealthSystem {
    public static final int INJURY_DAMAGE = 50;
    public static final int BLEED_OUT_TICKS = 20 * 150;   // 倒地 2.5 分钟流血死
    public static final int REVIVE_TICKS = 20 * 10;       // 队友近身 10s 救起
    private static final double REVIVE_RANGE_SQR = 3.0 * 3.0;

    // ── 60s 模式所有可能的死亡原因（用于 handleLethal 识别自身产生的死亡）──
    private static final Set<ResourceLocation> SIXTY_SECONDS_DEATH_REASONS = Set.of(
            GameConstants.DeathReasons.GUN_SHOT,
            GameConstants.DeathReasons.REVOLVER,
            GameConstants.DeathReasons.DERRINGER,
            GameConstants.DeathReasons.SNIPER_RIFLE,
            GameConstants.DeathReasons.EXECUTE,
            GameConstants.DeathReasons.ZERO_ONE_FIVE,
            GameConstants.DeathReasons.KNIFE,
            GameConstants.DeathReasons.BAT,
            GameConstants.DeathReasons.FIRE_AXE,
            GameConstants.DeathReasons.GENERAL_ATTACK,
            GameConstants.DeathReasons.FALL_DAMAGE,
            GameConstants.DeathReasons.STARVED,
            GameConstants.DeathReasons.THIRST,
            GameConstants.DeathReasons.DROWNED,
            GameConstants.DeathReasons.LAVA,
            GameConstants.DeathReasons.FROZEN,
            GameConstants.DeathReasons.GENERIC,
            GameConstants.DeathReasons.FELL_OUT_OF_TRAIN
    );

    /** 非 PvP 致死（环境/意外/自然）的死亡原因池，无击杀者时随机选取。 */
    private static final ResourceLocation[] ENVIRONMENTAL_DEATH_REASONS = {
            GameConstants.DeathReasons.FALL_DAMAGE,
            GameConstants.DeathReasons.STARVED,
            GameConstants.DeathReasons.THIRST,
            GameConstants.DeathReasons.DROWNED,
            GameConstants.DeathReasons.LAVA,
            GameConstants.DeathReasons.FROZEN,
            GameConstants.DeathReasons.GENERIC,
            GameConstants.DeathReasons.FELL_OUT_OF_TRAIN
    };

    /** 倒地玩家 UUID → 已累计救援 tick。 */
    private static final Map<UUID, Integer> REVIVE_PROGRESS = new HashMap<>();
    /** 倒地玩家 UUID → 救援进度 BossBar（倒地者和救援者可见）。 */
    private static final Map<UUID, ServerBossEvent> REVIVE_BOSS_BARS = new HashMap<>();

    private SixtySecondsHealthSystem() {
    }

    public static void register() {
        // 只在带 killer 的事件里拦截（总在 AllowPlayerDeath 之后触发，可拿到击杀者）
        AllowPlayerDeathWithKiller.EVENT.register(SixtySecondsHealthSystem::handleLethal);
        // 放行原版物品左键攻击：PlayerEntityMixin.attack 默认吞掉非 LeftClickHurtable 的近战攻击，
        // 60s 里任何物品都可攻击——放行后原版伤害链会被下面的 ALLOW_DAMAGE 转成健康伤害（数值按武器映射）
        io.wifi.starrailexpress.event.AllowPlayerPunching.EVENT.register(
                player -> net.exmo.sre.sixtyseconds.SixtySecondsMod.isActive(player.level()));
        // 攻击时自动解除隐身：覆盖玩家攻击任意实体的所有情况（PvP / PvE / 空手 / 武器），
        // AttackEntityCallback 在客户端/服务端双端触发，只取服务端处理。
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayer sp && !world.isClientSide()
                    && SixtySecondsMod.isActive(sp.level())
                    && GameUtils.isPlayerAliveAndSurvival(sp)
                    && sp.hasEffect(MobEffects.INVISIBILITY)) {
                sp.removeEffect(MobEffects.INVISIBILITY);
            }
            return InteractionResult.PASS;
        });
        // 兜底拦截漏网的原版致死（/kill、虚空、瞬间大额伤害等）：改走本系统死亡（原地变旁观），
        // 否则原版死亡→重生把玩家送回世界出生点（表现为「出现在世界边缘」）
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer player && SixtySecondsMod.isActive(player.level())
                    && GameUtils.isPlayerAliveAndSurvival(player)) {
                // 游戏未开始（非DAY阶段）禁止伤害和倒地
                if (SixtySecondsState.get(player.serverLevel()).phase != SixtySecondsPhase.DAY) {
                    player.setHealth(player.getMaxHealth());
                    return false;
                }
                player.setHealth(1.0F);
                ServerPlayer attacker = source.getEntity() instanceof ServerPlayer sp ? sp : null;
                die(player, attacker);
                return false;
            }
            return true;
        });
        // 原版环境伤害（坠落/火/生物等）改为健康值伤害
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer player && SixtySecondsMod.isActive(player.level())
                    && GameUtils.isPlayerAliveAndSurvival(player)) {
                // 游戏未开始（非DAY阶段）禁止任何伤害
                if (SixtySecondsState.get(player.serverLevel()).phase != SixtySecondsPhase.DAY) {
                    return false;
                }
                ServerPlayer attacker = source.getEntity() instanceof ServerPlayer sp ? sp : null;
                // 环境/自然伤害 = 无实体攻击者、也非生物攻击（火/岩浆/窒息/溺水/冰冻/坠落等）。这类伤害逐 tick
                // 触发，而 ALLOW_DAMAGE 取消原版伤害后原版无敌帧从不设置 → 每 tick 全额连扣，卡墙/掉火里瞬间秒杀。
                boolean environmental = attacker == null
                        && !(source.getEntity() instanceof net.minecraft.world.entity.LivingEntity);
                // 窒息（卡进方块）走非受伤致死：归零直接死亡、不进倒地——卡在墙里倒地既无法被救援
                // 也无法被处决（环境伤害对倒地者无效），是永久卡死状态；倒地者也一并放行处理。
                // 同样吃环境无敌帧，否则卡墙里逐 tick 直接秒。
                if (source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)) {
                    int dmg = envInvulnEffective(player, Math.max(5, Math.round(amount * 5.0F)));
                    if (dmg <= 0) {
                        return false; // 无敌帧内：取消原版伤害但不扣健康
                    }
                    SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
                    stats.health = Math.max(0, stats.health - dmg);
                    stats.sync();
                    if (stats.health <= 0) {
                        die(player, null);
                    }
                    return false;
                }
                // 玩家对玩家攻击受限（时段禁 PvP + 跨队一律禁）时无效，且不转成健康伤害（怪化玩家攻防不受限）
                if (attacker != null && player.level() instanceof ServerLevel serverLevel
                        && isPvpBlocked(serverLevel, attacker, player)) {
                    attacker.displayClientMessage(
                            Component.translatable("message.noellesroles.sixty_seconds.pvp_blocked."
                                    + pvpBlockedReason(serverLevel)), true);
                    return false;
                }
                // 伤害按来源分级：玩家武器查表（非武器物品/徒手=满蓄力 5，按攻击间隔充能削减）/ 怪物 20 /
                // 环境按原版伤害量比例映射（护甲统一减伤）。
                // 环境映射 amount×5（clamp 5..50）：火焰 tick(1)→5、10 格坠落(≈8)→40——健康值完全替代原版生命值，
                // 原版环境危害按强度换算而非一律 50（否则站进火里一秒就倒地）。
                int base = attacker != null ? SixtySecondsWeapons.injuryDamage(attacker, amount)
                        : source.getEntity() instanceof net.minecraft.world.entity.LivingEntity
                                ? SixtySecondsWeapons.MOB_DAMAGE
                                : Math.min(SixtySecondsWeapons.DEFAULT_DAMAGE,
                                        Math.max(5, Math.round(amount * 5.0F)));
                // 环境伤害：套无敌帧，只结算窗口外的一击（或窗口内更强一击的差额），杜绝逐 tick 秒杀
                if (environmental) {
                    int effective = envInvulnEffective(player, base);
                    if (effective <= 0) {
                        return false;
                    }
                    applyInjury(player, null, effective);
                    return false;
                }
                applyInjury(player, attacker, base);
                return false;
            }
            return true;
        });
    }

    /** 玩家 UUID → 上次环境伤害的 (游戏刻, 结算伤害额)，用于自建环境无敌帧。 */
    private static final Map<UUID, long[]> LAST_ENV_HURT = new HashMap<>();

    /**
     * 环境无敌帧结算：返回本次应实际扣除的健康值（0 = 处于无敌帧内且非更强的一击，应跳过）。
     * 仿原版 {@code invulnerableTime}：{@link SixtySecondsBalance#ENV_INVULN_TICKS} 窗口内，
     * 同强度/更弱的重复 tick 被完全吸收；只有「更强的一击」结算其超出上次的差额（防止一次小火 tick
     * 把随后的致命坠落也一并免掉）。窗口过期则全额结算并重置计时与基准。
     */
    private static int envInvulnEffective(ServerPlayer player, int base) {
        if (base <= 0) {
            return 0;
        }
        long now = player.level().getGameTime();
        UUID id = player.getUUID();
        long[] last = LAST_ENV_HURT.get(id);
        if (last != null && now - last[0] < SixtySecondsBalance.ENV_INVULN_TICKS) {
            int prev = (int) last[1];
            if (base <= prev) {
                return 0;
            }
            last[1] = base; // 更强一击：只补差额，不重置计时（同源持续伤害仍受节流）
            return base - prev;
        }
        LAST_ENV_HURT.put(id, new long[] { now, base });
        return base;
    }

    private static boolean handleLethal(Player victim, Player killer, ResourceLocation deathReason) {
        if (!SixtySecondsMod.isActive(victim.level()) || !(victim instanceof ServerPlayer player)) {
            return true;
        }
        // 游戏未开始（非DAY阶段）禁止伤害和倒地
        if (SixtySecondsState.get(player.serverLevel()).phase != SixtySecondsPhase.DAY) {
            player.setHealth(player.getMaxHealth());
            return false;
        }
        // 本系统 die() 走 forceKillPlayer 会再次触发本事件（forceDeath 只忽略否决、不跳过监听器），
        // 放行自己的死因，否则 handleLethal→applyInjury→die 无限递归爆栈
        if (SIXTY_SECONDS_DEATH_REASONS.contains(deathReason)) {
            return true;
        }
        ServerPlayer serverKiller = killer instanceof ServerPlayer sk ? sk : null;
        // 玩家对玩家攻击受限（时段禁 PvP + 跨队一律禁）时无效（环境伤害仍生效；怪化玩家攻防不受限）
        if (serverKiller != null && victim.level() instanceof ServerLevel serverLevel
                && isPvpBlocked(serverLevel, serverKiller, player)) {
            serverKiller.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.pvp_blocked."
                            + pvpBlockedReason(serverLevel)), true);
            return false;
        }
        applyInjury(player, serverKiller);
        return false; // 否决模组默认死亡，改由本系统接管
    }

    /**
     * PvP 判定（含队伍规则）：创造模式（管理员/未参与旁观）攻击一律放行；怪化玩家参与的攻防不受限
     * （怪是全场威胁，双向都要能打）；<b>拜访做客</b>全程 PvP 豁免（除了拜访——客人与主人互不伤害）；
     * <b>破门闯入别队避难所</b>默认开 PvP（绕过时段禁令，主人可随时反击）；其余未变怪玩家之间——
     * <b>只禁同队友伤</b>（家人互不伤害、可互救），跨队及无队伍一律放行，同队/野外还受时段限制
     * （{@link #isPvpBlocked(ServerLevel)}）。所有玩家伤害口子（原版链/枪/近战/手雷/RPG）统一走这里。
     */
    public static boolean isPvpBlocked(ServerLevel level, @Nullable ServerPlayer attacker,
            @Nullable ServerPlayer victim) {
        if (attacker == null || attacker == victim) {
            return false;
        }
        // 创造模式攻击不受任何限制：无队伍/时段门控（修复未参与游戏的创造玩家打不了人）
        if (attacker.isCreative()) {
            return false;
        }
        if (SixtySecondsStatsComponent.KEY.get(attacker).monster) {
            return false;
        }
        if (victim != null && SixtySecondsStatsComponent.KEY.get(victim).monster) {
            return false;
        }
        // 拜访做客豁免（「除了拜访」）：交战任一方是做客中的访客 → 一律禁 PvP。拜访是和平的：
        // 客人不能被打、也不能打主人（破门闯入才是敌对进入，见下）。
        if (SixtySecondsVisiting.isVisiting(attacker)
                || (victim != null && SixtySecondsVisiting.isVisiting(victim))) {
            return true;
        }
        // 「进别人家默认开 PvP」：交战任一方身处<b>别队</b>避难所盒内（=有人破门闯入了别人家），
        // 绕过时段禁令（清晨/新手保护期）——闯入是侵略行为，主人要能随时反击、闯入者也要担风险。
        // 纯野外/自己家的交战仍受时段禁令约束。
        // 此外，若任一方持有 BREAK_IN_INTRUDER 药水效果（撬锁器/开锁器闯入标记），同样豁免时段
        // 禁令——作为 isInsideForeignShelter 坐标检测的兜底（避难所盒边界/坐标精度偶发不命中）。
        boolean intrusion = victim != null
                && (isInsideForeignShelter(level, attacker) || isInsideForeignShelter(level, victim));
        boolean hasIntruderEffect = attacker.hasEffect(ModEffects.BREAK_IN_INTRUDER)
                || (victim != null && victim.hasEffect(ModEffects.BREAK_IN_INTRUDER));
        if (!intrusion && !hasIntruderEffect && isPvpBlocked(level)) {
            return true;
        }
        if (victim == null) {
            return false;
        }
        int attackerTeam = SixtySecondsStatsComponent.KEY.get(attacker).teamId;
        int victimTeam = SixtySecondsStatsComponent.KEY.get(victim).teamId;
        // 只在双方同属一支真实队伍时禁止（家人友伤关闭）；跨队 / 任一方无队伍（-1，含重连未恢复/旁观）一律放行。
        // 旧逻辑 attackerTeam<0||victimTeam<0||attackerTeam!=victimTeam 恰好反了：把跨队禁掉、把无队伍禁掉，
        // 导致「打不了别队的人」「重连后打不了人」「无队伍旁观打不了人」。
        return attackerTeam >= 0 && attackerTeam == victimTeam;
    }

    /** 玩家是否身处「非本队」的避难所盒内（= 破门闯入了别人家）。 */
    private static boolean isInsideForeignShelter(ServerLevel level, ServerPlayer player) {
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        int myTeam = SixtySecondsStatsComponent.KEY.get(player).teamId;
        double x = player.getX(), y = player.getY(), z = player.getZ();
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            if (team.teamId != myTeam && team.shelterBox != null && team.shelterBox.contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 准备阶段 / 前三天全天 / 每日清晨（{@link net.exmo.sre.sixtyseconds.SixtySecondsDayCycle#MORNING_TICKS}）禁止玩家互相攻击。
     * 前三天为新手保护期，全天禁 PvP；第 4 天起白天/晚上允许 PvP（清晨仍禁）。
     */
    public static boolean isPvpBlocked(ServerLevel level) {
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        if (data.phase != SixtySecondsPhase.DAY) {
            return true; // 准备/结算阶段一律禁 PvP
        }
        // 前三天全天禁 PvP（新手保护期）；第 4 天起开放 PvP
        if (data.dayNumber <= 3) {
            return true;
        }
        return net.exmo.sre.sixtyseconds.SixtySecondsDayCycle.subPhase(data, level.getGameTime())
                == net.exmo.sre.sixtyseconds.SixtySecondsDayCycle.SubPhase.MORNING;
    }

    /**
     * 返回 PvP 被阻止的具体原因，用于向攻击者展示不同的提示文本。
     * 仅在 {@link #isPvpBlocked(ServerLevel)} 已返回 true 后调用。
     */
    private static String pvpBlockedReason(ServerLevel level) {
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        if (data.phase != SixtySecondsPhase.DAY) {
            return "not_day";
        }
        if (data.dayNumber <= 3) {
            return "early_days";
        }
        return "morning";
    }

    /** 一次受伤（伤害按攻击者武器查表，见 {@link SixtySecondsWeapons}）；对倒地者受伤=处决。 */
    public static void applyInjury(ServerPlayer victim, @Nullable ServerPlayer attacker) {
        applyInjury(victim, attacker, SixtySecondsWeapons.injuryDamage(attacker));
    }

    /** 一次受伤：扣 baseDamage（经护甲减伤）点健康；怪物玩家不受护甲减免。 */
    public static void applyInjury(ServerPlayer victim, @Nullable ServerPlayer attacker, int baseDamage) {
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(victim);
        // PvP 伤害减免 -50%（怪物攻防不受限）
        if (attacker != null && !stats.monster) {
            baseDamage = (int) Math.max(1, baseDamage * SixtySecondsBalance.PVP_DAMAGE_MULT);
        }
        if (stats.monster) {
            // 怪物玩家：正常扣血（250 血），不受护甲减免，健康扣完即死
            hurtFeedback(victim, attacker);
            stats.health = Math.max(0, stats.health - baseDamage);
            stats.sync();
            if (stats.health <= 0) {
                die(victim, attacker);
            }
            return;
        }
        if (stats.downed) {
            // 倒地者：仅玩家攻击有效（补刀），扣减倒地健康值，归零才死
            if (attacker != null) {
                stats.health = Math.max(0, stats.health - SixtySecondsWeapons.reduceByArmor(victim, baseDamage));
                stats.sync();
                hurtFeedback(victim, attacker);
                if (stats.health <= 0) {
                    boolean saved = SixtySecondsMystic.tryUndyingTotem(victim, stats);
                    if (!saved) {
                        die(victim, attacker);
                        // 处决成立：处决者的污秽玻璃罐 → 存血的玻璃罐（神秘技术）
                        SixtySecondsMystic.onExecuteDowned(attacker);
                    }
                }
            }
            return;
        }
        hurtFeedback(victim, attacker);
        stats.health = Math.max(0, stats.health - SixtySecondsWeapons.reduceByArmor(victim, baseDamage));
        stats.sync();
        if (stats.health <= 0) {
            onHealthZero(victim, true, attacker);
        }
    }

    /**
     * 受击反馈：ALLOW_DAMAGE 取消原版伤害后没有任何表现（无红闪/音效/击退，打人像打空气）——
     * 这里手动补齐：受击动画广播 + 受伤音效 + 来自攻击者方向的击退。
     */
    private static void hurtFeedback(ServerPlayer victim, @Nullable ServerPlayer attacker) {
        ServerLevel level = victim.serverLevel();
        level.broadcastDamageEvent(victim, attacker != null
                ? victim.damageSources().playerAttack(attacker)
                : victim.damageSources().generic());
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                net.minecraft.sounds.SoundEvents.PLAYER_HURT,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
        if (attacker != null && attacker != victim) {
            victim.knockback(0.4D, attacker.getX() - victim.getX(), attacker.getZ() - victim.getZ());
            victim.hurtMarked = true; // 强制同步击退速度到客户端
        }
    }

    /** 健康归零处理。fromInjury=false（饥渴等）直接死亡。不死图腾（原版图腾）可拦一次死亡。 */
    public static void onHealthZero(ServerPlayer victim, boolean fromInjury, @Nullable ServerPlayer attacker) {
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(victim);
        if (!fromInjury) {
            if (!SixtySecondsMystic.tryUndyingTotem(victim, stats)) {
                die(victim, null);
            }
            return;
        }
        if (stats.downedCountToday >= 1) {
            if (!SixtySecondsMystic.tryUndyingTotem(victim, stats)) {
                die(victim, attacker);
            }
            return;
        }
        setDowned(victim, stats);
    }

    /** 管理指令：强制倒地（不增加当日倒地计数的语义不变——按正常倒地流程走）。 */
    public static void forceDown(ServerPlayer player) {
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
        if (!stats.downed) {
            setDowned(player, stats);
        }
    }

    private static void setDowned(ServerPlayer victim, SixtySecondsStatsComponent stats) {
        stats.downed = true;
        stats.downedFromInjury = true;
        stats.downedCountToday++;
        stats.health = SixtySecondsBalance.DOWNED_MAX_HEALTH;
        stats.bleedOutEndTick = 0L; // 不再使用定时流血，改为健康值自然流失
        stats.sync();
        REVIVE_PROGRESS.remove(victim.getUUID());
        // 即时禁止移动和攻击/使用物品
        victim.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, 40, 0, false, false, false));
        victim.addEffect(new MobEffectInstance(ModEffects.USED_BANED, 40, 0, false, false, false));
        victim.displayClientMessage(Component.translatable("message.noellesroles.sixty_seconds.downed"), false);
    }

    /**
     * 根据击杀者手持武器或环境类型解析死亡原因。
     * <ul>
     *   <li>击杀者持有枪械 → 对应枪械死因（左轮/德林加/狙击/处决/零一五）</li>
     *   <li>击杀者持有近战武器 → 对应近战死因（刀/球棒/消防斧/普通攻击）</li>
     *   <li>击杀者为空 → 从环境死因池随机选取（坠落/饥饿/口渴/溺水/岩浆/冰冻/未知/列车碾压）</li>
     * </ul>
     */
    private static ResourceLocation resolveDeathReason(@Nullable ServerPlayer killer, ServerPlayer victim) {
        if (killer != null && killer != victim) {
            ItemStack weapon = killer.getMainHandItem();
            Item item = weapon.getItem();

            // ── 枪械 ──
            if (item == TMMItems.REVOLVER || item == TMMItems.STANDARD_REVOLVER
                    || item == ModItems.SHERIFF_REVOLVER || item == ModItems.BANDIT_REVOLVER) {
                return GameConstants.DeathReasons.REVOLVER;
            }
            if (item == TMMItems.DERRINGER) {
                return GameConstants.DeathReasons.DERRINGER;
            }
            if (item == TMMItems.SNIPER_RIFLE) {
                return GameConstants.DeathReasons.SNIPER_RIFLE;
            }
            if (item == ModItems.EXECUTIONER_GUN) {
                return GameConstants.DeathReasons.EXECUTE;
            }
            if (item == ModItems.ZERO_ONE_FIVE_GUN) {
                return GameConstants.DeathReasons.ZERO_ONE_FIVE;
            }

            // ── 近战武器 ──
            if (item == ModItems.SIXTY_SECONDS_KNIFE) {
                return GameConstants.DeathReasons.KNIFE;
            }
            if (item == ModItems.SIXTY_SECONDS_SPIKED_BAT || item == ModItems.SIXTY_SECONDS_STUN_BATON) {
                return GameConstants.DeathReasons.BAT;
            }
            if (item == ModItems.SIXTY_SECONDS_FIRE_AXE || item == ModItems.SIXTY_SECONDS_HATCHET) {
                return GameConstants.DeathReasons.FIRE_AXE;
            }
            if (item instanceof net.exmo.sre.sixtyseconds.content.item.SixtySecondsMeleeWeaponItem) {
                return GameConstants.DeathReasons.GENERAL_ATTACK;
            }

            // 徒手/未知物品 → 普通攻击
            return GameConstants.DeathReasons.GENERAL_ATTACK;
        }

        // 无击杀者 → 从环境死因池随机选取
        return ENVIRONMENTAL_DEATH_REASONS[victim.getRandom().nextInt(ENVIRONMENTAL_DEATH_REASONS.length)];
    }

    public static void die(ServerPlayer victim, @Nullable ServerPlayer killer) {
        ResourceLocation deathReason = resolveDeathReason(killer, victim);
        applyKillSanityCapLoss(victim, killer);
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(victim);
        stats.downed = false;
        stats.bleedOutEndTick = 0L;
        stats.sanZeroTick = 0; // 死后清除变怪倒计时，防止旁观者仍能自我解脱
        stats.sync();
        victim.setSwimming(false);
        REVIVE_PROGRESS.remove(victim.getUUID());
        ServerBossEvent bar = REVIVE_BOSS_BARS.remove(victim.getUUID());
        if (bar != null) {
            bar.removeAllPlayers();
            bar.setVisible(false);
        }
        // 尸体处坐标要在 forceKillPlayer 之前取：那之后玩家已转旁观，位置不再是死亡点
        net.minecraft.core.BlockPos corpsePos = victim.blockPosition();
        GameUtils.forceKillPlayer(victim, true, killer, deathReason);
        // 自动复活（按图开关，默认开）：登记复活时刻 + 给本人打尸体标记
        SixtySecondsAutoRevive.onDeath(victim, corpsePos);
    }

    /**
     * 杀人代价：击杀者理智上限<b>永久</b>随机扣 {@link SixtySecondsBalance#KILL_SANITY_CAP_LOSS_MIN}~
     * {@link SixtySecondsBalance#KILL_SANITY_CAP_LOSS_MAX}（本局内不可恢复；恢复类回 san 以新上限为顶）。
     * 上限保底 {@link SixtySecondsBalance#SANITY_CAP_FLOOR}，防连环杀直接锁死到 0 变怪。自杀/无击杀者不扣。
     */
    private static void applyKillSanityCapLoss(ServerPlayer victim, @Nullable ServerPlayer killer) {
        if (killer == null || killer == victim
                || !(killer.level() instanceof ServerLevel level)) {
            return;
        }
        SixtySecondsStatsComponent killerStats = SixtySecondsStatsComponent.KEY.get(killer);
        int loss = SixtySecondsBalance.KILL_SANITY_CAP_LOSS_MIN + level.getRandom().nextInt(
                SixtySecondsBalance.KILL_SANITY_CAP_LOSS_MAX - SixtySecondsBalance.KILL_SANITY_CAP_LOSS_MIN + 1);
        int before = killerStats.sanityMax;
        killerStats.sanityMax = Math.max(SixtySecondsBalance.SANITY_CAP_FLOOR, killerStats.sanityMax - loss);
        if (killerStats.sanityMax == before) {
            return; // 已到保底
        }
        if (killerStats.sanity > killerStats.sanityMax) {
            killerStats.sanity = killerStats.sanityMax;
        }
        killerStats.sync();
        killer.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.kill_sanity_cap",
                before - killerStats.sanityMax, killerStats.sanityMax)
                .withStyle(net.minecraft.ChatFormatting.DARK_RED), false);
    }

    public static void tick(ServerLevel level) {
        boolean second = level.getGameTime() % 20 == 0;
        for (ServerPlayer player : level.players()) {
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
            // 绷带缓慢恢复：每秒恢复 1 点健康值
            if (second && stats.bandageHealRemaining > 0
                    && GameUtils.isPlayerAliveAndSurvival(player)) {
                stats.health = Math.min(stats.healthMax, stats.health + 1);
                stats.bandageHealRemaining--;
                stats.sync();
            }
            // 健康值完全替代原版生命值：每秒把原版血/饥饿钉满——伤害已被 ALLOW_DAMAGE 全量拦截转为健康伤害，
            // 这里兜底药水/中毒/原版饥饿等漏网掉血路径，保证原版层永不致死、原版饥饿不再干扰（60s 有自己的饥饿）。
            if (second && GameUtils.isPlayerAliveAndSurvival(player)) {
                if (player.getHealth() < player.getMaxHealth()) {
                    player.setHealth(player.getMaxHealth());
                }
                if (player.getFoodData().getFoodLevel() < 20) {
                    player.getFoodData().setFoodLevel(20);
                }
                // 看门狗：0 健康却既不倒地也没死的"幽灵存活"状态（历史上由 killPlayer 对无职业
                // 玩家静默不杀造成）——兜底补一次死亡，防状态卡死
                // 倒地玩家健康值 > 0，不触发此兜底
                if (stats.health <= 0 && !stats.downed && !stats.monster) {
                    die(player, null);
                    continue;
                }
            }
            if (!stats.downed) {
                continue;
            }
            // 倒地定身 + 禁止攻击/使用物品 + 趴下姿势
            player.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, 40, 0, false, false, false));
            player.addEffect(new MobEffectInstance(ModEffects.USED_BANED, 40, 0, false, false, false));
            player.setSwimming(true);
            player.setPose(net.minecraft.world.entity.Pose.SWIMMING);
            tickRevive(level, player, stats);
            // 每秒流失倒地健康值（有队友在救援范围内则不流血），归零死亡
            if (second && stats.health > 0 && !REVIVE_PROGRESS.containsKey(player.getUUID())) {
                stats.health = Math.max(0, stats.health - SixtySecondsBalance.DOWNED_BLEED_PER_SEC);
                stats.sync();
                if (stats.health <= 0) {
                    die(player, null);
                    continue;
                }
            }
        }
    }

    private static void tickRevive(ServerLevel level, ServerPlayer downed, SixtySecondsStatsComponent stats) {
        boolean rescuerNear = false;
        ServerPlayer rescuer = null;
        for (ServerPlayer other : level.players()) {
            if (other == downed || GameUtils.isPlayerEliminated(other)) {
                continue;
            }
            SixtySecondsStatsComponent otherStats = SixtySecondsStatsComponent.KEY.get(other);
            // 倒地/0 血/已变怪的队友不能充当救援者（倒地者互相贴身不该彼此救起）
            if (otherStats.downed || otherStats.health <= 0 || otherStats.monster
                    || stats.teamId < 0 || otherStats.teamId != stats.teamId) {
                continue;
            }
            if (other.distanceToSqr(downed) <= REVIVE_RANGE_SQR) {
                rescuerNear = true;
                rescuer = other;
                break;
            }
        }
        UUID id = downed.getUUID();
        if (rescuerNear && rescuer != null) {
            int progress = REVIVE_PROGRESS.merge(id, 1, Integer::sum);
            // 救援进度 BossBar：倒地者和救援者可见
            ServerBossEvent bar = REVIVE_BOSS_BARS.computeIfAbsent(id, k -> {
                ServerBossEvent b = new ServerBossEvent(
                        Component.translatable("message.noellesroles.sixty_seconds.revive_bar",
                                downed.getGameProfile().getName()),
                        BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
                b.setVisible(true);
                return b;
            });
            bar.setProgress(Math.min(1.0F, (float) progress / REVIVE_TICKS));
            // 确保倒地者和救援者都在观众列表中
            if (!bar.getPlayers().contains(downed)) bar.addPlayer(downed);
            if (!bar.getPlayers().contains(rescuer)) bar.addPlayer(rescuer);
            if (progress % 20 == 0) {
                downed.displayClientMessage(Component.translatable("message.noellesroles.sixty_seconds.reviving",
                        progress / 20, REVIVE_TICKS / 20), true);
            }
            if (progress >= REVIVE_TICKS) {
                revive(downed, stats);
            }
        } else {
            REVIVE_PROGRESS.remove(id);
            // 救援者走开→移除 BossBar
            ServerBossEvent bar = REVIVE_BOSS_BARS.remove(id);
            if (bar != null) {
                bar.removeAllPlayers();
                bar.setVisible(false);
            }
        }
    }

    /** 救起：清倒地，所有状态值 ×0.33，附缓慢。感染风险/生病判定交由 sickness 系统（P1 TODO）。 */
    public static void revive(ServerPlayer player, SixtySecondsStatsComponent stats) {
        stats.downed = false;
        stats.downedFromInjury = false;
        stats.bleedOutEndTick = 0L;
        stats.health = Math.max(1, (int) (SixtySecondsStatsComponent.MAX * 0.33));
        stats.hunger = (int) (stats.hunger * 0.33);
        stats.thirst = (int) (stats.thirst * 0.33);
        stats.sanity = (int) (stats.sanity * 0.33);
        // 未使用医疗包 → 感染风险：每 2 分钟 33% 生病（SixtySecondsSicknessSystem）；吃药/医疗包 cure() 可解除。
        stats.recovering = true;
        stats.sync();
        REVIVE_PROGRESS.remove(player.getUUID());
        ServerBossEvent bar = REVIVE_BOSS_BARS.remove(player.getUUID());
        if (bar != null) {
            bar.removeAllPlayers();
            bar.setVisible(false);
        }
        player.setSwimming(false);
        player.removeEffect(ModEffects.MOVE_BANED);
        player.removeEffect(ModEffects.USED_BANED);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 20, 1, false, false, true));
        player.displayClientMessage(Component.translatable("message.noellesroles.sixty_seconds.revived"), false);
    }

    public static void reset(ServerLevel level) {
        REVIVE_PROGRESS.clear();
        LAST_ENV_HURT.clear();
        for (ServerBossEvent bar : REVIVE_BOSS_BARS.values()) {
            bar.removeAllPlayers();
            bar.setVisible(false);
        }
        REVIVE_BOSS_BARS.clear();
        // 清除所有玩家的倒地姿态和效果（防止游戏结束时趴下状态残留）
        for (ServerPlayer player : level.players()) {
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
            stats.downed = false;
            stats.bleedOutEndTick = 0L;
            stats.sync();
            player.setSwimming(false);
            player.setPose(Pose.STANDING);
            player.removeEffect(ModEffects.MOVE_BANED);
            player.removeEffect(ModEffects.USED_BANED);
            player.removeEffect(ModEffects.BREAK_IN_INTRUDER);
        }
    }
}
