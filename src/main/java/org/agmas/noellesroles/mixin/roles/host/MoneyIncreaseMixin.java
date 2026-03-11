package org.agmas.noellesroles.mixin.roles.host;

import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.cca.PlayerPsychoComponent;
import io.wifi.starrailexpress.cca.PlayerShopComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameFunctions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameFunctions.class)
public class MoneyIncreaseMixin {
    @Inject(method = "killPlayer(Lnet/minecraft/world/entity/player/Player;ZLnet/minecraft/world/entity/player/Player;Lnet/minecraft/resources/ResourceLocation;)V", at = @At("HEAD"))
    private static void increaseMoney(Player victim, boolean spawnBody, Player killer, ResourceLocation identifier, CallbackInfo ci) {
        if (killer != null) {
            GameWorldComponent gameWorldComponent = (GameWorldComponent) GameWorldComponent.KEY.get(victim.level());
            if (gameWorldComponent.isRole(victim, ModRoles.CONDUCTOR) && !gameWorldComponent.isInnocent(killer)) {
                PlayerShopComponent component = PlayerShopComponent.KEY.get(killer);
                component.addToBalance(100);
            }
        }
    }
    @Inject(method = "killPlayer(Lnet/minecraft/world/entity/player/Player;ZLnet/minecraft/world/entity/player/Player;Lnet/minecraft/resources/ResourceLocation;)V", at = @At("HEAD"), cancellable = true)
    private static void jesterJest(Player victim, boolean spawnBody, Player killer, ResourceLocation identifier, CallbackInfo ci) {
        if (killer != null) {
            GameWorldComponent gameWorldComponent = (GameWorldComponent) GameWorldComponent.KEY.get(victim.level());
            if (gameWorldComponent.isRole(victim, ModRoles.JESTER) && !gameWorldComponent.isRole(killer, ModRoles.JESTER) && gameWorldComponent.isInnocent(killer)) {
                PlayerPsychoComponent component = (PlayerPsychoComponent)PlayerPsychoComponent.KEY.get(victim);
                if (component.getPsychoTicks() <= 0) {
                    component.startPsycho();
                    component.psychoTicks = GameConstants.getInTicks(0, 45);
                    component.armour = 0;
                    ci.cancel();
                }
            }
        }
    }
}
