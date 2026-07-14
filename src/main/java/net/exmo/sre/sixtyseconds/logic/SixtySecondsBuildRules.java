package net.exmo.sre.sixtyseconds.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * 功能方块放置规则：障碍/陷阱/发电机等只能放在<b>标记方块</b>（默认白色混凝土）上方
 * 2 格以内——直接放在白色混凝土上，或「正常方块下面是白色混凝土」时放在该方块上面。
 * 由地图作者用白色混凝土预铺可建造点。
 */
public final class SixtySecondsBuildRules {

    private SixtySecondsBuildRules() {
    }

    public static boolean canPlaceAt(Level level, BlockPos pos) {
        return isMarker(level, pos.below()) || isMarker(level, pos.below(2));
    }

    private static boolean isMarker(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.WHITE_CONCRETE);
    }
}
