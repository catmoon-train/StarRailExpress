package io.wifi.srepackcompat.config;

import com.google.common.collect.ImmutableList;
import io.wifi.srepackcompat.mixin.settings.SodiumOptionIdAccessor;
import net.caffeinemc.mods.sodium.client.config.structure.ModOptions;
import net.caffeinemc.mods.sodium.client.config.structure.Page;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** 过滤展示副本；Config 内部原始页、选项、覆盖与依赖保持完整。 */
public final class SodiumSettingsPages {
    private SodiumSettingsPages() { }

    public static List<ModOptions> visible(List<ModOptions> original) {
        List<ModOptions> result = new ArrayList<>();
        for (ModOptions mod : original) {
            if (SettingsPageVisibility.hideMod(mod.configId())) continue;
            if (!mod.configId().equals("sodium-extra")) {
                result.add(mod);
                continue;
            }
            ImmutableList<Page> pages = mod.pages().stream().filter(page -> !hiddenExtraPage(page))
                    .collect(ImmutableList.toImmutableList());
            if (pages.isEmpty()) continue;
            result.add(new ModOptions(mod.configId(), mod.name(), mod.version(), mod.theme(), mod.icon(),
                    mod.iconMonochrome(), pages, mod.overrides(), mod.overlays(), mod.flagHooks()));
        }
        return List.copyOf(result);
    }

    private static boolean hiddenExtraPage(Page page) {
        // Extra 的粒子页名称是本地化后的 literal，使用稳定选项 ID，不能按显示文本匹配。
        var paths = new HashSet<String>();
        for (var group : page.groups()) {
            for (var option : group.options()) {
                var id = ((SodiumOptionIdAccessor) option).sreCompat$optionId();
                if (id.getNamespace().equals("sodium-extra")) paths.add(id.getPath());
            }
        }
        return SettingsPageVisibility.hideExtraPage(paths);
    }
}
