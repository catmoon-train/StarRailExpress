package org.agmas.noellesroles.mixin.roles.framing;

import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import io.wifi.starrailexpress.cca.StarPlayerPoisonComponent;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(StarPlayerPoisonComponent.class)
public abstract class DoNotApplyFakePoisonMixin {

    @Shadow @Final private Player player;

    @Shadow public int poisonTicks;


    @Shadow public abstract void reset();

    @Shadow public UUID poisoner;

    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private void defenseVialApply(CallbackInfo ci) {
        StarGameWorldComponent gameWorldComponent = StarGameWorldComponent.KEY.get(player.level());
            if (gameWorldComponent.getRole(poisoner) == null) return;
            if (gameWorldComponent.isRole(poisoner, ModRoles.JESTER)) {
                // Don't interfere with any custom non-killer poisoning roles from other mods
                if (poisonTicks <= 5) {
                    reset();
                    ci.cancel();
                }
            }
        }

}
