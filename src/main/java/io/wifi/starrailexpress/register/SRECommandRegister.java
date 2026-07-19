package io.wifi.starrailexpress.register;

import org.agmas.noellesroles.commands.GameUtilsCommand;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.command.*;
import io.wifi.starrailexpress.content.command.argument.GameModeArgumentType;
import io.wifi.starrailexpress.content.command.argument.MapLoadArgumentType;
import io.wifi.starrailexpress.content.command.argument.SkinArgumentType;
import io.wifi.starrailexpress.content.command.argument.TimeOfDayArgumentType;
import io.wifi.starrailexpress.content.vote.command.SREVoteCommand;
import net.exmo.sre.mod_whitelist.server.command.ModWhitelistCommand;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;

/**
 * 命令参数类型与命令注册，从 {@link SRE#onInitialize()} 中按类别剥离归一化而来。
 */
public class SRECommandRegister {

    public static void registerCommandArgumentTypes() {
        ArgumentTypeRegistry.registerArgumentType(SRE.id("timeofday"), TimeOfDayArgumentType.class,
                SingletonArgumentInfo.contextFree(TimeOfDayArgumentType::timeofday));
        ArgumentTypeRegistry.registerArgumentType(SRE.id("gamemode"), GameModeArgumentType.class,
                SingletonArgumentInfo.contextFree(GameModeArgumentType::gameMode));
        ArgumentTypeRegistry.registerArgumentType(SRE.id("skin"), SkinArgumentType.class,
                SingletonArgumentInfo.contextFree(SkinArgumentType::string));
        ArgumentTypeRegistry.registerArgumentType(SRE.id("map_load"), MapLoadArgumentType.class,
                SingletonArgumentInfo.contextFree(MapLoadArgumentType::string));
    }

    public static void registerCommands() {
        StreamingSpectatorCommand.registerEvents();
        CommandRegistrationCallback.EVENT.register(((dispatcher, registryAccess, environment) -> {
            ServerUtilsCommands.register(dispatcher, registryAccess, environment);
            ModWhitelistCommand.registerGlobal(dispatcher);
            SREHelpCommand.register(dispatcher);
            SREVoteCommand.register(dispatcher, registryAccess);
            NarratorCommand.register(dispatcher, registryAccess);
            GiveRoomKeyCommand.register(dispatcher);
            ListRoleInRoundCommand.register(dispatcher);
            NonOPKickCommand.register(dispatcher, registryAccess);
            SetVisualCommand.register(dispatcher);
            ForceTeamCommand.register(dispatcher);
            SetTimerCommand.register(dispatcher);
            SetDeathPenaltyCommand.register(dispatcher);
            MoneyCommand.register(dispatcher);
            CustomReplayEventCommand.register(dispatcher, registryAccess);
            ReplayScreenCommand.register(dispatcher);
            net.exmo.sre.record.MatchRecordCommand.register(dispatcher);
            SetAutoTrainResetCommand.register(dispatcher);
            SetBoundCommand.register(dispatcher);
            AutoStartCommand.register(dispatcher);
            ParticipationCommand.register(dispatcher);
            AutoShutdownWhenNotRunningCommand.register(dispatcher);
            ConfigCommand.register(dispatcher);
            SwitchMapCommand.register(dispatcher);
            MapManagerCommand.register(dispatcher);
            ReloadReadyAreaCommand.register(dispatcher);
            EntityDataCommand.register(dispatcher);
            MoodChangeCommand.register(dispatcher);
            io.wifi.starrailexpress.content.command.MapVoteCommand.register(dispatcher);
            io.wifi.starrailexpress.content.command.CreateWaypointCommand.register(dispatcher);
            io.wifi.starrailexpress.content.command.DeleteWaypointCommand.register(dispatcher);
            io.wifi.starrailexpress.content.command.ToggleWaypointsCommand.register(dispatcher);
            AFKCommand.register(dispatcher);
            ShowStatsCommand.register(dispatcher);
            ShowSelectedMapUICommand.register(dispatcher);
            NetworkStatsCommand.register(dispatcher);
            FourthRoomCommand.register(dispatcher);
            ReloadMapConfigCommand.register(dispatcher);
            SkinsCommand.register(dispatcher);
            ProgressionCommand.register(dispatcher);
            BackpackCommand.register(dispatcher);
            RoleRosterCommand.register(dispatcher);
            PlushCommand.register(dispatcher);
            BreakingAndFakeBlockCommand.register(dispatcher, registryAccess, environment);
            PlayerInventoryCommand.register(dispatcher);
            StreamingSpectatorCommand.register(dispatcher);
            ShieldCommand.register(dispatcher);
            PoisonCommand.register(dispatcher);
            StaminaCommand.register(dispatcher);
            SceneCommand.register(dispatcher);
            SceneEventCommand.register(dispatcher);
            SceneTaskCommand.register(dispatcher);
            io.wifi.starrailexpress.content.command.MinigameTaskCommand.register(dispatcher);
            io.wifi.starrailexpress.cca.network.SkinsNetworkSyncCommand.register(dispatcher);
            io.wifi.starrailexpress.customrole.CustomRoleReloadCommand.register(dispatcher);
            // CoinModifier.register(dispatcher, registryAccess);
            net.exmo.sre.nametag.NameTagCommand.register(dispatcher, registryAccess);
            net.exmo.sre.subtitle.SubtitleCommand.register(dispatcher, registryAccess);
            net.exmo.sre.camera.AdvancedCameraCommand.register(dispatcher);
            // io.wifi.starrailexpress.contents.command.UnlockAllRolesCommand.register(dispatcher);
            StartCommand.register(dispatcher);
            StopCommand.register(dispatcher);
            GameUtilsCommand.register(dispatcher, registryAccess);
        }));
    }
}
