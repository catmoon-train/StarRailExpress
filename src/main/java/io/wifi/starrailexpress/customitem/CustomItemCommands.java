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

package io.wifi.starrailexpress.customitem;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.wifi.starrailexpress.SRE;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.List;

/**
 * 自定义列车物品相关指令。
 *
 * <p>
 * {@code /sre:givecustomitem <玩家> <自定义物品的id> <数量>}
 */
public final class CustomItemCommands {

    private CustomItemCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:givecustomitem")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.players())
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> give(context, 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                        .executes(context -> give(context,
                                                IntegerArgumentType.getInteger(context, "count")))))));
    }

    private static int give(CommandContext<CommandSourceStack> context, int count) {
        CommandSourceStack source = context.getSource();
        String id;
        try {
            id = StringArgumentType.getString(context, "id").toLowerCase();
        } catch (Exception e) {
            source.sendFailure(Component.translatable("sre.custom_item.error.empty_id"));
            return 0;
        }
        CustomItemData data = CustomItemLoader.get(id);
        if (data == null) {
            source.sendFailure(Component.translatable("sre.custom_item.error.unknown_id", id));
            return 0;
        }
        Collection<ServerPlayer> targets;
        try {
            targets = EntityArgument.getPlayers(context, "player");
        } catch (Exception e) {
            source.sendFailure(Component.translatable("sre.custom_item.error.no_target"));
            return 0;
        }
        int given = 0;
        for (ServerPlayer target : targets) {
            ItemStack stack = CustomItemLoader.buildStack(data, count);
            if (stack.isEmpty()) {
                source.sendFailure(Component.translatable("sre.custom_item.error.item_missing"));
                return 0;
            }
            if (!target.getInventory().add(stack)) {
                target.drop(stack, false);
            }
            target.containerMenu.broadcastChanges();
            given++;
        }
        final int finalGiven = given;
        source.sendSuccess(() -> Component.translatable("sre.custom_item.give.success", finalGiven, id, count)
                .withStyle(style -> style.withColor(0x55FF55)), true);
        SRE.LOGGER.info("[CustomItem] Gave {} x{} to {} player(s) by {}",
                id, count, finalGiven, source.getTextName());
        return finalGiven;
    }

    /** 供界面 / 其它逻辑复用：给玩家发一个自定义列车物品。 */
    public static boolean giveTo(ServerPlayer target, String id, int count) {
        CustomItemData data = CustomItemLoader.get(id);
        if (data == null) {
            return false;
        }
        ItemStack stack = CustomItemLoader.buildStack(data, Math.max(1, count));
        if (stack.isEmpty()) {
            return false;
        }
        if (!target.getInventory().add(stack)) {
            target.drop(stack, false);
        }
        target.containerMenu.broadcastChanges();
        return true;
    }

    /** 已配置的自定义物品 id 列表（供指令补全使用）。 */
    public static List<String> allIds() {
        return CustomItemLoader.getAllData().stream().map(data -> data.id).toList();
    }
}
