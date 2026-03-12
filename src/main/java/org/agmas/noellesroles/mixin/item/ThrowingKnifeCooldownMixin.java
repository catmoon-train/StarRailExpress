
package org.agmas.noellesroles.mixin.item;

import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.network.original.KnifeStabPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.agmas.noellesroles.init.ModItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KnifeStabPayload.Receiver.class)
public class ThrowingKnifeCooldownMixin {
    @Inject(method = "receive", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemCooldowns;addCooldown(Lnet/minecraft/world/item/Item;I)V"), cancellable = true)
    private void receive(KnifeStabPayload payload, ServerPlayNetworking.Context context, CallbackInfo ci) {
        context.player().getCooldowns().addCooldown(ModItems.THROWING_KNIFE, (Integer) GameConstants.ITEM_COOLDOWNS.get(TMMItems.KNIFE));
    }
}
