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

package io.wifi.starrailexpress.mixin.command;

import io.wifi.starrailexpress.game.ElevatedBlockCommandPermission;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 原版命令方块 / 命令方块矿车的执行权限写死为 2，只能通过 mixin 提升。
 * 开关开启后把 {@code createCommandSourceStack} 的权限改成 3。
 */
@Mixin(targets = {
        "net.minecraft.world.level.block.entity.CommandBlockEntity$1",
        "net.minecraft.world.entity.vehicle.MinecartCommandBlock$MinecartCommandBase"
})
public abstract class CommandBlockSourcePermissionMixin {
    @Inject(method = "createCommandSourceStack", at = @At("RETURN"), cancellable = true)
    private void sre$elevateCommandBlockPermission(CallbackInfoReturnable<CommandSourceStack> cir) {
        if (ElevatedBlockCommandPermission.isEnabled()) {
            cir.setReturnValue(cir.getReturnValue().withPermission(ElevatedBlockCommandPermission.ELEVATED_LEVEL));
        }
    }
}
