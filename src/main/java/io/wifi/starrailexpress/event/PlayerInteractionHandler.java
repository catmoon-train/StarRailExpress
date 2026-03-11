package io.wifi.starrailexpress.event;

import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.cca.PlayerMoodComponent;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.LoomBlock;
import net.minecraft.world.level.block.SmithingTableBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.state.BlockState;

public class PlayerInteractionHandler {

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClientSide) {
                GameWorldComponent game = GameWorldComponent.KEY.get(world);

                if (game.isRunning() && !player.isCreative() && !player.isSpectator()) {
                    BlockState state = world.getBlockState(hitResult.getBlockPos());
                    Block block = state.getBlock();
                    if(state.is(Blocks.NOTE_BLOCK)){
                        var moodC = PlayerMoodComponent.KEY.get(player);
                        if(moodC!=null){
                            moodC.playNoteBlock();
                        }
                    }
                    if (isVanillaWorkstation(block)) {
                        return InteractionResult.FAIL;
                    }
                }
            }
            return InteractionResult.PASS; // 继续正常处理
        });
    }

    private static boolean isVanillaWorkstation(Block block) {
        return block instanceof CraftingTableBlock ||
               block instanceof FurnaceBlock ||
               block instanceof AnvilBlock ||
               block instanceof EnchantingTableBlock ||
               block instanceof LoomBlock ||
               block instanceof CartographyTableBlock ||
               block instanceof SmithingTableBlock ||
               block instanceof GrindstoneBlock ||
               block instanceof StonecutterBlock ||
               block instanceof BrewingStandBlock;
    }
}
