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

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.wifi.starrailexpress.client.ReporterTerminalClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 记者终端视角的模型隐藏。
 *
 * <p>终端相机正好放在被观察玩家的眼睛位置，目标自己的脑袋会把画面完全堵住；
 * 同时自己的模型也不应该出现在别人的第一人称画面里。因此观察期间只对这一对玩家
 * （被观察者 + 观察者本人）关闭渲染，其他玩家照常可见。
 *
 * <p>用 {@code shouldRender} 的返回值拦截，而不是取消 {@code render}：这样主画面与
 * 小窗的离屏二次渲染都会自动生效，且不影响实体剔除以外的逻辑。
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class ReporterTerminalEntityMixin {

    @ModifyReturnValue(method = "shouldRender", at = @At("RETURN"))
    private boolean reporterTerminal$hideSubject(boolean original, Entity entity) {
        if (!original || !ReporterTerminalClient.isActive() || !(entity instanceof Player)) {
            return original;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && entity == minecraft.player) {
            return false;
        }
        Player target = ReporterTerminalClient.currentTargetPlayer();
        return target == null || entity != target;
    }
}
