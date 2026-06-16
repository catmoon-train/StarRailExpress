package io.wifi.ConfigCompact.config_gui_provider;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.agmas.noellesroles.config.NoellesRolesConfig.RoleSpawnInfoEntries;
import org.agmas.noellesroles.config.NoellesRolesConfig.SpawnInfo;
import org.agmas.noellesroles.utils.RoleUtils;

import me.shedaniel.autoconfig.gui.registry.GuiRegistry;
import me.shedaniel.autoconfig.gui.registry.api.GuiRegistryAccess;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.MultiElementListEntry;
import me.shedaniel.clothconfig2.gui.entries.NestedListListEntry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

@Environment(EnvType.CLIENT)
public class SpawnInfoGuiProvider {

    private static class EditableEntry {
        String key;
        Component name;
        SpawnInfo spawnInfo;

        EditableEntry(String key, SpawnInfo info) {
            this.key = key;
            this.spawnInfo = info;
            this.name = Component.literal(key != null ? key : "(empty)");
        }

        EditableEntry(String key, SpawnInfo info, Component name) {
            this.key = key;
            this.spawnInfo = info;
            this.name = name != null ? name : Component.literal(key != null ? key : "(empty)");
        }
    }

    public static void register(GuiRegistry registry) {
        registry.registerPredicateProvider(
                SpawnInfoGuiProvider::provide,
                field -> field.getType() == RoleSpawnInfoEntries.class);
    }

    @SuppressWarnings({ "rawtypes" })
    private static List<AbstractConfigListEntry> provide(
            String i18n,
            Field field,
            Object config,
            Object defaults,
            GuiRegistryAccess access) {

        RoleSpawnInfoEntries currentObj = (RoleSpawnInfoEntries) getFieldValue(field, config);
        RoleSpawnInfoEntries defaultObj = (RoleSpawnInfoEntries) getFieldValue(field, defaults);

        if (currentObj == null) {
            currentObj = new RoleSpawnInfoEntries();
            if (defaultObj != null) {
                currentObj = defaultObj;
            }
            setFieldValue(field, config, currentObj);
        }
        if (defaultObj == null) {
            if (currentObj.type == 1) {
                defaultObj = RoleSpawnInfoEntries.createDefaultRoleInfo();
            } else if (currentObj.type == 2) {
                defaultObj = RoleSpawnInfoEntries.createDefaultModifierInfo();
            } else {
                defaultObj = new RoleSpawnInfoEntries();
            }
            setFieldValue(field, defaults, defaultObj);
        }

        // ✅ 关键修正：使用 defaultObj.type 确保名称正确
        List<EditableEntry> entries = toEditableList(currentObj.maps, currentObj.type);
        List<EditableEntry> defaultEntries = toEditableList(defaultObj.maps, defaultObj.type);

        int pageSize = 20;
        List<AbstractConfigListEntry<?>> pageEntries = new ArrayList<>();

        // 构建分页
        if (entries.isEmpty()) {
            NestedListListEntry<EditableEntry, MultiElementListEntry<EditableEntry>> emptyPage = new NestedListListEntry<EditableEntry, MultiElementListEntry<EditableEntry>>(
                    Component.literal("0-0"),
                    Collections.emptyList(),
                    false,
                    () -> Optional.empty(),
                    updated -> saveAllEntries(field, config, collectAllEntries(pageEntries)),
                    () -> Collections.emptyList(),
                    Component.translatable("text.cloth-config.reset_value"),
                    false,
                    false,
                    (entry, self) -> buildRow(new EditableEntry("", new SpawnInfo()), i18n));
            pageEntries.add(emptyPage);
        } else {
            for (int i = 0; i < entries.size(); i += pageSize) {
                int start = i;
                int end = Math.min(i + pageSize, entries.size());
                List<EditableEntry> subEntries = entries.subList(start, end);
                List<EditableEntry> subDefaults = defaultEntries.size() > start
                        ? defaultEntries.subList(start, Math.min(end, defaultEntries.size()))
                        : Collections.emptyList();

                Component pageTitle = Component.literal((start + 1) + "-" + end);

                NestedListListEntry<EditableEntry, MultiElementListEntry<EditableEntry>> pageList = new NestedListListEntry<EditableEntry, MultiElementListEntry<EditableEntry>>(
                        pageTitle,
                        subEntries,
                        false,
                        () -> Optional.empty(),
                        updatedSubList -> saveAllEntries(field, config, collectAllEntries(pageEntries)),
                        () -> subDefaults,
                        Component.translatable("text.cloth-config.reset_value"),
                        false,
                        false,
                        (entry, self) -> buildRow(entry != null ? entry : new EditableEntry("", new SpawnInfo()),
                                i18n));
                pageEntries.add(pageList);
            }
        }

        // 父级容器
        MultiElementListEntry<Object> parentEntry = new MultiElementListEntry<Object>(
                Component.translatable(i18n),
                null,
                pageEntries,
                false);

        return Collections.singletonList(parentEntry);
    }

