package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.SRE;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.UUID;

/**
 * DNF模式下玩家断开连接时保存物品栏的组件
 */
public class DNFInventoryBackupComponent implements AutoSyncedComponent {
    public static final ComponentKey<DNFInventoryBackupComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("dnf_inventory_backup"),
            DNFInventoryBackupComponent.class);

    private final Player player;
    
    // 保存的物品栏数据
    private ListTag savedInventory = null;
    // 保存时的玩家UUID（用于验证）
    private UUID savedPlayerUuid = null;
    // 是否有备份数据
    private boolean hasBackup = false;

    public DNFInventoryBackupComponent(Player player) {
        this.player = player;
    }

    /**
     * 保存当前玩家的物品栏
     */
    public void saveInventory() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        
        Inventory inventory = serverPlayer.getInventory();
        savedInventory = new ListTag();
        inventory.save(savedInventory);
        savedPlayerUuid = serverPlayer.getUUID();
        hasBackup = true;
        
        SRE.LOGGER.info("Saved inventory backup for player: {}", serverPlayer.getName().getString());
    }

    /**
     * 恢复保存的物品栏到指定玩家
     * @param targetPlayer 要恢复物品栏的玩家
     * @return 是否成功恢复
     */
    public boolean restoreInventory(ServerPlayer targetPlayer) {
        if (!hasBackup || savedInventory == null) {
            SRE.LOGGER.warn("No inventory backup available for restoration");
            return false;
        }
        
        try {
            Inventory inventory = targetPlayer.getInventory();
            inventory.load(savedInventory);
            
            SRE.LOGGER.info("Restored inventory backup for player: {}", targetPlayer.getName().getString());
            
            // 恢复后清除备份
            clear();
            return true;
        } catch (Exception e) {
            SRE.LOGGER.error("Failed to restore inventory for player: {}", targetPlayer.getName().getString(), e);
            return false;
        }
    }

    /**
     * 检查是否有备份数据
     */
    public boolean hasBackup() {
        return hasBackup && savedInventory != null;
    }

    /**
     * 获取备份的玩家UUID
     */
    public UUID getSavedPlayerUuid() {
        return savedPlayerUuid;
    }

    /**
     * 清除备份数据
     */
    public void clear() {
        savedInventory = null;
        savedPlayerUuid = null;
        hasBackup = false;
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (tag.contains("HasBackup") && tag.getBoolean("HasBackup")) {
            if (tag.contains("SavedInventory")) {
                savedInventory = tag.getList("SavedInventory", net.minecraft.nbt.Tag.TAG_COMPOUND);
                hasBackup = true;
            }
            if (tag.hasUUID("SavedPlayerUuid")) {
                savedPlayerUuid = tag.getUUID("SavedPlayerUuid");
            }
        }
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("HasBackup", hasBackup);
        if (hasBackup && savedInventory != null) {
            tag.put("SavedInventory", savedInventory);
        }
        if (savedPlayerUuid != null) {
            tag.putUUID("SavedPlayerUuid", savedPlayerUuid);
        }
    }
}
