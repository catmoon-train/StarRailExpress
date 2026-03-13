package pro.fazeclan.river.stupid_express.mixin.role.avaricious;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import io.wifi.starrailexpress.game.StarRailMurderGameMode;
import net.minecraft.server.level.ServerPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pro.fazeclan.river.stupid_express.constants.SERoles;

@Mixin(StarRailMurderGameMode.class)
public class AvariciousPassiveIncomeDestroyer {

    @ModifyExpressionValue(
            method = "tickServerGameLoop",
            at = @At(
                    value = "INVOKE",
                    target = "Lio/wifi/starrailexpress/cca/GameWorldComponent;canUseKillerFeatures(Lnet/minecraft/world/entity/player/Player;)Z"
            )
    )
    private boolean noPassiveIncomeKiller(
            boolean original,
            @Local(name = "gameWorldComponent") StarGameWorldComponent component,
            @Local(name = "player") ServerPlayer player
    ) {
        return original && !component.isRole(player, SERoles.AVARICIOUS);
    }
}