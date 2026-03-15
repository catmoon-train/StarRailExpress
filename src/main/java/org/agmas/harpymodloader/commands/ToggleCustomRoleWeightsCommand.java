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
        if(!Harpymodloader.isMojangVerify) {
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