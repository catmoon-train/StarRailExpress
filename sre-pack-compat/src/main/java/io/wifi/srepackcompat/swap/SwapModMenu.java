package io.wifi.srepackcompat.swap;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** 可选的 Mod Menu 配置入口，不作为运行时硬依赖。 */
public final class SwapModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SwapConfigScreen::new;
    }
}
