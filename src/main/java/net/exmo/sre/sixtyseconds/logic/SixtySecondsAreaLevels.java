package net.exmo.sre.sixtyseconds.logic;

import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfig;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

/**
 * 探索区「危险等级」（1..{@link SixtySecondsBalance#AREA_LEVEL_MAX}）：
 * <ul>
 *   <li>等级越高，物资箱抽取时<b>低权重（稀有）条目越容易出</b>（{@code SixtySecondsLootTable.roll}
 *       按等级压平权重差）且掷出件数越多；</li>
 *   <li>等级越高，周围刷出的怪更多更强（{@code SixtySecondsPveSystem}）。</li>
 * </ul>
 * 等级按<b>坐标反查</b>（门绑定危险区盒都是世界绝对坐标）：先匹配岛屿单元格，再匹配门绑定危险区
 * （{@code DoorBinding.level}，0=继承全局），都不在则取全局基线（{@code searchZoneLevel}）。
 * 配置命令：{@code /sre:60s_area level <1..5>}（全局）、{@code /sre:60s_area level <1..5> <x y z>}（该点所在绑定区）。
 */
public final class SixtySecondsAreaLevels {

    private SixtySecondsAreaLevels() {
    }

    /**
     * 反查坐标的危险等级。优先级（从高到低）：
     * <ol>
     *   <li><b>星级区域覆盖</b>（{@code areaLevelOverrides}，管理员魔改用，可盖住岛屿）——重叠取靠后一条；</li>
     *   <li>岛屿单元格等级；</li>
     *   <li>门绑定危险区（{@code searchDoorBindings} 的 box + level）；</li>
     *   <li>全局基线 {@code searchZoneLevel}。</li>
     * </ol>
     */
    public static int levelAt(ServerLevel level, BlockPos pos) {
        SixtySecondsConfig config = SixtySecondsConfigStore.current(level).orElse(null);
        // 1) 星级区域覆盖：最高优先级，可盖岛屿/门绑定；重叠时后加的覆盖先加的（倒序遍历取第一条命中）。
        if (config != null && config.areaLevelOverrides != null) {
            for (int i = config.areaLevelOverrides.size() - 1; i >= 0; i--) {
                SixtySecondsConfig.LevelRegion reg = config.areaLevelOverrides.get(i);
                if (reg != null && reg.contains(pos.getX(), pos.getY(), pos.getZ())) {
                    return clamp(reg.level);
                }
            }
        }
        // 2) 海岛模式：岛屿单元格——物资箱稀有度/游荡怪强度随岛等级缩放
        int islandLevel = net.exmo.sre.sixtyseconds.island.SixtySecondsIslands.levelAt(level, pos);
        if (islandLevel > 0) {
            return clamp(islandLevel);
        }
        if (config == null) {
            return 1;
        }
        int global = clamp(config.searchZoneLevel);
        // 3) 门绑定危险区
        for (SixtySecondsConfig.DoorBinding binding : config.searchDoorBindings) {
            if (binding.boxMin == null || binding.boxMax == null) {
                continue;
            }
            if (inBox(binding, pos)) {
                return binding.level > 0 ? clamp(binding.level) : global;
            }
        }
        // 4) 全局基线
        return global;
    }

    /** loot 权重压平指数：weight^(1/(1+α(level-1)))——等级越高稀有条目相对权重越大。 */
    public static double lootExponent(int areaLevel) {
        return 1.0 / (1.0 + SixtySecondsBalance.AREA_LEVEL_LOOT_FLATTEN * (clamp(areaLevel) - 1));
    }

    /** 该等级物资箱每次搜刮的额外掷骰件数（1 级 +0 … 5 级 +2）。 */
    public static int bonusRolls(int areaLevel) {
        return (clamp(areaLevel) - 1) / 2;
    }

    public static int clamp(int level) {
        return Mth.clamp(level, 1, SixtySecondsBalance.AREA_LEVEL_MAX);
    }

    private static boolean inBox(SixtySecondsConfig.DoorBinding binding, BlockPos pos) {
        int minX = Math.min(binding.boxMin.x, binding.boxMax.x);
        int maxX = Math.max(binding.boxMin.x, binding.boxMax.x);
        int minY = Math.min(binding.boxMin.y, binding.boxMax.y);
        int maxY = Math.max(binding.boxMin.y, binding.boxMax.y);
        int minZ = Math.min(binding.boxMin.z, binding.boxMax.z);
        int maxZ = Math.max(binding.boxMin.z, binding.boxMax.z);
        return pos.getX() >= minX && pos.getX() <= maxX && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }
}
