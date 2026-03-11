package pro.fazeclan.river.stupid_express.mixin.role.arsonist;

import com.llamalad7.mixinextras.sugar.Local;
import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.game.GameFunctions;
import io.wifi.starrailexpress.game.StarRailMurderGameMode;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.constants.SERoles;
import pro.fazeclan.river.stupid_express.utils.StupidRoleUtils;

@Mixin(StarRailMurderGameMode.class)
public class ArsonistKeepAliveMixin {

    @Inject(method = "tickServerGameLoop", at = @At(value = "FIELD", target = "Lio/wifi/starrailexpress/game/GameFunctions$WinStatus;NONE:Lio/wifi/starrailexpress/game/GameFunctions$WinStatus;", ordinal = 3, opcode = Opcodes.GETSTATIC), cancellable = true)
    private void keepAlive(
            ServerLevel serverWorld, GameWorldComponent gameWorldComponent, CallbackInfo ci,
            @Local(name = "winStatus") GameFunctions.WinStatus winStatus) {
        var config = StupidExpress.CONFIG;
        if (config.rolesSection.arsonistSection.arsonistKeepsGameGoing) {
            var players = serverWorld.getPlayers(GameFunctions::isPlayerAliveAndSurvival);
            boolean arsonistAlive = false;
            for (ServerPlayer player : players) {
                if (gameWorldComponent.isRole(player, SERoles.ARSONIST)) {
                    arsonistAlive = true;
                }
            }

            if (players.size() == 1 && arsonistAlive) {
                // 纵火犯独立胜利统计：使用 RoleUtils.customWinnerWin
                StupidRoleUtils.customWinnerWin(serverWorld, GameFunctions.WinStatus.CUSTOM,
                        SERoles.ARSONIST.identifier().getPath(),
                        java.util.OptionalInt.of(SERoles.ARSONIST.color()));
            }

            if (arsonistAlive && (winStatus == GameFunctions.WinStatus.KILLERS
                    || winStatus == GameFunctions.WinStatus.PASSENGERS)) {
                ci.cancel();
            }
        }
    }

}
