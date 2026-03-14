package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 更好的义警组件
 *
 * 技能：开局自带一颗手榴弹
 */
public class BestVigilantePlayerComponent implements RoleComponent, ServerTickingComponent {

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<BestVigilantePlayerComponent> KEY = ModComponents.BEST_VIGILANTE;

    // ==================== 状态变量 ====================

    private final Player player;

    /** 是否已给予开局手榴弹 */
    public boolean givenGrenade = false;

    @Override
    public Player getPlayer() {
        return player;
    }

    /**
     * 构造函数
     */
    public BestVigilantePlayerComponent(Player player) {
        this.player = player;
    }

    /**
     * 重置组件状态
     */
    @Override
    public void init() {
        this.givenGrenade = false;
        this.sync();
    }

    @Override
    public void clear() {
        clearAll();
    }

    /**
     * 清除所有状态
     */
    public void clearAll() {
        this.givenGrenade = false;
        this.sync();
    }

    /**
     * 检查是否是活跃的更好的义警
     */
    public boolean isActiveBestVigilante() {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        return gameWorld.isRole(player, ModRoles.BEST_VIGILANTE);
    }

    /**
     * 给予开局手榴弹
     */
    public void giveStartingGrenade() {
        if (givenGrenade)
            return;
        if (!(player instanceof ServerPlayer))
            return;

        // 给予手榴弹
        player.getInventory().add(new ItemStack(TMMItems.GRENADE));

        givenGrenade = true;
        this.sync();
    }

    /**
     * 同步到客户端
     */
    public void sync() {
        ModComponents.BEST_VIGILANTE.sync(this.player);
    }

    // ==================== Tick 处理 ====================

    @Override
    public void serverTick() {
        // 只有活跃的更好的义警才需要检测
        if (!isActiveBestVigilante())
            return;
        if (!GameUtils.isPlayerAliveAndSurvival(player))
            return;

        // 如果已经给予过手榴弹，不需要再处理
        if (givenGrenade)
            return;

        // 在游戏开始时给予手榴弹
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld.isRunning() && !givenGrenade) {
            giveStartingGrenade();
        }
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("givenGrenade", this.givenGrenade);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.givenGrenade = tag.contains("givenGrenade") && tag.getBoolean("givenGrenade");
    }
}
