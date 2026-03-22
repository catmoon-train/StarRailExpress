package io.wifi.starrailexpress.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

public class GiveRoomKeyCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("tmm:giveRoomKey")
                        .requires(source -> source.hasPermission(2))
                        .then(
                                Commands.argument("roomName", StringArgumentType.string())
                                        .executes(context -> giveRoomKey(context.getSource(),
                                                StringArgumentType.getString(context, "roomName")))));
    }

    private static int giveRoomKey(CommandSourceStack source, String roomName) {
        ItemStack itemStack = new ItemStack(TMMItems.KEY);
        itemStack.set(DataComponents.ITEM_NAME, Component.literal("The Key of '" + roomName + "'"));
        itemStack.update(DataComponents.LORE, ItemLore.EMPTY, component -> new ItemLore(
                Component.literal(roomName).toFlatList(Style.EMPTY.withItalic(false).withColor(0xFF8C00))));
        if (source.getPlayer() != null) {
            source.getPlayer().addItem(itemStack);
            source.sendSuccess(
                    () -> Component.translatable("commands.sre.giveroomkey", roomName)
                            .withStyle(style -> style.withColor(0x00FF00)),
                    true);
        }
        return 1;
    }
}
