package io.wifi.starrailexpress.event;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 玩家与方块交互的事件处理器。
 * 在游戏运行期间，阻止非创造模式玩家使用原版工作台类方块，
 * 并在玩家与音符盒交互时触发心情效果。
 *
 * <p>Event handler for player-block interactions.
 * Prevents non-creative players from using vanilla workstation blocks while the game is running,
 * and triggers a mood effect when a player interacts with a note block.
 */
public class PlayerInteractionHandler {

    /**
     * 注册玩家与方块交互的事件处理器。
     *
     * <p>Registers the player-block interaction event handler.
     */
    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClientSide) {
                SREGameWorldComponent game = SREGameWorldComponent.KEY.get(world);

                if (game.isRunning() && !player.isCreative() && !player.isSpectator()) {
                    BlockState state = world.getBlockState(hitResult.getBlockPos());
                    Block block = state.getBlock();
                    if(state.is(Blocks.NOTE_BLOCK)){
                        var moodC = SREPlayerMoodComponent.KEY.get(player);
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

    /**
     * 判断给定方块是否属于原版工作台类方块（应在游戏中被禁用）。
     *
     * <p>Determines whether the given block is a vanilla workstation block
     * that should be disabled during the game.
     *
     * @param block 需要检测的方块 / the block to check
     * @return {@code true} 若该方块属于工作台类方块 / {@code true} if the block is a workstation
     */
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
