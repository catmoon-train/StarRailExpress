package org.agmas.noellesroles.mixin.roles.manipulator;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.roles.manipulator.InControlCCA;
import org.agmas.noellesroles.roles.manipulator.ManipulatorPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameUtils.class)
public class PlayerDeathMixin {
   @Inject(method = "killPlayer(Lnet/minecraft/world/entity/player/Player;ZLnet/minecraft/world/entity/player/Player;Lnet/minecraft/resources/ResourceLocation;)V", at = @At("HEAD"))
   private static void noe$killPlayer(Player victim, boolean spawnBody, Player killer, ResourceLocation deathReason, CallbackInfo ci) {
       final var level = victim.level();
       final var gameWorldComponent = SREGameWorldComponent.KEY.get(level);
       if (gameWorldComponent != null && gameWorldComponent.isRunning() ) {
           final var inControlCCA = InControlCCA.KEY.get(victim);
           if (inControlCCA != null) {
               inControlCCA.isControlling = false;
               inControlCCA.sync();
           }
           final var manipulatorPlayerComponent = ManipulatorPlayerComponent.KEY.get(victim);
           if (manipulatorPlayerComponent.isControlling) {
               level.players().forEach(
                       player -> {
                           if (GameUtils.isPlayerAliveAndSurvival(player) && gameWorldComponent.isRole(player, ModRoles.MANIPULATOR)) {
                               if (ManipulatorPlayerComponent.KEY.get(player).target.equals(manipulatorPlayerComponent.target)) {
                                   final var manipulatorPlayerComponent2 = ManipulatorPlayerComponent.KEY.get(player);
                                   if (manipulatorPlayerComponent2.isControlling) {
                                       manipulatorPlayerComponent2.stopControl(false);
                                   }
                               }
                           }
                       }
               );

           }
        }
    }
}
