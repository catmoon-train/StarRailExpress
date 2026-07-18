package org.agmas.noellesroles.client.map;

import net.minecraft.world.phys.AABB;

/**
 * 星级区域定义——地图上按星级评等的矩形区域。
 * <p>
 * 星级 1～5，对应不同的颜色和危险程度。区域定义在地图配置 JSON
 * 中作为 star_regions 数组下发，客户端据此在地图上绘制星级边框与标签。
 * </p>
 */
public final class StarRegion {
    /** 星级 1..5，决定区域主色。 */
    public final int star;
    /** 区域名称（翻译键）。 */
    public final String name;
    /** 区域边界框（世界坐标）。 */
    public final AABB bounds;
    /** ARGB 区域主色（由星级与名称 hash 衍生）。 */
    public final int color;

    /** 1..5 星区域对应的默认主色。 */
    public static final int[] STAR_COLORS = {
            0xFF55CC55, // ★ 绿
            0xFF4AB8C0, // ★★ 青
            0xFFFFD700, // ★★★ 金
            0xFFE07B39, // ★★★★ 橙
            0xFFD94040  // ★★★★★ 红
    };

    private StarRegion(int star, String name, AABB bounds, int color) {
        this.star = star;
        this.name = name;
        this.bounds = bounds;
        this.color = color;
    }

    /** 创建一个星级区域。颜色由星级自动选择，名称 hash 微调色调。 */
    public static StarRegion of(int star, String name, AABB bounds) {
        int base = STAR_COLORS[(star - 1) % STAR_COLORS.length];
        int hash = Math.abs(name.hashCode());
        int r = Mth.clamp((base >> 16 & 0xFF) + (hash % 13 - 6), 0, 255);
        int g = Mth.clamp((base >> 8 & 0xFF) + ((hash >> 4) % 13 - 6), 0, 255);
        int b = Mth.clamp((base & 0xFF) + ((hash >> 8) % 13 - 6), 0, 255);
        return new StarRegion(star, name, bounds, 0xFF000000 | r << 16 | g << 8 | b);
    }

    /** 获取星级的 Unicode 星形表示。 */
    public String starSymbol() {
        return "\u2605".repeat(star);
    }

    // 内联的 Mth.clamp 避免依赖（保持纯数据类）
    private static final class Mth {
        static int clamp(int value, int min, int max) {
            return value < min ? min : Math.min(value, max);
        }
    }
}
