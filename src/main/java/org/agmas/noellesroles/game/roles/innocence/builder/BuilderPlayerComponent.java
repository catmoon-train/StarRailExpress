package org.agmas.noellesroles.game.roles.innocence.builder;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.packet.BuilderRemoveWallS2CPacket;
import org.agmas.noellesroles.packet.BuilderWallS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.*;

/**
 * 建筑师组件
 * 
 * 管理建造师技能：
 * - 建造模式（默认）：按下技能键建造一堵客户端墙，20秒后消失，90秒冷却，开局120秒冷却
 * - 拆除模式：按下技能键拆除自己的墙，无冷却
 * - 蹲下按技能键切换模式
 */
public class BuilderPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<BuilderPlayerComponent> KEY = ModComponents.BUILDER;

    // ==================== 常量定义 ====================

    /** 开局冷却时间 */
    public static final int INITIAL_COOLDOWN = 2400;

    /** 建造技能冷却时间（90秒 = 1800 tick） */
    public static final int BUILD_COOLDOWN = 1800;

    /** 墙存在时间（20秒 = 400 tick） */
    public static final int WALL_DURATION = 400;

    /** 墙长度（格） */
    public static final int WALL_LENGTH = 4;

    /** 墙高度（格） */
    public static final int WALL_HEIGHT = 3;

    /** 墙厚度（格） */
    public static final int WALL_THICKNESS = 1;

    // ==================== 模式枚举 ====================

    public enum BuildMode {
        BUILD, // 建造模式
        DEMOLISH // 拆除模式
    }

    // ==================== 状态变量 ====================

    private final Player player;

    /** 建造技能冷却时间（tick） */
    public int cooldown = 0;

    /** 当前模式 */
    public BuildMode currentMode = BuildMode.BUILD;

    /** 已建造的墙数据：wallId -> WallData */
    private final Map<UUID, WallData> builtWalls = new LinkedHashMap<>();

    // ==================== 构造函数 ====================

    public BuilderPlayerComponent(Player player) {
        this.player = player;
    }

    // ==================== 初始化/清理 ====================

    @Override
    public void init() {
        this.cooldown = INITIAL_COOLDOWN;
        this.currentMode = BuildMode.BUILD;
        // 通知客户端清除所有墙
        this.builtWalls.clear();
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    // ==================== 技能逻辑 ====================

    /**
     * 切换模式
     */
    public void switchMode() {
        if (currentMode == BuildMode.BUILD) {
            currentMode = BuildMode.DEMOLISH;
        } else {
            currentMode = BuildMode.BUILD;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            Component modeName = currentMode == BuildMode.BUILD
                    ? Component.translatable("hud.noellesroles.builder.mode.build")
                    : Component.translatable("hud.noellesroles.builder.mode.demolish");
            serverPlayer.displayClientMessage(
                    Component.translatable("hud.noellesroles.builder.mode_switched", modeName)
                            .withStyle(ChatFormatting.YELLOW),
                    true);
        }
        this.sync();
    }

    /**
     * 使用建造技能
     */
    public boolean useBuildAbility() {
        if (cooldown > 0) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(
                        Component
                                .translatable("hud.noellesroles.builder.cooldown",
                                        String.format("%.1f", cooldown / 20.0f))
                                .withStyle(ChatFormatting.RED),
                        true);
            }
            return false;
        }

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.BUILDER)) {
            return false;
        }

        // 计算墙的位置
        List<BlockPos> wallPositions = calculateWallPositions();
        if (wallPositions.isEmpty()) {
            return false;
        }

        // 分离第三行（顶部y+2）为蜘蛛网位置
        int minY = wallPositions.stream().mapToInt(BlockPos::getY).min().orElse(0);
        int cobwebY = minY + 2;
        List<BlockPos> brickPositions = new ArrayList<>();
        List<BlockPos> cobwebPositions = new ArrayList<>();
        for (BlockPos pos : wallPositions) {
            if (pos.getY() == cobwebY) {
                cobwebPositions.add(pos);
            } else {
                brickPositions.add(pos);
            }
        }

        // 创建墙数据
        UUID wallId = UUID.randomUUID();
        WallData wallData = new WallData(wallId, wallPositions, WALL_DURATION);
        builtWalls.put(wallId, wallData);

        // 注册到全局墙位置表（用于弹射物碰撞检测）
        BuilderWallPositions.addWall(new HashSet<>(wallPositions));

        // 设置冷却
        this.cooldown = BUILD_COOLDOWN;

        // 发送S2C包给所有玩家
        sendWallToAllPlayers(wallId, brickPositions, cobwebPositions);

        // 播放方块放置的声音
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.serverLevel().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                    SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("hud.noellesroles.builder.wall_built")
                            .withStyle(ChatFormatting.GREEN),
                    true);
        }

        this.sync();
        return true;
    }

    /**
     * 使用拆除技能
     */
    public boolean useDemolishAbility() {
        if (builtWalls.isEmpty()) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(
                        Component.translatable("hud.noellesroles.builder.no_walls")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
            return false;
        }

        // 拆除最近建造的墙
        UUID lastWallId = null;
        for (UUID id : builtWalls.keySet()) {
            lastWallId = id;
        }

        if (lastWallId != null) {
            WallData removed = builtWalls.remove(lastWallId);
            if (removed != null) {
                // 从全局墙位置表中移除
                BuilderWallPositions.removeWall(new HashSet<>(removed.positions));
            }
            sendRemoveWallToAllPlayers(lastWallId);

            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(
                        Component.translatable("hud.noellesroles.builder.wall_demolished")
                                .withStyle(ChatFormatting.GREEN),
                        true);
            }
        }

        this.sync();
        return true;
    }

    /**
     * 计算墙的方块位置
     * 以玩家脚下方块为基准，按视角方向垂直建造
     */
    private List<BlockPos> calculateWallPositions() {
        List<BlockPos> positions = new ArrayList<>();

        // 获取玩家的水平朝向（最近的4个方向之一）
        Direction facing = player.getDirection();

        // 玩家脚下方块位置
        BlockPos basePos = player.blockPosition();

        // 计算墙的法线方向（与朝向垂直）
        Direction wallNormal = facing;

        // 计算墙的延伸方向（与朝向和法线都垂直的方向，即水平面上的垂直方向）
        Direction wallExtend = facing.getCounterClockWise();

        // 墙的中心在玩家位置
        // 墙沿wallExtend方向延伸WALL_LENGTH格，沿Y轴延伸WALL_HEIGHT格，沿wallNormal方向延伸WALL_THICKNESS格

        for (int thickness = 0; thickness < WALL_THICKNESS; thickness++) {
            for (int length = 0; length < WALL_LENGTH; length++) {
                for (int height = 0; height < WALL_HEIGHT; height++) {
                    int halfLength = WALL_LENGTH / 2;
                    BlockPos pos = basePos
                            .relative(wallExtend, length - halfLength)
                            .above(height)
                            .relative(wallNormal, thickness);
                    positions.add(pos);
                }
            }
        }

        return positions;
    }

    /**
     * 发送墙数据给所有玩家
     */
    private void sendWallToAllPlayers(UUID wallId, List<BlockPos> brickPositions, List<BlockPos> cobwebPositions) {
        if (!(player.level() instanceof ServerLevel serverLevel))
            return;

        BuilderWallS2CPacket packet = new BuilderWallS2CPacket(wallId, brickPositions, cobwebPositions, WALL_DURATION);
        for (ServerPlayer serverPlayer : serverLevel.players()) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(serverPlayer, packet);
        }
    }

    /**
     * 发送拆墙数据给所有玩家
     */
    private void sendRemoveWallToAllPlayers(UUID wallId) {
        if (!(player.level() instanceof ServerLevel serverLevel))
            return;

        BuilderRemoveWallS2CPacket packet = new BuilderRemoveWallS2CPacket(wallId);
        for (ServerPlayer serverPlayer : serverLevel.players()) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(serverPlayer, packet);
        }
    }

    /**
     * 游戏结束时清除所有墙
     */
    public void clearAllWalls() {
        for (Map.Entry<UUID, WallData> entry : builtWalls.entrySet()) {
            BuilderWallPositions.removeWall(new HashSet<>(entry.getValue().positions));
            sendRemoveWallToAllPlayers(entry.getKey());
        }
        builtWalls.clear();
    }

    // ==================== Getter ====================

    public BuildMode getCurrentMode() {
        return currentMode;
    }

    public float getCooldownSeconds() {
        return cooldown / 20.0f;
    }

    public boolean isBuildMode() {
        return currentMode == BuildMode.BUILD;
    }

    // ==================== Tick 处理 ====================

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void serverTick() {
        // 减少冷却时间
        if (this.cooldown > 0) {
            this.cooldown--;
            if (this.cooldown % 200 == 0 || this.cooldown == 0) {
                this.sync();
            }
        }

        // 处理墙过期
        Iterator<Map.Entry<UUID, WallData>> iterator = builtWalls.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, WallData> entry = iterator.next();
            WallData wall = entry.getValue();
            wall.remainingTicks--;
            if (wall.remainingTicks <= 0) {
                // 墙过期，从全局表中移除并通知所有客户端
                BuilderWallPositions.removeWall(new HashSet<>(wall.positions));
                sendRemoveWallToAllPlayers(entry.getKey());
                iterator.remove();
            }
        }
    }

    // ==================== 同步 ====================

    public void sync() {
        ModComponents.BUILDER.sync(this.player);
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("cooldown", this.cooldown);
        tag.putInt("currentMode", this.currentMode.ordinal());
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.cooldown = tag.contains("cooldown") ? tag.getInt("cooldown") : 0;
        this.currentMode = tag.contains("currentMode") ? BuildMode.values()[tag.getInt("currentMode")]
                : BuildMode.BUILD;
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    // ==================== 墙数据类 ====================

    public static class WallData {
        public final UUID wallId;
        public final List<BlockPos> positions;
        public int remainingTicks;

        public WallData(UUID wallId, List<BlockPos> positions, int durationTicks) {
            this.wallId = wallId;
            this.positions = positions;
            this.remainingTicks = durationTicks;
        }
    }

    @Override
    public void clientTick() {
        if (this.cooldown > 1) {
            this.cooldown--;
        }
    }
}
