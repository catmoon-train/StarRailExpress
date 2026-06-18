package io.wifi.starrailexpress.mixin.item;

import io.wifi.starrailexpress.progression.ProgressionDataManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public class ItemPickupProgressionMixin {

    @Inject(method = "playerTouch", at = @At("HEAD"))
    public void sre$trackItemPickup(Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.getItem().isEmpty()) {
            return;
        }
        String itemId = BuiltInRegistries.ITEM.getKey(self.getItem().getItem()).toString();
        ProgressionDataManager.onPickupItem(serverPlayer, itemId);
    }
}
