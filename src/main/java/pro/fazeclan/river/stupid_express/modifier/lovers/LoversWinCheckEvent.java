package pro.fazeclan.river.stupid_express.modifier.lovers;

import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.modifier.lovers.cca.LoversComponent;
import pro.fazeclan.river.stupid_express.utils.StupidRoleUtils;

import java.util.OptionalInt;
import java.util.UUID;

public class LoversWinCheckEvent {
    public static void register() {
        AllowGameEnd.EVENT.register((serverWorld, winStatus, isLooseend) -> {
            if (isLooseend)
                return WinStatus.NOT_MODIFY;
            var remainingPlayers = serverWorld.getPlayers(GameUtils::isPlayerAliveAndSurvival);
            var worldModifierComponent = WorldModifierComponent.KEY.get(serverWorld);
            for (ServerPlayer player : remainingPlayers) {
                if (worldModifierComponent.isModifier(player, SEModifiers.LOVERS)) {
                    var loversComponent = LoversComponent.KEY.get(player);
                    if (!loversComponent.isLover()) {
                        continue;
                    }

                    // check for only lovers win condition
                    if (loversComponent.won()) {
                        UUID loverUuid = loversComponent.getLover();
                        var gameRoundEndComponent = SREGameRoundEndComponent.KEY.get(serverWorld);
                        gameRoundEndComponent.CustomWinnerPlayers.clear();
                        gameRoundEndComponent.CustomWinnerPlayers.add(player.getUUID());
                        gameRoundEndComponent.CustomWinnerPlayers.add(loverUuid);
                        StupidRoleUtils.customWinnerWin(serverWorld, WinStatus.LOVERS,
                                SEModifiers.LOVERS.identifier().toLanguageKey(),
                                OptionalInt.of(SEModifiers.LOVERS.color()));
                        return WinStatus.LOVERS;
                    }
                }
            }
            return WinStatus.NOT_MODIFY;
        });
    }
}
