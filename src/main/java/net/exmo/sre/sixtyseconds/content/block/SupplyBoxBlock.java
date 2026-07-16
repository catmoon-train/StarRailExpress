package net.exmo.sre.sixtyseconds.content.block;

import com.mojang.serialization.MapCodec;
import net.exmo.sre.sixtyseconds.content.block_entity.SupplyBoxBlockEntity;
import net.exmo.sre.sixtyseconds.loot.SixtySecondsLootStore;
import net.exmo.sre.sixtyseconds.loot.SixtySecondsLootTable;
import net.exmo.sre.sixtyseconds.network.OpenLootTableEditS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 物资箱方块：玩家右键领取（从共享 loot 表按类别加权刷新）；创造模式右键打开 loot 表编辑 GUI、
 * 潜行+右键循环切换本箱类别。参照 {@code SupplyCrateBlock}。
 * <p>
 * 变体：{@code locked}（上锁——普通锁需<b>撬箱起子</b>、高级锁需<b>钳子</b>，每撬耗 1 耐久）、
 * {@code advanced}（高级——掷骰件数更多、稀有物更易出）。
 */
public class SupplyBoxBlock extends BaseEntityBlock {
    private static final MapCodec<SupplyBoxBlock> CODEC = simpleCodec(SupplyBoxBlock::new);

    private final boolean locked;
    private final boolean advanced;

    public SupplyBoxBlock(Properties properties) {
        this(properties, false, false);
    }

    public SupplyBoxBlock(Properties properties, boolean locked, boolean advanced) {
        super(properties);
        this.locked = locked;
        this.advanced = advanced;
    }

    public boolean advanced() {
        return advanced;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SupplyBoxBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        interact(level, pos, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        interact(level, pos, player);
        return ItemInteractionResult.SUCCESS;
    }

    private void interact(Level level, BlockPos pos, Player player) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!(serverLevel.getBlockEntity(pos) instanceof SupplyBoxBlockEntity box)) {
            return;
        }
        SixtySecondsLootTable table = SixtySecondsLootStore.get(serverLevel);
        // 创造 + 潜行：循环切换本箱类别
        if (serverPlayer.isCreative() && serverPlayer.isShiftKeyDown()) {
            List<String> names = table.categoryNames();
            if (!names.isEmpty()) {
                int i = names.indexOf(box.category);
                box.category = names.get((i + 1) % names.size());
                box.setChanged();
                serverPlayer.displayClientMessage(
                        Component.literal("[60s] supply box category = " + box.category), true);
            }
            return;
        }
        // 创造：打开 loot 表编辑 GUI
        if (serverPlayer.isCreative()) {
            ServerPlayNetworking.send(serverPlayer, new OpenLootTableEditS2CPacket(table));
            return;
        }
        // 上锁箱：先撬锁——普通锁用撬箱起子、高级锁用钳子（每撬耗 1 耐久，撬开后当天持续开放）
        if (locked && !box.isUnlocked()) {
            net.minecraft.world.item.Item tool = advanced
                    ? org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_PLIERS
                    : org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_BOX_PRY;
            ItemStack held = serverPlayer.getMainHandItem();
            if (!held.is(tool)) {
                serverPlayer.displayClientMessage(Component.translatable(
                        "message.noellesroles.sixty_seconds.supply_box_locked",
                        new ItemStack(tool).getHoverName()), true);
                serverLevel.playSound(null, pos, net.minecraft.sounds.SoundEvents.CHEST_LOCKED,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);
                return;
            }
            held.hurtAndBreak(1, serverPlayer, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            box.setUnlocked();
            serverLevel.playSound(null, pos, net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_OPEN,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.3F);
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.supply_box_unlocked"), true);
        }
        // 生存：开始「搜刮」定时（搜打撤式），完成才发放物资（见 SixtySecondsLootSearch）
        net.exmo.sre.sixtyseconds.logic.SixtySecondsLootSearch.start(serverLevel, serverPlayer, box, pos);
    }
}
