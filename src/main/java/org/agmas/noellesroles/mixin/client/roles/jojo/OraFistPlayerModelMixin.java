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

package org.agmas.noellesroles.mixin.client.roles.jojo;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role_data.vigilante.JojoRoleData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 欧拉连打期间左右臂反向交替挥拳，第三人称看起来像疯狂欧拉。
 */
@Mixin(HumanoidModel.class)
public abstract class OraFistPlayerModelMixin<T extends LivingEntity> {

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void noellesroles$oraBothArms(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof Player player) || !JojoRoleData.isRushing(player)) {
            return;
        }
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        float wave = ageInTicks * 1.85f;
        model.rightArm.xRot = -2.05f + Mth.sin(wave) * 1.35f;
        model.leftArm.xRot = -2.05f + Mth.sin(wave + (float) Math.PI) * 1.35f;
        model.rightArm.zRot = 0.18f;
        model.leftArm.zRot = -0.18f;
        model.rightArm.yRot = -0.12f;
        model.leftArm.yRot = 0.12f;
        if (model instanceof PlayerModel<?> playerModel) {
            copy(playerModel.rightSleeve, model.rightArm);
            copy(playerModel.leftSleeve, model.leftArm);
        }
    }

    private static void copy(ModelPart sleeve, ModelPart arm) {
        if (sleeve != null) {
            sleeve.copyFrom(arm);
        }
    }
}
