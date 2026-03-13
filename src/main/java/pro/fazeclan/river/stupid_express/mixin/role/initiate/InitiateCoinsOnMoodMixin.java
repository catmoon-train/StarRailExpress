package pro.fazeclan.river.stupid_express.mixin.role.initiate;

import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import io.wifi.starrailexpress.cca.StarPlayerMoodComponent;
import io.wifi.starrailexpress.cca.StarPlayerShopComponent;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.fazeclan.river.stupid_express.constants.SERoles;

@Mixin(StarPlayerMoodComponent.class)
public abstract class InitiateCoinsOnMoodMixin {

    @Shadow
    @Final
    private Player player;

    @Shadow
    public abstract float getMood();

    @Inject(method = "setMood", at = @At("HEAD"))
    private void initiateCoinsForMoodUp(float mood, CallbackInfo ci) {
        var gameWorldComponent = StarGameWorldComponent.KEY.get(player.level());
        if (mood < getMood()) {
            return;
        }
        if (!gameWorldComponent.isRole(player, SERoles.INITIATE)) {
            return;
        }
        if (TMMRoles.ROLES.values().stream()
                .anyMatch(role -> role.identifier().getNamespace().equals("noellesroles"))) {
            return;
        }
        var shopComponent = StarPlayerShopComponent.KEY.get(player);
        shopComponent.addToBalance(50);
    }

}
