package io.wifi.starrailexpress.mixin.entity.player;

import io.wifi.starrailexpress.SRE;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FoodData.class)
public class HungerManagerMixin {
    @Shadow
    private int foodLevel;

    @Shadow
    private float saturationLevel;

    @Shadow
    private float exhaustionLevel;

    @Inject(method = "tick", at = @At("HEAD"))
    public void tmm$overrideFood(Player player, CallbackInfo ci) {
        if (SRE.isLobby){
            return;
        }
        this.foodLevel = 20;
        this.saturationLevel = 0;
        this.exhaustionLevel = 0;
    }
}
