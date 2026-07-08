package net.exmo.sre.repair.state;

import net.exmo.sre.repair.*;
import net.exmo.sre.repair.role.*;
import net.exmo.sre.repair.arena.*;
import net.exmo.sre.repair.event.*;
import net.exmo.sre.repair.util.*;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.component.RepairRolePlayerComponent;
import org.agmas.noellesroles.content.block_entity.HotbarStorageBlockEntity;
import org.agmas.noellesroles.init.ModBlocks;

public final class RepairSearchState {
    public static final int SEARCH_TICKS = 20 * 2;

    private RepairSearchState() {
    }

    public static boolean begin(ServerPlayer player, BlockPos pos) {
        if (!(player.level() instanceof ServerLevel level) || !RepairModeState.canUseSurvivorUtility(player)) {
            return false;
        }
        if (!level.getBlockState(pos).is(ModBlocks.HOTBAR_STORAGE) || player.distanceToSqr(pos.getCenter()) > 4.5D * 4.5D) {
            return false;
        }
        var component = ModComponents.REPAIR_ROLES.get(player);
        component.searchTarget = RepairRolePlayerComponent.BlockPosTag.of(pos);
        component.searchStartTick = level.getGameTime();
        component.searchTotalTicks = SEARCH_TICKS;
        component.searchPromptKey = "hud.noellesroles.repair.searching";
        component.sync();
        return true;
    }

    public static void cancel(ServerPlayer player) {
        var component = ModComponents.REPAIR_ROLES.get(player);
        if (!component.searchTarget.present()) {
            return;
        }
        component.searchTarget = RepairRolePlayerComponent.BlockPosTag.NONE;
        component.searchStartTick = 0L;
        component.searchTotalTicks = 0;
        component.searchPromptKey = "";
        component.sync();
    }

    public static void tick(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            var component = ModComponents.REPAIR_ROLES.get(player);
            if (!component.searchTarget.present()) {
                continue;
            }
            BlockPos pos = component.searchTarget.toBlockPos();
            if (!RepairModeState.canUseSurvivorUtility(player)
                    || !level.getBlockState(pos).is(ModBlocks.HOTBAR_STORAGE)
                    || player.distanceToSqr(pos.getCenter()) > 4.8D * 4.8D) {
                cancel(player);
                continue;
            }
            if (level.getGameTime() - component.searchStartTick < component.searchTotalTicks) {
                continue;
            }
            complete(level, player, pos);
        }
    }

    private static void complete(ServerLevel level, ServerPlayer player, BlockPos pos) {
        ItemStack reward = ItemStack.EMPTY;
        if (level.getBlockEntity(pos) instanceof HotbarStorageBlockEntity storage) {
            for (int i = 0; i < storage.getContainerSize(); i++) {
                ItemStack stack = storage.removeItem(i, 1);
                if (!stack.isEmpty()) {
                    reward = stack;
                    break;
                }
            }
        }
        if (!reward.isEmpty()) {

            player.displayClientMessage(Component.translatable("message.noellesroles.repair.search_found",
                    reward.getHoverName()), true);
            if ("collector".equals(ModComponents.REPAIR_ROLES.get(player).activeRole)) {
                RepairModeState.addNeutralTaskProgress(player, "collector", 1, RepairModeState.COLLECTOR_TASK_NEEDED);
            }
            player.addItem(reward);
        } else {
            RepairLootSpawner.Reward generated = RepairLootSpawner.take(level, pos);
            switch (generated.kind()) {
                case ITEM -> {
                    ItemStack stack = generated.stack();
                    if (stack.isEmpty()) {
                        player.displayClientMessage(Component.translatable("message.noellesroles.repair.search_empty"), true);
                    } else {
                        player.addItem(stack);
                        player.displayClientMessage(Component.translatable("message.noellesroles.repair.search_found",
                                stack.getHoverName()), true);
                        if ("collector".equals(ModComponents.REPAIR_ROLES.get(player).activeRole)) {
                            RepairModeState.addNeutralTaskProgress(player, "collector", 1, RepairModeState.COLLECTOR_TASK_NEEDED);
                        }
                    }
                }
                case COINS -> {
                    RepairModeState.awardCoins(player, generated.coins(), "repair_coin_source.search");
                    player.displayClientMessage(Component.translatable("message.noellesroles.repair.search_coins",
                            generated.coins()), true);
                }
                case EMPTY -> player.displayClientMessage(Component.translatable("message.noellesroles.repair.search_empty"), true);
            }
        }
        cancel(player);
    }
}
