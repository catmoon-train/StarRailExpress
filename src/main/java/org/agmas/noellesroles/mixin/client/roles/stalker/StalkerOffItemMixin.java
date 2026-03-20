package org.agmas.noellesroles.mixin.client.roles.stalker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.world.entity.HumanoidArm;
import org.agmas.noellesroles.item.StalkerKnifeItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

@Mixin(Options.class)
public abstract class StalkerOffItemMixin {
    @Shadow
    public abstract void broadcastOptions();

    @Inject(method = "mainHand", at = @At("RETURN"), cancellable = true)
    private void mainHand(CallbackInfoReturnable<OptionInstance<HumanoidArm>> cir) {
        if (Minecraft.getInstance().player != null){
            if (Minecraft.getInstance().player.getOffhandItem().getItem() instanceof StalkerKnifeItem){
                if (cir.getReturnValue().get()==HumanoidArm.RIGHT){
                   cir.setReturnValue(new OptionInstance("options.mainHand", OptionInstance.noTooltip(), OptionInstance.forOptionEnum(), new OptionInstance.Enum(Arrays.asList(HumanoidArm.values()), HumanoidArm.CODEC), HumanoidArm.LEFT, (humanoidArm) -> broadcastOptions()));
                }
                else{
                    cir.setReturnValue(new OptionInstance("options.mainHand", OptionInstance.noTooltip(), OptionInstance.forOptionEnum(), new OptionInstance.Enum(Arrays.asList(HumanoidArm.values()), HumanoidArm.CODEC), HumanoidArm.RIGHT, (humanoidArm) -> broadcastOptions()));
                }
            }
        }
    }

}
