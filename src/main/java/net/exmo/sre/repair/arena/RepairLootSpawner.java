package net.exmo.sre.repair.arena;

import net.exmo.sre.repair.*;
import net.exmo.sre.repair.role.*;
import net.exmo.sre.repair.state.*;
import net.exmo.sre.repair.event.*;
import net.exmo.sre.repair.util.*;

import io.wifi.starrailexpress.game.data.MapConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;

import java.util.*;

public final class RepairLootSpawner {
    private static final Map<ServerLevel, Map<BlockPos, Reward>> LOOT = new WeakHashMap<>();
    private static final Map<ServerLevel, Set<BlockPos>> SEARCHED = new WeakHashMap<>();

    private RepairLootSpawner() {
    }

    public static void prepare(ServerLevel level, MapConfig.RepairConfig config) {
        Map<BlockPos, Reward> entries = new HashMap<>();
        if (config != null && config.lootPoints != null) {
            List<MapConfig.LootPointEntry> points = new ArrayList<>(config.lootPoints);
            Collections.shuffle(points, new Random(level.getSeed() ^ level.getGameTime()));
            for (MapConfig.LootPointEntry point : points) {
                if (point == null || point.pos == null) {
                    continue;
                }
                if (!point.guaranteed && level.random.nextDouble() > point.chance) {
                    continue;
                }
                entries.put(point.pos.toBlockPos(), chooseReward(level, point));
            }
        } else {
            prepareDefaultMansionLoot(level, entries);
        }
        LOOT.put(level, entries);
        SEARCHED.put(level, new HashSet<>());
    }

    private static void prepareDefaultMansionLoot(ServerLevel level, Map<BlockPos, Reward> entries) {
        BlockPos base = RepairArenaBuilder.defaultMansionBase(level);
        List<ItemStack> required = new ArrayList<>(List.of(
                new ItemStack(ModItems.REPAIR_CROWBAR),
                new ItemStack(ModItems.REPAIR_FUSE),
                new ItemStack(ModItems.REPAIR_GEAR_HANDLE),
                new ItemStack(ModItems.REPAIR_BATTERY),
                new ItemStack(ModItems.REPAIR_VALVE_HANDLE),
                new ItemStack(ModItems.REPAIR_BOLT_CUTTER),
                new ItemStack(ModItems.REPAIR_MEDKIT),
                new ItemStack(ModItems.RESCUE_FLARE),
                new ItemStack(ModItems.SPARE_PARTS)));
        Collections.shuffle(required, new Random(level.getSeed() ^ level.getGameTime() ^ 0x5EEDL));
        List<int[]> offsets = new ArrayList<>(RepairArenaBuilder.defaultLootOffsets());
        Collections.shuffle(offsets, new Random(level.getSeed() ^ 0x51A7EAL));
        int index = 0;
        for (ItemStack stack : required) {
            if (index >= offsets.size()) {
                break;
            }
            int[] offset = offsets.get(index++);
            entries.put(base.offset(offset[0], 1, offset[1]), Reward.item(stack));
        }
        while (index < offsets.size()) {
            int[] offset = offsets.get(index++);
            entries.put(base.offset(offset[0], 1, offset[1]), randomDefaultReward(level));
        }
        entries.put(base.offset(22, 7, 50), Reward.item(new ItemStack(random(level, ModItems.REPAIR_LOCKPICK,
                ModItems.REPAIR_TOOLBOX, ModItems.REPAIR_MEDKIT))));
    }

    public static Reward take(ServerLevel level, BlockPos pos) {
        Set<BlockPos> searched = SEARCHED.computeIfAbsent(level, ignored -> new HashSet<>());
        if (!searched.add(pos.immutable())) {
            return Reward.empty();
        }
        Reward reward = LOOT.getOrDefault(level, Map.of()).remove(pos);
        if (reward == null) {
            return randomDefaultReward(level);
        }
        return reward.copy();
    }

    public static boolean hasLoot(ServerLevel level, BlockPos pos) {
        return LOOT.getOrDefault(level, Map.of()).containsKey(pos);
    }

