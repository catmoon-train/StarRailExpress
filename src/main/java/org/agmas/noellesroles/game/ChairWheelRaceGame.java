package org.agmas.noellesroles.game;

import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerAFKComponent;
import io.wifi.starrailexpress.cca.SRETrainWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.ServerTaskInfoClasses;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import org.agmas.harpymodloader.events.GameInitializeEvent;
import org.agmas.noellesroles.commands.BroadcastCommand;
import org.agmas.noellesroles.entity.WheelchairEntity;
import org.agmas.noellesroles.init.ModEntities;

import java.util.ArrayList;
import java.util.List;

public class ChairWheelRaceGame extends GameMode {
    public static final ResourceLocation identifier = ResourceLocation.tryBuild("noellesroles", "chair_wheel_race");
    public static final int defaultStartTime = 10;
    public static final int minPlayerCount = 2;

    public ChairWheelRaceGame() {
        super(identifier, defaultStartTime, minPlayerCount);
    }

    private static void executeFunction(CommandSourceStack source, String function) {
        try {
            source.getServer().getCommands().performPrefixedCommand(source, "function " + function);
        } catch (Exception e) {
            Log.warn(LogCategory.GENERAL, "Failed to execute function: " + function + ", error: " + e.getMessage());
        }
    }

    public List<ServerPlayer> isWin = new ArrayList<>();

    @Override
    public void tickServerGameLoop(ServerLevel serverLevel, SREGameWorldComponent gameWorldComponent) {
        // 倒计时逻辑
        if (serverLevel.getGameTime() % 60 == 0) {
            for (ServerPlayer player : serverLevel.players()) {
                SREPlayerAFKComponent.KEY.get(player).updateActivity();
            }
        }
        if (gamePrepareTime > 0) {
            gamePrepareTime--;
            if (gamePrepareTime % 20 == 0) { // 每秒执行一次
                int secondsLeft = gamePrepareTime / 20;
                serverLevel.getServer().getCommands().performPrefixedCommand(
                        serverLevel.getServer().createCommandSourceStack(),
                        "title @a times 5 40 5");
                serverLevel.getServer().getCommands().performPrefixedCommand(
                        serverLevel.getServer().createCommandSourceStack(),
                        "title @a subtitle {\"text\":\"游戏即将开始: " + secondsLeft + " 秒\",\"color\":\"yellow\"}");
                serverLevel.getServer().getCommands().performPrefixedCommand(
                        serverLevel.getServer().createCommandSourceStack(),
                        "title @a title {\"text\":\"准备!\"}");
            }
        }

        serverLevel.players().forEach(player -> {
            if (GameUtils.isPlayerAliveAndSurvival(player)) {
                if (player.getVehicle() instanceof WheelchairEntity wheelchairEntity) {
                    if (serverLevel.getBlockState(player.getOnPos().above(-1)).getBlock() == Blocks.DIAMOND_BLOCK) {
                        isWin.add(player);
                        player.startRiding(wheelchairEntity);
                        wheelchairEntity.remove(Entity.RemovalReason.DISCARDED);
                        player.setGameMode(GameType.SPECTATOR);
                        serverLevel.players().forEach(op -> {
                            op.playNotifySound(SoundEvents.FIREWORK_ROCKET_SHOOT, SoundSource.PLAYERS, 1.0F, 1.0F);
                            op.playNotifySound(SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0F, 1.0F);
                        });
                        Component msg = Component.translatable("announcement.star.wheelgame.win.star.prefix")
                                .withStyle(ChatFormatting.GOLD)
                                .append(Component.translatable("announcement.star.wheelgame.win.star.rank",
                                        player.getScoreboardName(), isWin.indexOf(player) + 1))
                                .withStyle(ChatFormatting.AQUA);
                        serverLevel.players().forEach((o) -> {
                            BroadcastCommand.BroadcastMessage(o, msg);
                        });
                        executeFunction(player.createCommandSourceStack(),
                                "harpymodloader:chair_wheel_race/win");
                    }
                }
            }
        });

        if (!((SREGameTimeComponent) SREGameTimeComponent.KEY.get(serverLevel)).hasTime()
                || isWin.size() >= serverLevel.getPlayers(GameUtils::isPlayerAliveAndSurvival).size()) {
            endGame(serverLevel, gameWorldComponent);
        }
    }

    int gamePrepareTime = 0;

    public void endGame(ServerLevel serverLevel, SREGameWorldComponent gameWorldComponent) {
        var roundComponent = SREGameRoundEndComponent.KEY.get(serverLevel);
        roundComponent.CustomWinnerID = "chiar_wheel_race";
        // roundComponent
        var player = isWin.isEmpty() ? null : isWin.getFirst();
        roundComponent.CustomWinnerSubtitle = Component.translatable("game.win.star.chair_wheel_race.subtitle");
        roundComponent.CustomWinnerTitle = Component.translatable("game.win.star.chair_wheel_race",
                player == null ? "滚木" : player.getScoreboardName());
        roundComponent.setWinStatus(GameUtils.WinStatus.CUSTOM_COMPONENT);
        roundComponent.sync();
        executeFunction(serverLevel.getServer().createCommandSourceStack(), "harpymodloader:chair_wheel_race/over");
        GameUtils.stopGame(serverLevel);
    }

    @Override
    public void initializeGame(ServerLevel serverLevel, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> list) {
        GameInitializeEvent.EVENT.invoker().initializeGame(serverLevel, gameWorldComponent, list);
        ((SRETrainWorldComponent) SRETrainWorldComponent.KEY.get(serverLevel))
                .setTimeOfDay(SRETrainWorldComponent.TimeOfDay.DAY);
        isWin.clear();
        gamePrepareTime = 20 * 10;
        executeFunction(serverLevel.getServer().createCommandSourceStack(), "harpymodloader:chair_wheel_race/init");
        for (ServerPlayer player : list) {
            player.addEffect(new MobEffectInstance(MobEffects.BAD_OMEN, 20 * 5));
            gameWorldComponent.addRole(player, TMMRoles.DISCOVERY_CIVILIAN, false);
        }
        GameUtils.serverAsynTaskLists.add(new ServerTaskInfoClasses.SchedulerTask(120, () -> {
            for (ServerPlayer player : list) {
                var chair = new WheelchairEntity(ModEntities.WHEELCHAIR, serverLevel);
                chair.setPos(player.getX(), player.getY(), player.getZ());
                serverLevel.addFreshEntity(chair);
                player.startRiding(chair, true);
            }
        }));
        gameWorldComponent.syncRoles();
    }
}
