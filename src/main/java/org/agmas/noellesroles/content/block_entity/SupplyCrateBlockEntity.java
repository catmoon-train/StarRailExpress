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

package org.agmas.noellesroles.content.block_entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.block.SupplyCrateBlock;
import org.agmas.noellesroles.init.ModBlocks;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 物资箱方块实体，存储物资配置和当前可领取的物品
 */
public class SupplyCrateBlockEntity extends BlockEntity {

    private final List<SupplyCrateEntry> configItems = new ArrayList<>();
    private final List<ItemStack> currentItems = new ArrayList<>();
    private int refreshIntervalTicks = 200; // 默认10秒 (20 ticks/秒 * 10)
    private boolean refreshAllSimultaneously = false; // 默认否
    private boolean sharedSupplies = false; // 默认否
    /**
     * 全局仅能被拿一次：任何玩家领取后，其他玩家再右键也无法领取（非共享模式的
     * 「每人一次」升级为「全局一次」）。物资刷新与游戏结束重置时会恢复可领取。
     */
    private boolean globalOnceOnly = false; // 默认否（可重复领取）
    private long lastRefreshTick = -1;
    private final Set<UUID> claimedPlayers = new HashSet<>(); // 已领取的玩家（非共享/全局一次模式）

    // 按地图名追踪所有物资箱实例（用于游戏结束/开始时统一重置）
    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.Set<BlockPos>> mapInstances = new java.util.concurrent.ConcurrentHashMap<>();
    private boolean tracked = false;

    private static String getMapKey(Level level) {
        var areas = io.wifi.starrailexpress.cca.AreasWorldComponent.KEY.get(level);
        return areas != null && areas.mapName != null ? areas.mapName : "";
    }

    private void tryTrack() {
        if (!tracked && level != null && !level.isClientSide()) {
            tracked = true;
            mapInstances
                    .computeIfAbsent(getMapKey(level), k -> java.util.concurrent.ConcurrentHashMap.newKeySet())
                    .add(worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide()) {
            var set = mapInstances.get(getMapKey(level));
            if (set != null) {
                set.remove(worldPosition);
            }
        }
    }

    /**
     * 重置本局相关状态：清空当前物资与领取记录，间隔重新计时，并复位方块开启状态。
     * 配置（物品列表、刷新间隔、刷新/共享开关）保持不变。
     */
    public void resetForNewGame() {
        currentItems.clear();
        claimedPlayers.clear();
        lastRefreshTick = -1; // -1 表示下一 tick 重新计时
        setChanged();
        if (level == null || level.isClientSide()) return;
        BlockState state = level.getBlockState(worldPosition);
        if (state.hasProperty(SupplyCrateBlock.OPENED) && state.getValue(SupplyCrateBlock.OPENED)) {
            level.setBlockAndUpdate(worldPosition, state.setValue(SupplyCrateBlock.OPENED, false));
        }
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().blockChanged(worldPosition);
        }
    }

    /**
     * 重置指定世界中所有物资箱的本局状态（游戏结束/开局时调用）
     */
    public static void resetAll(Level level) {
        if (level == null || level.isClientSide()) return;
        var set = mapInstances.get(getMapKey(level));
        if (set == null || set.isEmpty()) return;
        for (BlockPos pos : new ArrayList<>(set)) {
            if (level.getBlockEntity(pos) instanceof SupplyCrateBlockEntity crate && !crate.isRemoved()) {
                crate.resetForNewGame();
            }
        }
    }

