package io.wifi.ConfigCompact;

import io.wifi.ConfigCompact.config_gui_provider.GenericEnumGuiProvider;
import io.wifi.ConfigCompact.config_gui_provider.GenericMapGuiProvider;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.ConfigManager;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;

public class ConfigClassHandler<T extends ConfigData> {
    private final Class<T> type; // 保存 T 的 Class

    @Environment(EnvType.CLIENT)
    public static class GuiGenerator<T extends ConfigData> {
        private final Class<T> type;

        public GuiGenerator(Class<T> type) {
            this.type = type;
        }

        // ✅ 可以安全引用 Screen，因为整个类已是 CLIENT-only
        public net.minecraft.client.gui.screens.Screen generateScreen(Screen parent) {
            GenericMapGuiProvider.register(AutoConfig.getGuiRegistry(type));
            GenericEnumGuiProvider.register(AutoConfig.getGuiRegistry(type));
            return AutoConfig.getConfigScreen(type, parent).get();
        }
    }

    public ConfigClassHandler(Class<T> type) {
        this.type = type;
        this.register();
    }

    @Environment(EnvType.CLIENT)
    public GuiGenerator<T> generateGui() {
        return new GuiGenerator<T>(type);
    }

    public void load() {
        try {
            var config = ((ConfigManager<T>) AutoConfig
                    .getConfigHolder(type));
            config.load();
        } catch (ClassCastException e) {
            // 理论上不会发生，除非 cloth-config 换了实现
            throw new RuntimeException("Failed to reload config", e);
        }
    }

    
    public void reset() {
        try {
            var config = ((ConfigManager<T>) AutoConfig
                    .getConfigHolder(type));
            config.resetToDefault();
        } catch (ClassCastException e) {
            // 理论上不会发生，除非 cloth-config 换了实现
            throw new RuntimeException("Failed to reload config", e);
        }
    }
    public void save() {
        try {
            var config = ((ConfigManager<T>) AutoConfig
                    .getConfigHolder(type));
            config.save();
        } catch (ClassCastException e) {
            // 理论上不会发生，除非 cloth-config 换了实现
            throw new RuntimeException("Failed to reload config", e);
        }
    }

    public T instance() {
        T config = AutoConfig.getConfigHolder(type).getConfig();
        return config;
    }

    public void register() {
        AutoConfig.register(type, GsonConfigSerializer::new);
    }
}
