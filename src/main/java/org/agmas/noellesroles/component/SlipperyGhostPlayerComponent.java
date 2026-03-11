package org.agmas.noellesroles.component;

import  org.agmas.noellesroles.role.ModRoles;
import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.cca.PlayerShopComponent;
import io.wifi.starrailexpress.game.GameFunctions;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import io.wifi.starrailexpress.api.RoleComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 滑头鬼玩家组件
 * 用于实现被动收入功能：每20秒获取50金币
 */
public class SlipperyGhostPlayerComponent implements RoleComponent, ServerTickingComponent {
    @Override
    public Player getPlayer() {
        return player;
    }
    // 被动收入间隔：20秒 = 400 ticks
    private static final int PASSIVE_INCOME_INTERVAL = 400;
    // 被动收入金额
    private static final int PASSIVE_INCOME_AMOUNT = 50;
    
    private final Player player;
    private int tickCounter = 0;
    
    // 物品使用冷却
    private int blankCartridgeCooldown = 0;  // 空包弹冷却：10秒 = 200 ticks
    private int blackoutCooldown = 0;        // 关灯冷却：2分钟 = 2400 ticks
    
    // 冷却时间常量
    private static final int BLANK_CARTRIDGE_COOLDOWN = 200;   // 10秒
    private static final int BLACKOUT_COOLDOWN = 2400;         // 2分钟
    
    public SlipperyGhostPlayerComponent(Player player) {
        this.player = player;
    }
    
    @Override
    public void serverTick() {
        // 检查玩家是否为滑头鬼
        GameWorldComponent gameWorld = GameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.SLIPPERY_GHOST)) {
            return;
        }
        
        // 检查游戏是否进行中
        if (!gameWorld.isRunning()) {
            return;
        }
        
        // 检查玩家是否存活
        if (!GameFunctions.isPlayerAliveAndSurvival(player)) {
            return;
        }
        
        // 计数器增加
        tickCounter++;
        
        // 每20秒给予金币
        if (tickCounter >= PASSIVE_INCOME_INTERVAL) {
            tickCounter = 0;
            
            // 获取商店组件并增加余额
            PlayerShopComponent shopComponent = PlayerShopComponent.KEY.get(player);
            shopComponent.addToBalance(PASSIVE_INCOME_AMOUNT);
        }
        
        // 更新物品冷却
        if (blankCartridgeCooldown > 0) {
            blankCartridgeCooldown--;
        }
        if (blackoutCooldown > 0) {
            blackoutCooldown--;
        }
    }
    
    /**
     * 重置组件
     */
    @Override
    public void reset() {
        this.tickCounter = 0;
        this.blankCartridgeCooldown = 0;
        this.blackoutCooldown = 0;
        sync();
    }

    @Override
    public void clear() {
        this.reset();
    }
    
    /**
     * 检查空包弹是否在冷却中
     */
    public boolean isBlankCartridgeOnCooldown() {
        return blankCartridgeCooldown > 0;
    }
    
    /**
     * 获取空包弹剩余冷却时间（秒）
     */
    public int getBlankCartridgeCooldownSeconds() {
        return (blankCartridgeCooldown + 19) / 20;
    }
    
    /**
     * 设置空包弹冷却
     */
    public void setBlankCartridgeCooldown() {
        this.blankCartridgeCooldown = BLANK_CARTRIDGE_COOLDOWN;
        sync();
    }
    
    /**
     * 检查关灯是否在冷却中
     */
    public boolean isBlackoutOnCooldown() {
        return blackoutCooldown > 0;
    }
    
    /**
     * 获取关灯剩余冷却时间（秒）
     */
    public int getBlackoutCooldownSeconds() {
        return (blackoutCooldown + 19) / 20;
    }
    
    /**
     * 设置关灯冷却
     */
    public void setBlackoutCooldown() {
        this.blackoutCooldown = BLACKOUT_COOLDOWN;
        sync();
    }
    
    /**
     * 同步到客户端
     */
    public void sync() {
        ModComponents.SLIPPERY_GHOST.sync(this.player);
    }
    
    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("TickCounter", tickCounter);
        tag.putInt("BlankCartridgeCooldown", blankCartridgeCooldown);
        tag.putInt("BlackoutCooldown", blackoutCooldown);
    }
    
    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.tickCounter = tag.getInt("TickCounter");
        this.blankCartridgeCooldown = tag.getInt("BlankCartridgeCooldown");
        this.blackoutCooldown = tag.getInt("BlackoutCooldown");
    }
}