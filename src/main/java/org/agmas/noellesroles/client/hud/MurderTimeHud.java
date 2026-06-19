package org.agmas.noellesroles.client.hud;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.cca.MurderTimeEventComponent;
import io.wifi.starrailexpress.cca.MurderTimeEventComponent.MurderTimeEvent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.client.StatusBarHUD;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MurderTimeHud {
    private static final Set<String> ACTIVE_STATUS_IDS = new HashSet<>();

    public static void register() {
        CommonHudRenderCallback.EVENT.register((graphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.level == null || SREClient.gameComponent == null) {
                clearStatusBars();
                return;
            }
            if (!SREClient.gameComponent.isRunning()
                    || SREClient.gameComponent.getGameMode() != SREGameModes.MURDER) {
                clearStatusBars();
                return;
            }

            MurderTimeEventComponent component = MurderTimeEventComponent.KEY.get(client.level);
            if (!component.isEnabled() || !component.isHudEnabled() || !canSeeMurderTime(client)) {
                clearStatusBars();
                return;
            }

            int remainingTicks = Math.max(0, SREGameTimeComponent.KEY.get(client.level).getTime());
            int elapsedTicks = Math.max(0, SREGameTimeComponent.KEY.get(client.level).getResetTime() - remainingTicks);

            Set<String> visibleNow = new HashSet<>();
            int shown = 0;
            for (MurderTimeEvent event : component.getEvents().stream()
                    .filter(event -> event.shouldShowInHud(elapsedTicks))
                    .sorted(Comparator.comparingInt(MurderTimeEvent::elapsedTicks))
                    .toList()) {
                String statusId = "murder_time_" + event.id();
                visibleNow.add(statusId);
                Component name = event.isWarning(elapsedTicks)
                        ? Component.translatable("hud.noellesroles.murder_time.warning",
                                formatTicks(Math.max(0, event.elapsedTicks() - elapsedTicks)), event.displayName())
                        : Component.translatable("hud.noellesroles.murder_time.active", event.displayName());
                float progress = event.hudProgress(elapsedTicks);
                StatusBarHUD.getInstance().addStatusBar(statusId,
                        name.copy().withStyle(event.isWarning(elapsedTicks) ? ChatFormatting.YELLOW : ChatFormatting.GOLD)
                                .getString(),
                        () -> progress,
                        1000L);
                shown++;
                if (shown >= 3) {
                    break;
                }
            }
            ACTIVE_STATUS_IDS.removeIf(id -> {
                if (visibleNow.contains(id)) {
                    return false;
                }
                StatusBarHUD.getInstance().removeStatusBar(id);
                return true;
            });
            ACTIVE_STATUS_IDS.addAll(visibleNow);
        });
    }

    private static boolean canSeeMurderTime(Minecraft client) {
        if (client.player == null || SREClient.gameComponent == null) {
            return false;
        }
        SRERole role = SREClient.gameComponent.getRole(client.player);
        return (role != null && role.canSeeTime())
                || GameUtils.isPlayerSpectatingOrCreative(client.player)
                || SREClient.cachedCanSeeTime;
    }

    private static void clearStatusBars() {
        ACTIVE_STATUS_IDS.forEach(id -> StatusBarHUD.getInstance().removeStatusBar(id));
        ACTIVE_STATUS_IDS.clear();
    }

    private static String formatTicks(int ticks) {
        int totalSeconds = Math.max(0, ticks / 20);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }
}
