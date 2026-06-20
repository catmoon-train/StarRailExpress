package io.wifi.starrailexpress.content.block;

import io.wifi.starrailexpress.content.block_entity.DoorBlockEntity;
import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.DoorCustomOpenItem;
import io.wifi.starrailexpress.event.AllowPlayerOpenLockedDoor;
import io.wifi.starrailexpress.event.DisallowPlayerOpenDoor;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.agmas.noellesroles.content.entity.LockEntityManager;
import org.agmas.noellesroles.init.FunnyItems;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.OpenLockGuiS2CPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import java.util.function.Supplier;

public class SmallDoorBlock extends DoorPartBlock {
    public static final int INTERACTION_COOLDOWN = 10;

    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    protected static final VoxelShape X_SHAPE = Block.box(7, 0, 0, 9, 16, 16);
    protected static final VoxelShape Z_SHAPE = Block.box(0, 0, 7, 16, 16, 9);
    private static final VoxelShape[] SHAPES = createShapes();
    private final Supplier<BlockEntityType<SmallDoorBlockEntity>> typeSupplier;

    @Override
    public boolean shouldHaveCollisionShapeWhenOpen(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context){
        return true;
    }
    
    public SmallDoorBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(
                super.defaultBlockState().setValue(HALF, DoubleBlockHalf.LOWER));
        this.typeSupplier = null;
    }

    public SmallDoorBlock(Supplier<BlockEntityType<SmallDoorBlockEntity>> typeSupplier, Properties settings) {
        super(settings);
        this.registerDefaultState(
                super.defaultBlockState().setValue(HALF, DoubleBlockHalf.LOWER));
        this.typeSupplier = typeSupplier;
    }

    private static VoxelShape[] createShapes() {
        VoxelShape[] shapes = new VoxelShape[16];
        VoxelShape lowerXShape = Block.box(7, 0, 0, 9, 32, 16);
        VoxelShape lowerZShape = Block.box(0, 0, 7, 16, 32, 9);
        VoxelShape upperXShape = Block.box(7, 0, 0, 9, 16, 16);
        VoxelShape upperZShape = Block.box(0, 0, 7, 16, 16, 9);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            int id = direction.get2DDataValue();
            boolean xAxis = direction.getAxis() == Direction.Axis.X;
            shapes[id] = xAxis ? lowerXShape : lowerZShape;
            shapes[id + 4] = xAxis ? upperXShape : upperZShape;
            Vector3f offset = direction.getClockWise().step().mul(7);
            AABB box = new AABB(7, 0, 7, 9, 32, 9).move(offset);
            shapes[id + 8] = Block.box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
            shapes[id + 12] = Block.box(box.minX, box.minY - 16, box.minZ, box.maxX, box.maxY - 16, box.maxZ);
        }
        return shapes;
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack itemStack) {
        world.setBlockAndUpdate(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (direction == half.getDirectionToOther() &&
                (!neighborState.is(this)
                        || neighborState.getValue(FACING) != state.getValue(FACING)
                        || neighborState.getValue(HALF) != half.getOtherHalf())) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    public BlockPos getLowerHalfPos(BlockState state, BlockPos pos) {
        if (state.getValue(HALF).equals(DoubleBlockHalf.UPPER)) {
            return pos.below();
        } else {
            return pos;
        }
    }

    public BlockPos getOtherHalfPos(BlockState state, BlockPos pos) {
        if (state.getValue(HALF).equals(DoubleBlockHalf.UPPER)) {
            return pos.below();
        } else {
            return pos.above();
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block block, BlockPos fromPos,
            boolean notify) {
        var blockPos2 = getOtherHalfPos(state, pos);
        if (!world.isClientSide) {
            BlockPos lowerPos = getLowerHalfPos(state, pos);
            BlockState lowerState = world.getBlockState(lowerPos);
            boolean isPowered = world.hasNeighborSignal(pos) || world.hasNeighborSignal(blockPos2);
            if (!(lowerState.getBlock() instanceof SmallDoorBlock)) {
                return;
            }
            if (isPowered != lowerState.getValue(POWERED)) {
                world.setBlock(lowerPos, lowerState.setValue(POWERED, isPowered), 2);
                if (isPowered) {
                    tryOpenDoors(world, lowerPos, -2);
                }
            }
        }
    }

    protected boolean tryOpenDoors(Level world, BlockPos pos, int ticks) {
        if (world.getBlockEntity(pos) instanceof SmallDoorBlockEntity entity) {
            if (entity.isJammed()) {
                if (!world.isClientSide)
                    world.playSound(null, entity.getBlockPos().getX() + .5f, entity.getBlockPos().getY() + 1,
                            entity.getBlockPos().getZ() + .5f, TMMSounds.BLOCK_DOOR_LOCKED, SoundSource.BLOCKS, 1f, 1f);
                return false;
            }
            if (entity.isBlasted())
                return false;
            toggleDoor(world.getBlockState(pos), world, entity, pos, ticks);
            return true;
        }
        return false;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState placementState = super.getStateForPlacement(ctx);
        if (placementState == null) {
            return null;
        }
        Player player = ctx.getPlayer();
        BlockPos pos = ctx.getClickedPos();
        Level world = ctx.getLevel();
        placementState = placementState.setValue(FACING, getFacingForDoorPlacement(ctx, player.isShiftKeyDown()));
        return pos.getY() < world.getMaxBuildHeight() - 1 && world.getBlockState(pos.above()).canBeReplaced(ctx)
                ? placementState
                : null;
    }

    /**
     * 根据玩家面对的方向，计算指定侧（左侧或右侧）的门应有的朝向（FACING）。
     * <p>
     * 规则：
     * </p>
     * <ul>
     * <li>若为左侧门（isLeftDoor = true），返回玩家朝向的反方向</li>
     * <li>若为右侧门（isLeftDoor = false），返回玩家朝向本身</li>
     * </ul>
     * <p>
     * 示例：玩家朝东，左侧门朝西，右侧门朝东。
     * </p>
     *
     * @param playerFacing 玩家面对的方向（水平方向）
     * @param isLeftDoor   是否为左侧门（true: 左侧门, false: 右侧门）
     * @return 该侧门应有的朝向
     */
    public static Direction getDoorFacing(Direction playerFacing, boolean isLeftDoor) {
        return isLeftDoor ? playerFacing.getOpposite() : playerFacing;
    }

    /**
     * 根据原版门的铰链判定逻辑（遮挡权重 + 相邻门 + 点击位置）计算适合的门的朝向（FACING）。
     * <p>
     * 此方法模拟
     * {@link net.minecraft.world.level.block.DoorBlock#getHinge(BlockPlaceContext)}
     * 的决策过程，
     * 但最终返回的是门的朝向（即 {@code FACING} 属性值），而非铰链侧。
     * </p>
     *
     * @param ctx 方块放置上下文
     * @return 推荐的门朝向（FACING），可直接用于 {@link BlockState#setValue}
     */
    public static Direction getFacingForDoorPlacement(BlockPlaceContext ctx, boolean ignoreDoorConnected) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Direction playerFacing = ctx.getHorizontalDirection(); // 玩家放置时的朝向（原版默认 FACING）

        // 左右两侧（逆时针为左，顺时针为右）
        Direction leftDir = playerFacing.getCounterClockWise();
        Direction rightDir = playerFacing.getClockWise();

        BlockPos leftLower = pos.relative(leftDir);
        BlockPos rightLower = pos.relative(rightDir);

        // // 计算遮挡权重：左侧方块每个减1，右侧每个加1
        // int weight = (level.getBlockState(leftLower).isCollisionShapeFullBlock(level,
        // leftLower) ? 1 : 0)
        // + (level.getBlockState(leftUpper).isCollisionShapeFullBlock(level, leftUpper)
        // ? 1 : 0)
        // + (level.getBlockState(rightLower).isCollisionShapeFullBlock(level,
        // rightLower) ? -1 : 0)
        // + (level.getBlockState(rightUpper).isCollisionShapeFullBlock(level,
        // rightUpper) ? -1 : 0);
        int weight = 0;

        // 检查左右两侧是否存在同类型门的下半部分（用于双开门联动）
        boolean hasLeftDoor = !ignoreDoorConnected
                && level.getBlockState(leftLower).getBlock() instanceof SmallDoorBlock
                && level.getBlockState(leftLower).getValue(HALF) == DoubleBlockHalf.LOWER
                && level.getBlockState(leftLower).getValue(FACING) == getDoorFacing(playerFacing, true);
        boolean hasRightDoor = !ignoreDoorConnected
                && level.getBlockState(rightLower).getBlock() instanceof SmallDoorBlock
                && level.getBlockState(rightLower).getValue(HALF) == DoubleBlockHalf.LOWER
                && level.getBlockState(rightLower).getValue(FACING) == getDoorFacing(playerFacing, false);

        // 决定是否翻转朝向（原版逻辑：权重偏向右侧或不翻转，偏向左侧则翻转）
        boolean flip;
        if ((!hasRightDoor || hasLeftDoor) && weight <= 0) {
            if ((!hasLeftDoor || hasRightDoor) && weight >= 0) {
                // 权重平衡时，根据玩家点击位置精确判断
                int stepX = playerFacing.getStepX();
                int stepZ = playerFacing.getStepZ();
                Vec3 click = ctx.getClickLocation();
                double dx = click.x - (double) pos.getX();
                double dz = click.z - (double) pos.getZ();
                // 点击在右侧时 rightSide=true，对应原版 HINGE=LEFT，我们这里翻转朝向的条件与之相反
                boolean rightSide = (stepX >= 0 || !(dz < 0.5F))
                        && (stepX <= 0 || !(dz > 0.5F))
                        && (stepZ >= 0 || !(dx > 0.5F))
                        && (stepZ <= 0 || !(dx < 0.5F));
                flip = !rightSide;
            } else {
                flip = true; // 权重偏左
            }
        } else {
            flip = false; // 权重偏右或存在右侧门
        }

        return flip ? playerFacing : playerFacing.getOpposite();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (context.equals(CollisionContext.empty())) {
            return this.getShape(state);
        }
        boolean lower = state.getValue(HALF) == DoubleBlockHalf.LOWER;
        boolean open = state.getValue(OPEN);
        return SHAPES[state.getValue(FACING).get2DDataValue() + (lower ? 0 : 4) + (open ? 8 : 0)];
    }

    @Override
    protected VoxelShape getShape(BlockState state) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? X_SHAPE : Z_SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HALF);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? this.typeSupplier.get().create(pos, state) : null;
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state,
            BlockEntityType<T> type) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? super.getTicker(world, state, type) : null;
    }

    @Override
    protected BlockEntityType<? extends DoorBlockEntity> getBlockEntityType() {
        return this.typeSupplier.get();
    }

    public static InteractionResult canOpenDoor(DoorOpenSuperFunction openFunction, BlockState state, Level world,
            BlockPos lowerPos, Player player,
            BlockHitResult hit) {
        ItemStack itemStack = player.getMainHandItem();
        if (itemStack.getItem() instanceof DoorCustomOpenItem) {
            return InteractionResult.PASS;
        }
        if (world.getBlockEntity(lowerPos) instanceof SmallDoorBlockEntity entity) {
            if (entity.isInCooldown()) {
                return InteractionResult.FAIL;
            }
            if (entity.isBlasted()) {
                return InteractionResult.FAIL;
            }

            if (!player.isCreative() && DisallowPlayerOpenDoor.EVENT.invoker().cantOpen(player)) {
                return InteractionResult.FAIL;
            }
            if (player.isCreative() || AllowPlayerOpenLockedDoor.EVENT.invoker().allowOpen(player)) {
                return openFunction.apply(state, world, entity, lowerPos);
            } else {
                boolean requiresKey = !entity.getKeyName().isEmpty();
                ItemStack mainhandItem = player.getMainHandItem();
                boolean canBeAffectedByLock = false;
                // 如果该物品会被锁影响，就在LockEntityManager构造函数的列表添加即可，然后在下面的if语句里具体操作
                // NOTE: 如果只在这里添加不在底下if处理，会导致锁把该物品屏蔽，该物品将对带锁的门毫无效果
                for (var iiiit : LockEntityManager.getInstance().getCanBeAffectedItems()) {
                    if (player.getMainHandItem().is(iiiit)) {
                        canBeAffectedByLock = true;
                        break;
                    }
                }
                if (canBeAffectedByLock) {
                    // 当当前门上无锁时，检查附近门的情况：实现锁对附近门的影响
                    var half = state.getOptionalValue(HALF).orElse(null);
                    BlockPos lockPos = null;
                    if (half != null) {
                        lockPos = lowerPos.above();
                    }

                    if (lockPos != null && LockEntityManager.getInstance().getLockEntity(lockPos) == null) {
                        if (world.getBlockEntity(lockPos.below()) instanceof SmallDoorBlockEntity sde) {
                            switch (sde.getFacing()) {
                                case NORTH:
                                case SOUTH:
                                    if (LockEntityManager.getInstance().getLockEntity(lockPos.east()) != null)
                                        lockPos = lockPos.east();
                                    else if (LockEntityManager.getInstance().getLockEntity(lockPos.west()) != null) {
                                        lockPos = lockPos.west();
                                    }
                                    break;
                                case EAST:
                                case WEST:
                                    if (LockEntityManager.getInstance().getLockEntity(lockPos.north()) != null)
                                        lockPos = lockPos.north();
                                    else if (LockEntityManager.getInstance().getLockEntity(lockPos.south()) != null) {
                                        lockPos = lockPos.south();
                                    }
                                    break;
                                default:
                                    break;
                            }
                        }
                    }

                    if (lockPos != null && LockEntityManager.getInstance().getLockEntity(lockPos) != null) {
                        // 根据手中物品决定锁的影响
                        boolean canUnLock = false;
                        for (var item : LockEntityManager.getInstance().getCanBeUsedToUnLock()) {
                            if (player.getMainHandItem().is(item)) {
                                canUnLock = true;
                                break;
                            }
                        }
                        if (canUnLock) {
                            // 当手持撬锁器且该门上锁时：进入撬锁小游戏
                            player.displayClientMessage(
                                    Component.translatable("message.lock.game.start").withStyle(ChatFormatting.AQUA),
                                    true);
                            player.playNotifySound(SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.5f, 1.5f);
                            // 客户端：打开GUI
                            var lockEntity = LockEntityManager.getInstance().getLockEntity(lockPos);
                            if (lockEntity != null) {
                                if (player instanceof ServerPlayer serverPlayer) {
                                    ServerPlayNetworking.send(serverPlayer, new OpenLockGuiS2CPacket(lockPos,
                                            lockEntity.getUUID(), lockEntity.getLength()));
                                }
                            }
                            // 返回 false 阻止原始方法执行
                            return (InteractionResult.FAIL);
                        } else {
                            // 默认行为：阻止原操作
                            return (InteractionResult.FAIL);
                        }

                    }
                }
                boolean hasLockpick = mainhandItem.is(TMMItems.LOCKPICK) || mainhandItem.is(ModItems.MASTER_KEY)
                        || mainhandItem.is(FunnyItems.BOWEN_BADGE);
                if (mainhandItem.is(ModItems.MASTER_KEY_P)) {
                    if (!player.isCreative()) {
                        mainhandItem.hurtAndBreak(1, player, player.getEquipmentSlotForItem(mainhandItem));
                    }
                    hasLockpick = true;
                }
                boolean jammed = entity.isJammed();

                if (entity.isOpen()) {
                    return openFunction.apply(state, world, entity, lowerPos);
                } else if (requiresKey && !jammed) {
                    if (player.getMainHandItem().is(TMMItems.CROWBAR))
                        return InteractionResult.FAIL;
                    if (player.getMainHandItem().is(TMMItems.KEY) || hasLockpick) {
                        ItemLore lore = player.getMainHandItem().get(DataComponents.LORE);
                        boolean isRightKey = lore != null && !lore.lines().isEmpty()
                                && lore.lines().getFirst().getString().equals(entity.getKeyName());
                        if (isRightKey || hasLockpick) {
                            if (isRightKey)
                                world.playSound(null, lowerPos.getX() + .5f, lowerPos.getY() + 1, lowerPos.getZ() + .5f,
                                        TMMSounds.ITEM_KEY_DOOR, SoundSource.BLOCKS, 1f, 1f);
                            if (hasLockpick)
                                world.playSound(null, lowerPos.getX() + .5f, lowerPos.getY() + 1, lowerPos.getZ() + .5f,
                                        TMMSounds.ITEM_LOCKPICK_DOOR, SoundSource.BLOCKS, 1f, 1f);
                            return openFunction.apply(state, world, entity, lowerPos);
                        } else {
                            if (!world.isClientSide) {
                                world.playSound(null, lowerPos.getX() + .5f, lowerPos.getY() + 1, lowerPos.getZ() + .5f,
                                        TMMSounds.BLOCK_DOOR_LOCKED, SoundSource.BLOCKS, 1f, 1f);
                                player.displayClientMessage(Component.translatable("tip.door.requires_different_key"),
                                        true);
                            }
                            return InteractionResult.FAIL;
                        }
                    }

                    if (!world.isClientSide) {
                        world.playSound(null, lowerPos.getX() + .5f, lowerPos.getY() + 1, lowerPos.getZ() + .5f,
                                TMMSounds.BLOCK_DOOR_LOCKED, SoundSource.BLOCKS, 1f, 1f);
                        player.displayClientMessage(Component.translatable("tip.door.requires_key"), true);
                    }
                    return InteractionResult.FAIL;
                } else {
                    if (jammed) {
                        if (!world.isClientSide) {
                            world.playSound(null, lowerPos.getX() + .5f, lowerPos.getY() + 1, lowerPos.getZ() + .5f,
                                    TMMSounds.BLOCK_DOOR_LOCKED, SoundSource.BLOCKS, 1f, 1f);
                            player.displayClientMessage(Component.translatable("tip.door.jammed"), true);
                        }
                        return InteractionResult.FAIL;
                    } else {
                        // open the door freely
                        return openFunction.apply(state, world, entity, lowerPos);
                    }
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult useWithoutItemStatic(
            DoorOpenSuperFunction openFunction, BlockState state, Level world, BlockPos pos, Player player,
            BlockHitResult hit) {
        BlockPos lowerPos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        return canOpenDoor(openFunction, state, world, lowerPos, player, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
            BlockHitResult hit) {
        return useWithoutItemStatic((a, b, c, d) -> this.open(a, b, c, d), state, world, pos, player, hit);
    }

    public @NotNull InteractionResult open(BlockState state, Level world, SmallDoorBlockEntity entity,
            BlockPos lowerPos) {
        if (world.isClientSide)
            return InteractionResult.SUCCESS;
        toggleDoor(state, world, entity, lowerPos);
        return InteractionResult.CONSUME;
    }

    static @NotNull InteractionResult openStatic(BlockState state, Level world, SmallDoorBlockEntity entity,
            BlockPos lowerPos) {
        if (world.isClientSide)
            return InteractionResult.SUCCESS;
        toggleDoorStatic(state, world, entity, lowerPos);
        return InteractionResult.CONSUME;
    }

    public void toggleDoor(BlockState state, Level world, SmallDoorBlockEntity entity, BlockPos lowerPos) {
        toggleDoor(state, world, entity, lowerPos, -2);
    }

    public void toggleDoor(BlockState state, Level world, SmallDoorBlockEntity entity, BlockPos lowerPos,
            int ticks) {
        toggleDoorStatic(state, world, entity, lowerPos, ticks);
    }

    public static void toggleDoorStatic(BlockState state, Level world, SmallDoorBlockEntity entity, BlockPos lowerPos) {
        toggleDoorStatic(state, world, entity, lowerPos, -2);
    }

    public static void toggleDoorStatic(BlockState state, Level world, SmallDoorBlockEntity entity, BlockPos lowerPos,
            int ticks) {
        entity.toggle(false, ticks);
        entity.setCooldown(INTERACTION_COOLDOWN);
        Direction facing = state.getValue(FACING);
        boolean open = state.getValue(OPEN);
        BlockPos neighborPos = lowerPos.relative(facing.getCounterClockWise());
        BlockState neighborState = world.getBlockState(neighborPos);
        if (neighborState.is(state.getBlock())
                && neighborState.getValue(FACING).getOpposite() == facing
                && neighborState.getValue(OPEN) == open
                && world.getBlockEntity(neighborPos) instanceof SmallDoorBlockEntity neighborEntity) {
            neighborEntity.toggle(true, ticks);
            neighborEntity.setCooldown(INTERACTION_COOLDOWN);
        }
    }

    @FunctionalInterface
    public interface DoorOpenSuperFunction {
        InteractionResult apply(BlockState state, Level world, SmallDoorBlockEntity entity,
                BlockPos lowerPos);
    }
}
