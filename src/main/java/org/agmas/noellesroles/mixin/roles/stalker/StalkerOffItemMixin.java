package org.agmas.noellesroles.mixin.roles.stalker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.item.StalkerKnifeItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

@Mixin(Player.class)
public abstract class StalkerOffItemMixin {


    @Inject(method = "getMainArm", at = @At("RETURN"), cancellable = true)
    private void mainHand(CallbackInfoReturnable<HumanoidArm> cir) {
        if (Minecraft.getInstance().player != null){
            if (Minecraft.getInstance().player.getMainHandItem().getItem() == ModItems.Stalker_Knife_2){
                if (cir.getReturnValue()==HumanoidArm.RIGHT){
                    cir.setReturnValue(HumanoidArm.LEFT);
                }
                else{
                    cir.setReturnValue(HumanoidArm.RIGHT);
                }
            }
        }
    }

}
