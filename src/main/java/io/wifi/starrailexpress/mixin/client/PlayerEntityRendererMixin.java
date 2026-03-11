package io.wifi.starrailexpress.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.event.AllowItemShowInHand;
import io.wifi.starrailexpress.index.TMMItems;

import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.UUID;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

@Mixin(PlayerRenderer.class)
public class PlayerEntityRendererMixin {
    @Inject(method = "getArmPose", at = @At("TAIL"), cancellable = true)
    private static void tmm$customArmPose(AbstractClientPlayer player,
            InteractionHand hand, CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
        if (player.getItemInHand(hand).is(TMMItems.BAT))
            cir.setReturnValue(HumanoidModel.ArmPose.CROSSBOW_CHARGE);
    }

    @ModifyExpressionValue(method = "getArmPose", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;getItemInHand(Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack tmm$changeNoteAndPsychosisItemsArmPos(ItemStack original, AbstractClientPlayer player,
            InteractionHand hand) {
        if (hand.equals(InteractionHand.MAIN_HAND)) {
            for (var i : TMMItems.INVISIBLE_ITEMS) {
                if (original.is(i)) {
                    // LoggerFactory.getLogger("train").info("Hide " + i.toString());
                    return ItemStack.EMPTY;
                }
            }
            var eventRes = AllowItemShowInHand.EVENT.invoker().allowShowInHand(player, original);
            if (eventRes != null) {
                return eventRes;
            }
            if (SREClient.moodComponent != null && SREClient.moodComponent.isLowerThanMid()) { // make sure it's only
                                                                                               // the main hand item
                                                                                               // that's being replaced
                HashMap<UUID, ItemStack> psychosisItems = SREClient.moodComponent.getPsychosisItems();
                UUID uuid = player.getUUID();
                if (psychosisItems.containsKey(uuid)) {
                    return psychosisItems.get(uuid);
                }
            }
        }

        return original;
    }
}
