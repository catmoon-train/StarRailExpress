package io.wifi.starrailexpress.content.command.misc;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;

// CommandPredicate.java（放在你自己的包里）
@FunctionalInterface
public interface CommandPredicate {
    boolean test(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
}
