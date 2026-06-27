package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wifi.starrailexpress.backpack.BackpackManager;
import io.wifi.starrailexpress.network.OpenBackpackScreenPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /sre:backpack} —— 打开场外背包 GUI。沿用代码库冒号字面量命令约定（同 {@code sre:pass}）。
 */
public final class BackpackCommand {
    private BackpackCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:backpack")
                .executes(context -> open(context.getSource())));
    }

    private static int open(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        // 开屏前重发一次背包分区，保证客户端展示的是最新计数
        BackpackManager.resend(player);
        ServerPlayNetworking.send(player, new OpenBackpackScreenPayload());
        return 1;
    }
}
