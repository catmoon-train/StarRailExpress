package org.agmas.noellesroles.game.roles.Innocent.pilot;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 飞行员组件
 * 
 * 管理喷气背包：
 * - 技能键可以脱下喷气背包
 */
public class PilotPlayerComponent implements RoleComponent, ServerTickingComponent {

    /** 组件键 */
    public static final ComponentKey<PilotPlayerComponent> KEY = ModComponents.PILOT;

    private final Player player;

    public PilotPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    /**
     * 重置组件状态
     */
    @Override
    public void init() {
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    /**
     * 脱下喷气背包
     * 
     * @return 是否成功脱下
     */
    public boolean removeJetpack() {
        // 验证是飞行员
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.PILOT)) {
            return false;
        }

        // 检查玩家是否存活
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }

        // 检查身上是否有喷气背包
        ItemStack chestplate = player.getInventory().getArmor(2); // 胸甲槽位
        if (!chestplate.is(ModItems.JETPACK)) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.pilot.no_jetpack"),
                        true);
            }
            return false;
        }

        // 脱下喷气背包并丢出
        ItemStack jetpack = chestplate.copy();
        player.getInventory().armor.set(2, ItemStack.EMPTY);
        
        // 将喷气背包放入玩家背包或丢到地上
        if (!player.getInventory().add(jetpack)) {
            // 背包满了就丢在地上
            player.drop(jetpack, true);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.pilot.jetpack_removed"),
                    true);
        }

        this.sync();
        return true;
    }

    /**
     * 同步到客户端
     */
    public void sync() {
        ModComponents.PILOT.sync(this.player);
    }

    // ==================== Tick 处理 ====================

    @Override
    public void serverTick() {
        // 飞行员不需要每tick处理，主要逻辑在喷气背包的tick处理中
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 飞行员不需要同步特殊状态
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 飞行员不需要读取特殊状态
    }
}
