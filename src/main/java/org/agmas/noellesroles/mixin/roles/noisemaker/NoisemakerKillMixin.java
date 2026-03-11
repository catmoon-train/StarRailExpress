package org.agmas.noellesroles.mixin.roles.noisemaker;

import com.llamalad7.mixinextras.sugar.Local;
import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameFunctions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameFunctions.class)
public abstract class NoisemakerKillMixin {

    @Inject(method = "killPlayer(Lnet/minecraft/world/entity/player/Player;ZLnet/minecraft/world/entity/player/Player;Lnet/minecraft/resources/ResourceLocation;)V", at = @At(value = "INVOKE", target = "Lio/wifi/starrailexpress/entity/PlayerBodyEntity;setYHeadRot(F)V"))
    private static void noisemakerKill(Player victim, boolean spawnBody, Player killer, ResourceLocation identifier, CallbackInfo ci, @Local PlayerBodyEntity body) {
        GameWorldComponent gameWorldComponent = (GameWorldComponent) GameWorldComponent.KEY.get(victim.level());
        if (gameWorldComponent.isRole(victim, ModRoles.NOISEMAKER)) {
           body.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20*60, 0));
        }
    }

}
