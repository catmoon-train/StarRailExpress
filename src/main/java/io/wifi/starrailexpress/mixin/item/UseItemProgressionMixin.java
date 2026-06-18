package io.wifi.starrailexpress.mixin.item;

import io.wifi.starrailexpress.progression.ProgressionDataManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public class UseItemProgressionMixin {

    @Inject(method = "use", at = @At("RETURN"))
    public void sre$trackUseItemProgression(Level level, Player player, InteractionHand usedHand,
            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (!(player instanceof ServerPlayer serverPlayer) || cir.getReturnValue() == null
                || !cir.getReturnValue().getResult().consumesAction()) {
            return;
        }
        String itemId = BuiltInRegistries.ITEM.getKey(player.getItemInHand(usedHand).getItem()).toString();
        ProgressionDataManager.onItemUsed(serverPlayer, itemId);
    }
}
