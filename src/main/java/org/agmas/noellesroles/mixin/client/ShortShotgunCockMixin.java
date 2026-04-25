package org.agmas.noellesroles.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.init.NRSounds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class ShortShotgunCockMixin {
    private ItemStack lastHeldShotgun = ItemStack.EMPTY;
    private boolean hasPlayedCockSound = false;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = (LocalPlayer) (Object) this;
        
        if (mc.level == null || player == null) {
            return;
        }

        ItemStack mainHandStack = player.getItemInHand(InteractionHand.MAIN_HAND);
        boolean isHoldingShotgun = mainHandStack.is(ModItems.SHORT_SHOTGUN);

        if (isHoldingShotgun) {
            if (!hasPlayedCockSound && (lastHeldShotgun.isEmpty() || !lastHeldShotgun.is(ModItems.SHORT_SHOTGUN))) {
                // 拿出手枪时播放上膛音效
                player.playSound(NRSounds.SHOTGUNU_COCK, 1.0F, 1.0F);
                hasPlayedCockSound = true;
            }
        } else {
            hasPlayedCockSound = false;
        }

        lastHeldShotgun = mainHandStack.copy();
    }
}
