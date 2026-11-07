package io.wifi.starrailexpress.client;

import net.caffeinemc.mods.sodium.api.config.option.OptionBinding;
import net.caffeinemc.mods.sodium.client.gui.SodiumOptions;
import net.minecraft.client.CloudStatus;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/** Sodium 0.8 配置 ID 策略；实体距离固定 100%，天气、树叶、粒子、AO 和暗角开放调整。 */
public final class SodiumOptionPolicy {
    private static final Set<String> EXTRA_TRUE = Set.of(
            "animations_all", "animate_water", "animate_lava", "animate_fire", "animate_portal", "block_animations", "animate_sculk_sensor",
            "particles_all", "rain_splash_particles", "block_break_particles", "block_breaking_particles",
            "sky", "stars", "sun", "moon", "rain_snow", "biome_colors", "sky_colors",
            "light_updates", "item_frame", "armor_stands", "paintings", "beacon_beam", "enchanting_table_book", "piston",
            "item_frame_name_tag", "player_name_tag");
    private static final Set<String> EXTRA_FALSE = Set.of("advanced_fog_settings", "multi_dimension_fog", "protected_gameplay_fog",
            "limit_beacon_beam_height", "cloud_height_override", "panini_projection", "instant_sneak", "prevent_shaders");

    private SodiumOptionPolicy() { }

    /** “禁止着色器”固定关闭且不在界面显示，其他受限项沿用原展示规则。 */
    public static boolean isHidden(ResourceLocation id) {
        return id.getNamespace().equals("sodium-extra") && id.getPath().equals("prevent_shaders");
    }

    public static boolean isLocked(ResourceLocation id) {
        return lockedValue(id) != null || (id.getNamespace().equals("sodium-extra")
                && (id.getPath().startsWith("fog.") || id.getPath().startsWith("fog_shape.")
                    || id.getPath().equals("fog_shape") || id.getPath().equals("panini_projection_strength")));
    }

    public static Object lockedValue(ResourceLocation id) {
        String path = id.getPath();
        if (id.getNamespace().equals("sodium")) {
            return switch (path) {
                case "general.gamma" -> 0;
                case "quality.clouds" -> CloudStatus.OFF;
                case "quality.entity_distance" -> 100;
                default -> null;
            };
        }
        if (id.getNamespace().equals("sodium-extra")) {
            if (EXTRA_TRUE.contains(path) || path.startsWith("particle.")) return true;
            if (EXTRA_FALSE.contains(path)) return false;
            if (path.equals("single_fog") || path.startsWith("fog.") || path.startsWith("protected_gameplay_fog.")) return 0;
            if (path.equals("fog_start") || path.startsWith("fog_start.") || path.equals("cloud_fog") || path.startsWith("cloud_fog.")) return 100;
            if (path.equals("panini_projection_strength")) return 25;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static <T> OptionBinding<T> binding(ResourceLocation id, OptionBinding<T> original) {
        if (!isLocked(id)) return original;
        T forced = (T) lockedValue(id);
        return new OptionBinding<>() {
            @Override public void save(T proposed) {
                // 没有固定标量的复合/枚举项只允许读取受运行策略修正后的值。
                if (forced != null) original.save(forced);
            }
            @Override public T load() { return forced != null ? forced : original.load(); }
        };
    }

    public static SodiumOptions enforce(SodiumOptions options) {
        // 此三项已经开放，不再覆盖用户的天气、树叶和暗角配置。
        return options;
    }
}
