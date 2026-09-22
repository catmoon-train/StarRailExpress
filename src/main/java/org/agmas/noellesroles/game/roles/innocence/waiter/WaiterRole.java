package org.agmas.noellesroles.game.roles.innocence.waiter;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.block_entity.PlateTrayBlockEntity;
import io.wifi.starrailexpress.content.item.CocktailItem;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.HoneyBottleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MilkBucketItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 传菜员的托盘识毒、取餐和喂食逻辑。 */
public class WaiterRole extends NormalRole {
    private static final int TRAY_TAKE_LIMIT = 3;

    public WaiterRole(ResourceLocation identifier, int color, boolean isInnocent,
            boolean canUseKiller, MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        return List.of(
                new ShopEntry(ModItems.TRAY_PURIFYING_REAGENT.getDefaultInstance(), 75, ShopEntry.Type.TOOL),
                new ShopEntry(TMMItems.WEAK_DEFENSE_VIAL.getDefaultInstance(), 200, ShopEntry.Type.TOOL),
                new ShopEntry(TMMItems.POISON_VIAL.getDefaultInstance(), 250, ShopEntry.Type.POISON));
    }

    public static boolean isWaiter(Player player) {
        if (player == null) {
            return false;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        return game != null && game.isRole(player, ModRoles.WAITER);
    }

    /** Handles right-click feeding while preserving the normal item finish path. */
    public static boolean tryFeed(Player feeder, Level level, InteractionHand hand, Entity entity) {
        if (!isWaiter(feeder) || !(entity instanceof ServerPlayer target)
                || hand == null || !isFoodOrDrink(feeder.getItemInHand(hand))
                || target == feeder || !target.isAlive() || target.isSpectator()) {
            return false;
        }
        if (level.isClientSide) {
            return true;
        }

        ItemStack held = feeder.getItemInHand(hand);
        ItemStack offered = held.copyWithCount(1);
        ItemStack remainder = offered.finishUsingItem(level, target);
        held.shrink(1);
        if (!remainder.isEmpty() && remainder.getItem() != held.getItem()) {
            RoleUtils.insertOrDropItem(feeder, remainder.copy());
        }
        feeder.swing(hand, true);
        return true;
    }

    public static boolean isFoodOrDrink(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        return stack.has(DataComponents.FOOD)
                || item instanceof PotionItem
                || item instanceof MilkBucketItem
                || item instanceof HoneyBottleItem
                || item instanceof CocktailItem
                || stack.getUseAnimation() == UseAnim.DRINK;
    }

    /** Handles the new reagent before PlatterBlock's normal item interaction. */
    public static boolean tryPurifyTray(Player player, Level level, InteractionHand hand, BlockPos pos) {
        ItemStack reagent = player.getItemInHand(hand);
        if (!isWaiter(player) || !reagent.is(ModItems.TRAY_PURIFYING_REAGENT)) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof PlateTrayBlockEntity tray)
                || (tray.getPoisoner() == null && !tray.isPoisonFake)) {
            return false;
        }
        if (level.isClientSide) {
            return true;
        }
        // Clear both real and fake poison together; armor and weak armor metadata is untouched.
        tray.isPoisonFake = false;
        tray.setPoisoner(null);
        reagent.shrink(1);
        player.playNotifySound(SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.5f, 1f);
        return true;
    }

    /** Implements the waiter-specific three-item tray capacity without changing upstream source. */
    public static boolean tryTakeFromTray(Player player, Level level, InteractionHand hand, BlockPos pos) {
        if (!isWaiter(player) || hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof PlateTrayBlockEntity tray)) {
            return false;
        }
        if (level.isClientSide) {
            return false;
        }
        List<ItemStack> platter = tray.getStoredItems();
        if (platter.isEmpty()) {
            return false;
        }

        Set<Item> platterTypes = new HashSet<>();
        for (ItemStack platterItem : platter) {
            platterTypes.add(platterItem.getItem());
        }
        int heldFromPlatter = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack inventoryStack = player.getInventory().getItem(i);
            if (inventoryStack.getItem() instanceof BundleItem) {
                BundleContents contents = inventoryStack.get(DataComponents.BUNDLE_CONTENTS);
                if (contents != null) {
                    for (ItemStack bundled : contents.items()) {
                        if (platterTypes.contains(bundled.getItem())) {
                            heldFromPlatter += bundled.getCount();
                        }
                    }
                }
            }
            if (platterTypes.contains(inventoryStack.getItem())) {
                heldFromPlatter += inventoryStack.getCount();
            }
        }
        if (heldFromPlatter >= TRAY_TAKE_LIMIT) {
            return true;
        }

        ItemStack randomItem = platter.get(level.random.nextInt(platter.size())).copy();
        randomItem.setCount(1);
        randomItem.set(DataComponents.MAX_STACK_SIZE, 1);
        String poisoner = tray.getPoisoner();
        String armorer = tray.getArmorer();
        String weakArmorer = tray.getWeakArmorer();
        if (poisoner != null) {
            randomItem.set(SREDataComponentTypes.POISONER, poisoner);
            if (tray.isPoisonFake) {
                randomItem.set(SREDataComponentTypes.FAKE_POISON, true);
                tray.isPoisonFake = false;
            }
            tray.setPoisoner(null);
        }
        if (armorer != null) {
            randomItem.set(SREDataComponentTypes.ARMORER, armorer);
            tray.setArmorer(null);
        }
        if (weakArmorer != null) {
            randomItem.set(SREDataComponentTypes.WEAK_ARMORER, weakArmorer);
            tray.setWeakArmorer(null);
        }
        randomItem.set(SREDataComponentTypes.TRAY_ITEM, true);
        player.playNotifySound(SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 1f);
        player.setItemInHand(InteractionHand.MAIN_HAND, randomItem);
        return true;
    }
}
