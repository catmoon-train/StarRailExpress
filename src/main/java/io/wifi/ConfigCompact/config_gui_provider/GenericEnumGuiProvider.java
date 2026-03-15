package io.wifi.ConfigCompact.config_gui_provider;

import me.shedaniel.autoconfig.gui.registry.GuiRegistry;
import me.shedaniel.autoconfig.gui.registry.api.GuiRegistryAccess;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Environment(EnvType.CLIENT)
public class GenericEnumGuiProvider {

    public static void register(GuiRegistry registry) {
        registry.registerPredicateProvider(
                GenericEnumGuiProvider::provide,
                field -> field.getType().isEnum()
        );
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static List<AbstractConfigListEntry> provide(
            String i18n, Field field, Object config, Object defaults,
            GuiRegistryAccess access) {

        Class<Enum> enumType = (Class<Enum>) field.getType();

        Enum currentValue;
        Enum defaultValue;
        try {
            field.setAccessible(true);
            currentValue = (Enum) field.get(config);
            defaultValue = (Enum) field.get(defaults);
            // 字段为 null 时降级到第一个枚举值
            if (currentValue == null) currentValue = enumType.getEnumConstants()[0];
            if (defaultValue == null) defaultValue = enumType.getEnumConstants()[0];
        } catch (IllegalAccessException e) {
            return Collections.emptyList();
        }

        Enum finalCurrentValue = currentValue;
        Enum finalDefaultValue = defaultValue;

        var widget = ConfigEntryBuilder.create()
                .startEnumSelector(
                        Component.translatable(i18n),
                        enumType,
                        finalCurrentValue
                )
                .setDefaultValue(finalDefaultValue)
                // ✅ 每个枚举值的显示名：优先用翻译键，fallback 到枚举常量名
                .setEnumNameProvider(value ->
                        Component.translatableWithFallback(
                                i18n + "." + value.name(),
                                value.name()
                        )
                )
                // ✅ tooltip：有翻译键则显示，否则不显示
                .setTooltipSupplier(value -> {
                    String tooltipKey = i18n + "." + value.name() + ".tooltip";
                    Component tip = Component.translatable(tooltipKey);
                    // 翻译键不存在时 getText() 会原样返回键名，以此判断
                    if (tip.getString().equals(tooltipKey)) return Optional.empty();
                    return Optional.of(new Component[]{ tip });
                })
                .setSaveConsumer(v -> {
                    try {
                        field.setAccessible(true);
                        field.set(config, v);
                    } catch (IllegalAccessException ignored) {}
                })
                .build();

        return Collections.singletonList(widget);
    }
}