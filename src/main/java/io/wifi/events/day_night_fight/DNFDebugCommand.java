package io.wifi.events.day_night_fight;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import io.wifi.events.day_night_fight.cca.DNFWorldComponent;
import io.wifi.events.day_night_fight.cca.DNFPlayerComponent;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

public class DNFDebugCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("tmm:dnf")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("give")
                                .then(Commands.literal("flying_knife").executes(ctx -> give(ctx, DNFItems.FLYING_KNIFE)))
                                .then(Commands.literal("lockpick").executes(ctx -> give(ctx, DNFItems.LOCKPICK)))
                                .then(Commands.literal("crowbar").executes(ctx -> give(ctx, DNFItems.CROWBAR)))
                                .then(Commands.literal("repair_tool").executes(ctx -> give(ctx, DNFItems.REPAIR_TOOL)))
                                .then(Commands.literal("paper_scrap").executes(ctx -> give(ctx, DNFItems.PAPER_SCRAP)))
                                .then(Commands.literal("cleaning_byproduct").executes(ctx -> give(ctx, DNFItems.CLEANING_BYPRODUCT)))
                                .then(Commands.literal("task_tool").executes(ctx -> give(ctx, DNFItems.TASK_TOOL)))
                                .then(Commands.literal("cleaning_task_point").executes(ctx -> give(ctx, DNFItems.CLEANING_TASK_POINT_ITEM)))
                                .then(Commands.literal("exchange_task_point").executes(ctx -> give(ctx, DNFItems.EXCHANGE_TASK_POINT_ITEM)))
                                .then(Commands.literal("food").executes(DNFDebugCommand::giveFood))
                                .then(Commands.literal("chef_supplies").executes(DNFDebugCommand::giveChefSupplies))
                                .then(Commands.literal("all").executes(DNFDebugCommand::giveAll)))
                        .then(Commands.literal("throw_flying_knife")
                                .executes(DNFDebugCommand::throwFlyingKnife))
                        .then(Commands.literal("blood")
                                .then(Commands.literal("get").executes(DNFDebugCommand::getBlood))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer())
                                                .executes(DNFDebugCommand::addBlood)))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                .executes(DNFDebugCommand::setBlood))))
                        .then(Commands.literal("task")
                                .then(Commands.literal("meal").executes(DNFDebugCommand::completeMeal))
                                .then(Commands.literal("toilet").executes(DNFDebugCommand::completeToilet))
                                .then(Commands.literal("lecture").executes(DNFDebugCommand::completeLecture))
                                .then(Commands.literal("web").executes(DNFDebugCommand::completeWeb))
                                .then(Commands.literal("dust").executes(DNFDebugCommand::completeDust))
                                .then(Commands.literal("chef_work").executes(DNFDebugCommand::completeChefWork))
                                .then(Commands.literal("water_check").executes(DNFDebugCommand::checkWater))
                                .then(Commands.literal("reset_day").executes(DNFDebugCommand::resetDay)))
                        .then(Commands.literal("config")
                                .then(Commands.literal("show").executes(DNFDebugCommand::showConfig))
                                .then(Commands.literal("set")
                                        .then(Commands.literal("food_box").executes(ctx -> setTargetPos(ctx, "food_box")))
                                        .then(Commands.literal("water_source").executes(ctx -> setTargetPos(ctx, "water_source")))
                                        .then(Commands.literal("meteor").executes(ctx -> setTargetPos(ctx, "meteor")))
                                        .then(Commands.literal("wall_hole").executes(ctx -> setTargetPos(ctx, "wall_hole")))
                                        .then(Commands.literal("old_chef_diary").executes(ctx -> setTargetPos(ctx, "old_chef_diary")))
                                        .then(Commands.literal("meeting_pos").executes(ctx -> setTargetPos(ctx, "meeting_pos")))
                                        .then(Commands.literal("meeting_radius")
                                                .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                                                        .executes(DNFDebugCommand::setMeetingRadius)))
                                        .then(Commands.literal("underworld_center").executes(ctx -> setTargetPos(ctx, "underworld_center")))
                                        .then(Commands.literal("underworld_radius")
                                                .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                                                        .executes(DNFDebugCommand::setUnderworldRadius)))
                                        .then(Commands.literal("cafeteria_area")
                                                .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                                                        .executes(DNFDebugCommand::setCafeteriaArea)))
                                        .then(Commands.literal("dorm_room")
                                                .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                                                        .executes(DNFDebugCommand::setDormRoom)))))));
    }

    private static int give(CommandContext<CommandSourceStack> ctx, net.minecraft.world.item.Item item)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        giveOrDrop(player, item.getDefaultInstance());
        ctx.getSource().sendSuccess(() -> Component.literal("Gave " + item), false);
        return 1;
    }

    private static int giveFood(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        giveOrDrop(player, new ItemStack(DNFItems.CORN_GRUEL, 10));
        giveOrDrop(player, new ItemStack(DNFItems.BLACK_BREAD, 10));
        giveOrDrop(player, new ItemStack(DNFItems.MEAT_RATION, 10));
        giveOrDrop(player, new ItemStack(DNFItems.WATER_BOTTLE, 4));
        ctx.getSource().sendSuccess(() -> Component.literal("Gave DNF food and water"), false);
        return 1;
    }

    private static int giveChefSupplies(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        giveOrDrop(player, DNFItems.CORNMEAL_BAG.getDefaultInstance());
        giveOrDrop(player, DNFItems.FLOUR_BAG.getDefaultInstance());
        giveOrDrop(player, DNFItems.SUSPICIOUS_MEAT.getDefaultInstance());
        giveOrDrop(player, new ItemStack(DNFItems.WATER_BOTTLE, 2));
        ctx.getSource().sendSuccess(() -> Component.literal("Gave DNF chef supplies"), false);
        return 1;
    }

    private static int giveAll(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        give(ctx, DNFItems.FLYING_KNIFE);
        give(ctx, DNFItems.LOCKPICK);
        give(ctx, DNFItems.CROWBAR);
        giveFood(ctx);
        giveChefSupplies(ctx);
        return 1;
    }

    private static int throwFlyingKnife(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        boolean thrown = DNFFlyingKnifeItem.throwDnfKnife(player, DNFItems.FLYING_KNIFE.getDefaultInstance(), false);
        ctx.getSource().sendSuccess(() -> Component.literal(thrown ? "Thrown DNF flying knife" : "Flying knife is on cooldown"),
                false);
        return thrown ? 1 : 0;
    }

    private static int getBlood(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int blood = DNFPlayerComponent.KEY.get(player).getBlood();
        ctx.getSource().sendSuccess(() -> Component.literal("DNF blood = " + blood), false);
        return blood;
    }

    private static int addBlood(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        DNFPlayerComponent component = DNFPlayerComponent.KEY.get(player);
        component.addBlood(amount);
        ctx.getSource().sendSuccess(() -> Component.literal("DNF blood = " + component.getBlood()), false);
        return component.getBlood();
    }

    private static int setBlood(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        DNFPlayerComponent component = DNFPlayerComponent.KEY.get(player);
        component.setBlood(amount);
        ctx.getSource().sendSuccess(() -> Component.literal("DNF blood = " + component.getBlood()), false);
        return component.getBlood();
    }

    private static int completeMeal(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        DNFPlayerComponent component = DNFPlayerComponent.KEY.get(player);
        component.markAteFood(player);
        component.markDrankWater(player);
        return 1;
    }

    private static int completeWeb(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        DNFPlayerComponent component = DNFPlayerComponent.KEY.get(player);
        if (!component.beginCleaningTask(player)) {
            return 0;
        }
        component.finishCleaningTask(player, io.wifi.starrailexpress.cca.SREPlayerTaskComponent.Task.DNF_LIBRARY_WEB,
                "message.dnf.task.library_web");
        return 1;
    }

    private static int completeToilet(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        return DNFPlayerComponent.KEY.get(player).completeToilet(player) ? 1 : 0;
    }

    private static int completeLecture(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        return DNFPlayerComponent.KEY.get(player).completeLecture(player) ? 1 : 0;
    }

    private static int completeDust(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        DNFPlayerComponent component = DNFPlayerComponent.KEY.get(player);
        if (!component.beginCleaningTask(player)) {
            return 0;
        }
        component.finishCleaningTask(player, io.wifi.starrailexpress.cca.SREPlayerTaskComponent.Task.DNF_PRISON_DUST,
                "message.dnf.task.prison_dust");
        return 1;
    }

    private static int completeChefWork(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        return DNFPlayerComponent.KEY.get(player).useChefCapacity(player, DNF.CHEF_RECIPE_OUTPUT) ? 1 : 0;
    }

    private static int checkWater(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        return DNFPlayerComponent.KEY.get(player).checkChefWater(player) ? 1 : 0;
    }

    private static int resetDay(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        DNFPlayerComponent component = DNFPlayerComponent.KEY.get(player);
        component.startDnfDay(player, component.getDnfDay() + 1, DNF.isDNFChef(player));
        ctx.getSource().sendSuccess(() -> Component.literal("Reset DNF daily state"), false);
        return 1;
    }

    private static int setTargetPos(CommandContext<CommandSourceStack> ctx, String key)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BlockHitResult hit = DNF.findLookedAtBlock(player, 8.0);
        if (hit == null) {
            ctx.getSource().sendFailure(Component.literal("No targeted block"));
            return 0;
        }
        BlockPos pos = hit.getBlockPos();
        DNFWorldComponent component = DNFWorldComponent.KEY.get(player.serverLevel());
        switch (key) {
            case "food_box" -> component.setFoodBoxPos(pos);
            case "water_source" -> component.setWaterSourcePos(pos);
            case "meteor" -> component.setMeteorPos(pos);
            case "wall_hole" -> component.setWallHolePos(pos);
            case "old_chef_diary" -> component.setOldChefDiaryPos(pos);
            case "meeting_pos" -> component.setMeetingPos(pos);
            case "underworld_center" -> component.setUnderworldCenter(pos);
            default -> {
                return 0;
            }
        }
        ctx.getSource().sendSuccess(() -> Component.literal("DNF " + key + " = " + pos.toShortString()), false);
        return 1;
    }

    private static int setMeetingRadius(CommandContext<CommandSourceStack> ctx) {
        int radius = IntegerArgumentType.getInteger(ctx, "radius");
        DNFWorldComponent component = DNFWorldComponent.KEY.get(ctx.getSource().getLevel());
        component.setMeetingRadius(radius);
        ctx.getSource().sendSuccess(() -> Component.literal("DNF meeting radius = " + radius), false);
        return 1;
    }

    private static int setUnderworldRadius(CommandContext<CommandSourceStack> ctx) {
        int radius = IntegerArgumentType.getInteger(ctx, "radius");
        DNFWorldComponent component = DNFWorldComponent.KEY.get(ctx.getSource().getLevel());
        component.setUnderworldRadius(radius);
        ctx.getSource().sendSuccess(() -> Component.literal("DNF underworld radius = " + radius), false);
        return 1;
    }

    private static int setCafeteriaArea(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int radius = IntegerArgumentType.getInteger(ctx, "radius");
        DNFWorldComponent component = DNFWorldComponent.KEY.get(player.serverLevel());
        component.setCafeteriaArea(new AABB(player.blockPosition()).inflate(radius, 3, radius));
        ctx.getSource().sendSuccess(() -> Component.literal("DNF cafeteria area centered at "
                + player.blockPosition().toShortString() + " radius " + radius), false);
        return 1;
    }

    private static int setDormRoom(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int radius = IntegerArgumentType.getInteger(ctx, "radius");
        DNFWorldComponent.KEY.get(player.serverLevel()).setDormRoom(player.getUUID(),
                new AABB(player.blockPosition()).inflate(radius, 3, radius));
        ctx.getSource().sendSuccess(() -> Component.literal("DNF dorm room set for "
                + player.getGameProfile().getName() + " radius " + radius), false);
        return 1;
    }

    private static int showConfig(CommandContext<CommandSourceStack> ctx) {
        DNFWorldComponent component = DNFWorldComponent.KEY.get(ctx.getSource().getLevel());
        ctx.getSource().sendSuccess(() -> Component.literal("DNF config: food_box="
                + component.getFoodBoxPos() + ", water_source=" + component.getWaterSourcePos()
                + ", meteor=" + component.getMeteorPos() + ", wall_hole=" + component.getWallHolePos()
                + ", old_chef_diary=" + component.getOldChefDiaryPos()
                + ", meeting_pos=" + component.getMeetingPos() + ", meeting_radius=" + component.getMeetingRadius()
                + ", underworld_center=" + component.getUnderworldCenter()
                + ", underworld_radius=" + component.getUnderworldRadius()
                + ", day=" + component.getCurrentDay() + ", night=" + component.isNight()), false);
        return 1;
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.addItem(stack.copy())) {
            player.drop(stack.copy(), false);
        }
    }
}
