package org.agmas.noellesroles.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.LevelResource;
import org.agmas.noellesroles.init.ModItems;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class RepairPresetCommand {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private RepairPresetCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("repairpreset")
                        .requires(source -> source.hasPermission(2))
                        .then(literal("export")
                                .then(argument("mapId", StringArgumentType.word())
                                        .then(argument("entryId", StringArgumentType.word())
                                                .executes(context -> {
                                                    try {
                                                        return export(context.getSource().getPlayerOrException(),
                                                                StringArgumentType.getString(context, "mapId"),
                                                                StringArgumentType.getString(context, "entryId"));
                                                    } catch (IOException exception) {
                                                        context.getSource().sendFailure(Component.translatable(
                                                                "message.noellesroles.repair.preset_export_failed",
                                                                exception.getMessage()));
                                                        return 0;
                                                    }
                                                }))))));
    }

    private static int export(ServerPlayer player, String mapId, String entryId) throws IOException {
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.REPAIR_PRESET_WAND)) {
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.preset_need_wand")
                    .withStyle(ChatFormatting.RED), false);
            return 0;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        ListTag points = tag.getList("Points", Tag.TAG_COMPOUND);
        if (points.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.preset_empty")
                    .withStyle(ChatFormatting.RED), false);
            return 0;
        }
        BlockPos anchor = hasPos(tag, "Anchor") ? readPos(tag, "Anchor") : readPos(points.getCompound(0), "Pos");
        BlockPos first = readPos(points.getCompound(0), "Pos");

        JsonObject root = new JsonObject();
        root.addProperty("mapId", mapId);
        root.addProperty("entryId", entryId);
        JsonObject repair = new JsonObject();
        JsonArray cloneEntries = new JsonArray();
        for (int i = 0; i < points.size(); i++) {
            BlockPos source = readPos(points.getCompound(i), "Pos");
            BlockPos target = anchor.offset(source.subtract(first));
            JsonObject entry = new JsonObject();
            entry.add("source", pos(source));
            entry.add("target", pos(target));
            entry.add("size", pos(new BlockPos(1, 1, 1)));
            cloneEntries.add(entry);
        }
        repair.add("cloneEntries", cloneEntries);
        root.add("repair", repair);

        Path dir = player.getServer().getWorldPath(LevelResource.ROOT).resolve("repair_preset_exports");
        Files.createDirectories(dir);
        Path file = dir.resolve(mapId + "_" + entryId + ".json");
        Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        player.displayClientMessage(Component.translatable("message.noellesroles.repair.preset_exported",
                points.size(), file.toString()).withStyle(ChatFormatting.GREEN), false);
        return points.size();
    }

    private static boolean hasPos(CompoundTag tag, String key) {
        return tag.contains(key + "X") && tag.contains(key + "Y") && tag.contains(key + "Z");
    }

    private static BlockPos readPos(CompoundTag tag, String key) {
        return new BlockPos(tag.getInt(key + "X"), tag.getInt(key + "Y"), tag.getInt(key + "Z"));
    }

    private static JsonObject pos(BlockPos pos) {
        JsonObject object = new JsonObject();
        object.addProperty("x", pos.getX());
        object.addProperty("y", pos.getY());
        object.addProperty("z", pos.getZ());
        return object;
    }
}
