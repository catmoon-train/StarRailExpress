package org.agmas.harpymodloader.client.ui;

import java.util.HashMap;

import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.api.TMMRoles;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class RoleManageConfigUI {

    private static HashMap<ResourceLocation, Boolean> RoleEnableStatus = new HashMap<>();
    private static HashMap<ResourceLocation, Boolean> ModifierEnableStatus = new HashMap<>();

    public static void setRoleInfo(HashMap<ResourceLocation, Boolean> packetInfo) {
        RoleEnableStatus.clear();
        RoleEnableStatus.putAll(packetInfo);
    }

    public static void setModifierInfo(HashMap<ResourceLocation, Boolean> packetInfo) {
        ModifierEnableStatus.clear();
        ModifierEnableStatus.putAll(packetInfo);
    }

    public static Screen getScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("title.starrailexpress.role_config"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        ConfigCategory roleCategory = builder
                .getOrCreateCategory(Component.translatable("category.starrailexpress.config.role"));
        ConfigCategory modifierCategory = builder
                .getOrCreateCategory(Component.translatable("category.starrailexpress.config.modifier"));
        if (RoleEnableStatus.isEmpty()) {
            RoleEnableStatus.clear();
            for (var info : TMMRoles.ROLES.keySet()) {
                if (HarpyModLoaderConfig.HANDLER.instance().disabled.contains(info.toString())) {
                    RoleEnableStatus.put(info, false);
                } else {
                    RoleEnableStatus.put(info, true);
                }
            }
        }
        if (ModifierEnableStatus.isEmpty()) {
            ModifierEnableStatus.clear();
            for (var info : HMLModifiers.MODIFIERS) {
                if (HarpyModLoaderConfig.HANDLER.instance().disabledModifiers.contains(info.identifier().toString())) {
                    RoleEnableStatus.put(info.identifier(), false);
                } else {
                    RoleEnableStatus.put(info.identifier(), true);
                }
            }
        }
        for (var info : RoleEnableStatus.entrySet()) {
            var roleId = info.getKey();
            roleCategory.addEntry(
                    entryBuilder
                            .startBooleanToggle(
                                    Component.translatable("option.starrailexpress.role_enable_option",
                                            RoleUtils.getRoleOrModifierNameWithColor(roleId)),
                                    info.getValue())
                            .setDefaultValue(true) // Recommended: Used when user click "Reset"
                            .setTooltip(Component.translatable("option.starrailexpress.role_id_tooltip",
                                    info.getKey().toString()))
                            .setSaveConsumer(newValue -> RoleEnableStatus.put(roleId, newValue))
                            .build());
        }
        for (var info : ModifierEnableStatus.entrySet()) {
            var roleId = info.getKey();
            modifierCategory.addEntry(
                    entryBuilder
                            .startBooleanToggle(
                                    Component.translatable("option.starrailexpress.modifier_enable_option",
                                            RoleUtils.getRoleOrModifierNameWithColor(roleId)),
                                    info.getValue())
                            .setDefaultValue(true) // Recommended: Used when user click "Reset"
                            .setTooltip(Component.translatable("option.starrailexpress.role_id_tooltip",
                                    info.getKey().toString()))
                            .setSaveConsumer(newValue -> RoleEnableStatus.put(roleId, newValue))
                            .build());
        }

        builder.setSavingRunnable(() -> {
            // Serialise the config into the config file. This will be called last after all
            // variables are updated.
        });
        return builder.build();
    }

    public static void startConfigUI() {
    Screen screen = getScreen(Minecraft.getInstance().screen);

        Minecraft.getInstance().setScreen(screen);
    }
}
