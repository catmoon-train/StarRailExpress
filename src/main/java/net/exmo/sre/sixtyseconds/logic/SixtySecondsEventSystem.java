package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.arena.SixtySecondsSearchZones;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.init.ModItems;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 天气/环境事件系统。定期尝试触发事件，并提供反制道具。
 * <ul>
 *   <li><b>污雨</b>：野外（搜索区）玩家<b>无雨伞</b>时每秒额外污染；持有 {@code SIXTY_SECONDS_UMBRELLA} 免除。</li>
 * </ul>
 * 后续可加：浓烟、商人等（在 {@link EventType} 扩展）。数值集中在 {@link SixtySecondsBalance}。
 */
public final class SixtySecondsEventSystem {
    public enum EventType {
        POLLUTION_RAIN
    }

    private static final Map<ServerLevel, Active> ACTIVE = new WeakHashMap<>();

    private record Active(EventType type, long endTick) {
    }

    private SixtySecondsEventSystem() {
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
            startPollutionRain(level, now);
        }
    }

    private static void startPollutionRain(ServerLevel level, long now) {
        ACTIVE.put(level, new Active(EventType.POLLUTION_RAIN, now + SixtySecondsBalance.POLLUTION_RAIN_DURATION));
        level.setWeatherParameters(0, SixtySecondsBalance.POLLUTION_RAIN_DURATION, true, false);
        broadcast(level, Component.translatable("message.noellesroles.sixty_seconds.event_pollution_rain_start")
                .withStyle(ChatFormatting.DARK_GREEN));
    }

    private static void applyEvent(ServerLevel level, Active active, long now) {
        if (active.type != EventType.POLLUTION_RAIN || now % 20 != 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (GameUtils.isPlayerEliminated(player) || !SixtySecondsSearchZones.isInSearchZone(player)
                    || hasUmbrella(player)) {
                continue;
            }
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
            int before = stats.pollution;
            stats.pollution = Math.min(SixtySecondsStatsComponent.MAX,
                    stats.pollution + SixtySecondsBalance.POLLUTION_RAIN_GAIN_PER_SEC);
            if (stats.pollution != before) {
                stats.sync();
            }
        }
    }

    private static void endEvent(ServerLevel level, Active active) {
        if (active.type == EventType.POLLUTION_RAIN) {
            level.setWeatherParameters(SixtySecondsBalance.POLLUTION_RAIN_DURATION, 0, false, false);
            broadcast(level, Component.translatable("message.noellesroles.sixty_seconds.event_pollution_rain_end")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static boolean hasUmbrella(ServerPlayer player) {
        return player.getMainHandItem().is(ModItems.SIXTY_SECONDS_UMBRELLA)
                || player.getOffhandItem().is(ModItems.SIXTY_SECONDS_UMBRELLA);
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
