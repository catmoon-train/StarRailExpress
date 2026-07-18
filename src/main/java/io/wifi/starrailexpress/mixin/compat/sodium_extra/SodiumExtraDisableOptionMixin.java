package io.wifi.starrailexpress.mixin.compat.sodium_extra;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;
import me.flashyreese.mods.sodiumextra.client.config.SodiumExtraConfig;
import me.flashyreese.mods.sodiumextra.client.config.SodiumExtraGameOptions;
import me.flashyreese.mods.sodiumextra.client.fog.FogDistanceHelper;
import me.flashyreese.mods.sodiumextra.common.util.ControlValueFormatterExtended;
import net.caffeinemc.mods.sodium.api.config.ConfigState;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionGroupBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.StatefulOptionBuilder;
import net.caffeinemc.mods.sodium.client.gui.options.control.ControlValueFormatterImpls;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.function.Function;

@Mixin(value = SodiumExtraConfig.class, remap = false)
public abstract class SodiumExtraDisableOptionMixin {
    @Shadow
    private static Component translatableName(ResourceLocation identifier, String category) {
        return null;
    }

    @Shadow
    private static Component parseVanillaString(String key) {
        return null;
    }

    @Shadow
    @Final
    private static ResourceLocation ADVANCED_FOG_OPTION_ID;

    @Shadow
    @Final
    private static ResourceLocation MULTI_DIMENSION_FOG_OPTION_ID;

    @Shadow
    @Final
    private static ResourceLocation PROTECTED_GAMEPLAY_FOG_OPTION_ID;

    @Shadow
    @Final
    private static ResourceLocation WAYLAND_FULLSCREEN_RESOLUTION_OPTION_ID;

    @Shadow
    @Final
    private static ResourceLocation CLOUD_HEIGHT_OVERRIDE_OPTION_ID;

    @Shadow
    @Final
    private static ResourceLocation PANINI_PROJECTION_OPTION_ID;

    @Shadow
    @Final
    private static ResourceLocation SODIUM_FULLSCREEN_OPTION_ID;

    @Shadow
    @Final
    private static ResourceLocation SODIUM_FULLSCREEN_RESOLUTION_OPTION_ID;
    @Shadow
    @Final
    private static ResourceLocation SODIUM_VSYNC_OPTION_ID;

    @Shadow
    private static <B extends StatefulOptionBuilder<?>> B fogOption(B option,
            Function<ConfigState, Boolean> enabledProvider, String nameKey, String tooltipKey) {
        return null;
    }

    @Overwrite
    private static boolean isFogMixinEnabled() {
        return false;
    }

    @Overwrite
    private static boolean isAdvancedFogOptionEnabled(ConfigState state) {
        return false;
    }

    @Overwrite

    private static boolean isFogShapeOptionEnabled(ConfigState state) {
        return false;
    }

    @Overwrite

    private static boolean isSingleFogOptionEnabled(ConfigState state) {
        return false;
    }

    @Overwrite

    private static boolean isDimensionFogOptionEnabled(ConfigState state) {
        return false;
    }

    @Overwrite

    private static boolean isDimensionFogShapeOptionEnabled(ConfigState state) {
        return false;
    }

    @Overwrite
    private static boolean isProtectedGameplayFogOptionEnabled(ConfigState state) {
        return false;
    }

    @Shadow
    private static ResourceLocation id(String path) {
        return null;
    }

    @Shadow
    private static SodiumExtraGameOptions.FogSettings fogSettings() {
        return null;
    }

    @Shadow
    private static List<ResourceLocation> getDimensionFogEffectIds(SodiumExtraGameOptions.FogSettings fogSettings) {
        return null;
    }