    private static List<EditableEntry> collectAllEntries(List<AbstractConfigListEntry<?>> pageEntries) {
        List<EditableEntry> all = new ArrayList<>();
        for (AbstractConfigListEntry<?> entry : pageEntries) {
            if (entry instanceof NestedListListEntry) {
                @SuppressWarnings("unchecked")
                NestedListListEntry<EditableEntry, MultiElementListEntry<EditableEntry>> page = (NestedListListEntry<EditableEntry, MultiElementListEntry<EditableEntry>>) entry;
                all.addAll(page.getValue());
            }
        }
        return all;
    }

    private static void saveAllEntries(Field field, Object config, List<EditableEntry> allEntries) {
        saveBack(field, config, allEntries);
    }

    // 构建每个条目（key-value 行）
    private static MultiElementListEntry<EditableEntry> buildRow(EditableEntry entry, String i18n) {
        ConfigEntryBuilder.create();

        List<AbstractConfigListEntry<?>> controls = buildSpawnInfoControls(entry.spawnInfo, i18n);

        // ✅ 使用 entry.name 作为显示标题，如果 name 为空则 fallback 到 key
        Component title = entry.name != null ? entry.name
                : Component.literal(entry.key != null ? entry.key : "(empty)");

        return new MultiElementListEntry<EditableEntry>(
                title,
                entry,
                controls,
                false);
    }

