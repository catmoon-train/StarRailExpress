package cn.zbx1425.minopp.gui;

import cn.zbx1425.minopp.entity.EntityAutoPlayer;
import cn.zbx1425.minopp.network.C2SAutoPlayerConfigPacket;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AutoPlayerScreen {

    public static Screen create(EntityAutoPlayer target, Screen parent) {
        ConfigCategory.Builder categoryBuilder = ConfigCategory.createBuilder();
        putEntityPreferences(categoryBuilder, target);
        putAIPreferences(categoryBuilder, target);
        putDismantlePreferences(categoryBuilder, target);
        return YetAnotherConfigLib.createBuilder()
                .title(Component.translatable("gui.minopp.bot_config.title"))
                .category(categoryBuilder.name(Component.translatable("gui.minopp.bot_config.title")).build())
                .save(() -> C2SAutoPlayerConfigPacket.Client.sendC2S(target))
                .build()
                .generateScreen(parent);
    }

    private static void putEntityPreferences(ConfigCategory.Builder builder, EntityAutoPlayer target) {
        OptionGroup entityOpts = OptionGroup.createBuilder()
                .name(Component.translatable("gui.minopp.bot_config.category.entity"))
                .option(Option.<Boolean>createBuilder()
                        .name(Component.translatable("gui.minopp.bot_config.active"))
                        .binding(true, target::getActive, target::setActive)
                        .controller(opt -> BooleanControllerBuilder.create(opt).onOffFormatter())
                        .build()
                )
                .option(Option.<String>createBuilder()
                        .name(Component.translatable("gui.minopp.bot_config.custom_name"))
                        .binding("",
                                () -> target.hasCustomName() ? target.getCustomName().getString() : "",
                                v -> target.setCustomName(v.isEmpty() ? null : Component.literal(v)))
                        .controller(opt -> StringControllerBuilder.create(opt))
                        .build()
                )
                .option(Option.<String>createBuilder()
                        .name(Component.translatable("gui.minopp.bot_config.skin"))
                        .description(OptionDescription.of(Component.translatable("gui.minopp.bot_config.skin.summary")))
                        .binding("", target::getSkin, target::setSkin)
                        .controller(opt -> StringControllerBuilder.create(opt))
                        .build()
                )
                .option(Option.<Boolean>createBuilder()
                        .name(Component.translatable("gui.minopp.bot_config.no_push"))
                        .description(OptionDescription.of(Component.translatable("gui.minopp.bot_config.no_push.summary")))
                        .binding(false, target::getNoPush, target::setNoPush)
                        .controller(opt -> BooleanControllerBuilder.create(opt).yesNoFormatter())
                        .build()
                )
                .build();
        builder.group(entityOpts);
    }

    private static void putAIPreferences(ConfigCategory.Builder builder, EntityAutoPlayer target) {
        OptionGroup aiOpts = OptionGroup.createBuilder()
                .name(Component.translatable("gui.minopp.bot_config.category.difficulty"))
                .option(Option.<Boolean>createBuilder()
                        .name(Component.translatable("gui.minopp.bot_config.no_win"))
                        .description(OptionDescription.of(Component.translatable("gui.minopp.bot_config.no_win.summary")))
                        .binding(false, () -> target.autoPlayer.aiNoWin, value -> target.autoPlayer.aiNoWin = value)
                        .controller(opt -> BooleanControllerBuilder.create(opt).yesNoFormatter())
                        .build()
                )
                .option(Option.<Boolean>createBuilder()
                        .name(Component.translatable("gui.minopp.bot_config.no_player_draw"))
                        .description(OptionDescription.of(Component.translatable("gui.minopp.bot_config.no_player_draw.summary")))
                        .binding(false, () -> target.autoPlayer.aiNoPlayerDraw, value -> target.autoPlayer.aiNoPlayerDraw = value)
                        .controller(opt -> BooleanControllerBuilder.create(opt).yesNoFormatter())
                        .build()
                )
                .option(Option.<Float>createBuilder()
                        .name(Component.translatable("gui.minopp.bot_config.forget_chance"))
                        .description(OptionDescription.of(Component.translatable("gui.minopp.bot_config.forget_chance.summary")))
                        .binding(0.2f, () -> target.autoPlayer.aiForgetChance, value -> target.autoPlayer.aiForgetChance = value)
                        .controller(opt -> FloatSliderControllerBuilder.create(opt).range(0f, 1f).step(0.05f)
                                .formatValue(f -> Component.literal("%d%%".formatted((int)(f * 100)))))
                        .build()
                )
                .build();
        OptionGroup aiMetaOpts = OptionGroup.createBuilder()
                .name(Component.translatable("gui.minopp.bot_config.category.behavior"))
                .option(Option.<Integer>createBuilder()
                        .name(Component.translatable("gui.minopp.bot_config.no_delay"))
                        .binding(0, () -> (int)target.autoPlayer.aiNoDelay, value -> target.autoPlayer.aiNoDelay = (byte)(int)value)
                        .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(0, 2).step(1).formatValue(i ->
                                Component.translatable("gui.minopp.bot_config.no_delay." + i)))
                        .build()
                )
                .option(Option.<Boolean>createBuilder()
                        .name(Component.translatable("gui.minopp.bot_config.start_game"))
                        .description(OptionDescription.of(Component.translatable("gui.minopp.bot_config.start_game.summary")))
                        .binding(false, () -> target.autoPlayer.aiStartGame, value -> target.autoPlayer.aiStartGame = value)
                        .controller(opt -> BooleanControllerBuilder.create(opt).yesNoFormatter())
                        .build()
                )
                .build();
        builder.group(aiOpts).group(aiMetaOpts);
    }

    private static void putDismantlePreferences(ConfigCategory.Builder builder, EntityAutoPlayer target) {
        OptionGroup dismantleOpts = OptionGroup.createBuilder()
                .name(Component.translatable("gui.minopp.bot_config.remove"))
                .option(ButtonOption.createBuilder()
                        .name(Component.translatable("gui.minopp.bot_config.remove").withStyle(ChatFormatting.RED))
                        .action((screen, opt) -> {
                            C2SAutoPlayerConfigPacket.Client.sendDeleteC2S(target);
                            screen.onClose();
                        })
                        .build()
                )
                .build();
        builder.group(dismantleOpts);
    }
}
