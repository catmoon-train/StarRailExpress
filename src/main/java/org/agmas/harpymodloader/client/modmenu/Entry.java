package org.agmas.harpymodloader.client.modmenu;

import org.agmas.harpymodloader.client.ui.RoleManageConfigUI;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class Entry implements ModMenuApi{
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            return RoleManageConfigUI.getScreen(parent);
        };
    }
}
