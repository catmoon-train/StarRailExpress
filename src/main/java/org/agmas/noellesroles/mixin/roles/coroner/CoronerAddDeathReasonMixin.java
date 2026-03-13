package org.agmas.noellesroles.mixin.roles.coroner;

import com.llamalad7.mixinextras.sugar.Local;
import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import io.wifi.starrailexpress.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameFunctions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.roles.coroner.BodyDeathReasonComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 这里删不了
 */
@Mixin(GameFunctions.class)
public abstract class CoronerAddDeathReasonMixin {

    @Inject(method = "killPlayer(Lnet/minecraft/world/entity/player/Player;ZLnet/minecraft/world/entity/player/Player;Lnet/minecraft/resources/ResourceLocation;)V", at = @At(value = "INVOKE", target = "Lio/wifi/starrailexpress/entity/PlayerBodyEntity;setYHeadRot(F)V"))
    private static void setDeathReason(Player victim, boolean spawnBody, Player killer, ResourceLocation identifier, CallbackInfo ci, @Local PlayerBodyEntity playerBodyEntity) {
        StarGameWorldComponent gameWorldComponent = StarGameWorldComponent.KEY.get(victim.level());
        if (gameWorldComponent.getRole(victim) == null) return;
        final var bodyDeathReasonComponent = BodyDeathReasonComponent.KEY.get(playerBodyEntity);
        bodyDeathReasonComponent.playerRole = gameWorldComponent.getRole(victim).identifier();
        bodyDeathReasonComponent.sync();
    }

}
