package io.wifi.sreperf;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MemoryServerCommandTest {
    @Test void bothServerRootsRequirePermissionTwoAndKeepAllocationBounded() throws Exception {
        var method = PerformanceCommon.class.getDeclaredMethod("command", String.class);
        method.setAccessible(true);
        for (String name : new String[]{"sreperf", "sreperfserver"}) {
            @SuppressWarnings("unchecked")
            var root = ((LiteralArgumentBuilder<CommandSourceStack>) method.invoke(null, name)).build();
            assertFalse(root.canUse(source(0)));
            assertFalse(root.canUse(source(1)));
            assertTrue(root.canUse(source(2)));
            assertTrue(root.canUse(source(4)));
            var memory = root.getChild("memory");
            assertNotNull(memory.getCommand());
            assertNotNull(memory.getChild("histogram").getCommand());
            var allocation = memory.getChild("allocation");
            assertNotNull(allocation.getCommand());
            var duration = (IntegerArgumentType) ((ArgumentCommandNode<?, ?>) allocation.getChild("seconds")).getType();
            assertEquals(10, duration.getMinimum());
            assertEquals(180, duration.getMaximum());
        }
    }

    private static CommandSourceStack source(int permission) {
        return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, permission,
                "", Component.empty(), null, null);
    }
}
