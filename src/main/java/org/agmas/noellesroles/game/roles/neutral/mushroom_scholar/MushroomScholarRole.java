package org.agmas.noellesroles.game.roles.neutral.mushroom_scholar;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.neutral.MushroomScholarRoleData;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class MushroomScholarRole extends NormalRole {
    private static final double BED_DISTANCE = 3.0;

    public MushroomScholarRole(ResourceLocation identifier, int color, boolean isInnocent,
            boolean canUseKiller, MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        List<ItemStack> items = new ArrayList<>(super.getDefaultItems());
        items.add(ModItems.MUSHROOM_SAMPLE.getDefaultInstance());
        return items;
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        return List.of(new ShopEntry(ModItems.MUSHROOM_SAMPLE.getDefaultInstance(), 150, ShopEntry.Type.TOOL));
    }

    @Override
    public InteractionResult onDropItem(Player player, ItemStack item) {
        if (item.is(ModItems.SAFE_MUSHROOM) || item.is(ModItems.POISONOUS_MUSHROOM)) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    public static boolean tryOpenCultivation(ServerPlayer player) {
        if (!isScholar(player) || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        MushroomScholarRoleData data = getData(player);
        if (data != null && data.cultivationCooldownTicks > 0) {
            player.displayClientMessage(Component.translatable("hud.noellesroles.mushroom_scholar.cooldown",
                    (data.cultivationCooldownTicks + 19) / 20), true);
            return false;
        }
        if (!canCultivate(player)) {
            player.displayClientMessage(Component.translatable("skill.noellesroles.mushroom_scholar.need_bed"), true);
            return false;
        }
        if (!player.getMainHandItem().is(ModItems.MUSHROOM_SAMPLE)) {
            player.displayClientMessage(Component.translatable("skill.noellesroles.mushroom_scholar.need_sample"), true);
            return false;
        }
        org.agmas.noellesroles.utils.OpenScreenManager.openScreen(player,
                org.agmas.noellesroles.utils.OpenScreenManager.MUSHROOM_CULTIVATION_SCREEN);
        return true;
    }

    public static boolean convertHeldMushroom(ServerPlayer player) {
        if (!isScholar(player) || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        MushroomScholarRoleData data = getData(player);
        if (data != null && data.essenceCooldownTicks > 0) {
            player.displayClientMessage(Component.translatable("hud.noellesroles.mushroom_scholar.cooldown",
                    (data.essenceCooldownTicks + 19) / 20), true);
            return false;
        }
        ItemStack held = player.getMainHandItem();
        boolean poisonous;
        if (held.is(ModItems.SAFE_MUSHROOM)) {
            poisonous = false;
        } else if (held.is(ModItems.POISONOUS_MUSHROOM)) {
            poisonous = true;
        } else {
            player.displayClientMessage(Component.translatable("skill.noellesroles.mushroom_scholar.need_mushroom"), true);
            return false;
        }
        held.shrink(1);
        RoleUtils.insertOrDropItem(player, (poisonous ? ModItems.POISONOUS_MUSHROOM_ESSENCE
                : ModItems.MUSHROOM_ESSENCE).getDefaultInstance());
        if (data != null) {
            data.essenceCooldownTicks = MushroomScholarRoleData.ESSENCE_COOLDOWN_TICKS;
            data.sync();
        }
        return true;
    }

    public static void handleCultivationResult(ServerPlayer player, int[] pours) {
        if (!isScholar(player) || !GameUtils.isPlayerAliveAndSurvival(player)
                || pours == null || pours.length != 3 || !canCultivate(player)) {
            return;
        }
        if (!player.getMainHandItem().is(ModItems.MUSHROOM_SAMPLE)) {
            return;
        }
        for (int pour : pours) {
            if (pour != 0 && pour != 1) {
                return;
            }
        }
        int left = 0;
        for (int pour : pours) {
            if (pour == 0) left++;
        }
        int right = pours.length - left;
        boolean poisonous = right > left || (right == left && pours[2] == 1);
        player.getMainHandItem().shrink(1);
        RoleUtils.insertOrDropItem(player, (poisonous ? ModItems.POISONOUS_MUSHROOM
                : ModItems.SAFE_MUSHROOM).getDefaultInstance());
        MushroomScholarRoleData data = getData(player);
        if (data != null) {
            data.cultivationCooldownTicks = MushroomScholarRoleData.CULTIVATION_COOLDOWN_TICKS;
            data.sync();
        }
        player.displayClientMessage(Component.translatable("screen.noellesroles.mushroom.finished"), true);
    }

    public static boolean canCultivate(Player player) {
        if (!sleepTaskEnabled(player.level())) return false;
        BlockPos origin = player.blockPosition();
        int radius = (int) BED_DISTANCE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius),
                origin.offset(radius, radius, radius))) {
            if (player.level().getBlockState(pos).is(BlockTags.BEDS)
                    && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                    <= BED_DISTANCE * BED_DISTANCE) {
                return true;
            }
        }
        return false;
    }

    private static boolean sleepTaskEnabled(Level level) {
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        return areas.areasSettings == null || areas.areasSettings.disabledTasks == null
                || !areas.areasSettings.disabledTasks.contains("sleep");
    }

    private static boolean isScholar(ServerPlayer player) {
        return SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.MUSHROOM_SCHOLAR);
    }

    private static MushroomScholarRoleData getData(ServerPlayer player) {
        return io.wifi.starrailexpress.api.data.RoleData.getNullable(MushroomScholarRoleData.class, player);
    }
}
