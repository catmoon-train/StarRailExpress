package io.wifi.ConfigCompact.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.wifi.ConfigCompact.ui.SettingMenuScreen;

public class StarRailConfigEntry implements ModMenuApi{
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            return new SettingMenuScreen(parent);
        };
    }
}
