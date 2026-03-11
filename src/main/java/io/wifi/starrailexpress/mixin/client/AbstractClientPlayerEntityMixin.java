package io.wifi.starrailexpress.mixin.client;

import com.mojang.authlib.GameProfile;
import io.wifi.starrailexpress.cca.PlayerPoisonComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.util.PoisonUtils;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerEntityMixin extends Player {
    public AbstractClientPlayerEntityMixin(Level world, BlockPos pos, float yaw, GameProfile gameProfile) {
        super(world, pos, yaw, gameProfile);
    }

    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void tmm$fovPulse(CallbackInfoReturnable<Float> cir) {
        if (SREClient.isInLobby) {
            return;
        }
        float original = cir.getReturnValueF();

        cir.setReturnValue(original * PoisonUtils.getFovMultiplier(1f, PlayerPoisonComponent.KEY.get(this)));
    }
}