    // 反射生成 SpawnInfo 字段控件
    private static List<AbstractConfigListEntry<?>> buildSpawnInfoControls(SpawnInfo info, String i18n) {
        List<AbstractConfigListEntry<?>> list = new ArrayList<>();
        ConfigEntryBuilder eb = ConfigEntryBuilder.create();

        for (Field field : SpawnInfo.class.getDeclaredFields()) {
            field.setAccessible(true);
            String fieldName = field.getName();
            String translationKey = i18n + "." + fieldName;

            try {
                Class<?> type = field.getType();
                Object currentValue = field.get(info);
                AbstractConfigListEntry<?> control = createControlForField(
                        eb, translationKey, type, currentValue, field, info);
                if (control != null) {
                    list.add(control);
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return list;
    }

    // 根据字段类型创建控件
    @SuppressWarnings("unchecked")
    private static AbstractConfigListEntry<?> createControlForField(
            ConfigEntryBuilder eb,
            String translationKey,
            Class<?> type,
            Object currentValue,
            Field field,
            SpawnInfo info) {

        Component label = Component.translatable(translationKey);

        if (type == int.class || type == Integer.class) {
            int val = currentValue != null ? (int) currentValue : 0;
            return eb.startIntField(label, val)
                    .setDefaultValue(0)
                    .setSaveConsumer(v -> {
                        try {
                            field.set(info, v);
                        } catch (IllegalAccessException ignored) {
                        }
                    })
                    .build();
        }
        if (type == long.class || type == Long.class) {
            long val = currentValue != null ? (long) currentValue : 0L;
            return eb.startLongField(label, val)
                    .setDefaultValue(0L)
                    .setSaveConsumer(v -> {
                        try {
                            field.set(info, v);
                        } catch (IllegalAccessException ignored) {
                        }
                    })
                    .build();
        }
        if (type == float.class || type == Float.class) {
            float val = currentValue != null ? (float) currentValue : 0f;
            return eb.startFloatField(label, val)
                    .setDefaultValue(0f)
                    .setSaveConsumer(v -> {
                        try {
                            field.set(info, v);
                        } catch (IllegalAccessException ignored) {
                        }
                    })
                    .build();
        }
        if (type == double.class || type == Double.class) {
            double val = currentValue != null ? (double) currentValue : 0.0;
            return eb.startDoubleField(label, val)
                    .setDefaultValue(0.0)
                    .setSaveConsumer(v -> {
                        try {
                            field.set(info, v);
                        } catch (IllegalAccessException ignored) {
                        }
                    })
                    .build();
        }
        if (type == boolean.class || type == Boolean.class) {
            boolean val = currentValue != null && (boolean) currentValue;
            return eb.startBooleanToggle(label, val)
                    .setDefaultValue(false)
                    .setSaveConsumer(v -> {
                        try {
                            field.set(info, v);
                        } catch (IllegalAccessException ignored) {
                        }
                    })
                    .build();
        }
        if (type == String.class) {
            String val = currentValue != null ? (String) currentValue : "";
            return eb.startStrField(label, val)
                    .setDefaultValue("")
                    .setSaveConsumer(v -> {
                        try {
                            field.set(info, v);
                        } catch (IllegalAccessException ignored) {
                        }
                    })
                    .build();
        }
        if (type == ArrayList.class) {
            Type genericType = field.getGenericType();
            if (genericType instanceof ParameterizedType pt) {
                Type[] args = pt.getActualTypeArguments();
                if (args.length == 1 && args[0] == String.class) {
                    List<String> currentList = (List<String>) currentValue;
                    if (currentList == null) {
                        currentList = new ArrayList<>();
                    }
                    List<String> finalCurrentList = currentList;
                    return new StringListListEntry(
                            label,
                            finalCurrentList,
                            false,
                            () -> Optional.empty(),
                            newList -> {
                                try {
                                    field.set(info, newList);
                                } catch (IllegalAccessException ignored) {
                                }
                            },
                            ArrayList::new,
                            Component.translatable("text.cloth-config.reset_value"),
                            false,
                            true,
                            false);
                }
            }
        }
        // fallback
        String val = currentValue != null ? currentValue.toString() : "";
        return eb.startStrField(label, val)
                .setDefaultValue("")
                .setSaveConsumer(v -> {
                    try {
                        field.set(info, v);
                    } catch (IllegalAccessException ignored) {
                    }
                })
                .build();
    }

    // ---- 工具方法 ----
    private static Object getFieldValue(Field field, Object obj) {
        try {
            field.setAccessible(true);
            return field.get(obj);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private static void setFieldValue(Field field, Object obj, Object value) {
        try {
            field.setAccessible(true);
            field.set(obj, value);
        } catch (IllegalAccessException ignored) {
        }
    }

    private static List<EditableEntry> toEditableList(HashMap<ResourceLocation, SpawnInfo> map, int type) {
        if (map == null)
            return new ArrayList<>();
        return map.entrySet().stream()
                .map(e -> new EditableEntry(e.getKey().toString(), cloneSpawnInfo(e.getValue()),
                        getKeyName(e.getKey(), type)))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    // ✅ 根据 type 返回正确的友好名称
    private static Component getKeyName(ResourceLocation key, int type) {
        if (type == 1) {
            // 角色类型：假设 RoleUtils.getRoleName 存在
            return RoleUtils.getRoleName(key).append(Component.translatable(" (%s)", key.toString()));
        } else if (type == 2) {
            // 修饰符类型
            return RoleUtils.getModifierName(key).append(Component.translatable(" (%s)", key.toString()));
        }
        // 未知类型则返回 key 本身的字符串
        return Component.literal(key.toString());
    }

    private static SpawnInfo cloneSpawnInfo(SpawnInfo original) {
        if (original == null)
            return new SpawnInfo();
        SpawnInfo copy = new SpawnInfo(
                original.minEnabledPlayer,
                original.maxEnabledPlayer,
                original.maxSpawn,
                original.enableChance);
        if (original.map != null) {
            copy.map = new ArrayList<>(original.map);
        }
        return copy;
    }

    // ✅ 保存时保留原有的 type
    private static void saveBack(Field field, Object config, List<EditableEntry> updated) {
        RoleSpawnInfoEntries obj = (RoleSpawnInfoEntries) getFieldValue(field, config);
        if (obj == null) {
            obj = new RoleSpawnInfoEntries();
            setFieldValue(field, config, obj);
        }
        int originalType = obj.type; // 保留 type

        HashMap<ResourceLocation, SpawnInfo> newMap = new HashMap<>();
        for (EditableEntry e : updated) {
            if (e.key == null || e.key.isBlank())
                continue;
            ResourceLocation rl = ResourceLocation.tryParse(e.key);
            if (rl == null)
                continue;
            newMap.put(rl, e.spawnInfo);
        }
        obj.maps = newMap;
        obj.type = originalType; // 确保 type 不被意外修改
    }
}