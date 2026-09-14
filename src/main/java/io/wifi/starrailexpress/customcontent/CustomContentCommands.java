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

package io.wifi.starrailexpress.customcontent;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.customblock.CustomBlockData;
import io.wifi.starrailexpress.customblock.CustomBlockEntity;
import io.wifi.starrailexpress.customblock.CustomBlockLoader;
import io.wifi.starrailexpress.customitem.CustomItemData;
import io.wifi.starrailexpress.customitem.CustomItemLoader;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * 自定义内容的统一指令。
 *
 * <ul>
 * <li>{@code /sre:give [<玩家>] block|item <id>[<组件>] [数量]} —— 与原版 {@code /give} 同形：
 * 物品参数支持原版组件语法（{@code [minecraft:custom_name="…",minecraft:unbreakable={}]}），
 * 不写目标玩家时发给自己；</li>
 * <li>{@code /sre:setblock <坐标> <id> [朝向]} —— 放置一个自定义方块。</li>
 * </ul>
 *
 * <p>
 * 这两条指令取代了原来的 {@code /sre:givecustomitem} / {@code /sre:givecustomblock} /
 * {@code /sre:setcustomblock}。
 */
public final class CustomContentCommands {

    private static final List<String> FACINGS = List.of("north", "south", "east", "west");

    private CustomContentCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        LiteralArgumentBuilder<CommandSourceStack> give = Commands.literal("sre:give")
                .requires(source -> source.hasPermission(2));
        // 不写玩家 = 给自己
        give.then(giveBranch(CustomContentArgument.Kind.BLOCK, null, buildContext));
        give.then(giveBranch(CustomContentArgument.Kind.ITEM, null, buildContext));
        // 与原版一致：<targets> 在最前
        give.then(Commands.argument("targets", EntityArgument.players())
                .then(giveBranch(CustomContentArgument.Kind.BLOCK, "targets", buildContext))
                .then(giveBranch(CustomContentArgument.Kind.ITEM, "targets", buildContext)));
        dispatcher.register(give);

