/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.HeldLikeBat;
import io.wifi.starrailexpress.event.AllowItemShowInHand;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.UUID;

@Mixin(PlayerRenderer.class)
public class PlayerEntityRendererMixin {
    @Inject(method = "getArmPose", at = @At("TAIL"), cancellable = true)
    private static void tmm$customArmPose(@NotNull AbstractClientPlayer player,
            @NotNull InteractionHand hand, CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
        ItemStack heldStack = player.getItemInHand(hand);
        // 自定义列车物品正在蓄力：第三人称的手臂姿势取「第三人称蓄力动作」
        // （第一人称那套由 CustomItem#getUseAnimation 交给原版 ItemInHandRenderer）
        if (player.getUsedItemHand() == hand && player.getUseItemRemainingTicks() > 0
                && io.wifi.starrailexpress.customitem.CustomItemLoader
                        .kind(heldStack) == io.wifi.starrailexpress.customitem.CustomItemData.Kind.CHARGE) {
            var thirdAnim = io.wifi.starrailexpress.customitem.CustomItemLoader.chargeAnimThirdPerson(heldStack);
            if (thirdAnim != null) {
                cir.setReturnValue(sre$chargeAnimToArmPose(thirdAnim));
                return;
            }
        }
        if (heldStack.is(TMMItemTags.HELD_LIKE_BAT_ITEMS) || heldStack.getItem() instanceof HeldLikeBat) {
            cir.setReturnValue(HumanoidModel.ArmPose.CROSSBOW_CHARGE);
            return;
        }
        // 自定义列车物品：按物品配置的手持姿势设置手臂姿势。
        // 只认「枪械道具」的 holdPose —— 它的默认值是 REVOLVER，其它性质读它会被默认值污染；
        // （REVOLVER 由 BipedEntityModelMixin 的持枪姿势处理，DEFAULT 不干预）
        var customPose = io.wifi.starrailexpress.customitem.CustomItemLoader.gunHoldPose(heldStack);
        if (customPose != null) {
            switch (customPose) {
                case RAISED -> cir.setReturnValue(HumanoidModel.ArmPose.CROSSBOW_CHARGE);
                case AIM -> cir.setReturnValue(HumanoidModel.ArmPose.CROSSBOW_HOLD);
                default -> {
                }
            }
        }
    }

    /**
     * 蓄力动作 → 原版手臂姿势：与原版 {@code PlayerRenderer#getArmPose} 自己的映射保持一致
     * （{@code EAT} / {@code DRINK} 在原版也是默认手持姿势）。
     */
    private static HumanoidModel.ArmPose sre$chargeAnimToArmPose(
            io.wifi.starrailexpress.customitem.CustomItemData.ChargeAnim anim) {
        return switch (anim) {
            case NONE, DRINK, EAT -> HumanoidModel.ArmPose.ITEM;
            case BOW -> HumanoidModel.ArmPose.BOW_AND_ARROW;
            case SPEAR -> HumanoidModel.ArmPose.THROW_SPEAR;
            case CROSSBOW -> HumanoidModel.ArmPose.CROSSBOW_CHARGE;
            case BLOCK -> HumanoidModel.ArmPose.BLOCK;
            case BRUSH -> HumanoidModel.ArmPose.BRUSH;
        };
    }

    @ModifyExpressionValue(method = "getArmPose", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;getItemInHand(Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack tmm$changeNoteAndPsychosisItemsArmPos(@NotNull ItemStack original,
            @NotNull AbstractClientPlayer player,
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
