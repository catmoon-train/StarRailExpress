package io.wifi.starrailexpress.mixin.compat.sodium_extra;

import com.google.common.collect.ImmutableList;
import io.wifi.starrailexpress.client.SREClient;
import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;
import me.flashyreese.mods.sodiumextra.client.gui.SodiumExtraGameOptionPages;
import me.flashyreese.mods.sodiumextra.client.gui.SodiumExtraGameOptions;
import me.flashyreese.mods.sodiumextra.client.gui.options.control.SliderControlExtended;
import me.flashyreese.mods.sodiumextra.client.gui.options.storage.SodiumExtraOptionsStorage;
import me.flashyreese.mods.sodiumextra.common.util.ControlValueFormatterExtended;
import net.caffeinemc.mods.sodium.client.gui.options.OptionFlag;
import net.caffeinemc.mods.sodium.client.gui.options.OptionGroup;
import net.caffeinemc.mods.sodium.client.gui.options.OptionImpl;
import net.caffeinemc.mods.sodium.client.gui.options.OptionPage;
import net.caffeinemc.mods.sodium.client.gui.options.control.ControlValueFormatter;
import net.caffeinemc.mods.sodium.client.gui.options.control.TickBoxControl;
import net.caffeinemc.mods.sodium.client.gui.options.storage.MinecraftOptionsStorage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mixin(value = SodiumExtraGameOptionPages.class, remap = false)
public class SodiumExtraDisableOptionMixin {
    private static Component parseVanillaString(String key) {
        return Component.literal((Component.translatable(key).getString()).replaceAll("§.", ""));
    }

    @Overwrite
    public static OptionPage render() {
        List<OptionGroup> groups = new ArrayList<>();
        SodiumExtraOptionsStorage sodiumExtraOpts = SodiumExtraGameOptionPages.sodiumExtraOpts;
        MinecraftOptionsStorage vanillaOpts = SodiumExtraGameOptionPages.vanillaOpts;
        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(boolean.class, sodiumExtraOpts)
                        .setEnabled(() -> SodiumExtraClientMod.mixinConfig().getOptions().get("mixin.fog").isEnabled())
                        .setName(Component.translatable("sodium-extra.option.multi_dimension_fog"))
                        .setTooltip(Component.translatable("sodium-extra.option.multi_dimension_fog.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((options, value) -> options.renderSettings.multiDimensionFogControl = value,
                                options -> options.renderSettings.multiDimensionFogControl)
                        .build())
                .add(OptionImpl.createBuilder(int.class, sodiumExtraOpts)
                        .setEnabled(() -> SodiumExtraClientMod.mixinConfig().getOptions().get("mixin.fog_falloff")
                                .isEnabled())
                        .setName(Component.translatable("sodium-extra.option.fog_start"))
                        .setTooltip(Component.translatable("sodium-extra.option.fog_start.tooltip"))
                        .setControl(option -> new SliderControlExtended(option, 20, 100, 1,
                                ControlValueFormatter.percentage(), false))
                        .setBinding((options, value) -> options.renderSettings.fogStart = value,
                                options -> options.renderSettings.fogStart)
                        .build())
                .build());

