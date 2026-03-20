package org.agmas.noellesroles.component;

import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import io.wifi.starrailexpress.api.RoleComponent;

import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 邮差组件
 *
 * 功能：
 * - 存储传递盒的交互状态
 * - 管理双方的物品交换
 * - 同步传递数据到客户端
 */
public class PostmanPlayerComponent implements RoleComponent {
    @Override
    public Player getPlayer() {
        return player;
    }
    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<PostmanPlayerComponent> KEY = ModComponents.POSTMAN;
    
    private final Player player;
    
    // 当前正在传递的目标玩家 UUID
    public UUID deliveryTarget = null;
    
    // 邮差放入的物品
    public ItemStack postmanItem = ItemStack.EMPTY;
    
    // 目标玩家放入的物品
    public ItemStack targetItem = ItemStack.EMPTY;
    
    // 邮差是否确认交换
    public boolean postmanConfirmed = false;
    
    // 目标是否确认交换
    public boolean targetConfirmed = false;
    
    // 目标玩家名字（用于显示）
    public String targetName = "";
    
    // 是否是接收方（true = 被邮差选中的目标，false = 邮差本人）
    public boolean isReceiver = false;
    
    public PostmanPlayerComponent(Player player) {
        this.player = player;
    }
    
    /**
     * 重置组件状态
     */
    @Override
    public void init() {
        this.deliveryTarget = null;
        this.postmanItem = ItemStack.EMPTY;
        this.targetItem = ItemStack.EMPTY;
        this.postmanConfirmed = false;
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
     * 开始传递（邮差发起）
     * 
     * @param targetUuid 目标玩家 UUID
     * @param targetPlayerName 目标玩家名字
     */
    public void startDelivery(UUID targetUuid, String targetPlayerName) {
        this.deliveryTarget = targetUuid;
        this.targetName = targetPlayerName;
        this.isReceiver = false;
        this.postmanItem = ItemStack.EMPTY;
        this.targetItem = ItemStack.EMPTY;
        this.postmanConfirmed = false;
        this.targetConfirmed = false;
        this.sync();
    }
    
    /**
     * 接收传递请求（目标玩家）
     * 
     * @param postmanUuid 邮差 UUID
     * @param postmanName 邮差名字
     */
    public void receiveDelivery(UUID postmanUuid, String postmanName) {
        this.deliveryTarget = postmanUuid;
        this.targetName = postmanName;
        this.isReceiver = true;
        this.postmanItem = ItemStack.EMPTY;
        this.targetItem = ItemStack.EMPTY;
        this.postmanConfirmed = false;
        this.targetConfirmed = false;
        this.sync();
    }
    
    /**
     * 设置物品
     * 
     * @param item 要放入的物品
     * @param isPostman 是否是邮差放入的
     */
    public void setItem(ItemStack item, boolean isPostman) {
        if (isPostman) {
            this.postmanItem = item.copy();
            this.postmanConfirmed = false;
        } else {
            this.targetItem = item.copy();
            this.targetConfirmed = false;
        }
        this.sync();
    }
    
    /**
     * 确认交换
     * 
     * @param isPostman 是否是邮差确认的
     */
    public void confirm(boolean isPostman) {
        if (isPostman) {
            this.postmanConfirmed = true;
        } else {
            this.targetConfirmed = true;
        }
        this.sync();
    }
    
    /**
     * 取消确认
     * 
     * @param isPostman 是否是邮差取消的
     */
    public void unconfirm(boolean isPostman) {
        if (isPostman) {
            this.postmanConfirmed = false;
        } else {
            this.targetConfirmed = false;
        }
        this.sync();
    }
    
    /**
     * 检查是否双方都确认
     */
    public boolean isBothConfirmed() {
        return postmanConfirmed && targetConfirmed;
    }
    
    /**
     * 检查传递是否激活
     */
    public boolean isDeliveryActive() {
        return deliveryTarget != null;
    }
    
    public void sync() {
        ModComponents.POSTMAN.sync(this.player);
    }
    
    // ==================== NBT 序列化 ====================
    
    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (deliveryTarget != null) {
            tag.putUUID("deliveryTarget", deliveryTarget);
        }
        
        if (!postmanItem.isEmpty()) {
            CompoundTag postmanItemTag = new CompoundTag();
            postmanItem.save(registryLookup, postmanItemTag);
            tag.put("postmanItem", postmanItemTag);
        }
        
        if (!targetItem.isEmpty()) {
            CompoundTag targetItemTag = new CompoundTag();
            targetItem.save(registryLookup, targetItemTag);
            tag.put("targetItem", targetItemTag);
        }
        
        tag.putBoolean("postmanConfirmed", postmanConfirmed);
        tag.putBoolean("targetConfirmed", targetConfirmed);
        tag.putString("targetName", targetName);
        tag.putBoolean("isReceiver", isReceiver);
    }
    
    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.deliveryTarget = tag.contains("deliveryTarget") ? tag.getUUID("deliveryTarget") : null;
        
        if (tag.contains("postmanItem")) {
            this.postmanItem = ItemStack.parseOptional(registryLookup, tag.getCompound("postmanItem"));
        } else {
            this.postmanItem = ItemStack.EMPTY;
        }
        
        if (tag.contains("targetItem")) {
            this.targetItem = ItemStack.parseOptional(registryLookup, tag.getCompound("targetItem"));
        } else {
            this.targetItem = ItemStack.EMPTY;
        }
        
        this.postmanConfirmed = tag.getBoolean("postmanConfirmed");
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