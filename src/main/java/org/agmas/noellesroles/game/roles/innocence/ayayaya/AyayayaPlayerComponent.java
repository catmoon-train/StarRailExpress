package org.agmas.noellesroles.game.roles.innocence.ayayaya;

import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;

import java.util.UUID;

/**
 * 射命丸文组件
 *
 * 功能：
 * - 存储传递盒的交互状态
 * - 管理双方的物品交换
 * - 同步传递数据到客户端
 */
public class AyayayaPlayerComponent implements RoleComponent {
    @Override
    public Player getPlayer() {
        return player;
    }
    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<AyayayaPlayerComponent> KEY = ModComponents.AYAYAYA;
    
    private final Player player;
    
    // 当前正在传递的目标玩家 UUID
    public UUID deliveryTarget = null;
    
    // 射命丸文放入的物品
    public ItemStack putItem = ItemStack.EMPTY;
    
    // 目标玩家放入的物品
    public ItemStack targetItem = ItemStack.EMPTY;
    
    // 射命丸文是否确认交换
    public boolean senderConfirmed = false;
    
    // 目标是否确认交换
    public boolean targetConfirmed = false;
    
    // 目标玩家名字（用于显示）
    public String targetName = "";
    
    // 是否是接收方（true = 被射命丸文选中的目标，false = 射命丸文本人）
    public boolean isReceiver = false;
    
    public AyayayaPlayerComponent(Player player) {
        this.player = player;
    }
    
    /**
     * 重置组件状态
     */
    @Override
    public void init() {
        this.deliveryTarget = null;
        this.putItem = ItemStack.EMPTY;
        this.targetItem = ItemStack.EMPTY;
        this.senderConfirmed = false;
        this.targetConfirmed = false;
        this.targetName = "";
        this.isReceiver = false;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }
    
    /**
     * 开始传递（射命丸文发起）
     * 
     * @param targetUuid 目标玩家 UUID
     * @param targetPlayerName 目标玩家名字
     */
    public void startDelivery(UUID targetUuid, String targetPlayerName) {
        this.deliveryTarget = targetUuid;
        this.targetName = targetPlayerName;
        this.isReceiver = false;
        this.putItem = ItemStack.EMPTY;
        this.targetItem = ItemStack.EMPTY;
        this.senderConfirmed = false;
        this.targetConfirmed = false;
        this.sync();
    }
    
    /**
     * 接收传递请求（目标玩家）
     * 
     * @param senderUuid 射命丸文 UUID
     * @param senderName 射命丸文名字
     */
    public void receiveDelivery(UUID senderUuid, String senderName) {
        this.deliveryTarget = senderUuid;
        this.targetName = senderName;
        this.isReceiver = true;
        this.putItem = ItemStack.EMPTY;
        this.targetItem = ItemStack.EMPTY;
        this.senderConfirmed = false;
        this.targetConfirmed = false;
        this.sync();
    }
    
    /**
     * 设置物品
     * 
     * @param item 要放入的物品
     * @param issender 是否是射命丸文放入的
     */
    public void setItem(ItemStack item, boolean issender) {
        if (issender) {
            this.putItem = item.copy();
            this.senderConfirmed = false;
        } else {
            this.targetItem = item.copy();
            this.targetConfirmed = false;
        }
        this.sync();
    }
    
    /**
     * 确认交换
     * 
     * @param issender 是否是射命丸文确认的
     */
    public void confirm(boolean issender) {
        if (!(player instanceof ServerPlayer))return;
        ConfigWorldComponent.onPlayerUsedSkill( (ServerPlayer) player);
        if (issender) {
            this.senderConfirmed = true;
        } else {
            this.targetConfirmed = true;
        }
        this.sync();
    }
    
    /**
     * 取消确认
     * 
     * @param issender 是否是射命丸文取消的
     */
    public void unconfirm(boolean issender) {
        if (issender) {
            this.senderConfirmed = false;
        } else {
            this.targetConfirmed = false;
        }
        this.sync();
    }
    
    /**
     * 检查是否双方都确认
     */
    public boolean isBothConfirmed() {
        return senderConfirmed && targetConfirmed;
    }
    
    /**
     * 检查传递是否激活
     */
    public boolean isDeliveryActive() {
        return deliveryTarget != null;
    }
    
    public void sync() {
        ModComponents.AYAYAYA.sync(this.player);
    }
    
    // ==================== NBT 序列化 ====================
    
    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (deliveryTarget != null) {
            tag.putUUID("deliveryTarget", deliveryTarget);
        }
        
        if (!putItem.isEmpty()) {
            CompoundTag senderItemTag = new CompoundTag();
            putItem.save(registryLookup, senderItemTag);
            tag.put("senderItem", senderItemTag);
        }
        
        if (!targetItem.isEmpty()) {
            CompoundTag targetItemTag = new CompoundTag();
            targetItem.save(registryLookup, targetItemTag);
            tag.put("targetItem", targetItemTag);
        }
        
        tag.putBoolean("senderConfirmed", senderConfirmed);
        tag.putBoolean("targetConfirmed", targetConfirmed);
        tag.putString("targetName", targetName);
        tag.putBoolean("isReceiver", isReceiver);
    }
    
    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.deliveryTarget = tag.contains("deliveryTarget") ? tag.getUUID("deliveryTarget") : null;
        
        if (tag.contains("senderItem")) {
            this.putItem = ItemStack.parseOptional(registryLookup, tag.getCompound("senderItem"));
        } else {
            this.putItem = ItemStack.EMPTY;
        }
        
        if (tag.contains("targetItem")) {
            this.targetItem = ItemStack.parseOptional(registryLookup, tag.getCompound("targetItem"));
        } else {
            this.targetItem = ItemStack.EMPTY;
        }
        
        this.senderConfirmed = tag.getBoolean("senderConfirmed");
        this.targetConfirmed = tag.getBoolean("targetConfirmed");
        this.targetName = tag.getString("targetName");
        this.isReceiver = tag.getBoolean("isReceiver");
    }
    
    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}