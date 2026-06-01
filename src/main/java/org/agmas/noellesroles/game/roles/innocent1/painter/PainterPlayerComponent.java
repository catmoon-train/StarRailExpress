package org.agmas.noellesroles.game.roles.innocent.painter;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 画家组件
 * 
 * 画家是平民阵营角色，拥有以下技能：
 * 技能一：绘画灵感 - 触发特定场景时获得画板
 * 技能二：求索 - 每4分钟自动获得画板
 * 技能三：挚友 - 与作家同时在场时互相获得画板
 */
public class PainterPlayerComponent implements RoleComponent, ServerTickingComponent {
    
    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<PainterPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "painter"),
            PainterPlayerComponent.class);
    
    // ==================== 常量定义 ====================
    
    /** 求索技能：每4分钟给予画板（240秒 = 4800 tick） */
    public static final int SEEKING_INTERVAL = 240 * 20; // 240秒
    
    /** 绘画灵感：坐着时间阈值（40秒 = 800 tick） */
    public static final int SITTING_TIME_THRESHOLD = 40 * 20; // 40秒
    
    // ==================== 状态变量 ====================
    
    private final Player player;
    
    /** 是否已激活 */
    public boolean isActive = false;
    
    // 技能一：绘画灵感 - 各场景触发状态
    /** 是否已触发照片灵感（捡起摄影师照片） */
    public boolean photoInspirationTriggered = false;
    
    /** 是否已触发手枪灵感（捡起左轮/巡警手枪） */
    public boolean gunInspirationTriggered = false;
    
    /** 是否已触发静坐灵感（坐着40秒） */
    public boolean sittingInspirationTriggered = false;
    
    /** 当前坐着时间（tick） */
    public int sittingTime = 0;
    
    /** 上次检测时玩家是否在坐着 */
    private boolean wasSneaking = false;
    
    // 技能二：求索
    /** 求索计时器 */
    public int seekingTimer = 0;
    
    // 技能三：挚友
    /** 上次检测时作家是否在场 */
    private boolean writerWasPresent = false;
    
    /**
     * 构造函数
     */
    public PainterPlayerComponent(Player player) {
        this.player = player;
    }

    /**
     * 获取关联的玩家
     */
    @Override
    public Player getPlayer() {
        return this.player;
    }
    
    /**
     * 重置组件状态
     */
    @Override
    public void init() {
        this.isActive = true;
        this.photoInspirationTriggered = false;
        this.gunInspirationTriggered = false;
        this.sittingInspirationTriggered = false;
        this.sittingTime = 0;
        this.seekingTimer = 0;
        this.wasSneaking = false;
        this.writerWasPresent = false;
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
        this.isActive = false;
        this.photoInspirationTriggered = false;
        this.gunInspirationTriggered = false;
        this.sittingInspirationTriggered = false;
        this.sittingTime = 0;
        this.seekingTimer = 0;
        this.wasSneaking = false;
        this.writerWasPresent = false;
        this.sync();
    }
    
    /**
     * 检查是否为激活的画家角色
     */
    public boolean isActivePainter() {
        if (!isActive || player == null || player.level().isClientSide())
            return false;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        return gameWorld.isRole(player, ModRoles.PAINTER);
    }
    
    /**
     * 给予画板物品（普通来源）
     */
    public void giveDrawingBoard() {
        giveDrawingBoard(false);
    }

    /**
     * 给予画板物品
     * @param fromFriend 是否来自挚友技能
     */
    public void giveDrawingBoard(boolean fromFriend) {
        if (player != null && !player.level().isClientSide()) {
            MCItemsUtils.insertStackInFreeSlot(player, TMMItems.DRAWING_BOARD.getDefaultInstance());
            if (player instanceof ServerPlayer sp) {
                String key = fromFriend
                        ? "message.noellesroles.painter.friend_drawing_board_received"
                        : "message.noellesroles.painter.drawing_board_received";
                sp.displayClientMessage(
                        Component.translatable(key)
                                .withStyle(ChatFormatting.GOLD),
                        true);
            }
        }
    }
    
    /**
     * 技能一触发检查：捡起物品时调用
     * 
     * @param itemId 物品的 ResourceLocation 字符串
     */
    public void onItemPickup(String itemId) {
        if (!isActivePainter())
            return;
        
        // 检查是否是照片
        if (!photoInspirationTriggered) {
            if (itemId.contains("stacked_photographs") || itemId.contains("photograph")) {
                photoInspirationTriggered = true;
                giveDrawingBoard();
                Noellesroles.LOGGER.info("画家 {} 捡起照片，触发绘画灵感", player.getName().getString());
                sync();
            }
        }
        
        // 检查是否是左轮手枪或巡警手枪
        if (!gunInspirationTriggered) {
            if (itemId.contains("revolver") || itemId.contains("derringer") || itemId.contains("patroller_revolver")) {
                gunInspirationTriggered = true;
                giveDrawingBoard();
                Noellesroles.LOGGER.info("画家 {} 捡起手枪，触发绘画灵感", player.getName().getString());
                sync();
            }
        }
    }
    
    /**
     * 同步到客户端
     */
    public void sync() {
        if (player != null && !player.level().isClientSide()) {
            KEY.sync(this.player);
        }
    }
    
    /**
     * 检查作家是否在场
     */
    private boolean isWriterPresent() {
        if (player == null || player.level().isClientSide())
            return false;
        
        ServerLevel level = (ServerLevel) player.level();
        for (Player p : level.players()) {
            if (p == player)
                continue;
            if (!GameUtils.isPlayerAliveAndSurvival(p))
                continue;
            if (SREGameWorldComponent.KEY.get(p.level()).isRole(p, ModRoles.WRITER)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检查画家是否在场
     */
    private boolean isPainterPresent() {
        if (player == null || player.level().isClientSide())
            return false;
        
        ServerLevel level = (ServerLevel) player.level();
        for (Player p : level.players()) {
            if (p == player)
                continue;
            if (!GameUtils.isPlayerAliveAndSurvival(p))
                continue;
            if (SREGameWorldComponent.KEY.get(p.level()).isRole(p, ModRoles.PAINTER)) {
                return true;
            }
        }
        return false;
    }
    
    // ==================== Tick 处理 ====================
    
    @Override
    public void serverTick() {
        if (!isActivePainter())
            return;
        
        // 检查技能一：坐着灵感
        checkSittingInspiration();
        
        // 检查技能二：求索
        checkSeekingSkill();
        
        // 检查技能三：挚友
        checkFriendSkill();
    }
    
    /**
     * 检查坐着灵感（技能一的一部分）
     */
    private void checkSittingInspiration() {
        if (sittingInspirationTriggered)
            return;
        
        // 检测坐在椅子上（参考座椅任务：getVehicle() instanceof SeatEntity）
        boolean isSitting = player.getVehicle() != null 
                && player.getVehicle() instanceof io.wifi.starrailexpress.content.block.entity.SeatEntity;
        
        if (isSitting) {
            // 玩家正在坐着
            sittingTime++; 
            
            // 每秒同步一次
            if (sittingTime % 20 == 0 && sittingTime <= SITTING_TIME_THRESHOLD) {
                // 可选：发送进度提示
                // int remaining = (SITTING_TIME_THRESHOLD - sittingTime) / 20;
                // Noellesroles.LOGGER.info("画家 {} 静坐中，还需 {} 秒", player.getName().getString(), remaining);
            }
            
            if (sittingTime >= SITTING_TIME_THRESHOLD) {
                // 触发坐着灵感
                sittingInspirationTriggered = true;
                giveDrawingBoard();
                Noellesroles.LOGGER.info("画家 {} 静坐40秒，触发绘画灵感", player.getName().getString());
                sync();
            }
        } else {
            // 玩家不再坐着，重置计时器
            if (sittingTime > 0) {
                sittingTime = 0;
            }
        }
    }
    
    /**
     * 检查求索技能（技能二）
     */
    private void checkSeekingSkill() {
        seekingTimer++;
        
        if (seekingTimer >= SEEKING_INTERVAL) {
            seekingTimer = 0;
            giveDrawingBoard();
            Noellesroles.LOGGER.info("画家 {} 求索技能触发，获得画板", player.getName().getString());
            sync();
        }
    }
    
    /**
     * 检查挚友技能（技能三）
     */
    private void checkFriendSkill() {
        boolean writerPresent = isWriterPresent();
        
        if (writerPresent) {
            // 作家在场，检查是否有变化（作家刚到达）
            if (!writerWasPresent) {
                // 作家刚到达，给予双方画板
                giveDrawingBoardToWriters();
                Noellesroles.LOGGER.info("画家 {} 发现作家在场，触发挚友技能", player.getName().getString());
            }
        }
        
        writerWasPresent = writerPresent;
    }
    
    /**
     * 给予所有在场画家和作家画板
     */
    private void giveDrawingBoardToWriters() {
        if (player == null || player.level().isClientSide())
            return;
        
        ServerLevel level = (ServerLevel) player.level();
        
        for (Player p : level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(p))
                continue;
            
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(p.level());
            
            // 给予画家画板（挚友技能）
            if (gameWorld.isRole(p, ModRoles.PAINTER)) {
                PainterPlayerComponent comp = KEY.get(p);
                if (comp != null) {
                    comp.giveDrawingBoard(true);
                }
            }
            
            // 给予作家画板（作家需要单独处理，因为作家没有组件）
            if (gameWorld.isRole(p, ModRoles.WRITER)) {
                MCItemsUtils.insertStackInFreeSlot(p, TMMItems.DRAWING_BOARD.getDefaultInstance());
                if (p instanceof ServerPlayer sp) {
                    sp.displayClientMessage(
                            Component.translatable("message.noellesroles.writer.painter_gave_drawing_board")
                                    .withStyle(ChatFormatting.GOLD),
                            true);
                }
            }
        }
    }
    
    // ==================== NBT 序列化 ====================
    
    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("isActive", this.isActive);
        tag.putBoolean("photoInspirationTriggered", this.photoInspirationTriggered);
        tag.putBoolean("gunInspirationTriggered", this.gunInspirationTriggered);
        tag.putBoolean("sittingInspirationTriggered", this.sittingInspirationTriggered);
        tag.putInt("sittingTime", this.sittingTime);
        tag.putInt("seekingTimer", this.seekingTimer);
    }
    
    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.isActive = tag.contains("isActive") && tag.getBoolean("isActive");
        this.photoInspirationTriggered = tag.contains("photoInspirationTriggered") && tag.getBoolean("photoInspirationTriggered");
        this.gunInspirationTriggered = tag.contains("gunInspirationTriggered") && tag.getBoolean("gunInspirationTriggered");
        this.sittingInspirationTriggered = tag.contains("sittingInspirationTriggered") && tag.getBoolean("sittingInspirationTriggered");
        this.sittingTime = tag.contains("sittingTime") ? tag.getInt("sittingTime") : 0;
        this.seekingTimer = tag.contains("seekingTimer") ? tag.getInt("seekingTimer") : 0;
    }
    
    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
    
    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
