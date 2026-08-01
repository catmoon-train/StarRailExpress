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

package io.wifi.starrailexpress.mixin.client.effects;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 处理隐身渲染
 */
@Mixin(EntityRenderer.class)
public class InvisiblePlayer {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void hideInvisiblePlayer(Entity entity, Frustum frustum, double x, double y, double z,
            CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Player player) {
            var self = Minecraft.getInstance().player;
            if (self == null)
                return;
            if (SREClient.gameComponent == null)
                return;

            if (!SREClient.gameComponent.isRunning())
                return;
            if (player.hasEffect(MobEffects.INVISIBILITY) || player.isInvisible())
                // 完全隐身，其他玩家看不到
                cir.setReturnValue(false);
        }
    }
}