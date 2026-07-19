package net.exmo.sre.sixtyseconds.content.item;

import io.wifi.starrailexpress.util.AdventureUsable;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.content.block.SixtySecondsBarricadeBlock;
import net.exmo.sre.sixtyseconds.content.block.SixtySecondsGeneratorBlock;
import net.exmo.sre.sixtyseconds.content.block.SixtySecondsLampBlock;
import net.exmo.sre.sixtyseconds.content.block.SixtySecondsSpikeTrapBlock;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsBuildRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 扳手：右键拆除 60s 功能方块，拆除后返还对应物品。
 * 蹲下 + 右键：拆除白色混凝土上方 2 格内的任意方块（掉落为物品形式）。
 */
public class SixtySecondsWrenchItem extends Item implements AdventureUsable {

    public SixtySecondsWrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)
                || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        if (!SixtySecondsMod.isActive(level) && !player.isCreative()) {
            return InteractionResult.PASS;
        }
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);

        // ── 蹲下 + 右键：拆卸白色混凝土上的任意方块（掉落为物品）──
        if (player.isShiftKeyDown() && SixtySecondsBuildRules.canPlaceAt(level, pos)) {
            // 不拆白色混凝土本身和不可破坏方块（基岩等）
            if (state.is(Blocks.WHITE_CONCRETE) || state.getDestroySpeed(level, pos) < 0) {
                return InteractionResult.PASS;
            }
            ItemStack drop = new ItemStack(state.getBlock().asItem());
            level.removeBlock(pos, false);
            if (!player.getInventory().add(drop)) {
                player.drop(drop, false);
            }
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6F, 1.3F);
            context.getItemInHand().hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            return InteractionResult.SUCCESS;
        }

        // ── 原有逻辑：右键拆卸 60s 功能方块 ──
        if (!isFunctionalBlock(state)) {
            return InteractionResult.PASS;
        }
        ItemStack drop;
        if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)) {
            drop = new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_TORCH);
        } else {
            drop = new ItemStack(state.getBlock().asItem());
        }
        level.removeBlock(pos, false); // onRemove 会注销登记
        if (!player.getInventory().add(drop)) {
            player.drop(drop, false);
        }
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6F, 1.3F);
        // 消耗 1 点耐久
        context.getItemInHand().hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        return InteractionResult.SUCCESS;
    }

    private static boolean isFunctionalBlock(BlockState state) {
        Block block = state.getBlock();
        return block instanceof SixtySecondsBarricadeBlock
                || block instanceof SixtySecondsSpikeTrapBlock
                || block instanceof SixtySecondsGeneratorBlock
                || block instanceof SixtySecondsLampBlock
                || block instanceof net.exmo.sre.sixtyseconds.content.block.SixtySecondsStationBlock
                || state.is(org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_DISMANTLER)
                || state.is(Blocks.TORCH)
                || state.is(Blocks.WALL_TORCH);
    }
}
