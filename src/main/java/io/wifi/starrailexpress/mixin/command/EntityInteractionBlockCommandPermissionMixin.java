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

import io.wifi.starrailexpress.content.block_entity.EntityInteractionBlockEntity;
import io.wifi.starrailexpress.game.ElevatedBlockCommandPermission;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 开关开启后，实体交互方块 EXECUTE_COMMAND 的执行权限提升为 3。
 */
@Mixin(EntityInteractionBlockEntity.class)
public abstract class EntityInteractionBlockCommandPermissionMixin {
    @ModifyArg(
            method = "executeSpecialAction",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/CommandSourceStack;withPermission(I)Lnet/minecraft/commands/CommandSourceStack;"
            ),
            index = 0
    )
    private int sre$elevateEntityInteractionPermission(int original) {
        return ElevatedBlockCommandPermission.resolve(original);
    }
}
