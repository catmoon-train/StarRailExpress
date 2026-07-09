package net.exmo.sre.repair.logic;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.repair.state.RepairModeState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.component.ModComponents;
import net.exmo.sre.repair.content.block_entity.HunterCageBlockEntity;

/** 每秒向所有玩家同步修机 HUD 状态（修机进度、闸门供电、受审/倒地人数）。 */
public final class RepairHudSync {
    private RepairHudSync() {
    }

    public static void tick(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        if (serverWorld.getGameTime() % 20 != 0) {
            return;
        }
        int completed = RepairModeState.getCompletedStationCount(serverWorld);
        int total = RepairModeState.getTotalStationCount(serverWorld);
        int speedPercent = RepairModeState.repairSpeedPercent(serverWorld);
        boolean gatesPowered = RepairModeState.areExitGatesPowered(serverWorld);
        int activeTrialPrisoners = 0;
        for (ServerPlayer player : serverWorld.players()) {
            if (ModComponents.REPAIR_ROLES.get(player).trialStand.present()) {
                activeTrialPrisoners++;
            }
        }
        for (ServerPlayer player : serverWorld.players()) {
            var component = ModComponents.REPAIR_ROLES.get(player);
            component.completedStations = completed;
            component.totalStations = total;
            component.repairSpeedPercent = speedPercent;
            component.gatesPowered = gatesPowered;
            component.activeTrialPrisoners = activeTrialPrisoners;
            component.downedAllies = countDownedAllies(serverWorld, gameWorldComponent, player);
            component.nearestTrialProgress = component.trialStand.present()
                    && serverWorld
                            .getBlockEntity(component.trialStand.toBlockPos()) instanceof HunterCageBlockEntity cage
                                    ? cage.getProgress(player.getUUID())
                                    : 0;
            component.sync();
        }
    }

    private static int countDownedAllies(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            ServerPlayer viewer) {
        boolean viewerHunter = RepairModeState.isHunter(viewer);
        int count = 0;
        for (ServerPlayer other : serverWorld.players()) {
            if (other == viewer || GameUtils.isPlayerEliminated(other)) {
                continue;
            }
            var otherComponent = ModComponents.REPAIR_ROLES.get(other);
            if (!otherComponent.downed) {
                continue;
            }
            boolean otherHunter = RepairModeState.isHunter(other);
            if (viewerHunter == otherHunter || (!viewerHunter && !otherHunter)) {
                count++;
            }
        }
        return count;
    }
}
