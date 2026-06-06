package io.wifi.starrailexpress.customrole;

import com.mojang.brigadier.CommandDispatcher;
import io.wifi.starrailexpress.SRE;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * 自定义职业重载命令: sre:reloadRoleConfig
 */
public class CustomRoleReloadCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:reloadRoleConfig")
            .requires(source -> source.hasPermission(3))
            .executes(context -> {
                CommandSourceStack source = context.getSource();
                try {
                    CustomRoleLoader.reload(source.getServer());
                    source.sendSuccess(
                        () -> Component.literal("[CustomRole] 自定义职业配置已重新加载")
                            .withStyle(s -> s.withColor(0x55FF55)),
                        true);
                    SRE.LOGGER.info("[CustomRole] Reloaded custom roles by {}", source.getTextName());
                    return 1;
                } catch (Exception e) {
                    source.sendFailure(Component.literal("[CustomRole] 重载失败: " + e.getMessage()));
                    SRE.LOGGER.error("[CustomRole] Reload failed", e);
                    return 0;
                }
            }));
    }
}
