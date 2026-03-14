package org.agmas.noellesroles.component;

import  org.agmas.noellesroles.role.ModRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import io.wifi.starrailexpress.api.RoleComponent;

/**
 * 退伍军人组件
 *
 * 功能：
 * - 追踪刀是否已使用
 * - 刀使用后（击杀一人）刀消失
 */
public class VeteranPlayerComponent implements RoleComponent {
    @Override
    public Player getPlayer() {
        return player;
    }
    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<VeteranPlayerComponent> KEY = ModComponents.VETERAN;
    
    private final Player player;
    
    // 是否已使用刀击杀
    public boolean knifeUsed = false;
    
    public VeteranPlayerComponent(Player player) {
        this.player = player;
    }
    
    /**
     * 重置组件状态
     */
    @Override
    public void reset() {
        this.knifeUsed = false;
        this.sync();
    }

    @Override
    public void clear() {
        this.reset();
    }
    
    /**
     * 检查玩家是否是活跃的退伍军人
     */
    public boolean isActiveVeteran() {
        if (player.level().isClientSide()) return false;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        return gameWorld.isRole(player, ModRoles.VETERAN);
    }
    
    /**
     * 标记刀已使用
     * 调用后刀会被移除
     */
    public void markKnifeUsed() {
        if (knifeUsed) return;
        
        // this.knifeUsed = true;
        
        // 发送消息给玩家
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.veteran.knife_used")
                    .withStyle(ChatFormatting.YELLOW),
                true
            );
        }
        
        this.sync();
    }
    
    /**
     * 检查刀是否还可以使用
     */
    public boolean canUseKnife() {
        return !knifeUsed;
    }
    
    public void sync() {
        ModComponents.VETERAN.sync(this.player);
    }
    
    // ==================== NBT 序列化 ====================
    
    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("knifeUsed", knifeUsed);
    }
    
    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.knifeUsed = tag.getBoolean("knifeUsed");
    }
}