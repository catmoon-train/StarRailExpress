package io.wifi.starrailexpress.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.Collection;

@SuppressWarnings("deprecation")
public class EntityDataCommand {
        public static final AttachmentType<String> ENTITY_CUSTOM_DATA_COMMAND = AttachmentRegistry.<String>builder()
                        .persistent(Codec.STRING)
                        .buildAndRegister(ResourceLocation.tryBuild("tmm", "entity_custom_data_interaction"));

        public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
                dispatcher.register(
                                Commands.literal("tmm:entityData")
                                                .requires(source -> source.hasPermission(2))
                                                .then(Commands.literal("set")
                                                                .then(Commands.argument("targets",
                                                                                EntityArgument.entities())
                                                                                .then(Commands.argument("data",
                                                                                                StringArgumentType
                                                                                                                .greedyString())
                                                                                                .executes(context -> setEntityData(
                                                                                                                context.getSource(),
                                                                                                                EntityArgument.getEntities(
                                                                                                                                context,
                                                                                                                                "targets"),
                                                                                                                StringArgumentType
                                                                                                                                .getString(context,
                                                                                                                                                "data")))))));
        }

        private static int setEntityData(CommandSourceStack source, Collection<? extends Entity> targets, String data) {
                int count = 0;
                for (Entity entity : targets) {
                        // 设置实体的自定义数据
                        entity.setAttached(ENTITY_CUSTOM_DATA_COMMAND, data);
                        count++;
                }

                int finalCount = count;
                source.sendSuccess(
                                () -> Component.translatable("commands.sre.entitydata.set.success", finalCount, data)
                                                .withStyle(style -> style.withColor(0x00FF00)),
                                true);
                return 0;
        }
}