    public static void reset(ServerLevel level) {
        LOOT.remove(level);
        SEARCHED.remove(level);
    }

    private static Reward chooseReward(ServerLevel level, MapConfig.LootPointEntry point) {
        if (point.pool != null && !point.pool.isEmpty()) {
            String id = point.pool.get(level.random.nextInt(point.pool.size()));
            if (id != null && id.startsWith("coins:")) {
                return Reward.coins(parseCoins(id.substring("coins:".length()), 8));
            }
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id.contains(":") ? id : "noellesroles:" + id));
            if (item != net.minecraft.world.item.Items.AIR) {
                return Reward.item(new ItemStack(item));
            }
        }
        return fallback(level, point.category);
    }

    private static Reward randomDefaultReward(ServerLevel level) {
        int roll = level.random.nextInt(100);
        if (roll < 18) {
            return Reward.coins(8 + level.random.nextInt(18));
        }
        if (roll < 24) {
            return Reward.empty();
        }
        if (roll < 40) {
            return Reward.item(new ItemStack(ModItems.REPAIR_MEDKIT));
        }
        if (roll < 56) {
            return Reward.item(new ItemStack(random(level, ModItems.RESCUE_FLARE, ModItems.SMOKE_PELLET)));
        }
        if (roll < 78) {
            return Reward.item(new ItemStack(random(level, ModItems.SPARE_PARTS, ModItems.REPAIR_TOOLBOX,
                    ModItems.REPAIR_LOCKPICK)));
        }
        return Reward.item(new ItemStack(random(level, ModItems.REPAIR_AREA_KEY, ModItems.REPAIR_OLD_KEY,
                ModItems.REPAIR_CROWBAR, ModItems.REPAIR_BOLT_CUTTER, ModItems.REPAIR_FUSE,
                ModItems.REPAIR_GEAR_HANDLE, ModItems.REPAIR_BATTERY, ModItems.REPAIR_VALVE_HANDLE)));
    }

    private static Reward fallback(ServerLevel level, String category) {
        return switch (category == null ? "" : category) {
            case "coin", "coins", "gold" -> Reward.coins(10 + level.random.nextInt(21));
            case "empty" -> Reward.empty();
            case "key" -> Reward.item(new ItemStack(random(level, ModItems.REPAIR_AREA_KEY, ModItems.REPAIR_LOCKPICK)));
            case "weapon" -> Reward.item(new ItemStack(level.random.nextBoolean() ? ModItems.HUNTER_HAMMER : ModItems.HUNTER_HOOK));
            case "escape" -> Reward.item(new ItemStack(random(level, ModItems.REPAIR_FUSE, ModItems.REPAIR_GEAR_HANDLE,
                    ModItems.REPAIR_BATTERY, ModItems.REPAIR_VALVE_HANDLE)));
            case "medical" -> Reward.item(new ItemStack(random(level, ModItems.RESCUE_FLARE, ModItems.SMOKE_PELLET,
                    ModItems.REPAIR_MEDKIT)));
            default -> Reward.item(new ItemStack(random(level, ModItems.REPAIR_CROWBAR, ModItems.REPAIR_BOLT_CUTTER,
                    ModItems.SPARE_PARTS)));
        };
    }

    private static int parseCoins(String value, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static net.minecraft.world.item.Item random(ServerLevel level, net.minecraft.world.item.Item... items) {
        return items[level.random.nextInt(items.length)];
    }

    public record Reward(Kind kind, ItemStack stack, int coins) {
        public static Reward item(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return empty();
            }
            return new Reward(Kind.ITEM, stack.copy(), 0);
        }

        public static Reward coins(int coins) {
            return new Reward(Kind.COINS, ItemStack.EMPTY, Math.max(1, coins));
        }

        public static Reward empty() {
            return new Reward(Kind.EMPTY, ItemStack.EMPTY, 0);
        }

        public Reward copy() {
            return new Reward(kind, stack.copy(), coins);
        }
    }

    public enum Kind {
        ITEM,
        COINS,
        EMPTY
    }
}
