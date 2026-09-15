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

import io.wifi.starrailexpress.customitem.CustomItemData;
import io.wifi.starrailexpress.customitem.CustomItemRuntime;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 「自定义手铐」被拷住时的姿势（四肢部分）。
 *
 * <p>
 * 姿势来源全部抄自项目里已有的实现，按配置的 {@link CustomItemData.CuffPose} 选择：
 * <ul>
 * <li>{@code CUFFS}：{@code org.agmas.noellesroles.mixin.client.HandCuffsPoseMixin}（双臂相交于身后）</li>
 * <li>{@code TREMBLE}：{@code ...mixin.client.general.FearPlayerModelMixin}（身体/头/四肢周期性晃）</li>
 * <li>{@code LIMP}：{@code ...mixin.client.general.LimpPlayerModelMixin}（走路时腿瘸）</li>
 * <li>{@code ORA}：{@code ...mixin.client.roles.jojo.OraFistPlayerModelMixin}（双臂前伸乱挥）</li>
 * <li>{@code HOLD}：{@code io.wifi.starrailexpress.mixin.client.items.BipedEntityModelMixin} 的举起姿势</li>
 * </ul>
 *
 * <p>
 * 坐下 / 游泳这类「整体身体姿势」由 {@code CustomCuffBodyPoseMixin} 设置实体 Pose，
 * 这里只负责四肢；袖子 / 裤腿 / 外套的复制与原版模型保持一致，避免出现贴图错位。
 */
@Mixin(HumanoidModel.class)
public abstract class CustomCuffPoseMixin<T extends LivingEntity> {

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("RETURN"))
    private void sre$customCuffPose(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof Player player)) {
            return;
        }
        CustomItemData data = CustomItemRuntime.getCuffData(player);
        if (data == null || data.kind() != CustomItemData.Kind.CUFF) {
            return;
        }
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        switch (data.cuffPose()) {
            case NONE -> {
                // 不改变姿势
            }
            case CUFFS -> applyCuffs(model);
            case TREMBLE -> applyTremble(model, ageInTicks);
            case LIMP -> applyLimp(model, limbSwingAmount);
            case ORA -> applyOra(model, ageInTicks);
            case HOLD -> applyHold(model);
            case SIT, SWIM -> {
                // 整体姿势由 CustomCuffBodyPoseMixin 设置
            }
        }
        copyOverlays(model);
    }

    /** 手铐姿势：双臂转到身后相交（与原版手铐后铐一致）。 */
    private static void applyCuffs(HumanoidModel<?> model) {
        setCuffedArm(model.rightArm, true);
        setCuffedArm(model.leftArm, false);
    }

    private static void setCuffedArm(ModelPart arm, boolean right) {
        float sign = right ? 1.0F : -1.0F;
        arm.xRot = (float) (Math.PI * 0.35);
        arm.yRot = sign * 0.7F;
        arm.zRot = sign * 0.2F;
    }

    /** 发抖：整体轻微左右晃 + 点头（恐惧同款公式）。 */
    private static void applyTremble(HumanoidModel<?> model, float ageInTicks) {
        float lean = Mth.sin(ageInTicks * 1.35F) * 0.035F;
        float nod = Mth.cos(ageInTicks * 1.7F) * 0.018F;
        model.head.zRot = lean;
        model.body.zRot = lean;
        model.rightArm.zRot += lean;
        model.leftArm.zRot += lean;
        model.rightLeg.zRot += lean;
        model.leftLeg.zRot += lean;
        model.head.xRot += nod;
    }

    /** 腿瘸：走路时右腿摆幅减小并扭转（腿瘸同款公式，严重度固定为中等）。 */
    private static void applyLimp(HumanoidModel<?> model, float limbSwingAmount) {
        if (limbSwingAmount < 0.01F) {
            return;
        }
        float weak = Math.min(1.0F, limbSwingAmount);
        float twist = 0.10F * 0.6F + 0.18F * weak;
        model.rightLeg.xRot *= 1.0F - 0.78F * weak;
        model.rightLeg.zRot = twist;
        model.leftLeg.xRot *= 1.0F + 0.26F * weak;
    }

    /** 双臂前伸反向乱挥（欧拉连打同款公式）。 */
    private static void applyOra(HumanoidModel<?> model, float ageInTicks) {
        float wave = ageInTicks * 1.85F;
        model.rightArm.xRot = -2.05F + Mth.sin(wave) * 1.35F;
        model.leftArm.xRot = -2.05F + Mth.sin(wave + (float) Math.PI) * 1.35F;
        model.rightArm.zRot = 0.18F;
        model.leftArm.zRot = -0.18F;
        model.rightArm.yRot = -0.12F;
        model.leftArm.yRot = 0.12F;
    }

    /** 举手/持枪式举起：双臂抬到身前。 */
    private static void applyHold(HumanoidModel<?> model) {
        model.rightArm.xRot = -1.45F;
        model.leftArm.xRot = -1.20F;
        model.rightArm.yRot = -0.30F;
        model.leftArm.yRot = 0.30F;
        model.rightArm.zRot = 0.10F;
        model.leftArm.zRot = -0.10F;
    }

    /** 把四肢旋转复制到玩家模型的贴图层（袖子 / 裤腿 / 外套 / 帽子）。 */
    private static void copyOverlays(HumanoidModel<?> model) {
        if (!(model instanceof PlayerModel<?> playerModel)) {
            return;
        }
        playerModel.hat.copyFrom(model.head);
        playerModel.jacket.copyFrom(model.body);
        playerModel.rightSleeve.copyFrom(model.rightArm);
        playerModel.leftSleeve.copyFrom(model.leftArm);
        playerModel.rightPants.copyFrom(model.rightLeg);
        playerModel.leftPants.copyFrom(model.leftLeg);
    }
}