    @Overwrite
    private OptionPageBuilder createRenderPage(ConfigBuilder builder) {
        OptionPageBuilder page = builder.createOptionPage()
                .setName(Component.translatable("sodium-extra.option.render"));

        SodiumExtraGameOptions.FogSettings fogSettings = fogSettings();
        List<ResourceLocation> dimensionFogEffectIds = getDimensionFogEffectIds(fogSettings);

        page.addOptionGroup(builder.createOptionGroup()
                .addOption(builder.createBooleanOption(ADVANCED_FOG_OPTION_ID)
                        .setEnabled(SodiumExtraClientMod.mixinConfig().getOptions().get("mixin.fog").isEnabled())
                        .setName(Component.translatable("sodium-extra.option.advanced_fog_settings"))
                        .setTooltip(Component.translatable("sodium-extra.option.advanced_fog_settings.tooltip"))
                        .setStorageHandler(SodiumExtraClientMod.options())
                        .setBinding(
                                value -> SodiumExtraClientMod.options().renderSettings.fogSettings.advanced = value,
                                () -> SodiumExtraClientMod.options().renderSettings.fogSettings.advanced)
                        .setDefaultValue(false)));

        page.addOptionGroup(builder.createOptionGroup()
                .addOption(fogOption(builder.createIntegerOption(id("single_fog")),
                        (a) -> false, "sodium-extra.option.fog_distance",
                        "sodium-extra.option.fog_distance.tooltip")
                        .setRangeProvider(FogDistanceHelper::getFogDistanceRange,
                                FogDistanceHelper.SODIUM_RENDER_DISTANCE_OPTION_ID, ConfigState.UPDATE_ON_REBUILD)
                        .setValueFormatter(ControlValueFormatterExtended.fogDistance())
                        .setBinding(
                                value -> SodiumExtraClientMod
                                        .options().renderSettings.fogSettings.atmospheric.distanceChunks = value,
                                () -> SodiumExtraClientMod
                                        .options().renderSettings.fogSettings.atmospheric.distanceChunks)
                        .setDefaultValue(0))
                .addOption(fogOption(builder.createIntegerOption(id("fog_start")),
                        (a) -> false, "sodium-extra.option.fog_start",
                        "sodium-extra.option.fog_start.tooltip")
                        .setRange(0, 100, 1)
                        .setValueFormatter(ControlValueFormatterImpls.percentage())
                        .setBinding((a) -> {
                        },
                                () -> SodiumExtraClientMod
                                        .options().renderSettings.fogSettings.atmospheric.startPercent)
                        .setDefaultValue(100))
                .addOption(fogOption(
                        builder.createEnumOption(id("fog_shape"), SodiumExtraGameOptions.FogShapeMode.class),
                        (a) -> false, "sodium-extra.option.fog_shape",
                        "sodium-extra.option.fog_shape.tooltip")
                        .setAllowedValues(SodiumExtraGameOptions.FogShapeMode.getAvailableOptions())
                        .setBinding((a) -> {
                        },
                                () -> SodiumExtraClientMod.options().renderSettings.fogSettings.atmospheric.shapeMode)
                        .setDefaultValue(SodiumExtraGameOptions.FogShapeMode.VANILLA))
                .addOption(fogOption(builder.createIntegerOption(id("cloud_fog")),
                        (a) -> false, "sodium-extra.option.cloud_fog",
                        "sodium-extra.option.cloud_fog.tooltip")
                        .setRange(0, 100, 1)
                        .setValueFormatter(ControlValueFormatterImpls.percentage())
                        .setBinding((a) -> {
                        },
                                () -> SodiumExtraClientMod
                                        .options().renderSettings.fogSettings.atmospheric.cloudFogPercent)
                        .setDefaultValue(FogDistanceHelper.VANILLA_CLOUD_FOG_PERCENT)));

        page.addOptionGroup(builder.createOptionGroup()
                .addOption(builder.createBooleanOption(MULTI_DIMENSION_FOG_OPTION_ID)
                        .setEnabledProvider((a) -> false, ADVANCED_FOG_OPTION_ID)
                        .setControlHiddenWhenDisabled(false)
                        .setName(Component.translatable("sodium-extra.option.multi_dimension_fog"))
                        .setTooltip(Component.translatable("sodium-extra.option.multi_dimension_fog.tooltip"))
                        .setStorageHandler(SodiumExtraClientMod.options())
                        .setBinding(
                                value -> SodiumExtraClientMod
                                        .options().renderSettings.fogSettings.multiDimensionFogControl = value,
                                () -> SodiumExtraClientMod
                                        .options().renderSettings.fogSettings.multiDimensionFogControl)
                        .setDefaultValue(false)));

        dimensionFogEffectIds.forEach(identifier -> {
            String dimensionKey = identifier.toLanguageKey("options.dimensions");
            OptionGroupBuilder dimensionFogGroup = builder.createOptionGroup()
                    .setName(translatableName(identifier, "dimensions"))
                    .addOption(fogOption(builder.createIntegerOption(id("fog." + dimensionKey)),
                            (a) -> false, "sodium-extra.option.fog_distance",
                            "sodium-extra.option.fog.tooltip")
                            .setRangeProvider(FogDistanceHelper::getFogDistanceRange,
                                    FogDistanceHelper.SODIUM_RENDER_DISTANCE_OPTION_ID, ConfigState.UPDATE_ON_REBUILD)
                            .setValueFormatter(ControlValueFormatterExtended.fogDistance())
                            .setBinding(
                                    value -> SodiumExtraClientMod.options().renderSettings.fogSettings
                                            .getOrCreateDimensionOverride(identifier).distanceChunks = value,
                                    () -> SodiumExtraClientMod.options().renderSettings.fogSettings
                                            .getDimensionFogDistance(identifier))
                            .setDefaultValue(0))
                    .addOption(fogOption(builder.createIntegerOption(id("fog_start." + dimensionKey)),
                            (a) -> false, "sodium-extra.option.fog_start",
                            "sodium-extra.option.fog_start.tooltip")
                            .setRange(0, 100, 1)
                            .setValueFormatter(ControlValueFormatterImpls.percentage())
                            .setBinding(
                                    value -> {},
                                    () -> SodiumExtraClientMod.options().renderSettings.fogSettings
                                            .getDimensionFogStart(identifier))
                            .setDefaultValue(100))
                    .addOption(fogOption(
                            builder.createEnumOption(id("fog_shape." + dimensionKey),
                                    SodiumExtraGameOptions.FogShapeMode.class),
                            (a) -> false, "sodium-extra.option.fog_shape",
                            "sodium-extra.option.fog_shape.tooltip")
                            .setAllowedValues(SodiumExtraGameOptions.FogShapeMode.getAvailableOptions())
                            .setBinding(
                                    value -> {
                                    },
                                    () -> SodiumExtraClientMod.options().renderSettings.fogSettings
                                            .getDimensionFogShape(identifier))
                            .setDefaultValue(SodiumExtraGameOptions.FogShapeMode.VANILLA))
                    .addOption(fogOption(builder.createIntegerOption(id("cloud_fog." + dimensionKey)),
                            (a) -> false, "sodium-extra.option.cloud_fog",
                            "sodium-extra.option.cloud_fog.tooltip")
                            .setRange(0, 100, 1)
                            .setValueFormatter(ControlValueFormatterImpls.percentage())
                            .setBinding(
                                    value -> {
                                    },
                                    () -> SodiumExtraClientMod.options().renderSettings.fogSettings
                                            .getDimensionCloudFogPercent(identifier))
                            .setDefaultValue(FogDistanceHelper.VANILLA_CLOUD_FOG_PERCENT));

            page.addOptionGroup(dimensionFogGroup);
        });

        page.addOptionGroup(builder.createOptionGroup()
                .addOption(builder.createBooleanOption(PROTECTED_GAMEPLAY_FOG_OPTION_ID)
                        .setEnabledProvider((a) -> false, ADVANCED_FOG_OPTION_ID)
                        .setControlHiddenWhenDisabled(false)
                        .setName(Component.translatable("sodium-extra.option.protected_gameplay_fog"))
                        .setTooltip(Component.translatable("sodium-extra.option.protected_gameplay_fog.tooltip"))
                        .setStorageHandler(SodiumExtraClientMod.options())
                        .setBinding(
                                value -> SodiumExtraClientMod
                                        .options().renderSettings.fogSettings.protectedGameplay.enabledWhenAllowed = value,
                                () -> SodiumExtraClientMod
                                        .options().renderSettings.fogSettings.protectedGameplay.enabledWhenAllowed)
                        .setDefaultValue(false))
                .addOption(builder.createIntegerOption(id("protected_gameplay_fog.blindness"))
                        .setEnabledProvider(
                                (a) -> false,
                                ADVANCED_FOG_OPTION_ID,
                                PROTECTED_GAMEPLAY_FOG_OPTION_ID)
                        .setControlHiddenWhenDisabled(false)
                        .setName(Component.translatable("sodium-extra.option.protected_gameplay_fog.blindness"))
                        .setTooltip(
                                Component.translatable("sodium-extra.option.protected_gameplay_fog.blindness.tooltip"))
                        .setStorageHandler(SodiumExtraClientMod.options())
                        .setRange(FogDistanceHelper.getProtectedGameplayFogDistanceRange())
                        .setValueFormatter(ControlValueFormatterExtended.protectedFogDistance())
                        .setBinding(
                                value -> SodiumExtraClientMod
                                        .options().renderSettings.fogSettings.protectedGameplay.blindnessDistanceBlocks = value,
                                () -> SodiumExtraClientMod
                                        .options().renderSettings.fogSettings.protectedGameplay.blindnessDistanceBlocks)
                        .setDefaultValue(0))
                .addOption(builder.createIntegerOption(id("protected_gameplay_fog.darkness"))
                        .setEnabledProvider(
                                (a) -> false,
                                ADVANCED_FOG_OPTION_ID,
                                PROTECTED_GAMEPLAY_FOG_OPTION_ID)
                        .setControlHiddenWhenDisabled(false)
                        .setName(Component.translatable("sodium-extra.option.protected_gameplay_fog.darkness"))
                        .setTooltip(
                                Component.translatable("sodium-extra.option.protected_gameplay_fog.darkness.tooltip"))
                        .setStorageHandler(SodiumExtraClientMod.options())
                        .setRange(FogDistanceHelper.getProtectedGameplayFogDistanceRange())
                        .setValueFormatter(ControlValueFormatterExtended.protectedFogDistance())
                        .setBinding(
                                value -> SodiumExtraClientMod
                                        .options().renderSettings.fogSettings.protectedGameplay.darknessDistanceBlocks = value,
                                () -> SodiumExtraClientMod
                                        .options().renderSettings.fogSettings.protectedGameplay.darknessDistanceBlocks)
                        .setDefaultValue(0))
                .addOption(builder.createIntegerOption(id("protected_gameplay_fog.lava"))
                        .setEnabledProvider(
                                (a) -> false,
                                ADVANCED_FOG_OPTION_ID,
                                PROTECTED_GAMEPLAY_FOG_OPTION_ID)
                        .setControlHiddenWhenDisabled(false)
                        .setName(Component.translatable("sodium-extra.option.protected_gameplay_fog.lava"))
                        .setTooltip(Component.translatable("sodium-extra.option.protected_gameplay_fog.lava.tooltip"))
                        .setStorageHandler(SodiumExtraClientMod.options())
                        .setRange(FogDistanceHelper.getProtectedGameplayFogDistanceRange())
                        .setValueFormatter(ControlValueFormatterExtended.protectedFogDistance())
                        .setBinding(
                                value -> SodiumExtraClientMod
                                        .options().renderSettings.fogSettings.protectedGameplay.lavaDistanceBlocks = value,
                                () -> SodiumExtraClientMod
                                        .options().renderSettings.fogSettings.protectedGameplay.lavaDistanceBlocks)
                        .setDefaultValue(0))
                .addOption(builder.createIntegerOption(id("protected_gameplay_fog.powder_snow"))
                        .setEnabledProvider(
                                (a) -> false,
                                ADVANCED_FOG_OPTION_ID,
                                PROTECTED_GAMEPLAY_FOG_OPTION_ID)
                        .setControlHiddenWhenDisabled(false)
                        .setName(Component.translatable("sodium-extra.option.protected_gameplay_fog.powder_snow"))
                        .setTooltip(Component
                                .translatable("sodium-extra.option.protected_gameplay_fog.powder_snow.tooltip"))
                        .setStorageHandler(SodiumExtraClientMod.options())
                        .setRange(FogDistanceHelper.getProtectedGameplayFogDistanceRange())
                        .setValueFormatter(ControlValueFormatterExtended.protectedFogDistance())
                        .setBinding(
                                value -> SodiumExtraClientMod
                                        .options().renderSettings.fogSettings.protectedGameplay.powderSnowDistanceBlocks = value,
                                () -> SodiumExtraClientMod
                                        .options().renderSettings.fogSettings.protectedGameplay.powderSnowDistanceBlocks)
                        .setDefaultValue(0))
                .addOption(builder.createIntegerOption(id("protected_gameplay_fog.water"))
                        .setEnabledProvider(
                                (a) -> false,
                                ADVANCED_FOG_OPTION_ID,
                                PROTECTED_GAMEPLAY_FOG_OPTION_ID)
                        .setControlHiddenWhenDisabled(false)
                        .setName(Component.translatable("sodium-extra.option.protected_gameplay_fog.water"))
                        .setTooltip(Component.translatable("sodium-extra.option.protected_gameplay_fog.water.tooltip"))
                        .setStorageHandler(SodiumExtraClientMod.options())
                        .setRange(FogDistanceHelper.getProtectedGameplayFogDistanceRange())
                        .setValueFormatter(ControlValueFormatterExtended.protectedFogDistance())
                        .setBinding(
                                value -> SodiumExtraClientMod
                                        .options().renderSettings.fogSettings.protectedGameplay.waterDistanceBlocks = value,
                                () -> SodiumExtraClientMod
                                        .options().renderSettings.fogSettings.protectedGameplay.waterDistanceBlocks)
                        .setDefaultValue(0)));

        page.addOptionGroup(builder.createOptionGroup()
                .addOption(builder.createBooleanOption(id("light_updates"))
                        .setEnabled(
                                false)
                        .setName(Component.translatable("sodium-extra.option.light_updates_starrailexpress"))
                        .setTooltip(Component.translatable("sodium-extra.option.light_updates_starrailexpress.tooltip"))
                        .setBinding((value) -> {
                        },
                                () -> true)
                        .setStorageHandler(SodiumExtraClientMod.options())
                        .setDefaultValue(true)));
        page.addOptionGroup(builder.createOptionGroup()
                .addOption(builder.createBooleanOption(id("item_frame"))
                        .setEnabled(
                                SodiumExtraClientMod.mixinConfig().getOptions().get("mixin.render.entity").isEnabled())
                        .setName(parseVanillaString("entity.minecraft.item_frame"))
                        .setTooltip(Component.translatable("sodium-extra.option.item_frames.tooltip"))
                        .setBinding((value) -> SodiumExtraClientMod.options().renderSettings.itemFrame = value,
                                () -> SodiumExtraClientMod.options().renderSettings.itemFrame)
                        .setStorageHandler(SodiumExtraClientMod.options())
                        .setDefaultValue(true))
                .addOption(builder.createBooleanOption(id("armor_stands"))
                        .setEnabled(
                                SodiumExtraClientMod.mixinConfig().getOptions().get("mixin.render.entity").isEnabled())
                        .setName(parseVanillaString("entity.minecraft.armor_stand"))
                        .setTooltip(Component.translatable("sodium-extra.option.armor_stands.tooltip"))
                        .setBinding((value) -> SodiumExtraClientMod.options().renderSettings.armorStand = value,
                                () -> SodiumExtraClientMod.options().renderSettings.armorStand)
                        .setStorageHandler(SodiumExtraClientMod.options())
                        .setDefaultValue(true))
                .addOption(builder.createBooleanOption(id("paintings"))
                        .setEnabled(
                                SodiumExtraClientMod.mixinConfig().getOptions().get("mixin.render.entity").isEnabled())
                        .setName(parseVanillaString("entity.minecraft.painting"))
                        .setTooltip(Component.translatable("sodium-extra.option.paintings.tooltip"))
                        .setBinding((value) -> SodiumExtraClientMod.options().renderSettings.painting = value,
                                () -> SodiumExtraClientMod.options().renderSettings.painting)
                        .setStorageHandler(SodiumExtraClientMod.options())
                        .setDefaultValue(true)));
        page.addOptionGroup(builder.createOptionGroup()
                .addOption(builder.createBooleanOption(id("beacon_beam"))
                        .setEnabled(SodiumExtraClientMod.mixinConfig().getOptions().get("mixin.render.block.entity")
                                .isEnabled())
                        .setName(Component.translatable("sodium-extra.option.beacon_beam"))
                        .setTooltip(Component.translatable("sodium-extra.option.beacon_beam.tooltip"))
                        .setBinding((value) -> SodiumExtraClientMod.options().renderSettings.beaconBeam = value,
                                () -> SodiumExtraClientMod.options().renderSettings.beaconBeam)
                        .setStorageHandler(SodiumExtraClientMod.options())
                        .setDefaultValue(true))
                .addOption(builder.createBooleanOption(id("limit_beacon_beam_height"))
                        .setEnabled(SodiumExtraClientMod.mixinConfig().getOptions().get("mixin.render.block.entity")
                                .isEnabled())
                        .setName(Component.translatable("sodium-extra.option.limit_beacon_beam_height"))
                        .setTooltip(Component.translatable("sodium-extra.option.limit_beacon_beam_height.tooltip"))
                        .setBinding(
                                (value) -> SodiumExtraClientMod.options().renderSettings.limitBeaconBeamHeight = value,
                                () -> SodiumExtraClientMod.options().renderSettings.limitBeaconBeamHeight)
                        .setStorageHandler(SodiumExtraClientMod.options())
                        .setDefaultValue(false))
                .addOption(builder.createBooleanOption(id("enchanting_table_book"))
                        .setEnabled(SodiumExtraClientMod.mixinConfig().getOptions().get("mixin.render.block.entity")
                                .isEnabled())
                        .setName(Component.translatable("sodium-extra.option.enchanting_table_book"))
                        .setTooltip(Component.translatable("sodium-extra.option.enchanting_table_book.tooltip"))
                        .setBinding(
                                (value) -> SodiumExtraClientMod.options().renderSettings.enchantingTableBook = value,
                                () -> SodiumExtraClientMod.options().renderSettings.enchantingTableBook)
                        .setStorageHandler(SodiumExtraClientMod.options())
                        .setDefaultValue(true))
                .addOption(builder.createBooleanOption(id("piston"))
                        .setEnabled(SodiumExtraClientMod.mixinConfig().getOptions().get("mixin.render.block.entity")
                                .isEnabled())
                        .setName(parseVanillaString("block.minecraft.piston"))
                        .setTooltip(Component.translatable("sodium-extra.option.piston.tooltip"))
                        .setBinding((value) -> SodiumExtraClientMod.options().renderSettings.piston = value,
                                () -> SodiumExtraClientMod.options().renderSettings.piston)
                        .setStorageHandler(SodiumExtraClientMod.options())
                        .setDefaultValue(true)));
        page.addOptionGroup(builder.createOptionGroup()
                .addOption(builder.createBooleanOption(id("item_frame_name_tag"))
                        .setEnabled(
                                SodiumExtraClientMod.mixinConfig().getOptions().get("mixin.render.entity").isEnabled())
                        .setName(Component.translatable("sodium-extra.option.item_frame_name_tag"))
                        .setTooltip(Component.translatable("sodium-extra.option.item_frame_name_tag.tooltip"))
                        .setBinding((value) -> SodiumExtraClientMod.options().renderSettings.itemFrameNameTag = value,
                                () -> SodiumExtraClientMod.options().renderSettings.itemFrameNameTag)
                        .setStorageHandler(SodiumExtraClientMod.options())
                        .setDefaultValue(true))
                .addOption(builder.createBooleanOption(id("player_name_tag"))
                        .setEnabled(
                                SodiumExtraClientMod.mixinConfig().getOptions().get("mixin.render.entity").isEnabled())
                        .setName(Component.translatable("sodium-extra.option.player_name_tag"))
                        .setTooltip(Component.translatable("sodium-extra.option.player_name_tag.tooltip"))
                        .setBinding((value) -> SodiumExtraClientMod.options().renderSettings.playerNameTag = value,
                                () -> SodiumExtraClientMod.options().renderSettings.playerNameTag)
                        .setStorageHandler(SodiumExtraClientMod.options())
                        .setDefaultValue(true)));

        return page;
    }

}
