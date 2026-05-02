package io.wifi.events.day_night_fight;

import io.wifi.events.day_night_fight.block.WashingMachineBlock;
import io.wifi.events.day_night_fight.block.DNFBlocks;
import io.wifi.events.day_night_fight.cca.DNFPlayerComponent;
import io.wifi.events.day_night_fight.cca.DNFWorldComponent;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.ChargeableItem;
import io.wifi.starrailexpress.api.ChargeableItemRegistry;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import io.wifi.starrailexpress.content.block.TrainDoorBlock;
import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.util.SREItemUtils;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import org.agmas.noellesroles.utils.RoleUtils;

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
    public static final Item TOXIC_HEART = register("dnf_toxic_heart",
            new Item(new Item.Properties().stacksTo(16)) {
                @Override
                public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                        TooltipFlag flag) {
                    appendDnfTooltip(stack, context, tooltip, flag,
                            "item.starrailexpress.dnf_toxic_heart.tooltip");
                }
            });
    public static final Item REDEMPTION_POTION = register("dnf_redemption_potion",
            new Item(new Item.Properties().stacksTo(2)) {
                @Override
                public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                        TooltipFlag flag) {
                    appendDnfTooltip(stack, context, tooltip, flag,
                            "item.starrailexpress.dnf_redemption_potion.tooltip");
                }
            });
    public static final Item OLD_CHEF_DIARY = register("dnf_old_chef_diary",
            new Item(new Item.Properties().stacksTo(1)) {
                @Override
                public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                        TooltipFlag flag) {
                    appendDnfTooltip(stack, context, tooltip, flag,
                            "item.starrailexpress.dnf_old_chef_diary.tooltip");
                }
            });
    public static final Item REDEMPTION_FORMULA = register("dnf_redemption_formula",
            new DNFRedemptionFormulaItem(new Item.Properties().stacksTo(1)));
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
    public static final Item CHEF_HAT = register("dnf_chef_hat",
            new DNFChefHatItem(new Item.Properties().stacksTo(1)));
    public static final Item REPAIR_TOOL = register("dnf_repair_tool",
            new DNFRepairToolItem(new Item.Properties().stacksTo(16)));
    public static final Item PAPER_SCRAP = register("dnf_paper_scrap",
            new DNFPaperScrapItem(new Item.Properties().stacksTo(16)));
    public static final Item SOAP = register("dnf_soap",
            new Item(new Item.Properties().stacksTo(1).durability(8)) {
                @Override
                public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                        TooltipFlag flag) {
                    appendDnfTooltip(stack, context, tooltip, flag, "message.dnf.soap.tooltip");
                }
            });
    public static final Item DNF_CLOCK = register("dnf_clock",
            new DNFClockItem(new Item.Properties().stacksTo(1)));
    public static final Item CLEANING_BYPRODUCT = register("dnf_cleaning_byproduct",
            new Item(new Item.Properties().stacksTo(64)) {
                @Override
                public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                        TooltipFlag flag) {
                    appendDnfTooltip(stack, context, tooltip, flag,
                            "item.starrailexpress.dnf_cleaning_byproduct.tooltip");
                }
            });
    public static final Item TASK_TOOL = register("dnf_task_tool",
            new DNFTaskToolItem(new Item.Properties().stacksTo(1)));
    public static final Item CLEANING_TASK_POINT_ITEM = register("dnf_cleaning_task_point",
            new BlockItem(DNFBlocks.CLEANING_TASK_POINT, new Item.Properties()));
    public static final Item WHITE_BLOCK = register("white_block",
            new BlockItem(DNFBlocks.WHITE_BLOCK, new Item.Properties()));
    public static final Item EXCHANGE_TASK_POINT_ITEM = register("dnf_exchange_task_point",
            new BlockItem(DNFBlocks.EXCHANGE_TASK_POINT, new Item.Properties()));
    public static final Block WASHING_MACHINE = registerBlock("dnf_washing_machine",
            new WashingMachineBlock(FabricBlockSettings.create()
                    .strength(2.0f)
                    .requiresTool()));
    public static final Item WASHING_MACHINE_ITEM = register("dnf_washing_machine",
            new BlockItem(WASHING_MACHINE, new Item.Properties()));

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
            entries.accept(TOXIC_HEART);
            entries.accept(REDEMPTION_POTION);
            entries.accept(OLD_CHEF_DIARY);
            entries.accept(REDEMPTION_FORMULA);
            entries.accept(BLOOD_BUY_FLYING_KNIFE);
            entries.accept(BLOOD_BUY_LOCKPICK);
            entries.accept(ABYSS_VIAL);
            entries.accept(ABYSS_TENTACLE);
            entries.accept(CHEF_HAT);
            entries.accept(REPAIR_TOOL);
            entries.accept(PAPER_SCRAP);
            entries.accept(SOAP);
            entries.accept(DNF_CLOCK);
            entries.accept(CLEANING_BYPRODUCT);
            entries.accept(TASK_TOOL);
            entries.accept(CLEANING_TASK_POINT_ITEM);
            entries.accept(EXCHANGE_TASK_POINT_ITEM);
            entries.accept(WASHING_MACHINE_ITEM);
        });
        ChargeableItemRegistry.register(TASK_TOOL, new ChargeableItem() {
            @Override
            public int getMaxChargeTime(ItemStack stack, Player player) {
                return DNF.CLEANING_TICKS;
            }

            @Override
            public float getChargePercentage(ItemStack stack, Player player, int ticksUsingItem) {
                return Math.min(1.0f, (float) ticksUsingItem / DNF.CLEANING_TICKS);
            }

            @Override
            public float getMaxStamina(ItemStack stack, Player player) {
                return DNF.CLEANING_TICKS / 20.0f;
            }
        });
    }

    private static Item register(String id, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, SRE.id(id), item);
    }

    private static Block registerBlock(String id, Block block) {
        return Registry.register(BuiltInRegistries.BLOCK, SRE.id(id), block);
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
        if (!SREItemUtils.hasItem(player, REPAIR_TOOL)) {
            if (!world.isClientSide) {
                player.displayClientMessage(Component.translatable("message.dnf.locksmith.need_tool")
                        .withStyle(ChatFormatting.YELLOW), true);
            }
            return InteractionResult.FAIL;
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
            if (!player.isCreative()) {
                SREItemUtils.clearItem(player, REPAIR_TOOL, 1);
            }
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
            if (DNF.inspectFoodBox(player)) {
                return InteractionResult.SUCCESS;
            }
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
        ItemStack output = new ItemStack(MEAT_RATION, DNF.CHEF_RECIPE_OUTPUT);
        if (!canFoodBoxAccept(player, output)) {
            return InteractionResult.FAIL;
        }
        if (!component.useChefCapacity(player, DNF.CHEF_RECIPE_OUTPUT)) {
            return InteractionResult.FAIL;
        }
        body.discard();
        if (!putFoodBoxOrFail(player, output)) {
            return InteractionResult.FAIL;
        }
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
        ItemStack outputStack = new ItemStack(output, DNF.CHEF_RECIPE_OUTPUT);
        if (!canFoodBoxAccept(player, outputStack)) {
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
        if (!putFoodBoxOrFail(player, outputStack)) {
            return InteractionResult.FAIL;
        }
        player.displayClientMessage(Component.translatable(messageKey, DNF.CHEF_RECIPE_OUTPUT)
                .withStyle(ChatFormatting.DARK_GREEN), false);
        return InteractionResult.SUCCESS;
    }

    public static boolean seedInitialFood(ServerPlayer player) {
        boolean ok = putFoodBoxOrFail(player, new ItemStack(CORN_GRUEL, DNF.INITIAL_CAFETERIA_FOOD));
        if (ok) {
            player.displayClientMessage(Component.translatable("message.dnf.chef.initial_food",
                    DNF.INITIAL_CAFETERIA_FOOD).withStyle(ChatFormatting.GREEN), false);
        }
        return ok;
    }

    public static ItemStack createWaterBottle(ServerPlayer player, int count) {
        ItemStack stack = new ItemStack(WATER_BOTTLE, count);
        DNFWorldComponent world = DNFWorldComponent.KEY.get(player.serverLevel());
        if (world.isWaterPoisonedToday() && world.getWaterPoisonerToday() != null) {
            stack.set(io.wifi.starrailexpress.index.SREDataComponentTypes.POISONER,
                    world.getWaterPoisonerToday().toString());
        }
        return stack;
    }

    public static boolean putFoodBoxOrFail(ServerPlayer player, ItemStack stack) {
        DNFWorldComponent world = DNFWorldComponent.KEY.get(player.serverLevel());
        if (world.getFoodBoxContainer() == null) {
            player.displayClientMessage(Component.translatable("message.dnf.food_box.missing")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        ItemStack toInsert = stack.copy();
        boolean ok = world.addToFoodBox(toInsert);
        if (!ok) {
            player.displayClientMessage(Component.translatable("message.dnf.food_box.full")
                    .withStyle(ChatFormatting.RED), true);
        }
        return ok;
    }

    private static boolean canFoodBoxAccept(ServerPlayer player, ItemStack stack) {
        DNFWorldComponent world = DNFWorldComponent.KEY.get(player.serverLevel());
        Container container = world.getFoodBoxContainer();
        if (container == null) {
            player.displayClientMessage(Component.translatable("message.dnf.food_box.missing")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!DNFWorldComponent.canFit(container, stack)) {
            player.displayClientMessage(Component.translatable("message.dnf.food_box.full")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        return true;
    }

    public static int countContaminated(Container container) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (isContaminated(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public static boolean isDnfFoodOrWater(ItemStack stack) {
        return stack.is(CORN_GRUEL) || stack.is(BLACK_BREAD) || stack.is(MEAT_RATION) || stack.is(WATER_BOTTLE);
    }

    public static boolean isDnfFood(ItemStack stack) {
        return stack.is(CORN_GRUEL) || stack.is(BLACK_BREAD) || stack.is(MEAT_RATION);
    }

    public static boolean isContaminated(ItemStack stack) {
        return isDnfFoodOrWater(stack)
                && stack.getOrDefault(io.wifi.starrailexpress.index.SREDataComponentTypes.POISONER, null) != null;
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

    public static void ensureDefaultClothes(ServerPlayer player) {
        equipOrGive(player, EquipmentSlot.CHEST, createDnfClothing(Items.LEATHER_CHESTPLATE));
        equipOrGive(player, EquipmentSlot.LEGS, createDnfClothing(Items.LEATHER_LEGGINGS));
        DNF.ensureHas(player, DNF_CLOCK.getDefaultInstance());
    }

    private static void equipOrGive(ServerPlayer player, EquipmentSlot slot, ItemStack stack) {
        if (player.getItemBySlot(slot).isEmpty()) {
            player.setItemSlot(slot, stack);
            return;
        }
        giveOrDrop(player, stack);
    }

    private static ItemStack createDnfClothing(Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
        return stack;
    }

    public static void settleClothesEndOfDay(ServerPlayer player) {
        dirtyClothes(player);
        float penalty = getClothingSanPenalty(player);
        if (penalty <= 0f) {
            return;
        }
        SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(player);
        mood.addMood(-penalty);
        player.displayClientMessage(Component.translatable(
                penalty >= DNF.SAN_NO_CLOTHES_PENALTY ? "message.dnf.clothes.penalty_missing"
                        : "message.dnf.clothes.penalty_dirty",
                (int) (penalty * 100)).withStyle(ChatFormatting.DARK_RED), false);
    }

    private static void dirtyClothes(ServerPlayer player) {
        damageClothingPiece(player.getItemBySlot(EquipmentSlot.CHEST), DNF.CLOTHES_DAILY_DIRT_DAMAGE);
        damageClothingPiece(player.getItemBySlot(EquipmentSlot.LEGS), DNF.CLOTHES_DAILY_DIRT_DAMAGE);
    }

    private static void damageClothingPiece(ItemStack stack, int damage) {
        if (!isDnfClothing(stack) || stack.getMaxDamage() <= 1) {
            return;
        }
        stack.setDamageValue(Math.min(stack.getMaxDamage() - 1, stack.getDamageValue() + damage));
    }

    public static boolean washClothes(ServerPlayer player) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        if (!isDnfClothing(chest) || !isDnfClothing(legs)) {
            player.displayClientMessage(Component.translatable("message.dnf.clothes.need_worn")
                    .withStyle(ChatFormatting.YELLOW), true);
            return false;
        }
        if (chest.getDamageValue() == 0 && legs.getDamageValue() == 0) {
            player.displayClientMessage(Component.translatable("message.dnf.clothes.already_clean")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        chest.setDamageValue(0);
        legs.setDamageValue(0);
        player.displayClientMessage(Component.translatable("message.dnf.clothes.washed")
                .withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    public static float getClothingSanPenalty(Player player) {
        if (!isWearingDnfClothes(player)) {
            return DNF.SAN_NO_CLOTHES_PENALTY;
        }
        return getClothingDirtiness(player) >= DNF.CLOTHES_DIRTY_THRESHOLD
                ? DNF.SAN_DIRTY_CLOTHES_PENALTY
                : 0f;
    }

    public static boolean isWearingDnfClothes(Player player) {
        return isDnfClothing(player.getItemBySlot(EquipmentSlot.CHEST))
                && isDnfClothing(player.getItemBySlot(EquipmentSlot.LEGS));
    }

    public static float getClothingDirtiness(Player player) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        if (!isDnfClothing(chest) || !isDnfClothing(legs)) {
            return 1f;
        }
        return Math.max(pieceDirtiness(chest), pieceDirtiness(legs));
    }

    private static float pieceDirtiness(ItemStack stack) {
        if (stack.getMaxDamage() <= 0) {
            return 0f;
        }
        return Math.clamp((float) stack.getDamageValue() / (float) stack.getMaxDamage(), 0f, 1f);
    }

    public static boolean isDnfClothing(ItemStack stack) {
        return stack.is(Items.LEATHER_CHESTPLATE) || stack.is(Items.LEATHER_LEGGINGS);
    }
}
