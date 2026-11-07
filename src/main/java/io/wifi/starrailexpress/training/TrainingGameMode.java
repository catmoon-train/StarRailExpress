package io.wifi.starrailexpress.training;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;

/** 训练保持真实职业、伤害和死亡事件，但不使用比赛分配、计时胜负或结算。 */
public final class TrainingGameMode extends GameMode {
    public static final net.minecraft.resources.ResourceLocation ID = SRE.id("training");
    public TrainingGameMode() { super(ID, 0, 1); }
    @Override public void initializeGame(ServerLevel level, SREGameWorldComponent game, List<ServerPlayer> players) { }
    @Override public boolean shouldRecordPlayerStats() { return false; }
    @Override public boolean shouldStopWhenNoPlayers() { return false; }
    @Override public boolean enableEnvironmentDetection() { return false; }
    @Override public boolean enablePlayAreaDetections() { return false; }
    @Override public boolean hasSafeTime() { return false; }
    @Override public boolean hasMood() { return false; }
    @Override public boolean suppressMoodTasks() { return true; }
    @Override public boolean canHaveMeetingVote() { return false; }
    @Override public boolean canHaveMeeting() { return false; }
    @Override public boolean shouldGrantDefaultQuestMoneyOnFinish() { return false; }
    @Override public boolean shouldGiveKillerBalance(net.minecraft.world.entity.player.Player victim, net.minecraft.world.entity.player.Player killer, net.minecraft.resources.ResourceLocation reason) { return false; }
    @Override public void tickServerGameLoop(ServerLevel level, SREGameWorldComponent game) { }
    @Override public void killPlayer(net.minecraft.world.entity.player.Player victim, boolean spawnBody,
            net.minecraft.world.entity.player.Player killer, net.minecraft.resources.ResourceLocation reason, boolean forceDeath) {
        if (killer instanceof ServerPlayer actor && !TrainingRules.canInteract(actor, victim)) return;
        super.killPlayer(victim, spawnBody, killer, reason, forceDeath);
    }
    @Override public boolean isPlayerWinning(ServerLevel level, ServerPlayer player, io.wifi.starrailexpress.api.SRERole role,
            io.wifi.starrailexpress.cca.SREGameRoundEndComponent end, SREGameWorldComponent game) { return false; }
    @Override public void limitSpectatorPlayer(ServerPlayer player, SREGameWorldComponent game, AreasWorldComponent areas) { }
}