    public SupplyCrateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.SUPPLY_CRATE_BLOCK_ENTITY, pos, state);
    }

    /**
     * 每 tick 调用，检查是否需要刷新物资
     */
    public static void tick(Level level, BlockPos pos, BlockState state, SupplyCrateBlockEntity entity) {
        if (level.isClientSide()) return;

        entity.tryTrack();

        long currentTick = level.getGameTime();
        if (entity.lastRefreshTick < 0) {
            entity.lastRefreshTick = currentTick;
        }

        long elapsed = currentTick - entity.lastRefreshTick;
        if (elapsed >= entity.refreshIntervalTicks && entity.refreshIntervalTicks > 0) {
            entity.refreshItems(level);
            entity.lastRefreshTick = currentTick;
        }
    }

    /**
     * 刷新物资
     */
    private void refreshItems(Level level) {
        currentItems.clear();
        claimedPlayers.clear();

        if (configItems.isEmpty()) return;

        if (refreshAllSimultaneously) {
            // 同时刷新所有物品（无视概率）
            for (SupplyCrateEntry entry : configItems) {
                ItemStack stack = createItemStack(entry.itemId(), entry.count());
                if (!stack.isEmpty()) {
                    currentItems.add(stack);
                }
            }
        } else {
            // 根据概率随机选择一个物品
            double totalWeight = configItems.stream().mapToDouble(SupplyCrateEntry::probability).sum();
            if (totalWeight <= 0) return;

            double random = level.getRandom().nextDouble() * totalWeight;
            double cumulative = 0;
            for (SupplyCrateEntry entry : configItems) {
                cumulative += entry.probability();
                if (random <= cumulative) {
                    ItemStack stack = createItemStack(entry.itemId(), entry.count());
                    if (!stack.isEmpty()) {
                        currentItems.add(stack);
                    }
                    break;
                }
            }
        }

        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().blockChanged(worldPosition);

            // 更新客户端: 如果非共享，重置方块打开状态
            if (!sharedSupplies) {
                BlockState currentState = level.getBlockState(worldPosition);
                if (currentState.hasProperty(SupplyCrateBlock.OPENED) && currentState.getValue(SupplyCrateBlock.OPENED)) {
                    level.setBlockAndUpdate(worldPosition, currentState.setValue(SupplyCrateBlock.OPENED, false));
                }
            }
        }
    }

    /**
     * 根据物品 ID 创建 ItemStack
     */
    private ItemStack createItemStack(String itemId, int count) {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            if (rl == null) return ItemStack.EMPTY;
            var item = BuiltInRegistries.ITEM.get(rl);
            if (item == null) return ItemStack.EMPTY;
            return new ItemStack(item, Math.max(1, count));
        } catch (Exception e) {
            Noellesroles.LOGGER.warn("[SupplyCrate] 无法创建物品: {}", itemId);
            return ItemStack.EMPTY;
        }
    }

    /**
     * 玩家领取物资
     * @return 是否成功领取（返回物品列表，空列表表示无物资可领）
     */
    public List<ItemStack> claimItems(Player player) {
        if (currentItems.isEmpty()) return Collections.emptyList();

        // 全局仅能拿一次：已有人领取过（刷新/重置会清空记录），任何人都不能再领
        if (globalOnceOnly && !claimedPlayers.isEmpty()) {
            return Collections.emptyList();
        }

        if (sharedSupplies) {
            // 共享模式：每个玩家都能领取
            claimedPlayers.add(player.getUUID());
            setChanged();
            return new ArrayList<>(currentItems);
        } else {
            // 非共享：只有第一个领取的玩家能拿到
            if (claimedPlayers.contains(player.getUUID())) {
                return Collections.emptyList();
            }
            claimedPlayers.add(player.getUUID());
            setChanged();
            return new ArrayList<>(currentItems);
        }
    }

    /**
     * 检查当前是否有可领取的物资
     */
    public boolean hasItems(Player player) {
        if (currentItems.isEmpty()) return false;
        // 全局仅能拿一次：已有人领取过则所有人都不可领
        if (globalOnceOnly && !claimedPlayers.isEmpty()) return false;
        if (sharedSupplies) return true;
        return !claimedPlayers.contains(player.getUUID());
    }

    // ========== Getter/Setter ==========

    public List<SupplyCrateEntry> getConfigItems() {
        return Collections.unmodifiableList(configItems);
    }

    public void setConfigItems(List<SupplyCrateEntry> items) {
        configItems.clear();
        configItems.addAll(items);
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public int getRefreshIntervalTicks() { return refreshIntervalTicks; }
    public void setRefreshIntervalTicks(int ticks) {
        this.refreshIntervalTicks = Math.max(20, ticks); // 最小1秒
        setChanged();
    }

    public boolean isRefreshAllSimultaneously() { return refreshAllSimultaneously; }
    public void setRefreshAllSimultaneously(boolean v) {
        this.refreshAllSimultaneously = v;
        setChanged();
    }

    public boolean isSharedSupplies() { return sharedSupplies; }
    public void setSharedSupplies(boolean v) {
        this.sharedSupplies = v;
        setChanged();
    }

    public boolean isGlobalOnceOnly() { return globalOnceOnly; }
    public void setGlobalOnceOnly(boolean v) {
        this.globalOnceOnly = v;
        setChanged();
    }

    public List<ItemStack> getCurrentItems() {
        return Collections.unmodifiableList(currentItems);
    }

    // ========== NBT 序列化 ==========

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);

        // 保存配置物品列表
        ListTag configList = new ListTag();
        for (SupplyCrateEntry entry : configItems) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("itemId", entry.itemId());
            entryTag.putInt("count", entry.count());
            entryTag.putDouble("probability", entry.probability());
            configList.add(entryTag);
        }
        tag.put("configItems", configList);

        // 保存当前可领取物品
        ListTag currentList = new ListTag();
        for (ItemStack stack : currentItems) {
            if (!stack.isEmpty()) {
                currentList.add(stack.save(provider));
            }
        }
        tag.put("currentItems", currentList);

        tag.putInt("refreshIntervalTicks", refreshIntervalTicks);
        tag.putBoolean("refreshAllSimultaneously", refreshAllSimultaneously);
        tag.putBoolean("sharedSupplies", sharedSupplies);
        tag.putBoolean("globalOnceOnly", globalOnceOnly);
        tag.putLong("lastRefreshTick", lastRefreshTick);

        // 保存已领取玩家
        ListTag claimedList = new ListTag();
        for (UUID uuid : claimedPlayers) {
            CompoundTag uuidTag = new CompoundTag();
            uuidTag.putUUID("uuid", uuid);
            claimedList.add(uuidTag);
        }
        tag.put("claimedPlayers", claimedList);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);

        configItems.clear();
        if (tag.contains("configItems", Tag.TAG_LIST)) {
            ListTag list = tag.getList("configItems", Tag.TAG_COMPOUND);
            for (Tag t : list) {
                CompoundTag entryTag = (CompoundTag) t;
                String itemId = entryTag.getString("itemId");
                int count = entryTag.getInt("count");
                double probability = entryTag.getDouble("probability");
                configItems.add(new SupplyCrateEntry(itemId, count, probability));
            }
        }

        currentItems.clear();
        if (tag.contains("currentItems", Tag.TAG_LIST)) {
            ListTag list = tag.getList("currentItems", Tag.TAG_COMPOUND);
            for (Tag t : list) {
                ItemStack stack = ItemStack.parse(provider, t).orElse(ItemStack.EMPTY);
                if (!stack.isEmpty()) {
                    currentItems.add(stack);
                }
            }
        }

        refreshIntervalTicks = tag.getInt("refreshIntervalTicks");
        if (refreshIntervalTicks <= 0) refreshIntervalTicks = 200;
        refreshAllSimultaneously = tag.getBoolean("refreshAllSimultaneously");
        sharedSupplies = tag.getBoolean("sharedSupplies");
        // 缺省（旧存档）时保持默认开启
        globalOnceOnly = tag.contains("globalOnceOnly") ? tag.getBoolean("globalOnceOnly") : true;
        lastRefreshTick = tag.getLong("lastRefreshTick");

        claimedPlayers.clear();
        if (tag.contains("claimedPlayers", Tag.TAG_LIST)) {
            ListTag list = tag.getList("claimedPlayers", Tag.TAG_COMPOUND);
            for (Tag t : list) {
                CompoundTag uuidTag = (CompoundTag) t;
                claimedPlayers.add(uuidTag.getUUID("uuid"));
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) {
        return this.saveWithoutMetadata(registryLookup);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * 物资配置条目
     */
    public record SupplyCrateEntry(String itemId, int count, double probability) {}
}
