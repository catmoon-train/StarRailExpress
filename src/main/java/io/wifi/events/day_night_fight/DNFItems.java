package io.wifi.events.day_night_fight;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import io.wifi.starrailexpress.content.block.TrainDoorBlock;
import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.List;

public class DNFItems {
    public static final Item FLYING_KNIFE = register("dnf_flying_knife",
            new DNFFlyingKnifeItem(new Item.Properties().stacksTo(16)));
    public static final Item LOCKPICK = register("dnf_lockpick",
            new DNFLockpickItem(new Item.Properties().stacksTo(1).durability(3)));
    public static final Item CROWBAR = register("dnf_crowbar",
            new DNFCrowbarItem(new Item.Properties().stacksTo(1)));
    public static final Item CORNMEAL_BAG = register("dnf_cornmeal_bag",
            new Item(new Item.Properties().stacksTo(16)));
    public static final Item FLOUR_BAG = register("dnf_flour_bag",
            new Item(new Item.Properties().stacksTo(16)));
    public static final Item SUSPICIOUS_MEAT = register("dnf_suspicious_meat",
            new Item(new Item.Properties().stacksTo(16)));
    public static final Item CORN_GRUEL = register("dnf_corn_gruel",
            new DNFFoodItem(new Item.Properties().stacksTo(64).food(new FoodProperties.Builder()
                    .nutrition(3).saturationModifier(0.35f).alwaysEdible().build())));
    public static final Item BLACK_BREAD = register("dnf_black_bread",
            new DNFFoodItem(new Item.Properties().stacksTo(64).food(new FoodProperties.Builder()
                    .nutrition(5).saturationModifier(0.5f).alwaysEdible().build())));
    public static final Item MEAT_RATION = register("dnf_meat_ration",
            new DNFFoodItem(new Item.Properties().stacksTo(64).food(new FoodProperties.Builder()
                    .nutrition(6).saturationModifier(0.7f).alwaysEdible().build())));
    public static final Item WATER_BOTTLE = register("dnf_water_bottle",
            new DNFWaterItem(new Item.Properties().stacksTo(16)));
    public static final Item BLOOD_BUY_FLYING_KNIFE = register("dnf_blood_buy_flying_knife",
            new DNFBloodPurchaseItem(new Item.Properties().stacksTo(1), DNF.BLOOD_PRICE,
                    DNFItems.FLYING_KNIFE::getDefaultInstance, "item.starrailexpress.dnf_flying_knife"));
    public static final Item BLOOD_BUY_LOCKPICK = register("dnf_blood_buy_lockpick",
            new DNFBloodPurchaseItem(new Item.Properties().stacksTo(1), DNF.BLOOD_PRICE,
                    DNFItems.LOCKPICK::getDefaultInstance, "item.starrailexpress.dnf_lockpick"));
    public static final Item ABYSS_VIAL = register("dnf_abyss_vial",
            new DNFKillerPsychoItem(new Item.Properties().stacksTo(1)));
    public static final Item ABYSS_TENTACLE = register("dnf_abyss_tentacle",
            new DNFTentacleItem(new Item.Properties().stacksTo(1)));

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(TMMItems.EQUIPMENT_GROUP).register(entries -> {
            entries.accept(FLYING_KNIFE);
            entries.accept(LOCKPICK);
            entries.accept(CROWBAR);
            entries.accept(CORNMEAL_BAG);
            entries.accept(FLOUR_BAG);
            entries.accept(SUSPICIOUS_MEAT);
            entries.accept(CORN_GRUEL);
            entries.accept(BLACK_BREAD);
            entries.accept(MEAT_RATION);
            entries.accept(WATER_BOTTLE);
            entries.accept(BLOOD_BUY_FLYING_KNIFE);
            entries.accept(BLOOD_BUY_LOCKPICK);
            entries.accept(ABYSS_VIAL);
            entries.accept(ABYSS_TENTACLE);
        });
    }

    private static Item register(String id, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, SRE.id(id), item);
    }

    public static InteractionResult tryOpenWithLockpick(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        Level world = context.getLevel();
        BlockPos lowerPos = lowerDoorPos(world, context.getClickedPos());
        if (lowerPos == null || !(world.getBlockEntity(lowerPos) instanceof SmallDoorBlockEntity door)) {
            return InteractionResult.PASS;
        }
        if (door.isBlasted()) {
            return InteractionResult.FAIL;
        }
        if (!world.isClientSide) {
            if (!door.isOpen()) {
                SmallDoorBlock.toggleDoor(world.getBlockState(lowerPos), world, door, lowerPos);
            }
            door.setJammed(Integer.MAX_VALUE / 4);
            world.playSound(null, lowerPos.getX() + .5f, lowerPos.getY() + 1, lowerPos.getZ() + .5f,
                    TMMSounds.ITEM_LOCKPICK_DOOR, SoundSource.BLOCKS, 1f, .75f);
            if (!player.isCreative()) {
                ItemStack stack = player.getItemInHand(context.getHand());
                stack.hurtAndBreak(1, player, player.getEquipmentSlotForItem(stack));
            }
            player.displayClientMessage(Component.translatable("message.dnf.lockpick.opened")
                    .withStyle(ChatFormatting.YELLOW), true);
        }
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult tryRepairLockpickedDoor(Player player, Level world, BlockPos clickedPos) {
        if (!DNF.isDNFLocksmith(player)) {
            return InteractionResult.PASS;
        }
        BlockPos lowerPos = lowerDoorPos(world, clickedPos);
        if (lowerPos == null || !(world.getBlockEntity(lowerPos) instanceof SmallDoorBlockEntity door)) {
            return InteractionResult.PASS;
        }
        if (door.isBlasted()) {
            if (!world.isClientSide) {
                player.displayClientMessage(Component.translatable("message.dnf.locksmith.blasted")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        if (!door.isJammed()) {
            return InteractionResult.PASS;
        }
        if (!world.isClientSide) {
            door.setJammed(0);
            world.playSound(null, lowerPos, TMMSounds.ITEM_KEY_DOOR, SoundSource.BLOCKS, 1f, 1.2f);
            player.displayClientMessage(Component.translatable("message.dnf.locksmith.repaired")
                    .withStyle(ChatFormatting.GREEN), true);
        }
        return InteractionResult.SUCCESS;
    }

    static BlockPos lowerDoorPos(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof SmallDoorBlock) && !(state.getBlock() instanceof TrainDoorBlock)) {
            return null;
        }
        return state.getValue(SmallDoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }

    static void appendDnfTooltip(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag,
            String key) {
        tooltip.add(Component.translatable(key).withStyle(ChatFormatting.DARK_RED));
        if (stack.has(DataComponents.MAX_DAMAGE)) {
            tooltip.add(Component.translatable("message.dnf.item.durability",
                    stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage()).withStyle(ChatFormatting.GRAY));
        }
    }

    public static InteractionResult tryChefWork(ServerPlayer player, boolean checkWater) {
        if (!DNF.isDNFChef(player)) {
            return InteractionResult.PASS;
        }
        if (checkWater) {
            return DNFPlayerComponent.KEY.get(player).checkChefWater(player)
                    ? InteractionResult.SUCCESS
                    : InteractionResult.FAIL;
        }

        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.is(CORNMEAL_BAG)) {
            return craftChefFood(player, CORNMEAL_BAG, WATER_BOTTLE, CORN_GRUEL,
                    "message.dnf.chef.craft_corn_gruel");
        }
        if (mainHand.is(FLOUR_BAG)) {
            return craftChefFood(player, FLOUR_BAG, WATER_BOTTLE, BLACK_BREAD,
                    "message.dnf.chef.craft_black_bread");
        }
        if (mainHand.is(SUSPICIOUS_MEAT)) {
            return craftChefFood(player, SUSPICIOUS_MEAT, null, MEAT_RATION,
                    "message.dnf.chef.craft_meat");
        }

        player.displayClientMessage(Component.translatable("message.dnf.chef.need_ingredient")
                .withStyle(ChatFormatting.YELLOW), true);
        return InteractionResult.FAIL;
    }

    public static InteractionResult cookBodyAsChef(ServerPlayer player, net.minecraft.world.entity.Entity body) {
        if (!DNF.isDNFChef(player)) {
            return InteractionResult.PASS;
        }
        DNFPlayerComponent component = DNFPlayerComponent.KEY.get(player);
        if (!component.useChefCapacity(player, DNF.CHEF_RECIPE_OUTPUT)) {
            return InteractionResult.FAIL;
        }
        body.discard();
        giveOrDrop(player, new ItemStack(MEAT_RATION, DNF.CHEF_RECIPE_OUTPUT));
        player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_EAT,
                SoundSource.PLAYERS, 0.8f, 0.7f);
        player.displayClientMessage(Component.translatable("message.dnf.chef.craft_meat_from_body",
                DNF.CHEF_RECIPE_OUTPUT).withStyle(ChatFormatting.DARK_GREEN), false);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult craftChefFood(ServerPlayer player, Item ingredient, Item liquid, Item output,
            String messageKey) {
        if (!hasItem(player, ingredient, 1) || (liquid != null && !hasItem(player, liquid, 1))) {
            player.displayClientMessage(Component.translatable("message.dnf.chef.missing_recipe")
                    .withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.FAIL;
        }
        DNFPlayerComponent component = DNFPlayerComponent.KEY.get(player);
        if (!component.useChefCapacity(player, DNF.CHEF_RECIPE_OUTPUT)) {
            return InteractionResult.FAIL;
        }
        consumeItem(player, ingredient, 1);
        if (liquid != null) {
            consumeItem(player, liquid, 1);
            giveOrDrop(player, new ItemStack(Items.GLASS_BOTTLE));
        }
        giveOrDrop(player, new ItemStack(output, DNF.CHEF_RECIPE_OUTPUT));
        player.displayClientMessage(Component.translatable(messageKey, DNF.CHEF_RECIPE_OUTPUT)
                .withStyle(ChatFormatting.DARK_GREEN), false);
        return InteractionResult.SUCCESS;
    }

    private static boolean hasItem(ServerPlayer player, Item item, int count) {
        int found = 0;
        for (List<ItemStack> compartment : player.getInventory().compartments) {
            for (ItemStack stack : compartment) {
                if (stack.is(item)) {
                    found += stack.getCount();
                    if (found >= count) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void consumeItem(ServerPlayer player, Item item, int count) {
        int remaining = count;
        for (List<ItemStack> compartment : player.getInventory().compartments) {
            for (ItemStack stack : compartment) {
                if (!stack.is(item)) {
                    continue;
                }
                int taken = Math.min(remaining, stack.getCount());
                stack.shrink(taken);
                remaining -= taken;
                if (remaining <= 0) {
                    return;
                }
            }
        }
    }

    static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.addItem(stack.copy())) {
            player.drop(stack.copy(), false);
        }
    }
}
