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

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.wifi.starrailexpress.customitem.CustomItemData;
import io.wifi.starrailexpress.customitem.CustomItemLoader;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自定义列车物品「蓄力道具」的第一人称蓄力动作。
 *
 * <p>
 * 原版的 {@code ItemInHandRenderer.renderArmWithItem} 里，{@code UseAnim.BLOCK} 只做默认持物位移
 * （没有独立变换），而 {@code SPEAR} / {@code BRUSH} 的位移又很轻微；自定义物品的外观是
 * {@code builtin/entity} 的平面图形，于是选这三种蓄力动作时看起来「完全没有动作」。
 *
 * <p>
 * 这里在渲染手臂前按 {@link CustomItemData.ChargeAnim} 补一段明显、且随蓄力进度变化的位移与旋转：
 * 刷子 = 前伸并小幅摆动、举矛 = 抬到肩前后拉、格挡 = 横向举到身前。
 *
 * <p>
 * 只影响第一人称手持的自定义蓄力物品，其它物品与第三人称不受影响；下面这些数值是手感起点，
 * 需要微调直接改这里的常量即可。
 */
@Mixin(ItemInHandRenderer.class)
public class CustomItemChargeAnimMixin {

    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void sre$customChargeAnim(AbstractClientPlayer player, float partialTicks, float pitch,
            InteractionHand hand, float swingProgress, ItemStack stack, float equippedProgress,
            PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        CustomItemData data = CustomItemLoader.getData(stack);
        if (data == null || data.kind() != CustomItemData.Kind.CHARGE) {
            return;
        }
        // 只在「真的在蓄力」时补动作
        if (!player.isUsingItem() || player.getUsedItemHand() != hand || player.getUseItemRemainingTicks() <= 0) {
            return;
        }
        int total = Math.max(1, data.chargeTicks);
        float progress = Mth.clamp(
                1.0F - (player.getUseItemRemainingTicks() - partialTicks) / (float) total, 0.0F, 1.0F);
        float side = player.getMainArm() == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        if (hand != InteractionHand.MAIN_HAND) {
            side = -side;
        }

        switch (data.chargeAnim()) {
            case BRUSH -> {
                // 刷子：向前伸，蓄力越满伸得越远，并左右小幅摆动
                float sway = Mth.sin(player.tickCount * 0.7F) * 0.08F;
                poseStack.translate(side * sway, -0.02F + progress * 0.06F, -0.2F - progress * 0.3F);
                poseStack.mulPose(Axis.XP.rotationDegrees(-20.0F - 25.0F * progress));
                poseStack.mulPose(Axis.ZP.rotationDegrees(side * Mth.sin(player.tickCount * 0.7F) * 8.0F));
            }
            case SPEAR -> {
                // 举矛：抬到肩前，蓄力越满越向后拉
                poseStack.translate(side * -0.15F, -0.1F + progress * 0.1F, -0.15F - progress * 0.45F);
                poseStack.mulPose(Axis.XP.rotationDegrees(-25.0F - 35.0F * progress));
                poseStack.mulPose(Axis.YP.rotationDegrees(side * 12.0F));
            }
            case BLOCK -> {
                // 格挡：横向举到身前挡住
                poseStack.translate(side * -0.35F, -0.2F, -0.45F);
                poseStack.mulPose(Axis.YP.rotationDegrees(side * 70.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(side * -12.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(-12.0F - 8.0F * progress));
            }
            default -> {
                // BOW / CROSSBOW / EAT / DRINK / NONE：原版已有明显的变换，不额外处理
            }
        }
    }
}