        dispatcher.register(Commands.literal("sre:setblock")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("id", CustomContentArgument.block(buildContext))
                                .executes(context -> setBlock(context, null))
                                .then(Commands.argument("facing", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            FACINGS.forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> setBlock(context,
                                                StringArgumentType.getString(context, "facing")))))));
    }

    // ==================== /sre:give ====================

    private static LiteralArgumentBuilder<CommandSourceStack> giveBranch(CustomContentArgument.Kind kind,
            @Nullable String targetsArg, CommandBuildContext buildContext) {
        CustomContentArgument argument = kind == CustomContentArgument.Kind.BLOCK
                ? CustomContentArgument.block(buildContext)
                : CustomContentArgument.item(buildContext);
        return Commands.literal(kind == CustomContentArgument.Kind.BLOCK ? "block" : "item")
                .then(Commands.argument("id", argument)
                        .executes(context -> give(context, kind, targetsArg, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                .executes(context -> give(context, kind, targetsArg,
                                        IntegerArgumentType.getInteger(context, "count")))));
    }

    private static int give(CommandContext<CommandSourceStack> context, CustomContentArgument.Kind kind,
            @Nullable String targetsArg, int count) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        CustomContentArgument.Value value = CustomContentArgument.getValue(context, "id");
        String id = value.id().toLowerCase(Locale.ROOT);

        // 先校验内容存在，再解析组件（避免报错顺序反了让人困惑）
        if (kind == CustomContentArgument.Kind.BLOCK) {
            if (CustomBlockLoader.get(id) == null) {
                source.sendFailure(Component.translatable("sre.custom_content.error.unknown_block", id));
                return 0;
            }
        } else if (CustomItemLoader.get(id) == null) {
            source.sendFailure(Component.translatable("sre.custom_content.error.unknown_item", id));
            return 0;
        }
        // 组件在参数解析阶段就由原版物品参数解析好了（语法 / 报错都与原版一致）
        DataComponentPatch patch = value.hasComponents() ? value.components() : null;

        Collection<ServerPlayer> targets = targetsArg == null
                ? List.of(source.getPlayerOrException())
                : EntityArgument.getPlayers(context, targetsArg);

        int given = 0;
        for (ServerPlayer target : targets) {
            ItemStack stack = buildStack(kind, id, count);
            if (stack.isEmpty()) {
                source.sendFailure(Component.translatable("sre.custom_content.error.missing_item"));
                return 0;
            }
            if (patch != null) {
                stack.applyComponents(patch);
            }
            if (!target.getInventory().add(stack)) {
                target.drop(stack, false);
            }
            target.containerMenu.broadcastChanges();
            given++;
        }
        final int finalGiven = given;
        source.sendSuccess(() -> Component.translatable("sre.custom_content.give.success", finalGiven, id, count)
                .withStyle(style -> style.withColor(0x55FF55)), true);
        SRE.LOGGER.info("[CustomContent] Gave {} {} x{} to {} player(s) by {}",
                kind.name().toLowerCase(Locale.ROOT), id, count, finalGiven, source.getTextName());
        return finalGiven;
    }

    /** 按类型构建基础物品栈（自定义列车物品 / 自定义方块物品）。 */
    private static ItemStack buildStack(CustomContentArgument.Kind kind, String id, int count) {
        if (kind == CustomContentArgument.Kind.BLOCK) {
            CustomBlockData data = CustomBlockLoader.get(id);
            return data == null ? ItemStack.EMPTY : CustomBlockLoader.buildStack(data, count);
        }
        CustomItemData data = CustomItemLoader.get(id);
        return data == null ? ItemStack.EMPTY : CustomItemLoader.buildStack(data, count);
    }

    // ==================== /sre:setblock ====================

    private static int setBlock(CommandContext<CommandSourceStack> context, @Nullable String facingArg)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        CustomContentArgument.Value value = CustomContentArgument.getValue(context, "id");
        String id = value.id().toLowerCase(Locale.ROOT);
        CustomBlockData data = CustomBlockLoader.get(id);
        if (data == null) {
            source.sendFailure(Component.translatable("sre.custom_content.error.unknown_block", id));
            return 0;
        }
        if (value.hasComponents()) {
            // 方块实体目前不保存任何物品组件，直接说清楚，避免玩家以为写进去生效了
            source.sendFailure(Component.translatable("sre.custom_content.error.block_components"));
            return 0;
        }

        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
        var registered = CustomBlockLoader.customBlock();
        if (!(registered instanceof io.wifi.starrailexpress.customblock.CustomBlock customBlock)) {
            source.sendFailure(Component.translatable("sre.custom_content.error.missing_block"));
            return 0;
        }
        if (!level.getBlockState(pos).canBeReplaced()) {
            source.sendFailure(Component.translatable("sre.custom_block.error.occupied", pos.toShortString()));
            return 0;
        }
        Direction facing = parseFacing(facingArg);
        if (facing == null) {
            ServerPlayer player = source.getPlayer();
            facing = player != null ? player.getDirection().getOpposite() : Direction.NORTH;
        }
        boolean waterlogged = level.getFluidState(pos).getType() == Fluids.WATER;
        BlockState state = customBlock.placedState(data, facing, waterlogged);
        level.setBlockAndUpdate(pos, state);
        if (level.getBlockEntity(pos) instanceof CustomBlockEntity entity) {
            entity.setCustomBlockId(data.id);
        }
        source.sendSuccess(() -> Component.translatable("sre.custom_content.setblock.success", id, pos.toShortString())
                .withStyle(style -> style.withColor(0x55FF55)), true);
        return 1;
    }

    /** 解析朝向名（非法返回 null → 用执行者朝向兜底）。 */
    @Nullable
    private static Direction parseFacing(@Nullable String facing) {
        if (facing == null || facing.isBlank()) {
            return null;
        }
        String value = facing.trim().toLowerCase(Locale.ROOT);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (direction.getSerializedName().equals(value) || direction.getSerializedName().startsWith(value)) {
                return direction;
            }
        }
        return null;
    }
}
