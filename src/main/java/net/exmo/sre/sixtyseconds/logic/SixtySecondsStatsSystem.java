package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * 生存四值 + 健康值驱动：
 * <ul>
 *   <li>每分钟消耗 饥饿/口渴/san、累积污染（在家 ×{@link SixtySecondsBalance#HOME_DRAIN_MULT}）；</li>
 *   <li><b>健康保护</b>：饥饿或口渴清空 → 每秒最多扣 {@link SixtySecondsBalance#HEALTH_LOSS_PER_SEC}（单一来源、封顶、不叠加），
 *       归零走非受伤直接死亡；</li>
 *   <li>污染满 → 缓慢 + 概率生病（<b>不</b>扣健康，避免掉血过快）。</li>
 * </ul>
 * san 归零变怪物仍为 TODO。数值集中在 {@link SixtySecondsBalance}。
 */
public final class SixtySecondsStatsSystem {
    private static final int MAX = SixtySecondsStatsComponent.MAX;
    private static final int MINUTE = 20 * 60;

    private SixtySecondsStatsSystem() {
    }

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        long now = level.getGameTime();
        boolean minuteTick = now % MINUTE == 0;
        SixtySecondsState.Data data = SixtySecondsState.get(level);

        for (ServerPlayer player : level.players()) {
            if (GameUtils.isPlayerEliminated(player)) {
                continue;
            }
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
            if (stats.downed) {
                continue;
            }
            // 怪物/变异者：不消耗饥饿/口渴/san，不累积污染
            if (stats.monster) {
                continue;
            }
            boolean changed = false;

            // 理智上限兜底（杀人永久降上限）：任何漏网的回 san 路径每秒都会被压回上限
            if (stats.sanity > stats.sanityMax) {
                stats.sanity = stats.sanityMax;
                changed = true;
            }

            // 每分钟消耗（在家基准；户外 ×1.2；门被攻破=户外×2=×2.4；前 3 天 -70%）
            if (minuteTick) {
                SixtySecondsState.TeamData team = data.teams.get(stats.teamId);
                // 门被攻破 = 在家也视同户外
                boolean sheltered = (team == null || !team.doorBroken) && isInHome(player, data, stats.teamId);
                double mult;
                if (team != null && team.doorBroken) {
                    mult = SixtySecondsBalance.OUTDOOR_DRAIN_MULT * SixtySecondsBalance.DOOR_BROKEN_DRAIN_MULT;
                } else if (sheltered) {
                    mult = SixtySecondsBalance.HOME_DRAIN_MULT;
                } else {
                    mult = SixtySecondsBalance.OUTDOOR_DRAIN_MULT;
                }
                // 全局 -20% + 前三天 -70% + 第四天起 -35%
                double dayMult = data.dayNumber >= 1 && data.dayNumber <= 3
                        ? SixtySecondsBalance.DRAIN_MULT_EARLY_DAYS
                        : SixtySecondsBalance.DRAIN_MULT_LATE_DAYS;
                double finalMult = mult * SixtySecondsBalance.DRAIN_MULT_GLOBAL * dayMult;
                // 每日事件日级修正：各属性分别 × 事件修正倍率
                double hungerMod = 1.0, thirstMod = 1.0, sanityMod = 1.0, polluteMod = 1.0;
                if (team != null) {
                    hungerMod = team.modifier("drain_hunger");
                    thirstMod = team.modifier("drain_thirst");
                    sanityMod = team.modifier("drain_sanity");
                    polluteMod = team.modifier("drain_pollution");
                }
                stats.hunger = clampDown(stats.hunger,
                        scale(SixtySecondsBalance.HUNGER_DRAIN_PER_MIN, finalMult * hungerMod));
                stats.thirst = clampDown(stats.thirst,
                        scale(SixtySecondsBalance.THIRST_DRAIN_PER_MIN, finalMult * thirstMod));
                stats.sanity = clampDown(stats.sanity,
                        scale(SixtySecondsBalance.SANITY_DRAIN_PER_MIN, finalMult * sanityMod));
                // 污染增速：户外额外 -60%（POLLUTION_OUTDOOR_MULT）；防化服（胸甲位）再减半。
                // 基数小（1/分钟），改用概率进位结算避免小倍率被四舍五入吞成 0/1 两极。
                double pollutionMult = finalMult * SixtySecondsBalance.POLLUTION_DRAIN_MULT;
                if (!sheltered) {
                    pollutionMult *= SixtySecondsBalance.POLLUTION_OUTDOOR_MULT;
                }
                if (player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)
                        .is(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_HAZMAT_SUIT)) {
                    pollutionMult *= 0.5;
                }
                stats.pollution = clampUp(stats.pollution,
                        scaleChance(level, SixtySecondsBalance.POLLUTION_GAIN_PER_MIN, pollutionMult * polluteMod));
                changed = true;
            }

            // 健康保护：饥饿或口渴<b>低于 15</b> 时每秒扣血（单一来源、封顶；不再等到清空为 0 才掉血）
            if ((stats.hunger < 15 || stats.thirst < 15) && stats.health > 0) {
                stats.health = Math.max(0, stats.health - SixtySecondsBalance.HEALTH_LOSS_PER_SEC);
                changed = true;
                if (stats.health <= 0) {
                    SixtySecondsHealthSystem.onHealthZero(player, false, null);
                    continue;
                }
            }

            // 高污染侵蚀健康：≥70 基准每分钟 -3、满(100) -5，整体 ×0.4（-60%，小数按概率进位 ≈1.2/2）
            if (minuteTick && stats.pollution >= SixtySecondsBalance.POLLUTION_HEALTH_THRESHOLD
                    && stats.health > 0) {
                int base = stats.pollution >= MAX ? SixtySecondsBalance.POLLUTION_HEALTH_LOSS_FULL
                        : SixtySecondsBalance.POLLUTION_HEALTH_LOSS_HIGH;
                int loss = scaleChance(level, base, SixtySecondsBalance.POLLUTION_HEALTH_LOSS_MULT);
                stats.health = Math.max(0, stats.health - loss);
                changed = true;
                if (stats.health <= 0) {
                    SixtySecondsHealthSystem.onHealthZero(player, false, null);
                    continue;
                }
            }

            // 污染满：缓慢 + 概率生病
            if (stats.pollution >= MAX) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false, false));
                if (now % SixtySecondsBalance.POLLUTION_SICK_ROLL_INTERVAL == 0
                        && level.getRandom().nextDouble() < SixtySecondsBalance.POLLUTION_SICK_CHANCE) {
                    SixtySecondsSicknessSystem.makeSick(player);
                }
            }

            // 饥饿药水效果：按等级加速饱食度消耗
            // 等级 0→每30秒-1(2/min), 等级 1→每15秒-1(4/min), 等级 2→每10秒-1(6/min)
            MobEffectInstance hungerEffect = player.getEffect(MobEffects.HUNGER);
            if (hungerEffect != null && stats.hunger > 0) {
                int interval = Math.max(1, 30 / (hungerEffect.getAmplifier() + 1));
                if (now % (20L * interval) == 0) {
                    stats.hunger = clampDown(stats.hunger, 1);
                    changed = true;
                }
            }

            // TODO(P2): san 归零一段时间后变怪物 / 自我解脱换队胜。

            if (changed) {
                stats.sync();
            }
        }
    }

    private static boolean isInHome(ServerPlayer player, SixtySecondsState.Data data, int teamId) {
        SixtySecondsState.TeamData team = data.teams.get(teamId);
        if (team == null) {
            return false;
        }
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        return (team.residentialBox != null && team.residentialBox.contains(x, y, z))
                || (team.shelterBox != null && team.shelterBox.contains(x, y, z));
    }

    private static int scale(int base, double mult) {
        return Math.max(0, (int) Math.round(base * mult));
    }

    /** 小倍率概率进位：整数部分照给，小数部分按概率 +1（如 0.38 → 38% 概率给 1）。 */
    private static int scaleChance(ServerLevel level, int base, double mult) {
        double exact = base * mult;
        int amount = (int) exact;
        if (level.getRandom().nextDouble() < exact - amount) {
            amount++;
        }
        return amount;
    }

    private static int clampDown(int value, int amount) {
        return Math.max(0, value - amount);
    }

    private static int clampUp(int value, int amount) {
        return Math.min(MAX, value + amount);
    }
}
