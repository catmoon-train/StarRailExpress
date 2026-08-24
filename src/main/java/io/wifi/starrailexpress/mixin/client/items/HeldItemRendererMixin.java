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

package io.wifi.starrailexpress.mixin.client.items;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.HeldLikeRevolver;
import io.wifi.starrailexpress.event.AllowItemShowInHand;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.util.MatrixParticleManager;
import io.wifi.starrailexpress.util.MatrixUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class HeldItemRendererMixin {

    @Shadow
    private ItemStack mainHandItem;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private ItemRenderer itemRenderer;

    private static ItemStack noellesroles$resolve(Player player, ItemStack stack) {
        // 修复原代码 bug：根据 mainHand 参数取对应手的物品
        ItemStack eventRes = AllowItemShowInHand.EVENT.invoker().allowShowInHand(player, stack, true);
        return eventRes != null ? eventRes : stack;
    }

    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V", shift = At.Shift.AFTER))
    private void tmm$itemVFX(LivingEntity entity, ItemStack stackD, ItemDisplayContext renderMode, boolean leftHanded,
            PoseStack matrices, MultiBufferSource vertexConsumers, int light, CallbackInfo ci) {
        if (SRE.isLobby)
            return;
        if (renderMode.firstPerson()) {
            SREClient.handParticleManager.render(matrices, vertexConsumers, light);
        }
        var stack = stackD;
        if (entity instanceof LocalPlayer player) {
            stack = noellesroles$resolve(player,stack);
        }
        if (entity instanceof Player playerEntity
                && (stack.is(TMMItemTags.HELD_LIKE_GUNS_ITEMS)
                        || (stack.getItem() instanceof HeldLikeRevolver))) {
            if (playerEntity.getUUID() != Minecraft.getInstance().player.getUUID()) {
                MatrixParticleManager.muzzlePosForPlayer$set(playerEntity, MatrixUtils.matrixToVec(matrices));
            } else if (!renderMode.firstPerson()) {
                MatrixParticleManager.muzzlePosForPlayer$set(playerEntity, MatrixUtils.matrixToVec(matrices));
            }
        }
    }

    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;matches(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean tmm$ignoreNbtUpdateForRevolver(boolean original, @Local(ordinal = 0) ItemStack newItemStack) {
        if (SRE.isLobby)
            return original;
        if (!original) {
            if (this.mainHandItem.is(TMMItemTags.HELD_LIKE_GUNS_ITEMS)
                    && newItemStack.is(TMMItemTags.HELD_LIKE_GUNS_ITEMS)
                    || (this.mainHandItem.getItem() instanceof HeldLikeRevolver)) {
                return true;
            }
        }
        return original;
    }
}