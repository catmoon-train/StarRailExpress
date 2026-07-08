package net.exmo.sre.repair;

import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.exmo.sre.repair.arena.RepairArenaBuilder;
import net.exmo.sre.repair.arena.RepairLootSpawner;
import net.exmo.sre.repair.event.RepairEventSystem;
import net.exmo.sre.repair.logic.RepairCarrySystem;
import net.exmo.sre.repair.logic.RepairGameSetup;
import net.exmo.sre.repair.logic.RepairHudSync;
import net.exmo.sre.repair.logic.RepairRolePassives;
import net.exmo.sre.repair.logic.RepairRoleSelection;
import net.exmo.sre.repair.logic.RepairWinConditions;
import net.exmo.sre.repair.role.RepairForcedRoleState;
import net.exmo.sre.repair.state.RepairLockedDoorState;
import net.exmo.sre.repair.state.RepairModeState;
import net.exmo.sre.repair.state.RepairSearchState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * 修机逃脱模式入口：生命周期钩子只做编排，
 * 具体逻辑拆分在 {@link net.exmo.sre.repair.logic} 包中。
 */
public class RepairEscapeGameMode extends GameMode {
    private final RepairRoleSelection roleSelection = new RepairRoleSelection();

    public RepairEscapeGameMode(ResourceLocation identifier) {
        super(identifier, 14, 2);
    }

    @Override
    public boolean shouldRecordPlayerStats() {
        return true;
    }

    @Override
    public boolean hasMood() {
        return false;
    }

    @Override
    public void beforeInitializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        // 修机模式不调用 baseInitialize，避免加载正常 Areas 配置
        RepairGameSetup.prepareWorld(serverWorld, gameWorldComponent, players);
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        RepairModeState.reset(serverWorld);
        RepairEventSystem.reset(serverWorld);
        roleSelection.begin(serverWorld, gameWorldComponent, players);
    }

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        roleSelection.tick(serverWorld, gameWorldComponent);
        if (roleSelection.isFinalized()) {
            RepairRolePassives.tick(serverWorld, gameWorldComponent);
            RepairEventSystem.tick(serverWorld);
            RepairSearchState.tick(serverWorld);
        }
        RepairCarrySystem.tick(serverWorld);
        RepairHudSync.tick(serverWorld, gameWorldComponent);
        if (!roleSelection.isFinalized()) {
            return;
        }
        RepairWinConditions.tick(serverWorld, gameWorldComponent);
    }

    @Override
    public void stopGame(ServerLevel world) {
        RepairArenaBuilder.restoreAll(world);
        RepairLootSpawner.reset(world);
        RepairLockedDoorState.reset(world);
        RepairModeState.reset(world);
        RepairEventSystem.reset(world);
        RepairForcedRoleState.clearAll();
        roleSelection.reset();
    }
}
