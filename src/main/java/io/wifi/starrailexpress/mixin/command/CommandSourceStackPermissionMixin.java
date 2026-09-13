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
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.level.BaseCommandBlock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 原版命令方块 / 命令方块矿车的执行权限写死为 2，只能通过 mixin 提升。
 * 仅在开关开启且当前源是 {@link BaseCommandBlock} 时，把检查上限提到 3。
 */
@Mixin(CommandSourceStack.class)
public abstract class CommandSourceStackPermissionMixin {
    @Shadow
    @Final
    private CommandSource source;

    @Shadow
    @Final
    private int permissionLevel;

    @Inject(method = "hasPermission", at = @At("HEAD"), cancellable = true)
    private void sre$elevateCommandBlockPermission(int level, CallbackInfoReturnable<Boolean> cir) {
        if (this.source instanceof BaseCommandBlock && ElevatedBlockCommandPermission.isEnabled()) {
            cir.setReturnValue(Math.max(this.permissionLevel, ElevatedBlockCommandPermission.ELEVATED_LEVEL) >= level);
        }
    }
}
