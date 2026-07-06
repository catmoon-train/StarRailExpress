package org.agmas.noellesroles.scene;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.item.CocktailItem;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.data.MapStatusBarType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.block.scene.StoveBlock;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.init.ModSceneBlocks;
import org.agmas.noellesroles.packet.MapStatusBarSyncS2CPacket;
import pro.fazeclan.river.stupid_express.constants.SEItems;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class MapStatusBarRuntime {
    public static final int MAX_VALUE = 20;
    private static final TagKey<Item> NOELLES_DRINKS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("noellesroles", "food_drink"));
    private static final Map<UUID, State> STATES = new HashMap<>();

    private MapStatusBarRuntime() {
    }

    public static void tick(ServerLevel level) {
        MapStatusBarType type = currentStatusBar(level);
        if (!isGameRunning(level) || type == MapStatusBarType.NONE) {
            clear(level);
            return;
        }

        for (ServerPlayer player : level.players()) {
            if (!shouldTrack(player)) {
                remove(player);
                continue;
            }
            State state = STATES.computeIfAbsent(player.getUUID(), id -> new State(type));
            if (state.type != type) {
                state.reset(type);
            }
            tickPlayer(level, player, state);
            if (level.getGameTime() % 20 == 0) {
                state.sync(player);
            }
        }

        Iterator<UUID> iterator = STATES.keySet().iterator();
        while (iterator.hasNext()) {
            UUID id = iterator.next();
            if (level.getServer().getPlayerList().getPlayer(id) == null) {
                iterator.remove();
            }
        }
    }

    public static void clear(ServerLevel level) {
        if (STATES.isEmpty()) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (STATES.remove(player.getUUID()) != null) {
                ServerPlayNetworking.send(player,
                        new MapStatusBarSyncS2CPacket(MapStatusBarType.NONE, MAX_VALUE, MAX_VALUE));
            }
        }
    }

    public static void addWarmth(ServerPlayer player, int delta) {
        add(player, MapStatusBarType.WARMTH, delta);
    }

    public static void addThirst(ServerPlayer player, int delta) {
        add(player, MapStatusBarType.THIRST, delta);
    }

    public static void addHunger(ServerPlayer player, int delta) {
        add(player, MapStatusBarType.HUNGER, delta);
    }

    public static void onFinishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (level.isClientSide || !(user instanceof ServerPlayer player)) {
            return;
        }
        if (isDrink(stack)) {
            int amount = stack.is(ModItems.A_BOTTLE_OF_WATER) ? 4 : 2;
            add(player, MapStatusBarType.THIRST, amount);
        }
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food != null) {
            add(player, MapStatusBarType.HUNGER, Math.max(1, food.nutrition()));
        }
    }

    private static void add(ServerPlayer player, MapStatusBarType type, int delta) {
        ServerLevel level = (ServerLevel) player.level();
        if (!isGameRunning(level) || currentStatusBar(level) != type || !shouldTrack(player)) {
            return;
        }
        State state = STATES.computeIfAbsent(player.getUUID(), id -> new State(type));
        if (state.type != type) {
            state.reset(type);
        }
        state.change(delta);
        if (state.value <= 0) {
            killByStatus(player, type);
            STATES.remove(player.getUUID());
            return;
        }
        state.sync(player);
    }

    private static void tickPlayer(ServerLevel level, ServerPlayer player, State state) {
        switch (state.type) {
            case WARMTH -> tickWarmth(level, player, state);
            case THIRST -> tickThirst(player, state);
            case HUNGER -> tickHunger(player, state);
            default -> {
            }
        }
        if (state.value <= 0) {
            killByStatus(player, state.type);
            STATES.remove(player.getUUID());
        }
    }

    private static void tickWarmth(ServerLevel level, ServerPlayer player, State state) {
        if (nearLitStove(level, player.blockPosition())) {
            state.set(MAX_VALUE);
            state.clearTimers();
            return;
        }

        int leatherPieces = leatherArmorPieces(player);
        boolean protectedFromCold = leatherPieces >= 3;
        int coldInterval = 15 * 20 * Math.max(1, leatherPieces + 1);
        boolean canSeeSky = level.canSeeSky(player.blockPosition());
        boolean inPowderSnow = level.getBlockState(player.blockPosition()).is(Blocks.POWDER_SNOW);

        if (canSeeSky && !protectedFromCold) {
            if (++state.skyTicks >= coldInterval) {
                state.skyTicks = 0;
                state.change(-1);
            }
            state.indoorTicks = 0;
        } else if (!canSeeSky) {
            state.skyTicks = 0;
            if (++state.indoorTicks >= 10 * 20) {
                state.indoorTicks = 0;
                state.change(1);
            }
        }

        if (inPowderSnow && leatherPieces == 0) {
            if (++state.powderSnowTicks >= 2 * 20) {
                state.powderSnowTicks = 0;
                state.change(-1);
            }
        } else {
            state.powderSnowTicks = 0;
        }

        if (isColdGround(level.getBlockState(player.blockPosition().below())) && !protectedFromCold) {
            if (++state.blockTicks >= coldInterval) {
                state.blockTicks = 0;
                state.change(-1);
            }
        } else {
            state.blockTicks = 0;
        }
    }

    private static void tickThirst(ServerPlayer player, State state) {
        if (player.level().canSeeSky(player.blockPosition())) {
            if (++state.skyTicks >= 15 * 20) {
                state.skyTicks = 0;
                state.change(-1);
            }
        } else {
            state.skyTicks = 0;
        }
        if (++state.passiveTicks >= 90 * 20) {
            state.passiveTicks = 0;
            state.change(-2);
        }
        if (isSpendingSprint(player)) {
            if (++state.sprintTicks >= 20 * 20) {
                state.sprintTicks = 0;
                state.change(-1);
            }
        } else {
            state.sprintTicks = 0;
        }
        if (player.isInWater() || player.isUnderWater()) {
            if (++state.waterTicks >= 20 * 20) {
                state.waterTicks = 0;
                state.change(1);
            }
        } else {
            state.waterTicks = 0;
        }
    }

    private static void tickHunger(ServerPlayer player, State state) {
        if (++state.passiveTicks >= 90 * 20) {
            state.passiveTicks = 0;
            state.change(-2);
        }
        if (isSpendingSprint(player)) {
            if (++state.sprintTicks >= 20 * 20) {
                state.sprintTicks = 0;
                state.change(-2);
            }
        } else {
            state.sprintTicks = 0;
        }
    }

    private static boolean shouldTrack(ServerPlayer player) {
        return GameUtils.isPlayerAliveAndSurvival(player)
                && player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE;
    }

    private static void remove(ServerPlayer player) {
        if (STATES.remove(player.getUUID()) != null) {
            ServerPlayNetworking.send(player,
                    new MapStatusBarSyncS2CPacket(MapStatusBarType.NONE, MAX_VALUE, MAX_VALUE));
        }
    }

    private static MapStatusBarType currentStatusBar(ServerLevel level) {
        MapStatusBarType type = AreasWorldComponent.KEY.get(level).areasSettings.mapStatusBar;
        return type == null ? MapStatusBarType.NONE : type;
    }

    private static boolean isGameRunning(ServerLevel level) {
        return SREGameWorldComponent.KEY.get(level).isRunning();
    }

    private static boolean isColdGround(BlockState state) {
        return state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)
                || state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK);
    }

    private static int leatherArmorPieces(ServerPlayer player) {
        int count = 0;
        if (player.getItemBySlot(EquipmentSlot.HEAD).is(Items.LEATHER_HELMET))
            count++;
        if (player.getItemBySlot(EquipmentSlot.CHEST).is(Items.LEATHER_CHESTPLATE))
            count++;
        if (player.getItemBySlot(EquipmentSlot.LEGS).is(Items.LEATHER_LEGGINGS))
            count++;
        if (player.getItemBySlot(EquipmentSlot.FEET).is(Items.LEATHER_BOOTS))
            count++;
        return count;
    }

    private static boolean nearLitStove(ServerLevel level, BlockPos center) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -3; x <= 3; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -3; z <= 3; z++) {
                    pos.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(ModSceneBlocks.STOVE) && state.hasProperty(StoveBlock.LIT)
                            && state.getValue(StoveBlock.LIT)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isSpendingSprint(ServerPlayer player) {
        Vec3 movement = player.getDeltaMovement();
        return player.isSprinting() && movement.x * movement.x + movement.z * movement.z > 0.01D;
    }

    private static boolean isDrink(ItemStack stack) {
        return stack.getItem() instanceof CocktailItem
                || stack.getItem() instanceof PotionItem
                || stack.getItem() instanceof HoneyBottleItem
                || stack.is(NOELLES_DRINKS)
                || stack.is(SEItems.DRINKS);
    }

    private static void killByStatus(ServerPlayer player, MapStatusBarType type) {
        switch (type) {
            case WARMTH -> GameUtils.forceKillPlayer(player, true, null, GameConstants.DeathReasons.FROZEN);
            case THIRST -> GameUtils.forceKillPlayer(player, true, null, GameConstants.DeathReasons.THIRST);
            case HUNGER -> GameUtils.forceKillPlayer(player, true, null, GameConstants.DeathReasons.STARVED);
            default -> {
            }
        }
    }

    private static final class State {
        private MapStatusBarType type;
        private int value = MAX_VALUE;
        private int lastSyncedValue = -1;
        private MapStatusBarType lastSyncedType = null;
        private int skyTicks;
        private int blockTicks;
        private int indoorTicks;
        private int powderSnowTicks;
        private int passiveTicks;
        private int sprintTicks;
        private int waterTicks;

        private State(MapStatusBarType type) {
            this.type = type;
        }

        private void reset(MapStatusBarType type) {
            this.type = type;
            this.value = MAX_VALUE;
            this.lastSyncedValue = -1;
            this.lastSyncedType = null;
            clearTimers();
        }

        private void clearTimers() {
            skyTicks = 0;
            blockTicks = 0;
            indoorTicks = 0;
            powderSnowTicks = 0;
            passiveTicks = 0;
            sprintTicks = 0;
            waterTicks = 0;
        }

        private void change(int delta) {
            set(value + delta);
        }

        private void set(int value) {
            this.value = Math.max(0, Math.min(MAX_VALUE, value));
        }

        private void sync(ServerPlayer player) {
            if (lastSyncedType == type && lastSyncedValue == value) {
                return;
            }
            lastSyncedType = type;
            lastSyncedValue = value;
            ServerPlayNetworking.send(player, new MapStatusBarSyncS2CPacket(type, value, MAX_VALUE));
        }
    }
}
