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

package org.agmas.harpymodloader.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;

public class ToggleCustomRoleWeightsCommand {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("toggleCustomRoleWeights")
                .requires(serverCommandSource -> serverCommandSource.hasPermission(2))
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(context -> execute(context.getSource(),
                                BoolArgumentType.getBool(context, "enabled")))));
    }

    private static int execute(CommandSourceStack source, boolean enabled) throws CommandSyntaxException {
        if(!Harpymodloader.officialVerify) {
            return 1;
        }
        // 更新配置中的自定义权重开关
        HarpyModLoaderConfig.HANDLER.instance().useCustomRoleWeights = enabled;
        
        // 保存配置
        HarpyModLoaderConfig.HANDLER.save();

        source.sendSuccess(() -> Component.translatable("commands.sre.togglecustomroleweights.success", 
                enabled ? "enabled" : "disabled"), true);
        return 1;
    }
}