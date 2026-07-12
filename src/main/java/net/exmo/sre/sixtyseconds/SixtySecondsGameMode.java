package net.exmo.sre.sixtyseconds;

import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.exmo.sre.sixtyseconds.arena.SixtySecondsArena;
import net.exmo.sre.sixtyseconds.arena.SixtySecondsSearchZones;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsManager;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * 末日60秒模式入口：生命周期只做编排，具体逻辑在 {@code logic} / {@code arena} / {@code state} 包中。
 * 走标准 startGame 路径（默认 {@code beforeInitializeGame} → baseInitialize）；60s 地图请设
 * {@code AreasSettings.noReset=true}，模板方能不被 FullTrainReset 覆盖。
 */
public class SixtySecondsGameMode extends GameMode {

    public SixtySecondsGameMode(ResourceLocation identifier) {
        super(identifier, 60, 1);
    }

    @Override
    public boolean hasMood() {
        return false;
    }

    @Override
    public boolean shouldRecordPlayerStats() {
        return false;
    }

    /** 家庭身份不是 SRERole：允许没有职业的存活生还者（否则会被强制变旁观）。 */
    @Override
    public boolean requiresAssignedRole() {
        return false;
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        SixtySecondsSearchZones.reset(serverWorld);
        SixtySecondsArena.restoreAll(serverWorld);
        SixtySecondsState.reset(serverWorld);
        SixtySecondsManager.begin(serverWorld, gameWorldComponent, players);
    }

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        super.tickServerGameLoop(serverWorld, gameWorldComponent);
        SixtySecondsManager.tick(serverWorld, gameWorldComponent);
    }

    @Override
    public void stopGame(ServerLevel world) {
        net.exmo.sre.sixtyseconds.SixtySecondsMod.RUNNING = false;
        net.exmo.sre.sixtyseconds.logic.SixtySecondsInventoryLimit.clear(world);
        net.exmo.sre.sixtyseconds.logic.SixtySecondsVisitSystem.reset();
        net.exmo.sre.sixtyseconds.logic.SixtySecondsVisitChat.reset();
        net.exmo.sre.sixtyseconds.logic.SixtySecondsTrade.reset();
        net.exmo.sre.sixtyseconds.logic.SixtySecondsEventSystem.reset(world);
        net.exmo.sre.sixtyseconds.logic.SixtySecondsMinigameRotation.reset(world);
        net.exmo.sre.sixtyseconds.logic.SixtySecondsDoorHighlight.reset(world);
        SixtySecondsSearchZones.reset(world);
        SixtySecondsArena.restoreAll(world);
        SixtySecondsState.reset(world);
    }
}
