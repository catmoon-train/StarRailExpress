package net.exmo.sre.sixtyseconds.logic;

import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfig;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore;
import net.exmo.sre.subtitle.SubtitleCommand;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

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
    private static final Map<ServerLevel, Map<UUID, String>> LAST_ANNOUNCED_REGIONS = new WeakHashMap<>();

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
        return levelAt(level, pos, config);
    }

    private static int levelAt(ServerLevel level, BlockPos pos, SixtySecondsConfig config) {
        // 1) 星级区域覆盖：最高优先级，可盖岛屿/门绑定；重叠时后加的覆盖先加的（倒序遍历取第一条命中）。
        SixtySecondsConfig.LevelRegion override = overrideAt(config, pos);
        if (override != null) {
            return clamp(override.level);
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

    /**
     * 玩家进入另一个管理员配置的星级区域时显示 SubtitleHUD。海岛已有独立的岛屿名报幕，
     * 故不在此重复提示海岛等级。
     */
    public static void tickAnnouncements(ServerLevel level) {
        if (level.getGameTime() % 10 != 0) {
            return;
        }
        SixtySecondsConfig config = SixtySecondsConfigStore.current(level).orElse(null);
        if (config == null || config.areaLevelOverrides == null || config.areaLevelOverrides.isEmpty()) {
            LAST_ANNOUNCED_REGIONS.remove(level);
            return;
        }
        Map<UUID, String> lastRegions = LAST_ANNOUNCED_REGIONS.computeIfAbsent(level, ignored -> new HashMap<>());
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            SixtySecondsConfig.LevelRegion region = overrideAt(config, player.blockPosition());
            String key = region == null ? "" : regionKey(region);
            String previous = lastRegions.put(player.getUUID(), key);
            if (region == null || previous == null || previous.equals(key)) {
                continue;
            }
            int levelValue = levelAt(level, player.blockPosition(), config);
            ChatFormatting color = levelValue >= 4 ? ChatFormatting.RED
                    : levelValue >= 2 ? ChatFormatting.YELLOW : ChatFormatting.GREEN;
            Component title = Component.translatable("message.noellesroles.sixty_seconds.area_level_enter", levelValue)
                    .withStyle(color, ChatFormatting.BOLD);
            Component subtitle = region.name == null || region.name.isBlank()
                    ? Component.empty()
                    : Component.literal(region.name).withStyle(ChatFormatting.GOLD);
            SubtitleCommand.sendToPlayerTop(player, title, subtitle, 70, false);
        }
    }

    /** 游戏结束时释放仅用于报幕去重的运行时记录。 */
    public static void reset(ServerLevel level) {
        LAST_ANNOUNCED_REGIONS.remove(level);
    }

    private static SixtySecondsConfig.LevelRegion overrideAt(SixtySecondsConfig config, BlockPos pos) {
        if (config == null || config.areaLevelOverrides == null) {
            return null;
        }
        for (int i = config.areaLevelOverrides.size() - 1; i >= 0; i--) {
            SixtySecondsConfig.LevelRegion region = config.areaLevelOverrides.get(i);
            if (region != null && region.contains(pos.getX(), pos.getY(), pos.getZ())) {
                return region;
            }
        }
        return null;
    }

    private static String regionKey(SixtySecondsConfig.LevelRegion region) {
        return region.min.x + ":" + region.min.y + ":" + region.min.z + ":"
                + region.max.x + ":" + region.max.y + ":" + region.max.z + ":" + region.level;
    }

    /** loot 权重压平指数：weight^(1/(1+α(level-1)))——等级越高稀有条目相对权重越大。 */
    public static double lootExponent(int areaLevel) {
        return 1.0 / (1.0 + SixtySecondsBalance.AREA_LEVEL_LOOT_FLATTEN * (clamp(areaLevel) - 1));
    }

    /** 该等级物资箱每次搜刮的额外掷骰件数（1 级 +0 … 5 级 +2）。 */
    public static int bonusRolls(int areaLevel) {
        return (clamp(areaLevel) - 1) / 2;
    }

    /**
     * 星级连锁概率额外物资：按星级依次掷骰，成功则 +1 件并继续判定下一档，失败则停止。
     * <ul>
     *   <li>1 星：10% 概率 +1 件；</li>
     *   <li>2 星：20% 概率 +1 件；</li>
     *   <li>3 星：25% +1 件，再 10% +1 件；</li>
     *   <li>4 星：30% +1 件，再 20% +1 件；</li>
     *   <li>5 星：40% +1 件，再 30% +1 件，再 5% +1 件。</li>
     * </ul>
     * 用于物资箱搜刮（按箱子坐标星级反查），替代旧的固定 {@link #bonusRolls}。
     */
    private static final double[][] CHAIN_BONUS_PROBS = {
            {0.00},                  // 0（占位，不使用）
            {0.10},                  // 1 星
            {0.20},                  // 2 星
            {0.25, 0.10},            // 3 星
            {0.30, 0.20},            // 4 星
            {0.40, 0.30, 0.05}       // 5 星
    };

    public static int chainBonusRolls(RandomSource random, int areaLevel) {
        double[] probs = CHAIN_BONUS_PROBS[clamp(areaLevel)];
        int extra = 0;
        for (double p : probs) {
            if (random.nextDouble() < p) {
                extra++;
            } else {
                break; // 连锁：一档失败则停止后续判定
            }
        }
        return extra;
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
