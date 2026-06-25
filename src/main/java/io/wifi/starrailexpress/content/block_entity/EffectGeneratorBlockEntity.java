package io.wifi.starrailexpress.content.block_entity;

import java.util.ArrayList;
import java.util.List;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.index.TMMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class EffectGeneratorBlockEntity extends SyncingBlockEntity {
    public static final int MAX_RADIUS = 100;
    private static final int APPLY_INTERVAL = 20;
    private static final int EFFECT_DURATION = 60;

    private int radius = 8;
    private final List<EffectEntry> effects = new ArrayList<>();

    public EffectGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(TMMBlockEntities.EFFECT_GENERATOR, pos, state);
    }

    public int getRadius() {
        return radius;
    }

    public List<EffectEntry> getEffects() {
        return effects;
    }

    public CompoundTag toConfigTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Radius", radius);
        ListTag list = new ListTag();
        for (EffectEntry entry : effects) {
            CompoundTag e = new CompoundTag();
            e.putString("Id", entry.id());
            e.putInt("Level", entry.level());
            list.add(e);
        }
        tag.put("Effects", list);
        return tag;
    }

    public void loadConfig(CompoundTag tag) {
        this.radius = Math.max(0, Math.min(MAX_RADIUS, tag.getInt("Radius")));
        this.effects.clear();
        ListTag list = tag.getList("Effects", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            String id = normalizeId(entryTag.getString("Id"));
            int level = Math.max(1, entryTag.getInt("Level"));
            if (!id.isBlank()) {
                this.effects.add(new EffectEntry(id, level));
            }
        }
        sync();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registryLookup) {
        super.saveAdditional(tag, registryLookup);
        tag.merge(toConfigTag());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registryLookup) {
        super.loadAdditional(tag, registryLookup);
        loadConfig(tag);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EffectGeneratorBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (serverLevel.getGameTime() % APPLY_INTERVAL != 0) {
            return;
        }
        if (!SREGameWorldComponent.KEY.get(serverLevel).isRunning() || be.effects.isEmpty() || be.radius <= 0) {
            return;
        }
        double radiusSq = (double) be.radius * be.radius;
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        for (var player : serverLevel.players()) {
            if (player.distanceToSqr(cx, cy, cz) > radiusSq) {
                continue;
            }
            for (EffectEntry entry : be.effects) {
                var effect = resolveEffect(entry.id());
                if (effect != null) {
                    player.addEffect(new MobEffectInstance(effect, EFFECT_DURATION, Math.max(0, entry.level() - 1),
                            true, false, true));
                }
            }
        }
    }

    private static net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> resolveEffect(String id) {
        ResourceLocation resource = ResourceLocation.tryParse(normalizeId(id));
        if (resource == null) {
            return null;
        }
        return BuiltInRegistries.MOB_EFFECT.getHolder(resource).orElse(null);
    }

    public static String normalizeId(String id) {
        if (id == null) {
            return "";
        }
        String trimmed = id.trim();
        return trimmed.isBlank() || trimmed.contains(":") ? trimmed : "minecraft:" + trimmed;
    }

    public record EffectEntry(String id, int level) {}
}
