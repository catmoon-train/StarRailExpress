package org.agmas.noellesroles.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class DeathReasonRegistry {
    public enum KillUsage {
        NORMAL,
        FORCE
    }

    public static final class Entry {
        private final ResourceLocation id;
        private final EnumSet<KillUsage> usages;
        private boolean itemDeathReason;
        private boolean gameConstant;

        private Entry(ResourceLocation id, boolean itemDeathReason, boolean gameConstant) {
            this.id = id;
            this.usages = EnumSet.noneOf(KillUsage.class);
            this.itemDeathReason = itemDeathReason;
            this.gameConstant = gameConstant;
        }

        public ResourceLocation id() {
            return id;
        }

        public Set<KillUsage> usages() {
            return Collections.unmodifiableSet(usages);
        }

        public boolean isNormalKill() {
            return usages.contains(KillUsage.NORMAL);
        }

        public boolean isForceKill() {
            return usages.contains(KillUsage.FORCE);
        }

        public boolean isItemDeathReason() {
            return itemDeathReason;
        }

        public boolean isGameConstant() {
            return gameConstant;
        }
    }

    private static final Map<ResourceLocation, Entry> ENTRIES = new LinkedHashMap<>();
    private static final Set<ResourceLocation> GAME_CONSTANT_IDS = new LinkedHashSet<>();

    private DeathReasonRegistry() {
    }

    public static ResourceLocation register(String namespace, String path, KillUsage... usages) {
        return register(ResourceLocation.fromNamespaceAndPath(namespace, path), usages);
    }

    public static ResourceLocation register(ResourceLocation id, KillUsage... usages) {
        return register(id, false, false, usages);
    }

    public static ResourceLocation normal(ResourceLocation id) {
        return register(id, KillUsage.NORMAL);
    }

    public static ResourceLocation force(ResourceLocation id) {
        return register(id, KillUsage.FORCE);
    }

    public static ResourceLocation both(ResourceLocation id) {
        return register(id, KillUsage.NORMAL, KillUsage.FORCE);
    }

    public static ResourceLocation gameConstant(ResourceLocation id, KillUsage... usages) {
        GAME_CONSTANT_IDS.add(id);
        return register(id, false, true, usages);
    }

    public static ResourceLocation registerItem(String namespace, String path, KillUsage... usages) {
        return register(ResourceLocation.fromNamespaceAndPath(namespace, path), true, false, usages);
    }

    public static ResourceLocation registerItem(ResourceLocation id, KillUsage... usages) {
        return register(id, true, false, usages);
    }

    public static ResourceLocation registerItem(Item item, KillUsage... usages) {
        return registerItem(BuiltInRegistries.ITEM.getKey(item), usages);
    }

    public static ResourceLocation registerUsage(ResourceLocation id, boolean forceDeath) {
        return register(id, forceDeath ? KillUsage.FORCE : KillUsage.NORMAL);
    }

    private static ResourceLocation register(ResourceLocation id, boolean itemDeathReason, boolean gameConstant,
                                             KillUsage... usages) {
        if (id == null) {
            throw new IllegalArgumentException("Death reason id cannot be null");
        }
        Entry entry = ENTRIES.computeIfAbsent(id, key -> new Entry(key, itemDeathReason, gameConstant));
        if (usages.length == 0) {
            entry.usages.add(KillUsage.NORMAL);
        } else {
            Collections.addAll(entry.usages, usages);
        }
        entry.itemDeathReason |= itemDeathReason;
        entry.gameConstant |= gameConstant;
        if (gameConstant) {
            GAME_CONSTANT_IDS.add(id);
        }
        return id;
    }

    public static Optional<Entry> get(ResourceLocation id) {
        return Optional.ofNullable(ENTRIES.get(id));
    }

    public static boolean isNormalKill(ResourceLocation id) {
        return get(id).map(Entry::isNormalKill).orElse(false);
    }

    public static boolean isForceKill(ResourceLocation id) {
        return get(id).map(Entry::isForceKill).orElse(false);
    }

    public static boolean isItemDeathReason(ResourceLocation id) {
        return get(id).map(Entry::isItemDeathReason).orElse(false);
    }

    public static Set<ResourceLocation> ids() {
        return Collections.unmodifiableSet(ENTRIES.keySet());
    }

    public static Set<ResourceLocation> gameConstantIds() {
        return Collections.unmodifiableSet(GAME_CONSTANT_IDS);
    }

    public static Collection<Entry> entries() {
        return Collections.unmodifiableCollection(ENTRIES.values());
    }
}
