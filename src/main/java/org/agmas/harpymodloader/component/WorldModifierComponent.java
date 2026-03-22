package org.agmas.harpymodloader.component;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.*;

public class WorldModifierComponent implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<WorldModifierComponent> KEY = ComponentRegistry
            .getOrCreate(ResourceLocation.fromNamespaceAndPath(Harpymodloader.MOD_ID, "modifier"),
                    WorldModifierComponent.class);
    private final Level world;
    public HashMap<UUID, ArrayList<SREModifier>> modifiers = new HashMap<>();

    public WorldModifierComponent(Level world) {
        this.world = world;
    }

    @Override
    public void serverTick() {

    }

    @Deprecated
    public boolean isRole(@NotNull Player player, SREModifier modifier) {
        return isModifier(player, modifier);
    }

    @Deprecated
    public boolean isRole(@NotNull UUID uuid, SREModifier modifier) {
        return isModifier(uuid, modifier);
    }

    public boolean isModifier(@NotNull Player player, SREModifier modifier) {
        return this.isModifier(player.getUUID(), modifier);
    }

    public boolean isModifier(@NotNull UUID uuid, SREModifier modifier) {
        return getModifiers(uuid).contains(modifier);
    }

    public HashMap<UUID, ArrayList<SREModifier>> getModifiers() {
        return this.modifiers;
    }

    public ArrayList<SREModifier> getModifiers(Player player) {
        return this.getModifiers(player.getUUID());
    }

    public ArrayList<SREModifier> getModifiers(UUID uuid) {
        synchronized (this.modifiers) {
            if (!modifiers.containsKey(uuid))
                modifiers.put(uuid, new ArrayList<>());
            return this.modifiers.get(uuid);
        }
    }

    public List<UUID> getAllWithModifier(SREModifier modifier) {
        List<UUID> ret = new ArrayList<>();
        synchronized (this.modifiers) {
            this.modifiers.forEach((uuid, playerModifier) -> {
                if (playerModifier.contains(modifier)) {
                    ret.add(uuid);
                }
            });
        }
        return ret;
    }

    public void setModifiers(List<UUID> players, SREModifier modifier) {

        for (UUID player : players) {
            addModifier(player, modifier);
            this.sync();
        }

    }

    public void removeModifier(UUID player, SREModifier modifier, boolean sync) {
        synchronized (this.modifiers) {
            var pp = getModifiers(player);
            if (pp != null) {
                pp.remove(modifier);
            }
        }
        if (sync)
            this.sync();
    }

    public void removeModifier(UUID player, SREModifier modifier) {
        this.removeModifier(player, modifier, true);
    }

    public void addModifier(UUID player, SREModifier modifier, boolean sync) {
        getModifiers(player).add(modifier);
        if (sync)
            this.sync();
    }

    public void addModifier(UUID player, SREModifier modifier) {
        this.addModifier(player, modifier, true);
    }

    @Override
    public void readFromNbt(CompoundTag nbtCompound, HolderLookup.Provider wrapperLookup) {

        modifiers.clear();
        for (SREModifier modifier : HMLModifiers.MODIFIERS) {
            setModifiers(this.uuidListFromNbt(nbtCompound, modifier.identifier().toString()), modifier);
        }
    }

    @Override
    public void writeToNbt(CompoundTag nbtCompound, HolderLookup.Provider wrapperLookup) {
        synchronized (this.modifiers) {
            for (SREModifier modifier : HMLModifiers.MODIFIERS) {
                // 在同步块内直接查找，避免嵌套同步调用
                List<UUID> uuidsWithModifier = new ArrayList<>();
                for (Map.Entry<UUID, ArrayList<SREModifier>> entry : this.modifiers.entrySet()) {
                    if (entry.getValue().contains(modifier)) {
                        uuidsWithModifier.add(entry.getKey());
                    }
                }
                if (uuidsWithModifier.isEmpty())
                    continue;
                nbtCompound.put(modifier.identifier().toString(), this.nbtFromUuidList(uuidsWithModifier));
            }
        }
    }

    public void sync() {
        KEY.sync(this.world);
    }

    @Override
    public void clientTick() {

    }

    private ArrayList<UUID> uuidListFromNbt(CompoundTag nbtCompound, String listName) {
        ArrayList<UUID> ret = new ArrayList<>();
        if (nbtCompound.contains(listName, Tag.TAG_LIST)) {
            for (Tag e : nbtCompound.getList(listName, 11)) {
                ret.add(NbtUtils.loadUUID(e));
            }
        }
        return ret;
    }

    private ListTag nbtFromUuidList(List<UUID> list) {
        ListTag ret = new ListTag();

        for (UUID player : list) {
            ret.add(NbtUtils.createUUID(player));
        }

        return ret;
    }

    public ArrayList<SREModifier> getDisplayableModifiers(Player player) {
        var modifiers = new ArrayList<SREModifier>(this.getModifiers(player.getUUID()));
        modifiers.removeIf((modifier) -> {
            if (Harpymodloader.HIDDEN_MODIFIERS.contains(modifier.identifier().getPath())) {
                return true;
            }
            return false;
        });
        return modifiers;
    }
}
