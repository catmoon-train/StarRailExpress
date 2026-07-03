package io.wifi.starrailexpress.cca.gamemode;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

/**
 * 职业轮选模式玩家组件
 * 用于管理每个玩家的轮选状态
 */
public class RoleRotationPlayerComponent implements RoleComponent {

    @Override
    public Player getPlayer() {
        return player;
    }

    /** 组件键 */
    public static final ComponentKey<RoleRotationPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("role_rotation_player"),
            RoleRotationPlayerComponent.class);

    private final Player player;

    // 玩家是否已选择职业
    private boolean hasSelected = false;

    // 玩家选择的职业ID
    private ResourceLocation selectedRoleId = null;

    // 玩家是否正在等待选择
    private boolean isWaiting = false;

    public RoleRotationPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public void init() {
        this.hasSelected = false;
        this.selectedRoleId = null;
        this.isWaiting = false;
        sync();
    }

    public boolean hasSelected() {
        return hasSelected;
    }

    public void setSelected(boolean selected) {
        this.hasSelected = selected;
    }

    public ResourceLocation getSelectedRoleId() {
        return selectedRoleId;
    }

    public void setSelectedRoleId(ResourceLocation roleId) {
        this.selectedRoleId = roleId;
        this.hasSelected = true;
        // sync removed: client never reads these fields (UI reads from RoleRotationWorldComponent)
    }

    public boolean isWaiting() {
        return isWaiting;
    }

    public void setWaiting(boolean waiting) {
        this.isWaiting = waiting;
        // sync removed: client never reads these fields
    }

    public void sync() {
        KEY.sync(this.player);
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 客户端不使用此组件数据（UI 从 RoleRotationWorldComponent 读取），仅保留最小标签满足 CCA 协议
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 客户端不使用此数据，无需反序列化
    }

    @Override
    public void clear() {
        this.init();
    }

    public void reset() {
        this.hasSelected = false;
        this.selectedRoleId = null;
        this.isWaiting = false;
    }
}
