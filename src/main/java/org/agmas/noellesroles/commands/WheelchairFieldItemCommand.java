package org.agmas.noellesroles.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.entity.WheelchairFieldItemEntity;
import org.agmas.noellesroles.init.ModEntities;

public class WheelchairFieldItemCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var fieldItemCommand = Commands.literal("tmm:nr")
                    .then(Commands.literal("fielditem")
                            .requires(source -> source.hasPermission(2))
                            .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                    .then(Commands.argument("always", BoolArgumentType.bool()))
                                    .then(Commands.argument("effect_type", IntegerArgumentType.integer(0, 2))
                                            .executes(context -> createFieldItem(
                                                    context,
                                                    BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                    IntegerArgumentType.getInteger(context, "effect_type"),
                                                    BoolArgumentType.getBool(context, "always")
                                            ))
                                    )
                            )
                    );
            dispatcher.register(fieldItemCommand);
        });
    }

    private static int createFieldItem(CommandContext<CommandSourceStack> context, BlockPos pos, int effectTypeId, boolean always) {
        Level world = context.getSource().getLevel();
        WheelchairFieldItemEntity.EffectType effectType = WheelchairFieldItemEntity.EffectType.fromId(effectTypeId);
        
        WheelchairFieldItemEntity entity = new WheelchairFieldItemEntity(
                ModEntities.WHEELCHAIR_FIELD_ITEM,
                world,
                pos.getX() + 0.5,
                pos.getY() + 0.1,
                pos.getZ() + 0.5,
                effectType
        );
        // 设置物品显示，确保与效果类型匹配
        entity.setPickedUp(true);
        entity.setItem(effectType.getDisplayItem());
        world.addFreshEntity(entity);
        context.getSource().sendSuccess(() -> Component.literal("成功在 " + pos.toShortString() + " 生成道具: " + effectType.name()), true);
        return 1;
    }
}