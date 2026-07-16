package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.arena.SixtySecondsSearchZones;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.agmas.noellesroles.init.ModItems;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 天气/环境事件系统。每 {@link SixtySecondsBalance#EVENT_CHECK_INTERVAL} 尝试从事件池随机触发：
 * <ul>
 *   <li><b>污雨</b>：野外（搜索区）无雨伞每秒额外污染；下雨天气。</li>
 *   <li><b>浓烟</b>：不在家（住宅/避难所外）每 2 秒额外污染 + 失明脉冲；雨伞无效。</li>
 *   <li><b>寒潮</b>：不在家者饥饿加速消耗 + 缓慢。</li>
 *   <li><b>空投物资</b>（瞬发）：每队避难所出生点旁落一箱随机物资（按 loot 表多类别掷出）。</li>
 * </ul>
 */
public final class SixtySecondsEventSystem {
    public enum EventType {
        POLLUTION_RAIN, SMOG, COLD_SNAP, AIRDROP,
        /** 酸雾：户外中毒 + 持续扣血 */
        ACID_FOG,
        /** 电磁风暴：全队虚弱 + 怪物强化 + 理智下降 */
        ELECTROMAGNETIC_STORM,
        /** 虫潮：户外缓慢 + 持续受击掉血 */
        SWARM,
        /** 热浪：全队虚弱 + 口渴消耗加速 */
        HEAT_WAVE
    }

    private static final Map<ServerLevel, Active> ACTIVE = new WeakHashMap<>();

    private record Active(EventType type, long endTick) {
    }

    private SixtySecondsEventSystem() {
    }

    /** 收音机：当前进行中事件的播报键（无事件返回 null）。 */
    public static String activeEventKey(ServerLevel level) {
        Active active = ACTIVE.get(level);
        if (active == null) {
            return null;
        }
        return switch (active.type) {
            case POLLUTION_RAIN -> "message.noellesroles.sixty_seconds.event_pollution_rain_start";
            case SMOG -> "message.noellesroles.sixty_seconds.event_smog_start";
            case COLD_SNAP -> "message.noellesroles.sixty_seconds.event_cold_start";
            case ACID_FOG -> "message.noellesroles.sixty_seconds.event_acid_fog_start";
            case ELECTROMAGNETIC_STORM -> "message.noellesroles.sixty_seconds.event_em_storm_start";
            case SWARM -> "message.noellesroles.sixty_seconds.event_swarm_start";
            case HEAT_WAVE -> "message.noellesroles.sixty_seconds.event_heat_wave_start";
            default -> null;
        };
    }

    public static void tick(ServerLevel level) {
        long now = level.getGameTime();
        Active active = ACTIVE.get(level);
        if (active != null) {
            if (now >= active.endTick) {
                endEvent(level, active);
                ACTIVE.remove(level);
            } else {
                applyEvent(level, active, now);
            }
            return;
        }
        if (now % SixtySecondsBalance.EVENT_CHECK_INTERVAL == 0
                && level.getRandom().nextDouble() < SixtySecondsBalance.EVENT_CHANCE) {
            EventType[] pool = EventType.values();
            startEvent(level, pool[level.getRandom().nextInt(pool.length)], now);
        }
    }

    private static void startEvent(ServerLevel level, EventType type, long now) {
        switch (type) {
            case POLLUTION_RAIN -> {
                ACTIVE.put(level, new Active(type, now + SixtySecondsBalance.POLLUTION_RAIN_DURATION));
                level.setWeatherParameters(0, SixtySecondsBalance.POLLUTION_RAIN_DURATION, true, false);
                broadcast(level, Component.translatable("message.noellesroles.sixty_seconds.event_pollution_rain_start")
                        .withStyle(ChatFormatting.DARK_GREEN));
                subtitleAll(level, "message.noellesroles.sixty_seconds.event_pollution_rain_name",
                        "message.noellesroles.sixty_seconds.event_pollution_rain_start", ChatFormatting.DARK_GREEN);
            }
            case SMOG -> {
                ACTIVE.put(level, new Active(type, now + SixtySecondsBalance.SMOG_DURATION));
                broadcast(level, Component.translatable("message.noellesroles.sixty_seconds.event_smog_start")
                        .withStyle(ChatFormatting.DARK_GRAY));
                subtitleAll(level, "message.noellesroles.sixty_seconds.event_smog_name",
                        "message.noellesroles.sixty_seconds.event_smog_start", ChatFormatting.GRAY);
            }
            case COLD_SNAP -> {
                ACTIVE.put(level, new Active(type, now + SixtySecondsBalance.COLD_SNAP_DURATION));
                broadcast(level, Component.translatable("message.noellesroles.sixty_seconds.event_cold_start")
                        .withStyle(ChatFormatting.AQUA));
                subtitleAll(level, "message.noellesroles.sixty_seconds.event_cold_name",
                        "message.noellesroles.sixty_seconds.event_cold_start", ChatFormatting.AQUA);
            }
            case ACID_FOG -> {
                ACTIVE.put(level, new Active(type, now + SixtySecondsBalance.EVENT_BASE_DURATION));
                broadcast(level, Component.translatable("message.noellesroles.sixty_seconds.event_acid_fog_start")
                        .withStyle(ChatFormatting.GREEN));
                subtitleAll(level, "message.noellesroles.sixty_seconds.event_acid_fog_name",
                        "message.noellesroles.sixty_seconds.event_acid_fog_start", ChatFormatting.GREEN);
            }
            case ELECTROMAGNETIC_STORM -> {
                ACTIVE.put(level, new Active(type, now + SixtySecondsBalance.EVENT_BASE_DURATION));
                broadcast(level, Component.translatable("message.noellesroles.sixty_seconds.event_em_storm_start")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                subtitleAll(level, "message.noellesroles.sixty_seconds.event_em_storm_name",
                        "message.noellesroles.sixty_seconds.event_em_storm_start", ChatFormatting.LIGHT_PURPLE);
            }
            case SWARM -> {
                ACTIVE.put(level, new Active(type, now + SixtySecondsBalance.EVENT_BASE_DURATION));
                broadcast(level, Component.translatable("message.noellesroles.sixty_seconds.event_swarm_start")
                        .withStyle(ChatFormatting.DARK_RED));
                subtitleAll(level, "message.noellesroles.sixty_seconds.event_swarm_name",
                        "message.noellesroles.sixty_seconds.event_swarm_start", ChatFormatting.DARK_RED);
            }
            case HEAT_WAVE -> {
                ACTIVE.put(level, new Active(type, now + SixtySecondsBalance.EVENT_BASE_DURATION));
                broadcast(level, Component.translatable("message.noellesroles.sixty_seconds.event_heat_wave_start")
                        .withStyle(ChatFormatting.RED));
                subtitleAll(level, "message.noellesroles.sixty_seconds.event_heat_wave_name",
                        "message.noellesroles.sixty_seconds.event_heat_wave_start", ChatFormatting.RED);
            }
            case AIRDROP -> airdrop(level); // 瞬发，不进 ACTIVE
        }
    }

    /** 事件开始的 SubtitleHUD TOP 播报：大字=事件名，小字=事件描述（与聊天栏广播并行）。 */
    private static void subtitleAll(ServerLevel level, String nameKey, String descKey, ChatFormatting color) {
        for (ServerPlayer player : level.players()) {
            net.exmo.sre.subtitle.SubtitleCommand.sendToPlayerTop(player,
                    Component.translatable(nameKey).withStyle(color),
                    Component.translatable(descKey), 80, false);
        }
    }

    private static void applyEvent(ServerLevel level, Active active, long now) {
        if (now % 20 != 0) {
            return;
        }
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        for (ServerPlayer player : level.players()) {
            if (GameUtils.isPlayerEliminated(player)) {
                continue;
            }
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
            if (stats.downed || stats.monster) {
                continue;
            }
            boolean inHome = isInHome(player, data, stats.teamId);
            switch (active.type) {
                case POLLUTION_RAIN -> {
                    if (!SixtySecondsSearchZones.isInSearchZone(player) || hasUmbrella(player)
                            || hasGasMask(player)) {
                        continue;
                    }
                    // 无伞淋污雨：每 10 秒结算一次（增速较旧版 2/秒 已降 65%）
                    if (now % (20 * 10) == 0) {
                        addPollution(stats, SixtySecondsBalance.POLLUTION_RAIN_GAIN_PER_10S);
                    }
                }
                case SMOG -> {
                    if (inHome || hasGasMask(player)) {
                        continue; // 防毒面具：浓烟免疫（雨伞对浓烟无效）
                    }
                    // 浓烟视野限制：15 级失明，每秒刷新（离开浓烟/事件结束后约 1 秒自动消退）
                    if (now % 20 == 0) {
                        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 25, 14, false, false, false));
                    }
                    // 每 2 秒结算一次（增速较旧版 1/秒 已降 50%）
                    if (now % (20 * 2) == 0) {
                        addPollution(stats, SixtySecondsBalance.SMOG_POLLUTION_PER_2S);
                    }
                }
                case COLD_SNAP -> {
                    if (inHome) {
                        continue;
                    }
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0,
                            false, false, false));
                    if (now % (20 * 10) == 0) {
                        stats.hunger = Math.max(0, stats.hunger - SixtySecondsBalance.COLD_HUNGER_PER_10S);
                        stats.sync();
                    }
                }
                case ACID_FOG -> {
                    // 酸雾：户外中毒效果 + 持续扣血
                    if (inHome && hasGasMask(player)) {
                        continue;
                    }
                    if (!inHome) {
                        player.addEffect(new MobEffectInstance(MobEffects.POISON, 40, 0, false, false, false));
                        if (now % (20 * 5) == 0) {
                            player.hurt(player.damageSources().magic(), 1.0F);
                        }
                    }
                    if (now % (20 * 10) == 0) {
                        addPollution(stats, 2);
                    }
                }
                case ELECTROMAGNETIC_STORM -> {
                    // 电磁风暴：全员虚弱 + 理智下降 + 怪物获得强化
                    if (!player.hasEffect(MobEffects.WEAKNESS)) {
                        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0, false, false, false));
                    }
                    if (now % (20 * 15) == 0) {
                        stats.sanity = Math.max(0, stats.sanity - 3);
                        stats.sync();
                    }
                }
                case SWARM -> {
                    // 虫潮：户外缓慢 + 持续受击
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0,
                            false, false, false));
                    if (!inHome && now % (20 * 8) == 0) {
                        player.hurt(player.damageSources().generic(), 0.5F);
                    }
                    if (now % (20 * 10) == 0) {
                        stats.sanity = Math.max(0, stats.sanity - 2);
                        stats.sync();
                    }
                }
                case HEAT_WAVE -> {
                    // 热浪：全员虚弱 + 口渴消耗加速
                    if (!player.hasEffect(MobEffects.WEAKNESS)) {
                        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0, false, false, false));
                    }
                    // 每 5 秒额外消耗口渴
                    if (now % (20 * 5) == 0) {
                        stats.thirst = Math.max(0, stats.thirst - 1);
                        stats.sync();
                    }
                }
                default -> {
                }
            }
        }
    }

    /**
     * 空投：走 {@link SixtySecondsAirdrop} 在共用探索区内可见下落一个<b>一次性奖励箱</b>
     * （一次搜出 {@link SixtySecondsBalance#AIRDROP_ROLLS} 件），落地广播具体坐标。
     */
    private static void airdrop(ServerLevel level) {
        if (SixtySecondsAirdrop.dropRandom(level)) {
            broadcast(level, Component.translatable("message.noellesroles.sixty_seconds.event_airdrop")
                    .withStyle(ChatFormatting.GOLD));
            subtitleAll(level, "message.noellesroles.sixty_seconds.event_airdrop_name",
                    "message.noellesroles.sixty_seconds.event_airdrop", ChatFormatting.GOLD);
        }
    }

    private static void endEvent(ServerLevel level, Active active) {
        switch (active.type) {
            case POLLUTION_RAIN -> {
                level.setWeatherParameters(0, 0, false, false);
                broadcast(level, Component.translatable("message.noellesroles.sixty_seconds.event_pollution_rain_end")
                        .withStyle(ChatFormatting.GRAY));
            }
            case SMOG -> {
                for (ServerPlayer p : level.players()) {
                    p.removeEffect(MobEffects.BLINDNESS);
                }
                broadcast(level, Component.translatable(
                        "message.noellesroles.sixty_seconds.event_smog_end").withStyle(ChatFormatting.GRAY));
            }
            case COLD_SNAP -> broadcast(level, Component.translatable(
                    "message.noellesroles.sixty_seconds.event_cold_end").withStyle(ChatFormatting.GRAY));
            case ACID_FOG -> {
                for (ServerPlayer p : level.players()) {
                    p.removeEffect(MobEffects.POISON);
                }
                broadcast(level, Component.translatable(
                        "message.noellesroles.sixty_seconds.event_acid_fog_end").withStyle(ChatFormatting.GRAY));
            }
            case ELECTROMAGNETIC_STORM -> {
                for (ServerPlayer p : level.players()) {
                    p.removeEffect(MobEffects.WEAKNESS);
                }
                broadcast(level, Component.translatable(
                        "message.noellesroles.sixty_seconds.event_em_storm_end").withStyle(ChatFormatting.GRAY));
            }
            case SWARM -> broadcast(level, Component.translatable(
                    "message.noellesroles.sixty_seconds.event_swarm_end").withStyle(ChatFormatting.GRAY));
            case HEAT_WAVE -> {
                for (ServerPlayer p : level.players()) {
                    p.removeEffect(MobEffects.WEAKNESS);
                }
                broadcast(level, Component.translatable(
                        "message.noellesroles.sixty_seconds.event_heat_wave_end").withStyle(ChatFormatting.GRAY));
            }
            default -> {
            }
        }
    }

    private static void addPollution(SixtySecondsStatsComponent stats, int amount) {
        int before = stats.pollution;
        stats.pollution = Math.min(SixtySecondsStatsComponent.MAX, stats.pollution + amount);
        if (stats.pollution != before) {
            stats.sync();
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

    private static boolean hasUmbrella(ServerPlayer player) {
        return player.getMainHandItem().is(ModItems.SIXTY_SECONDS_UMBRELLA)
                || player.getOffhandItem().is(ModItems.SIXTY_SECONDS_UMBRELLA);
    }

    /** 防毒面具（头部佩戴）：污雨/浓烟污染免疫。 */
    private static boolean hasGasMask(ServerPlayer player) {
        return player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)
                .is(ModItems.SIXTY_SECONDS_GAS_MASK);
    }

    private static void broadcast(ServerLevel level, Component message) {
        for (ServerPlayer player : level.players()) {
            player.displayClientMessage(message, false);
        }
    }

    public static void reset(ServerLevel level) {
        ACTIVE.remove(level);
    }
}
