package org.agmas.harpymodloader.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public class PlayerWeightResetMixin {
    //

    @Inject(method = "remove", at = @At("HEAD"))
    public void onPlayerDisconnect(ServerPlayer player,
            CallbackInfo ci) {
        PlayerRoleWeightManager.clearWeight(player.getUUID());
    }
}
