package io.wifi.starrailexpress.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.event.AllowItemShowInHand;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

@Mixin(PlayerRenderer.class)
public class PlayerEntityRendererMixin {
    @Inject(method = "getArmPose", at = @At("TAIL"), cancellable = true)
    private static void tmm$customArmPose(@NotNull AbstractClientPlayer player,
            @NotNull InteractionHand hand, CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
        ItemStack heldStack = player.getItemInHand(Objects.requireNonNull(hand));

        if (heldStack.is(Objects.requireNonNull(ModItems.SHORT_SHOTGUN))) {
            cir.setReturnValue(HumanoidModel.ArmPose.CROSSBOW_CHARGE);
            return;
        }

        if (heldStack.is(Objects.requireNonNull(TMMItems.BAT))
            || heldStack.is(Objects.requireNonNull(ModItems.FAKE_BAT)))
            cir.setReturnValue(HumanoidModel.ArmPose.CROSSBOW_CHARGE);
    }

    @ModifyExpressionValue(method = "getArmPose", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;getItemInHand(Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack tmm$changeNoteAndPsychosisItemsArmPos(@NotNull ItemStack original, @NotNull AbstractClientPlayer player,
            @NotNull InteractionHand hand) {
        if (hand.equals(InteractionHand.MAIN_HAND)) {
            for (var i : TMMItems.INVISIBLE_ITEMS) {
                if (i != null && original.is(i)) {
                    return ItemStack.EMPTY;
                }
            }
            var eventRes = AllowItemShowInHand.EVENT.invoker().allowShowInHand(player, original, true);
            if (eventRes != null) {
                return eventRes;
            }
            if (SREClient.moodComponent != null && SREClient.moodComponent.isLowerThanMid() && !player.isInvisible()) {
                HashMap<UUID, ItemStack> psychosisItems = SREClient.moodComponent.getPsychosisItems();
                UUID uuid = player.getUUID();
                if (psychosisItems.containsKey(uuid)) {
                    return psychosisItems.get(uuid);
                }
            }
        } else {
            for (var i : TMMItems.INVISIBLE_ITEMS) {
                if (i != null && original.is(i)) {
                    return ItemStack.EMPTY;
                }
            }
            var eventRes = AllowItemShowInHand.EVENT.invoker().allowShowInHand(player, original, false);
            if (eventRes != null) {
                return eventRes;
            }
        }

        return original;
    }
}
