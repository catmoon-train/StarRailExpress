package org.agmas.noellesroles.mixin.roles.elf;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BowItem.class)
public class BowMixin {
    @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
    private void noellesroles$releaseUsing(ItemStack itemStack, Level level, LivingEntity livingEntity, int i, CallbackInfo ci) {
        if (livingEntity instanceof ServerPlayer serverPlayer) {
            if (serverPlayer.isSpectator()) {
                ci.cancel();
            }
        }
    }
}
