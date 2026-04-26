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

import java.util.ArrayList;

/**
 * 通用技能组件
 *
 * 用于管理玩家的技能冷却时间和使用次数
 * 该组件会自动在客户端和服务端之间同步
 *
 * 功能：
 * - 冷却时间管理（自动递减）
 * - 技能使用次数限制
 * - 自动同步到客户端（用于 HUD 显示）
 */
public class CustomRoleGameModeTeamsPlayerComponent
        implements RoleComponent {

    @Override
    public Player getPlayer() {
        return player;
    }

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<CustomRoleGameModeTeamsPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("custom_role_team"),
            CustomRoleGameModeTeamsPlayerComponent.class);

    // 持有该组件的玩家
    private final Player player;
    private int team = 0;

    private boolean selected = false;

    /**
     * 构造函数
     */
    public CustomRoleGameModeTeamsPlayerComponent(Player player) {
        this.player = player;
    }

    /**
     * 重置组件状态
     * 在游戏开始时或角色分配时调用
     */
    @Override
    public void init() {
        this.team = 0;
        this.sync();
    }

    public int getTeam() {
        return this.team;
    }

    public boolean selected() {
        return selected;
    }

    public void setTeam(int type) {
        this.team = type;
    }

    /**
     * 同步到客户端
     */
    public void sync() {
        KEY.sync(this.player);
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("team", this.team);
        if (selected)
            tag.putBoolean("s", this.selected);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.team = tag.contains("team") ? tag.getInt("team") : 0;
        this.selected = tag.contains("s") ? tag.getBoolean("s") : false;
    }

    public ArrayList<ResourceLocation> getAvailableRoles() {
        if (this.team <= 0)
            return new ArrayList<>();
        CustomRoleGameModeWorldComponent crgmwcca = CustomRoleGameModeWorldComponent.KEY.get(this.player.level());
        ArrayList<ResourceLocation> result = new ArrayList<>();
        result.addAll(crgmwcca.getRole(this.team).stream().map(t -> t.identifier()).toList());

        return result;
    }

    @Override
    public void clear() {
        this.init();
    }

    public void setTeamAndSync(int roleType) {
        this.setTeam(roleType);
        this.sync();
    }

    public void setSelected(boolean b) {
        this.selected = b;
    }

    public void setSelectedAndSync(boolean b) {
        setSelected(b);
        sync();
    }
}