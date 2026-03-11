package org.agmas.harpymodloader.mixin;


import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.game.GameFunctions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.ModdedRoleRemoved;
import org.agmas.harpymodloader.events.ModifierRemoved;
import org.agmas.harpymodloader.events.ResetPlayerEvent;
import org.agmas.harpymodloader.modifiers.Modifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameFunctions.class)
public class ModifierAndRoleResetMixin {
    @Inject(method = "resetPlayer", at = @At("HEAD"))
    private static void a(ServerPlayer player, CallbackInfo ci) {
        GameWorldComponent gameComponent = (GameWorldComponent)GameWorldComponent.KEY.get(player.level());
        if (gameComponent.getRole(player) != null) {
            ModdedRoleRemoved.EVENT.invoker().removeModdedRole(player, gameComponent.getRole(player));
        }
        WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(player.level());
        for (Modifier modifier : worldModifierComponent.getModifiers(player)) {
            ModifierRemoved.EVENT.invoker().removeModifier(player, modifier);
        }
        ResetPlayerEvent.EVENT.invoker().resetPlayer(player);
    }

    @Inject(method = "initializeGame", at = @At("HEAD"))
    private static void b(ServerLevel serverWorld, CallbackInfo ci) {
        for (ServerPlayer player : serverWorld.players()) {
            ResetPlayerEvent.EVENT.invoker().resetPlayer(player);
        }
    }
}
