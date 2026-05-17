package org.agmas.noellesroles.game.modes.repair;

import io.wifi.starrailexpress.game.data.MapConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import org.agmas.noellesroles.init.ModItems;

import java.util.*;

public final class RepairLootSpawner {
    private static final Map<ServerLevel, Map<BlockPos, LootEntry>> LOOT = new WeakHashMap<>();
    private static final Map<ServerLevel, Set<BlockPos>> SEARCHED = new WeakHashMap<>();

    private RepairLootSpawner() {
    }

    public static void prepare(ServerLevel level, MapConfig.RepairConfig config) {
        Map<BlockPos, LootEntry> entries = new HashMap<>();
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
                entries.put(point.pos.toBlockPos(), new LootEntry(point.category, chooseStack(level, point)));
            }
        }
        LOOT.put(level, entries);
        SEARCHED.put(level, new HashSet<>());
    }

    public static ItemStack take(ServerLevel level, BlockPos pos) {
        Set<BlockPos> searched = SEARCHED.computeIfAbsent(level, ignored -> new HashSet<>());
        if (!searched.add(pos.immutable())) {
            return ItemStack.EMPTY;
        }
        LootEntry entry = LOOT.getOrDefault(level, Map.of()).remove(pos);
        if (entry == null) {
            return fallback(level, "tool");
        }
        return entry.stack.copy();
    }

    public static boolean hasLoot(ServerLevel level, BlockPos pos) {
        return LOOT.getOrDefault(level, Map.of()).containsKey(pos);
    }

    public static void reset(ServerLevel level) {
        LOOT.remove(level);
        SEARCHED.remove(level);
    }

    private static ItemStack chooseStack(ServerLevel level, MapConfig.LootPointEntry point) {
        if (point.pool != null && !point.pool.isEmpty()) {
            String id = point.pool.get(level.random.nextInt(point.pool.size()));
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id.contains(":") ? id : "noellesroles:" + id));
            if (item != net.minecraft.world.item.Items.AIR) {
                return new ItemStack(item);
            }
        }
        return fallback(level, point.category);
    }

    private static ItemStack fallback(ServerLevel level, String category) {
        return switch (category == null ? "" : category) {
            case "key" -> new ItemStack(random(level, ModItems.REPAIR_AREA_KEY, ModItems.REPAIR_OLD_KEY,
                    ModItems.REPAIR_LOCKPICK));
            case "weapon" -> new ItemStack(level.random.nextBoolean() ? ModItems.HUNTER_HAMMER : ModItems.HUNTER_HOOK);
            case "escape" -> new ItemStack(random(level, ModItems.REPAIR_FUSE, ModItems.REPAIR_GEAR_HANDLE,
                    ModItems.REPAIR_BATTERY, ModItems.REPAIR_VALVE_HANDLE));
            case "medical" -> new ItemStack(level.random.nextBoolean() ? ModItems.RESCUE_FLARE : ModItems.SMOKE_PELLET);
            default -> new ItemStack(random(level, ModItems.REPAIR_CROWBAR, ModItems.REPAIR_BOLT_CUTTER,
                    ModItems.SPARE_PARTS));
        };
    }

    private static net.minecraft.world.item.Item random(ServerLevel level, net.minecraft.world.item.Item... items) {
        return items[level.random.nextInt(items.length)];
    }

    private record LootEntry(String category, ItemStack stack) {
    }
}
