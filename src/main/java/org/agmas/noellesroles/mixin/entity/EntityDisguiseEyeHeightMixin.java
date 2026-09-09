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

package org.agmas.noellesroles.mixin.entity;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.wifi.starrailexpress.index.TMMEntities;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

import org.agmas.noellesroles.game.modifier.NRModifiers;
import org.agmas.noellesroles.role_data.innocence.LeatherPigRoleData;
import org.agmas.noellesroles.role_data.innocence.TomatoHeadRoleData;
import org.agmas.noellesroles.role_data.killer.InsaneKillerRoleData;
import org.agmas.noellesroles.role_data.neutral.PhantomSpiritRoleData;
import org.agmas.noellesroles.utils.RoleUtils;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 皮革噶的：伪装成猪期间把玩家的眼高压到猪的眼高。
 * 幻灵：伪装成悦灵期间改用悦灵碰撞箱与眼高。
 * 亡语杀手：伪装成尸体期间改用尸体碰撞箱（眼高保持原值，否则第三人称相机会掉到地里）。
 *
 * <p>
 * 猪的碰撞箱（0.6×1.8）保持不变——地图是按人的尺寸做的。只改眼高，于是相机、准星射线、
 * 枪械命中判定这些读 {@code getEyeY()} 的地方一起下移，画面和命中点不会错开。
 */
@Mixin(Player.class)
public abstract class EntityDisguiseEyeHeightMixin {

    /** 上一 tick 是否处于伪装尸体形态，用于在形态切换时刷新碰撞箱。 */
    @Unique
    private boolean noellesroles$corpseBoxActive;

    /**
     * 碰撞箱不会随 {@code getDefaultDimensions} 自动重算：形态切换时手动 refreshDimensions，
     * 否则旁观者客户端会一直用旧的站立碰撞箱去判定瞄准命中。
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void noellesroles$refreshCorpseBox(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        boolean corpse = InsaneKillerRoleData.isCorpseForm(self);
        if (corpse != noellesroles$corpseBoxActive) {
            noellesroles$corpseBoxActive = corpse;
            self.refreshDimensions();
        }
    }

    @ModifyReturnValue(method = "getDefaultDimensions", at = @At("RETURN"))
    private EntityDimensions noellesroles$lowerEyeToPig(EntityDimensions dimensions, Pose pose) {
        Player self = (Player) (Object) this;
        EntityDimensions cachResult = getResult(self, dimensions, pose);
        if (cachResult != null)
            return cachResult;
        return dimensions;
    }

    private EntityDimensions getResult(Player self, EntityDimensions original, Pose pose) {
        if (noellesroles$corpseBoxActive) {
            // 伪装成尸体：碰撞箱换成尸体的 1.0×0.25，眼高保持原值
            return TMMEntities.PLAYER_BODY.getDimensions().withEyeHeight(original.eyeHeight());
        }
        if (PhantomSpiritRoleData.isDisguised(self)) {
            EntityDimensions allay = EntityType.ALLAY.getDimensions();
            if (pose == Pose.SLEEPING || pose == Pose.SWIMMING || pose == Pose.SPIN_ATTACK) {
                return original.withEyeHeight(Math.min(original.eyeHeight(), allay.eyeHeight()));
            }
            return allay;
        }
        if (TomatoHeadRoleData.isTomatoForm(self)) {
            return original.withEyeHeight(TomatoHeadRoleData.TOMATO_EYE_HEIGHT);
        }
        if (RoleUtils.isPlayerTheModifier(self, NRModifiers.RABBIT_SHAPE)) {
            // 取较小值：游泳、睡觉等姿态的眼高本就低于猪，不该被抬回来
            float eyeHeight = Math.min(original.eyeHeight(), LeatherPigRoleData.PIG_EYE_HEIGHT);
            return original.withEyeHeight(eyeHeight);
        }
        if (LeatherPigRoleData.isDisguised(self)) {
            // 取较小值：游泳、睡觉等姿态的眼高本就低于猪，不该被抬回来
            float eyeHeight = Math.min(original.eyeHeight(), LeatherPigRoleData.PIG_EYE_HEIGHT);
            return original.withEyeHeight(eyeHeight);
        }
        return original;
    }
}
