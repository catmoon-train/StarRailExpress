package io.wifi.ConfigCompact.ui;

import java.util.HashMap;
import java.util.Map.Entry;

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
                    ModifierEnableStatus.put(info.identifier(), false);
                } else {
                    ModifierEnableStatus.put(info.identifier(), true);
                }
            }
        }
        for (var info : RoleEnableStatus.entrySet()) {
            var roleId = info.getKey();
            roleCategory.addEntry(
                    entryBuilder
                            .startBooleanToggle(
                                    Component.translatable("option.starrailexpress.role_enable_option",
                                            RoleUtils.getRoleName(roleId)),
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
                                            RoleUtils.getModifierName(roleId)),
                                    info.getValue())
                            .setDefaultValue(true) // Recommended: Used when user click "Reset"
                            .setTooltip(Component.translatable("option.starrailexpress.role_id_tooltip",
                                    info.getKey().toString()))
                            .setSaveConsumer(newValue -> ModifierEnableStatus.put(roleId, newValue))
                            .build());
        }

        builder.setSavingRunnable(() -> {
            HarpyModLoaderConfig.HANDLER.instance().disabled.clear();
            for (Entry<ResourceLocation, Boolean> entry : RoleEnableStatus.entrySet()) {
                if (!entry.getValue()) {
                    HarpyModLoaderConfig.HANDLER.instance().disabled.add(entry.getKey().toString());
                }
            }
            HarpyModLoaderConfig.HANDLER.instance().disabledModifiers.clear();
            for (Entry<ResourceLocation, Boolean> entry : ModifierEnableStatus.entrySet()) {
                if (!entry.getValue()) {
                    HarpyModLoaderConfig.HANDLER.instance().disabledModifiers.add(entry.getKey().toString());
                }
            }
            HarpyModLoaderConfig.HANDLER.save();
        });
        return builder.build();
    }

    public static void startConfigUI() {
        Screen screen = getScreen(Minecraft.getInstance().screen);

        Minecraft.getInstance().setScreen(screen);
    }
}
