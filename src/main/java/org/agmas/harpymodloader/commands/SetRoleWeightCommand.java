package org.agmas.harpymodloader.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wifi.starrailexpress.api.SRERole;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.argument.RoleArgumentType;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;

public class SetRoleWeightCommand {
//     private static final SimpleCommandExceptionType FAILED_EXCEPTION = new SimpleCommandExceptionType(
//             Text.translatable("commands.sre.setroleweight.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("setRoleWeight")
                .requires(serverCommandSource -> serverCommandSource.hasPermission(2))
                .then(Commands.argument("role", RoleArgumentType.skipVanilla())
                        .then(Commands.argument("weight", FloatArgumentType.floatArg(0))
                                .executes(context -> execute(context.getSource(),
                                        RoleArgumentType.getRole(context, "role"),
                                        FloatArgumentType.getFloat(context, "weight"))))));
    }

    private static int execute(CommandSourceStack source, SRERole role, float weight) throws CommandSyntaxException {
        if(!Harpymodloader.isMojangVerify) {
            return 1;
        }
        // 更新配置中的角色权重
        HarpyModLoaderConfig.HANDLER.instance().roleWeights.put(role.identifier().toString(), weight);
        
        // 保存配置
        HarpyModLoaderConfig.HANDLER.save();

        source.sendSuccess(() -> Component.translatable("commands.sre.setroleweight.success", 
                role.getIdentifier().toString(), weight), true);
        return 1;
    }
}