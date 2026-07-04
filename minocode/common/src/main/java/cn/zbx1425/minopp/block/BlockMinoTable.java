package cn.zbx1425.minopp.block;

import cn.zbx1425.minopp.Mino;
import cn.zbx1425.minopp.MinoClient;
import cn.zbx1425.minopp.game.Card;
import cn.zbx1425.minopp.game.CardPlayer;
import cn.zbx1425.minopp.gui.SeatControlScreen;
import cn.zbx1425.minopp.gui.TurnDeadMan;
import cn.zbx1425.minopp.gui.WildSelectionScreen;
import cn.zbx1425.minopp.item.ItemHandCards;
import cn.zbx1425.minopp.mixin.KeyMappingAccessor;
import cn.zbx1425.minopp.network.C2SPlayCardPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockMinoTable extends Block implements EntityBlock {

    public static final EnumProperty<TablePartType> PART = EnumProperty.create("part", TablePartType.class);

    public BlockMinoTable() {
        super(BlockBehaviour.Properties.of()
                .strength(2.0F)
                .noOcclusion());
    }

    @Override
    protected @NotNull ItemInteractionResult useItemOn(ItemStack itemStack, BlockState blockState, Level level, BlockPos blockPos, Player player, InteractionHand interactionHand, BlockHitResult blockHitResult) {
        if (level.isClientSide && itemStack.is(Mino.ITEM_HAND_CARDS.get())) {
            BlockPos corePos = getCore(blockState, blockPos);
            ItemHandCards.CardGameBindingComponent gameBinding = itemStack.get(Mino.DATA_COMPONENT_TYPE_CARD_GAME_BINDING.get());
            int handIndex = itemStack.getOrDefault(Mino.DATA_COMPONENT_TYPE_CLIENT_HAND_INDEX.get(), 0);
            CardPlayer playerWithoutHand = ItemHandCards.getCardPlayer(player);
            BlockEntity blockEntity = level.getBlockEntity(corePos);
            if (blockEntity instanceof BlockEntityMinoTable tableEntity) {
                if (tableEntity.game != null) {
                    if (gameBinding == null || !gameBinding.tablePos().equals(corePos)) {
                        player.displayClientMessage(Component.translatable("game.minopp.play.no_player"), true);
                        return ItemInteractionResult.FAIL;
                    }
                    TurnDeadMan.pedal();

                    CardPlayer realPlayer = tableEntity.game.deAmputate(playerWithoutHand);
                    if (realPlayer == null) return ItemInteractionResult.FAIL;
                    if (Client.isCursorHittingPile()) {
                        C2SPlayCardPacket.Client.sendPlayNoCardC2S(corePos, playerWithoutHand);
                    } else {
                        Card selectedCard = realPlayer.hand.get(Mth.clamp(handIndex, 0, realPlayer.hand.size() - 1));
                        if (selectedCard.suit == Card.Suit.WILD) {
                            Client.openWildSelectionScreen(corePos, playerWithoutHand, selectedCard, Client.isShoutModifierHeld());
                        } else {
                            C2SPlayCardPacket.Client.sendPlayCardC2S(corePos, playerWithoutHand, selectedCard,
                                    null, Client.isShoutModifierHeld());
                        }
                    }
                    return ItemInteractionResult.SUCCESS;
                }
            }
        }
        return super.useItemOn(itemStack, blockState, level, blockPos, player, interactionHand, blockHitResult);
    }

    public static class Client {

        public static void openWildSelectionScreen(BlockPos corePos, CardPlayer player, Card selectedCard, boolean shout) {
            Minecraft.getInstance().setScreen(new WildSelectionScreen(corePos, player, selectedCard, shout));
        }

        public static void openSeatControlScreen(BlockPos corePos) {
            Minecraft.getInstance().setScreen(new SeatControlScreen(corePos));
        }

        public static boolean isShoutModifierHeld() {
            InputConstants.Key boundKey = ((KeyMappingAccessor)MinoClient.KEY_SHOUT_MODIFIER.get()).getKey();
            return boundKey.getType() == InputConstants.Type.KEYSYM
                    && InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), boundKey.getValue());
        }

        public static @Nullable BlockPos getCursorPickedGame() {
            HitResult hitResult = Minecraft.getInstance().hitResult;
            ClientLevel level = Minecraft.getInstance().level;
            if (hitResult.getType() == HitResult.Type.BLOCK) {
                BlockPos potentialTablePos = ((BlockHitResult)hitResult).getBlockPos();
                if (level.getBlockState(potentialTablePos).is(Mino.BLOCK_MINO_TABLE.get())) {
                    return BlockMinoTable.getCore(level.getBlockState(potentialTablePos), potentialTablePos);
                }
            }
            return null;
        }

        public static boolean isCursorHittingPile() {
            BlockPos gamePos = getCursorPickedGame();
            if (gamePos == null) return false;
            ClientLevel level = Minecraft.getInstance().level;
            if (level.getBlockEntity(gamePos) instanceof BlockEntityMinoTable tableEntity) {
                if (tableEntity.game == null) return false;
                AABB pileAabb = getPileAabb(tableEntity);
                Entity cameraEntity = Minecraft.getInstance().getCameraEntity();
                float partialTicks = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
                float hitDistance = 20;
                Vec3 rayBegin = cameraEntity.getEyePosition(partialTicks);
                Vec3 rayDir = cameraEntity.getViewVector(partialTicks);
                Vec3 rayEnd = rayBegin.add(rayDir.x * hitDistance, rayDir.y * hitDistance, rayDir.z * hitDistance);
                return pileAabb.move(gamePos).clip(rayBegin, rayEnd).isPresent();
            }
            return true;
        }

        public static AABB getPileAabb(BlockEntityMinoTable tableEntity) {
            if (tableEntity.game == null) return new AABB(0, 0, 0, 0, 0, 0);
            return AABB.ofSize(new Vec3(0.5, 0.94, 0.5), 0.3, 1 / 16f, 0.5)
                    .expandTowards(0, Math.ceil(tableEntity.game.deck.size() / 5f) * (1 / 16f) * 0.3f, 0);
        }
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState blockState, Level level, BlockPos blockPos, Player player, BlockHitResult blockHitResult) {
        BlockPos corePos = getCore(blockState, blockPos);
        BlockEntity blockEntity = level.getBlockEntity(corePos);
        if (blockEntity instanceof BlockEntityMinoTable tableEntity) {
            CardPlayer cardPlayer = ItemHandCards.getCardPlayer(player);
            if (tableEntity.demo) {
                player.displayClientMessage(Component.translatable("game.minopp.play.table_in_demo"), true);
                return InteractionResult.FAIL;
            }
            if (level.isClientSide) {
                Client.openSeatControlScreen(corePos);
                return InteractionResult.SUCCESS;
            }

            if (tableEntity.game == null && !player.isSecondaryUseActive()) {
                // Join player to table
                tableEntity.joinPlayerToTable(cardPlayer, player.position());
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.FAIL;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext blockPlaceContext) {
        BlockPos firstPartPos = blockPlaceContext.getClickedPos();
        Level level = blockPlaceContext.getLevel();
        for (int i = 0; i < 4; i++) {
            TablePartType part = TablePartType.values()[i];
            BlockPos thisPartPos = firstPartPos.offset(part.xOff, 0, part.zOff);
            boolean isPlaceable = level.getBlockState(thisPartPos).canBeReplaced(blockPlaceContext)
                    && level.getWorldBorder().isWithinBounds(thisPartPos);
            if (!isPlaceable) return null;
        }
        return this.defaultBlockState().setValue(PART, TablePartType.X_LESS_Z_LESS);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos blockPos, BlockState blockState, @Nullable LivingEntity livingEntity, ItemStack itemStack) {
        super.setPlacedBy(level, blockPos, blockState, livingEntity, itemStack);
        if (!level.isClientSide) {
            for (int i = 1; i < 4; i++) {
                TablePartType thisPart = TablePartType.values()[i];
                BlockPos thisPartPos = blockPos.offset(thisPart.xOff, 0, thisPart.zOff);
                level.setBlock(thisPartPos, this.defaultBlockState().setValue(PART, thisPart), Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }

    @Override
    protected @NotNull BlockState updateShape(BlockState blockState, Direction direction, BlockState blockState2, LevelAccessor levelAccessor, BlockPos blockPos, BlockPos blockPos2) {
        BlockPos firstPartPos = getCore(blockState, blockPos);
        for (int i = 0; i < 4; i++) {
            TablePartType thisPart = TablePartType.values()[i];
            BlockPos thisPartPos = firstPartPos.offset(thisPart.xOff, 0, thisPart.zOff);
            if (!levelAccessor.getBlockState(thisPartPos).is(this)) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(blockState, direction, blockState2, levelAccessor, blockPos, blockPos2);
    }

    @Override
    public @NotNull BlockState playerWillDestroy(Level level, BlockPos blockPos, BlockState blockState, Player player) {
        if (!level.isClientSide && player.isCreative()) {
            BlockPos firstPartPos = getCore(blockState, blockPos);
            for (int i = 0; i < 4; i++) {
                TablePartType thisPart = TablePartType.values()[i];
                BlockPos thisPartPos = firstPartPos.offset(thisPart.xOff, 0, thisPart.zOff);
                level.setBlock(thisPartPos, Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
            }
        }
        return super.playerWillDestroy(level, blockPos, blockState, player);
    }

    public static BlockPos getCore(BlockState blockState, BlockPos blockPos) {
        if (!blockState.is(Mino.BLOCK_MINO_TABLE.get())) return blockPos;
        TablePartType part = blockState.getValue(PART);
        return blockPos.offset(-part.xOff, 0, -part.zOff);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        if (blockState.getValue(PART) != TablePartType.X_LESS_Z_LESS) return null;
        return new BlockEntityMinoTable(blockPos, blockState);
    }

    public enum TablePartType implements StringRepresentable {
        X_LESS_Z_LESS,
        X_LESS_Z_MORE,
        X_MORE_Z_LESS,
        X_MORE_Z_MORE;

        public final int xOff;
        public final int zOff;

        TablePartType() {
            this.xOff = this.ordinal() / 2;
            this.zOff = this.ordinal() % 2;
        }

        @Override
        public @NotNull String getSerializedName() {
            return this.name().toLowerCase();
        }
    }

    @Override
    protected float getShadeBrightness(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos) {
        return 1.0F;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos) {
        return true;
    }

    private static final VoxelShape VOXEL_SHAPE = Block.box(0, 0, 0, 16, 14.9, 16);

    @Override
    protected @NotNull VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return VOXEL_SHAPE;
    }
}
