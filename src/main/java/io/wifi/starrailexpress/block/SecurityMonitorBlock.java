package io.wifi.starrailexpress.block;

import com.mojang.serialization.MapCodec;
import io.wifi.starrailexpress.block_entity.SecurityMonitorBlockEntity;
import io.wifi.starrailexpress.network.PacketTracker;
import io.wifi.starrailexpress.network.SecurityCameraExitRequestPayload;
import io.wifi.starrailexpress.network.SecurityCameraModePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class SecurityMonitorBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    protected static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 12.0D, 16.0D);

    // 添加监控模式相关字段
    private static BlockPos currentCameraPos = null;
    private static BlockPos currentCameraOffset = null;

    private static BlockPos currentMonitorPos = null; // 当前监控控制台的位置
    private static boolean isInSecurityMode = false;
    public static float lastCameraYaw;
    public static float lastCameraPitch;
    public static float yawIncrease;
    public static float pitchIncrease;
    public static float currentYaw = 0.0f; // 记录当前视角的yaw偏移量
    // public static float currentPitch = 0.0f; // 记录当前视角的pitch偏移量

    public static boolean onPlayerRotated(double pitchAdd) {
        if (isInSecurityMode()) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null)
                return false;

            // float scale = 0.02f;

            // 累加视角偏移量
            // currentPitch = Mth.clamp(currentPitch + (float) ((pitchAdd - currentPitch) *
            // scale), -90, 90);

            // 不更新玩家实体朝向，只更新相机视角
            // 移除 player.turn() 调用以避免与相机视角冲突
            // player.turn((float) (yawAdd * scale), (float) (pitchAdd * scale));
            // player.yHeadRotO = player.yHeadRot;
            // player.xRotO = player.getXRot();

            return true;
        }
        return false;
    }

    private static boolean preventShiftTillNextKeyUp = false;

    public static void onInputUpdate(Input input) {
        // resets input
        if (isInSecurityMode()) {
            input.down = false;
            input.up = false;
            input.left = false;
            input.right = false;
            input.forwardImpulse = 0;
            input.leftImpulse = 0;
        }
        input.shiftKeyDown = false;
        input.jumping = false;
    }

    public static void modifyInputUpdate(Input instance, LocalPlayer player) {
        if (isInSecurityMode()) {
            onInputUpdate(instance);
            preventShiftTillNextKeyUp = true;
        } else if (preventShiftTillNextKeyUp) {
            if (!instance.shiftKeyDown) {
                preventShiftTillNextKeyUp = false;
            } else {
                instance.shiftKeyDown = false;
            }
        }
    }

    public static boolean onEarlyKeyPress(int key, int scanCode, int action, int modifiers) {
        if (!isInSecurityMode())
            return false;
        if (action != GLFW.GLFW_PRESS)
            return false;
        var options = Minecraft.getInstance().options;
        // ESC 键退出监控模式 - 发送退出请求到服务端
        if (key == 256) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                // 发送退出请求到服务端
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new SecurityCameraExitRequestPayload());
            }
            return true;
        } else if (options.keyInventory.matches(key, scanCode)) {
            return true;
        }
        if (options.keyJump.matches(key, scanCode)) {
            return true;
        }
        if (options.keyShift.matches(key, scanCode)) {
            return false;
        }
        return false;
    }

    public SecurityMonitorBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    private static final MapCodec<SecurityMonitorBlock> CODEC = simpleCodec(SecurityMonitorBlock::new);

    public static boolean setupCameraMod(Camera camera, BlockGetter level, Entity entity,
            boolean detached, boolean thirdPersonReverse, float partialTick) {

        if (!SecurityMonitorBlock.isInSecurityMode())
            return false;
        BlockPos cameraPos = SecurityMonitorBlock.getCurrentCameraPos();

        float targetXRot;
        // currentPitch = 0f;

        // 获取监控控制台的位置（用于获取监控方块朝向）
        // BlockPos monitorPos = getCurrentMonitorPos();

        if (level != null) {
            BlockState monitorState = level.getBlockState(cameraPos);
            if (monitorState.getBlock() instanceof CameraBlock) {
                Direction monitorFacing = monitorState.getValue(FACING);

                // 根据监控控制台方向计算基础旋转角度
                float baseYaw = getBaseYawFromDirection(monitorFacing);
                // 计算目标视角：基础角度 + 玩家调整的偏移量
                targetXRot = baseYaw;
                currentYaw = baseYaw;

            } else {
                // 如果无法获取监控方块，使用默认值
                targetXRot = currentYaw;
            }
        } else {
            // 如果无法获取世界或监控方块位置，则使用默认行为
            targetXRot = currentYaw;
        }

        camera.setRotation(targetXRot, 0);
        // 设置相机位置到摄像头位置
        Vec3 targetCameraPos = cameraPos.getCenter().add(0, -1.2, 0);

        camera.setPosition(targetCameraPos);

        lastCameraYaw = camera.getYRot();
        lastCameraPitch = camera.getXRot();

        yawIncrease = 0;
        pitchIncrease = 0;

        return true;
    }

    /**
     * 根据方向获取基础偏航角
     * 
     * @param direction 摄像头方向
     * @return 对应的基础偏航角
     */
    private static float getBaseYawFromDirection(Direction direction) {
        switch (direction) {
            case SOUTH: // -Z方向
                return 180.0f;
            case NORTH: // +Z方向
                return 0.0f;
            case EAST: // -X方向
                return 90.0f;
            case WEST: // +X方向
                return -90.0f;
            default:
                return 0.0f;
        }
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SecurityMonitorBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (world.isClientSide) {
            return InteractionResult.SUCCESS; // 客户端直接返回，主要逻辑在服务端
        }

        // 检查玩家是否按下了Shift键
        if (player.isShiftKeyDown()) {
            // 退出监控模式
            exitSecurityMode((net.minecraft.server.level.ServerPlayer) player);
            return InteractionResult.SUCCESS;
        } else {
            // 检查监控器上是否有摄像头位置数据
            SecurityMonitorBlockEntity monitorEntity = (SecurityMonitorBlockEntity) world.getBlockEntity(pos);
            if (monitorEntity != null) {
                List<BlockPos> cameraPositions = monitorEntity.getCameraPositions();
                if (cameraPositions.isEmpty()) {
                    player.displayClientMessage(Component.literal("此监控器未连接任何摄像头").withStyle(ChatFormatting.RED), true);
                    return InteractionResult.SUCCESS;
                } else {
                    // 记录当前监控控制台的位置
                    currentMonitorPos = pos;
                    // 进入监控模式，循环切换摄像头
                    cycleToNextCamera(player, cameraPositions);
                    enterSecurityMode((net.minecraft.server.level.ServerPlayer) player);
                    return InteractionResult.SUCCESS;
                }
            } else {
                player.displayClientMessage(Component.literal("监控器数据错误").withStyle(ChatFormatting.RED), true);
                return InteractionResult.SUCCESS;
            }
        }
    }

    private static void cycleToNextCamera(Player player, List<BlockPos> cameraPositions) {
        if (cameraPositions.isEmpty())
            return;

        int currentIndex = -1;
        if (currentCameraOffset != null) {
            currentIndex = cameraPositions.indexOf(currentCameraOffset);
        }

        int nextIndex = (currentIndex + 1) % cameraPositions.size();
        currentCameraOffset = cameraPositions.get(nextIndex);
        currentCameraPos = AddBlockPosOffset(currentMonitorPos, currentCameraOffset);

        // 重置视角为新摄像头的初始视角
        currentYaw = 0.0f;
        // currentPitch = 0.0f;

        // 获取摄像头的朝向信息
        if (player.level() != null) {
            var blockState = player.level().getBlockState(currentCameraPos);
            if (blockState.getBlock() instanceof CameraBlock) {
                Direction cameraFacing = blockState.getValue(CameraBlock.FACING);
                player.displayClientMessage(Component.literal("切换到摄像头 " + (nextIndex + 1) +
                        ": X=" + currentCameraPos.getX() + ", Y=" + currentCameraPos.getY() +
                        ", Z=" + currentCameraPos.getZ() + ", 方向=" + cameraFacing.getName())
                        .withStyle(ChatFormatting.AQUA),
                        true);
                currentYaw = getBaseYawFromDirection(cameraFacing);
            } else {
                player.displayClientMessage(Component.literal("切换到摄像头 " + (nextIndex + 1) +
                        ": X=" + currentCameraPos.getX() + ", Y=" + currentCameraPos.getY() +
                        ", Z=" + currentCameraPos.getZ()).withStyle(ChatFormatting.AQUA), true);
            }
        }

    }

    private static BlockPos AddBlockPosOffset(BlockPos pos, BlockPos add) {
        var x1 = pos.getX();
        var y1 = pos.getY();
        var z1 = pos.getZ();
        var addx = add.getX();
        var addy = add.getY();
        var addz = add.getZ();
        return new BlockPos(x1 + addx, y1 + addy, z1 + addz);
    }

    private static void enterSecurityMode(net.minecraft.server.level.ServerPlayer player) {
        BlockPos monitorPos = getCurrentMonitorPos();
        var level = player.level();
        if (level != null && monitorPos != null) {
            BlockState monitorState = level.getBlockState(getCurrentCameraPos());
            if (monitorState.getBlock() instanceof CameraBlock) {
                Direction monitorFacing = monitorState.getValue(FACING);

                // 根据监控控制台方向计算基础旋转角度
                float baseYaw = getBaseYawFromDirection(monitorFacing);
                currentYaw = baseYaw;
            }
        }
        isInSecurityMode = true;
        // currentPitch = 0f;
        player.displayClientMessage(Component.literal("已进入监控模式").withStyle(ChatFormatting.GREEN), true);
        // 发送网络包到客户端以更新视角
        ServerPlayNetworking.send(player,
                new SecurityCameraModePayload(true, currentCameraPos, currentYaw));
    }

    public static void exitSecurityMode(net.minecraft.server.level.ServerPlayer player) {
        isInSecurityMode = false;
        currentCameraPos = null;
        currentCameraOffset = null;
        currentMonitorPos = null; // 清除监控控制台位置
        // 重置视角参数
        currentYaw = 0.0f;
        player.displayClientMessage(Component.literal("已退出监控模式").withStyle(ChatFormatting.RED), true);

        // 发送网络包到客户端以更新视角
        PacketTracker.sendToClient(player,
                new SecurityCameraModePayload(false, BlockPos.ZERO, currentYaw));
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return null;
    }

    // 提供公共方法供其他类使用
    public static boolean isInSecurityMode() {
        return isInSecurityMode;
    }

    public static BlockPos getCurrentCameraPos() {
        return currentCameraPos;
    }

    public static BlockPos getCurrentMonitorPos() {
        return currentMonitorPos;
    }

    public static void setCurrentCameraPos(BlockPos pos) {
        currentCameraPos = pos;
    }

    public static void setSecurityMode(boolean mode) {
        isInSecurityMode = mode;
    }
}