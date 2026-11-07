package io.wifi.srepackcompat.config;

import java.util.Set;

/** 仅控制设置页展示，不删除选项注册、不关闭模组或修改其配置。 */
public final class SettingsPageVisibility {
    private static final Set<String> HIDDEN_MODS = Set.of(
            "betterblockentities", "chloride", "gnetum", "lambdynlights", "lambdynlights_runtime",
            "reeses-sodium-options", "reeses_sodium_options", "cullify");
    private static final Set<String> HIDDEN_EXTRA_PAGE_MARKERS = Set.of(
            "animations_all", "sky", "advanced_fog_settings");

    private SettingsPageVisibility() { }

    public static boolean hideMod(String configId) { return HIDDEN_MODS.contains(configId); }

    public static boolean hideExtraPage(Set<String> optionPaths) {
        return optionPaths.stream().anyMatch(HIDDEN_EXTRA_PAGE_MARKERS::contains);
    }
}
