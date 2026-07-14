package net.exmo.sre.sixtyseconds.content.item;

import io.wifi.starrailexpress.util.AdventureUsable;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 火把（放置原版火把方块）：照亮家中黑暗角落防止夜间刷低语怪 / 清晨黑暗惩罚
 * （见 {@link net.exmo.sre.sixtyseconds.logic.SixtySecondsWhisperSystem}）。
 * 占 1 格物资；冒险模式可放置，仅限本模式（不限白色混凝土——照明需要自由摆放）。
 */
public class SixtySecondsTorchItem extends StandingAndWallBlockItem implements AdventureUsable {

    public SixtySecondsTorchItem(Properties properties) {
        super(Blocks.TORCH, Blocks.WALL_TORCH, properties, Direction.DOWN);
    }

    @Override
    protected boolean canPlace(BlockPlaceContext context, BlockState state) {
        if (!super.canPlace(context, state)) {
            return false;
        }
        Player player = context.getPlayer();
        return (player != null && player.isCreative()) || SixtySecondsMod.isActive(context.getLevel());
    }
}
