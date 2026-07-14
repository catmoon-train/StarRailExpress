package net.exmo.sre.sixtyseconds.content.block;

import net.exmo.sre.sixtyseconds.logic.SixtySecondsRecipes;
import net.minecraft.world.level.block.Block;

/**
 * 60s 专用合成台功能方块（简易工作台 / 厨房灶台 / 净化台）：
 * 与原版家具合成站（工作台/熔炉/炼药锅…）完全等价的<b>可携带版本</b>——右键打开对应合成站
 * 配方 GUI（{@link net.exmo.sre.sixtyseconds.logic.SixtySecondsStations} 按
 * {@link SixtySecondsRecipes#stationOf} 识别），供避难所里没有对应原版家具时自行制作摆放。
 * 冒险模式仅可放在白色混凝土标记上方（{@code SixtySecondsPlaceableBlockItem}），扳手可拆除返还。
 */
public class SixtySecondsStationBlock extends Block {
    private final SixtySecondsRecipes.Station station;

    public SixtySecondsStationBlock(Properties properties, SixtySecondsRecipes.Station station) {
        super(properties);
        this.station = station;
    }

    public SixtySecondsRecipes.Station station() {
        return station;
    }
}
