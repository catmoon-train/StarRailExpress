package org.agmas.noellesroles.mixin.client.roles.stalker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.item.StalkerKnifeItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StalkerKnifeItem.class)
public class AutoUseKnifeMixin {
    @Inject(method = "inventoryTick", at = @At("HEAD"))
    public void inventoryTick(ItemStack itemStack, Level level, Entity entity, int i, boolean b, CallbackInfo ci) {
        if (Minecraft.getInstance().player==entity){
            if (!((LocalPlayer) entity).isCrouching())return;
            Entity crosshairPickEntity = Minecraft.getInstance().crosshairPickEntity;
            if ( crosshairPickEntity instanceof Player && ((LocalPlayer) entity).getTicksUsingItem() > 3 ){
                ((LocalPlayer) entity).releaseUsingItem();
            }
        }
    }
}
