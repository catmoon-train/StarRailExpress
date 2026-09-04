/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.harpymodloader.component;

import net.fabricmc.api.EnvType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
import org.ladysnake.cca.api.v3.util.CheckEnvironment;

import java.util.*;

public class WorldModifierComponent implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<WorldModifierComponent> KEY = ComponentRegistry
            .getOrCreate(ResourceLocation.fromNamespaceAndPath(Harpymodloader.MOD_ID, "modifier"),
                    WorldModifierComponent.class);

    // 同步包类型：FULL=全量快照（新玩家加入/重生/换维度时由 CCA 触发），DIFF=差异（仅变更过的玩家条目）
    private static final byte MODE_FULL = 0;
    private static final byte MODE_DIFF = 1;

    private final Level world;
    public HashMap<UUID, HashSet<SREModifier>> modifiers = new HashMap<>();

    // 自上次发包以来真正发生过变更的玩家UUID，差异包只包含这些玩家
    private final Set<UUID> dirtyUuids = new HashSet<>();
    // 本tick内收到过sync()请求，推迟到serverTick统一发包，把多次广播合并成一次
    private boolean syncPending = false;
    // 仅服务端在差异发包期间为true；区分"我们自己的差异同步"与"CCA的新玩家全量同步"
    private boolean diffMode = false;

    public WorldModifierComponent(Level world) {
        this.world = world;
    }

    @Override
    public void serverTick() {
        flushPendingDiff();
    }

    /**
     * 把本tick内积累的差异统一广播给世界内所有玩家。没有任何改动时不发包。
     */
    private void flushPendingDiff() {
        if (this.world.isClientSide)
            return;
        synchronized (this.modifiers) {
            if (!this.syncPending || this.dirtyUuids.isEmpty()) {
                this.syncPending = false;
                return;
            }
        }
        this.diffMode = true;
        try {
            KEY.sync(this.world);
        } finally {
            this.diffMode = false;
            synchronized (this.modifiers) {
                this.dirtyUuids.clear();
                this.syncPending = false;
            }
        }
    }

    public boolean isModifier(@NotNull Player player, SREModifier modifier) {
        return this.isModifier(player.getUUID(), modifier);
    }

    public boolean isModifier(@NotNull UUID uuid, SREModifier modifier) {
        return getModifiers(uuid).contains(modifier);
    }

    public HashMap<UUID, HashSet<SREModifier>> getModifiers() {
        return this.modifiers;
    }

    public HashSet<SREModifier> getModifiers(Player player) {
        return this.getModifiers(player.getUUID());
    }

    public HashSet<SREModifier> getModifiers(UUID uuid) {
        synchronized (this.modifiers) {
            if (!modifiers.containsKey(uuid))
                modifiers.put(uuid, new HashSet<>());
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
        if (players.isEmpty())
            return;
        synchronized (this.modifiers) {
            for (UUID player : players) {
                if (modifier != null && getModifiers(player).add(modifier)) {
                    this.dirtyUuids.add(player);
                }
            }
        }
        this.sync();
    }

    /**
     * 清空整张表并标记所有旧条目待同步删除，替代外部直接操作modifiers字段的clear。
     * 调用方仍需自行调用sync()。
     */
    public void clearAll() {
        synchronized (this.modifiers) {
            this.dirtyUuids.addAll(this.modifiers.keySet());
            this.modifiers.clear();
        }
    }

    public void removeModifier(UUID player, SREModifier modifier, boolean sync) {
        synchronized (this.modifiers) {
            HashSet<SREModifier> pp = this.modifiers.get(player);
            if (pp != null && pp.remove(modifier)) {
                this.dirtyUuids.add(player);
            }
        }
        if (sync)
            this.sync();
    }

    public void removeModifier(Player player, SREModifier modifier) {
        this.removeModifier(player.getUUID(), modifier);
    }

    public void removeModifier(UUID player, SREModifier modifier) {
        this.removeModifier(player, modifier, true);
    }

    public void addModifier(UUID player, SREModifier modifier, boolean sync) {
        if (modifier == null)
            return;
        synchronized (this.modifiers) {
            if (!getModifiers(player).add(modifier))
                return; // 没有真正变化，不标记脏
            this.dirtyUuids.add(player);
        }
        if (sync)
            this.sync();
    }

    public void addModifier(Player player, SREModifier modifier, boolean sync) {
        if (player == null)
            return;
        this.addModifier(player.getUUID(), modifier, sync);
    }

    public void addModifier(Player player, SREModifier modifier) {
        if (player == null)
            return;
        this.addModifier(player.getUUID(), modifier);
    }

    public void addModifier(UUID player, SREModifier modifier) {
        this.addModifier(player, modifier, true);
    }

    @Override
    public void readFromNbt(CompoundTag nbtCompound, HolderLookup.Provider wrapperLookup) {
        synchronized (this.modifiers) {
            modifiers.clear();
            for (SREModifier modifier : HMLModifiers.MODIFIERS) {
                for (UUID uuid : this.uuidListFromNbt(nbtCompound, modifier.identifier().toString())) {
                    this.modifiers.computeIfAbsent(uuid, k -> new HashSet<>()).add(modifier);
                }
            }
            // 读档只是填充服务端状态，不广播；新玩家加入时CCA会发送全量快照
            this.dirtyUuids.clear();
            this.syncPending = false;
        }
    }

    @Override
    public void writeToNbt(CompoundTag nbtCompound, HolderLookup.Provider wrapperLookup) {
        synchronized (this.modifiers) {
            for (SREModifier modifier : HMLModifiers.MODIFIERS) {
                // 在同步块内直接查找，避免嵌套同步调用
                List<UUID> uuidsWithModifier = new ArrayList<>();
                for (Map.Entry<UUID, HashSet<SREModifier>> entry : this.modifiers.entrySet()) {
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

    /**
     * 请求一次同步。真正的发包推迟到serverTick执行，同一tick内的多次调用会合并成一次差异广播。
     */
    public void sync() {
        if (this.world.isClientSide)
            return;
        synchronized (this.modifiers) {
            if (this.dirtyUuids.isEmpty())
                return;
            this.syncPending = true;
        }
    }

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        synchronized (this.modifiers) {
            if (this.diffMode) {
                // 差异：只写自上次发包以来变更过的玩家条目（含清空条目用于通知删除）
                buf.writeByte(MODE_DIFF);
                buf.writeVarInt(this.dirtyUuids.size());
                for (UUID uuid : this.dirtyUuids) {
                    HashSet<SREModifier> set = this.modifiers.get(uuid);
                    writeEntry(buf, uuid, set == null ? Collections.emptySet() : set);
                }
            } else {
                // 全量快照：新玩家加入/重生/换维度时由CCA触发，等价于旧版的整表覆盖
                buf.writeByte(MODE_FULL);
                int count = 0;
                for (HashSet<SREModifier> set : this.modifiers.values()) {
                    if (!set.isEmpty())
                        count++;
                }
                buf.writeVarInt(count);
                for (Map.Entry<UUID, HashSet<SREModifier>> entry : this.modifiers.entrySet()) {
                    if (!entry.getValue().isEmpty())
                        writeEntry(buf, entry.getKey(), entry.getValue());
                }
            }
        }
    }

    @Override
    @CheckEnvironment(EnvType.CLIENT)
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        // 按identifier反查本端注册表；未知identifier（如版本不一致）直接跳过
        final Map<ResourceLocation, SREModifier> modifiersById = new HashMap<>();
        for (SREModifier modifier : HMLModifiers.MODIFIERS) {
            modifiersById.put(modifier.identifier(), modifier);
        }

        final byte mode = buf.readByte();
        final int entryCount = buf.readVarInt();
        if (mode == MODE_FULL) {
            this.modifiers.clear();
        }
        for (int i = 0; i < entryCount; i++) {
            UUID uuid = buf.readUUID();
            int modCount = buf.readVarInt();
            HashSet<SREModifier> set = new HashSet<>();
            for (int j = 0; j < modCount; j++) {
                SREModifier modifier = modifiersById.get(buf.readResourceLocation());
                if (modifier != null) {
                    set.add(modifier);
                }
            }
            if (set.isEmpty()) {
                // 空条目 = 该玩家已没有任何modifier，删除键保持与全量覆盖一致
                this.modifiers.remove(uuid);
            } else {
                this.modifiers.put(uuid, set);
            }
        }
    }

    private static void writeEntry(RegistryFriendlyByteBuf buf, UUID uuid, Collection<SREModifier> set) {
        buf.writeUUID(uuid);
        buf.writeVarInt(set.size());
        for (SREModifier modifier : set) {
            buf.writeResourceLocation(modifier.identifier());
        }
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
        modifiers.removeIf(WorldModifierComponent::isHiddenModifier);
        return modifiers;
    }

    public static boolean isHiddenModifier(SREModifier modifier) {
        if (modifier == null)
            return false;
        return modifier.isFlag("inner.hidden");
    }

    public static WorldModifierComponent getInstance(Player player) {
        return KEY.get(player.level());
    }

    public static WorldModifierComponent getInstance(Level level) {
        return KEY.get(level);
    }
}
