package org.agmas.harpymodloader.client.modmenu;

import org.agmas.harpymodloader.client.ui.SettingMenuScreen;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class StarRailConfigEntry implements ModMenuApi{
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            return new SettingMenuScreen(parent);
        };
    }
}