        if (SodiumExtraClientMod.options().renderSettings.multiDimensionFogControl) {
            WorldDimensions
                    .keysInOrder(Stream.empty())
                    .filter(dim -> !SodiumExtraClientMod.options().renderSettings.dimensionFogDistanceMap
                            .containsKey(dim.location()))
                    .forEach(dim -> SodiumExtraClientMod.options().renderSettings.dimensionFogDistanceMap
                            .put(dim.location(), 0));
            groups.add(SodiumExtraClientMod.options().renderSettings.dimensionFogDistanceMap.keySet().stream()
                    .map(identifier -> (OptionImpl<SodiumExtraGameOptions, Integer>) OptionImpl
                            .createBuilder(Integer.class, sodiumExtraOpts)
                            .setEnabled(
                                    () -> SodiumExtraClientMod.mixinConfig().getOptions().get("mixin.fog").isEnabled())
                            .setName(Component.translatable("sodium-extra.option.fog",
                                    translatableName(identifier, "dimensions").getString()))
                            .setTooltip(Component.translatable("sodium-extra.option.fog.tooltip"))
                            .setControl(option -> new SliderControlExtended(option, 0, 33, 1,
                                    ControlValueFormatterExtended.fogDistance(), false))
                            .setBinding((opts, val) -> opts.renderSettings.dimensionFogDistanceMap.put(identifier, val),
                                    opts -> opts.renderSettings.dimensionFogDistanceMap.getOrDefault(identifier, 0))
                            .build())
                    .collect(
                            OptionGroup::createBuilder,
                            OptionGroup.Builder::add,
                            (b1, b2) -> {
                            })
                    .build());
        } else {
            groups.add(OptionGroup.createBuilder()
                    .add(OptionImpl.createBuilder(int.class, sodiumExtraOpts)
                            .setEnabled(
                                    () -> SodiumExtraClientMod.mixinConfig().getOptions().get("mixin.fog").isEnabled())
                            .setName(Component.translatable("sodium-extra.option.single_fog"))
                            .setTooltip(Component.translatable("sodium-extra.option.single_fog.tooltip"))
                            .setControl(option -> new SliderControlExtended(option, 0, 33, 1,
                                    ControlValueFormatterExtended.fogDistance(), false))
                            .setBinding((options, value) -> options.renderSettings.fogDistance = value,
                                    options -> options.renderSettings.fogDistance)
                            .build())
                    .build());
        }

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(boolean.class, sodiumExtraOpts)
                        .setEnabled(() -> SREClient.isInLobby())
                        .setName(Component.translatable("sodium-extra.option.light_updates_starrailexpress"))
                        .setTooltip(Component.translatable("sodium-extra.option.light_updates_starrailexpress.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((options, value) -> options.renderSettings.lightUpdates = true,
                                options -> options.renderSettings.lightUpdates)
                        .build())
                .build());
        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(boolean.class, sodiumExtraOpts)
                        .setEnabled(() -> SREClient.isInLobby())
                        .setName(parseVanillaString("entity.minecraft.item_frame"))
                        .setTooltip(Component.translatable("sodium-extra.option.item_frames.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.renderSettings.itemFrame = value,
                                opts -> opts.renderSettings.itemFrame)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, sodiumExtraOpts)
                        .setEnabled(() -> SREClient.isInLobby())
                        .setName(parseVanillaString("entity.minecraft.armor_stand"))
                        .setTooltip(Component.translatable("sodium-extra.option.armor_stands.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((options, value) -> options.renderSettings.armorStand = value,
                                options -> options.renderSettings.armorStand)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, sodiumExtraOpts)
                        .setEnabled(() -> SREClient.isInLobby())
                        .setName(parseVanillaString("entity.minecraft.painting"))
                        .setTooltip(Component.translatable("sodium-extra.option.paintings.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((options, value) -> options.renderSettings.painting = value,
                                options -> options.renderSettings.painting)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .build());
        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(boolean.class, sodiumExtraOpts)
                        .setEnabled(() -> SodiumExtraClientMod.mixinConfig().getOptions()
                                .get("mixin.render.block.entity").isEnabled())
                        .setName(Component.translatable("sodium-extra.option.beacon_beam"))
                        .setTooltip(Component.translatable("sodium-extra.option.beacon_beam.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.renderSettings.beaconBeam = value,
                                opts -> opts.renderSettings.beaconBeam)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, sodiumExtraOpts)
                        .setEnabled(() -> SodiumExtraClientMod.mixinConfig().getOptions()
                                .get("mixin.render.block.entity").isEnabled())
                        .setName(Component.translatable("sodium-extra.option.limit_beacon_beam_height"))
                        .setTooltip(Component.translatable("sodium-extra.option.limit_beacon_beam_height.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.renderSettings.limitBeaconBeamHeight = value,
                                opts -> opts.renderSettings.limitBeaconBeamHeight)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, sodiumExtraOpts)
                        .setEnabled(() -> SodiumExtraClientMod.mixinConfig().getOptions()
                                .get("mixin.render.block.entity").isEnabled())
                        .setName(Component.translatable("sodium-extra.option.enchanting_table_book"))
                        .setTooltip(Component.translatable("sodium-extra.option.enchanting_table_book.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.renderSettings.enchantingTableBook = value,
                                opts -> opts.renderSettings.enchantingTableBook)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, sodiumExtraOpts)
                        .setEnabled(() -> SodiumExtraClientMod.mixinConfig().getOptions()
                                .get("mixin.render.block.entity").isEnabled())
                        .setName(parseVanillaString("block.minecraft.piston"))
                        .setTooltip(Component.translatable("sodium-extra.option.piston.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((options, value) -> options.renderSettings.piston = value,
                                options -> options.renderSettings.piston)
                        .build())
                .build());
        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(boolean.class, sodiumExtraOpts)
                        .setEnabled(() -> SREClient.isInLobby())
                        .setName(Component.translatable("sodium-extra.option.item_frame_name_tag"))
                        .setTooltip(Component.translatable("sodium-extra.option.item_frame_name_tag.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.renderSettings.itemFrameNameTag = value,
                                opts -> opts.renderSettings.itemFrameNameTag)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, sodiumExtraOpts)
                        .setEnabled(() -> SREClient.isInLobby())
                        .setName(Component.translatable("sodium-extra.option.player_name_tag"))
                        .setTooltip(Component.translatable("sodium-extra.option.player_name_tag.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((options, value) -> options.renderSettings.playerNameTag = value,
                                options -> options.renderSettings.playerNameTag)
                        .build())
                .build());
        return new OptionPage(Component.translatable("sodium-extra.option.render"), ImmutableList.copyOf(groups));
    }

    private static Component translatableName(ResourceLocation identifier, String category) {
        String key = identifier.toLanguageKey("options.".concat(category));

        Component translatable = Component.translatable(key);
        if (!ComponentUtils.isTranslationResolvable(translatable)) {
            translatable = Component.literal(
                    Arrays.stream(key.substring(key.lastIndexOf('.') + 1).split("_"))
                            .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1))
                            .collect(Collectors.joining(" ")));
        }
        return translatable;
    }
}