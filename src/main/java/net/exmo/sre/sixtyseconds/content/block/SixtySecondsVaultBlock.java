package net.exmo.sre.sixtyseconds.content.block;

import com.mojang.serialization.MapCodec;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.content.block_entity.SixtySecondsVaultBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 保险库/基地箱子（真实容器）：
 * <ul>
 *   <li>{@code restricted=true}（保险库）：放置时记录队伍，<b>只有本队能打开</b>；
 *       别队玩家须手持<b>保险库撬锁器套组</b>（每撬一次耗 1 耐久）才能打开偷窃。</li>
 *   <li>{@code restricted=false}（基地小/大箱子）：任何人可开的普通储物箱。</li>
 * </ul>
 * 小 18 / 中 27 / 大 54 格；破坏时倒出全部内容。
 */
public class SixtySecondsVaultBlock extends BaseEntityBlock {

    private final int rows;
    private final boolean restricted;

    public SixtySecondsVaultBlock(Properties properties, int rows, boolean restricted) {
        super(properties);
        this.rows = rows;
        this.restricted = restricted;
    }

    public int rows() {
        return rows;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(p -> new SixtySecondsVaultBlock(p, 3, true));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SixtySecondsVaultBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof ServerPlayer player
                && level.getBlockEntity(pos) instanceof SixtySecondsVaultBlockEntity be) {
            be.ownerTeamId = SixtySecondsStatsComponent.KEY.get(player).teamId;
            be.setChanged();
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        return interact(level, pos, player);
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state,
            Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand,
            BlockHitResult hit) {
        InteractionResult result = interact(level, pos, player);
        return result == InteractionResult.SUCCESS
                ? net.minecraft.world.ItemInteractionResult.SUCCESS
                : net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    private InteractionResult interact(Level level, BlockPos pos, Player player) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof SixtySecondsVaultBlockEntity be)) {
            return InteractionResult.PASS;
        }
        // 保险库锁：对局运行时对外队上锁，撬锁器套组可破
        if (restricted && SixtySecondsMod.isActive(level) && !serverPlayer.isCreative()) {
            int teamId = SixtySecondsStatsComponent.KEY.get(serverPlayer).teamId;
            if (be.ownerTeamId >= 0 && be.ownerTeamId != teamId) {
                ItemStack held = serverPlayer.getMainHandItem();
                if (!held.is(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_VAULT_PICK_KIT)) {
                    serverPlayer.displayClientMessage(Component.translatable(
                            "message.noellesroles.sixty_seconds.vault_locked"), true);
                    level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.8F, 1.0F);
                    return InteractionResult.FAIL;
                }
                held.hurtAndBreak(1, serverPlayer, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                serverPlayer.displayClientMessage(Component.translatable(
                        "message.noellesroles.sixty_seconds.vault_picked"), true);
            }
        }
        MenuType<?> menuType = switch (rows) {
            case 2 -> MenuType.GENERIC_9x2;
            case 6 -> MenuType.GENERIC_9x6;
            default -> MenuType.GENERIC_9x3;
        };
        serverPlayer.openMenu(new SimpleMenuProvider(
                (syncId, inventory, p) -> new ChestMenu(menuType, syncId, inventory, be, rows),
                Component.translatable(getDescriptionId())));
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6F, 1.2F);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof SixtySecondsVaultBlockEntity be) {
            Containers.dropContents(level, pos, be.contents());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
