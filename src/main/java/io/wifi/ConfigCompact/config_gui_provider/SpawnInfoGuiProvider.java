package io.wifi.ConfigCompact.config_gui_provider;

import com.google.common.collect.Iterators;
import me.shedaniel.autoconfig.gui.registry.GuiRegistry;
import me.shedaniel.autoconfig.gui.registry.api.GuiRegistryAccess;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.MultiElementListEntry;
import me.shedaniel.clothconfig2.gui.entries.SubCategoryListEntry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.config.NoellesRolesConfig.RoleSpawnInfoEntries;
import org.agmas.noellesroles.config.NoellesRolesConfig.SpawnInfo;
import org.agmas.noellesroles.utils.RoleUtils;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.text.Collator;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Environment(EnvType.CLIENT)
@SuppressWarnings({"deprecation","rawtypes"})
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

    private static class SavableSubCategoryEntry extends SubCategoryListEntry {
        private final Runnable saveCallback;

        public SavableSubCategoryEntry(Component fieldName, List<AbstractConfigListEntry> children,
                boolean expanded, Runnable saveCallback) {
            super(fieldName, children, expanded);
            this.saveCallback = saveCallback;
        }

        @Override
        public void save() {
            super.save();
            if (saveCallback != null) {
                saveCallback.run();
            }
        }
    }

    private static class SearchableRowEntry extends MultiElementListEntry<EditableEntry> {
        private final EditableEntry entry;

        public SearchableRowEntry(Component title, EditableEntry entry,
                List<AbstractConfigListEntry<?>> children,
                boolean defaultExpanded) {
            super(title, entry, children, defaultExpanded);
            this.entry = entry;
        }

        public EditableEntry getEntry() {
            return entry;
        }

        @Override
        public Iterator<String> getSearchTags() {
            Iterator<String> base = super.getSearchTags();
            if (entry == null) {
                return base;
            }
            String keyTag = entry.key != null ? entry.key : "";
            String nameTag = entry.name != null ? entry.name.getString() : "";
            return Iterators.concat(
                    base,
                    Stream.of(keyTag, nameTag).filter(s -> !s.isEmpty()).iterator());
        }
    }

    public static void register(GuiRegistry registry) {
        registry.registerPredicateProvider(
                SpawnInfoGuiProvider::provide,
                field -> field.getType() == RoleSpawnInfoEntries.class);
    }

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

        List<EditableEntry> entries = toEditableList(currentObj.maps, currentObj.type);
        List<EditableEntry> defaultEntries = toEditableList(defaultObj.maps, defaultObj.type);

        int pageSize = 20;
        List<SavableSubCategoryEntry> pageContainers = new ArrayList<>();
        List<SearchableRowEntry> allRowContainers = new ArrayList<>();

        Runnable saveAllRowsRunnable = () -> saveAllRows(field, config, allRowContainers);

        if (entries.isEmpty()) {
            SearchableRowEntry emptyRow = buildRow(
                    new EditableEntry("", new SpawnInfo()),
                    new EditableEntry("", new SpawnInfo()),
                    i18n);
            allRowContainers.add(emptyRow);
            List<AbstractConfigListEntry> rowList = Collections.singletonList(emptyRow);
            SavableSubCategoryEntry emptyPage = new SavableSubCategoryEntry(
                    Component.literal("0-0"),
                    rowList,
                    false,
                    saveAllRowsRunnable);
            pageContainers.add(emptyPage);
        } else {
            for (int i = 0; i < entries.size(); i += pageSize) {
                int start = i;
                int end = Math.min(i + pageSize, entries.size());
                List<EditableEntry> subEntries = entries.subList(start, end);
                List<EditableEntry> subDefaults = defaultEntries.size() > start
                        ? defaultEntries.subList(start, Math.min(end, defaultEntries.size()))
                        : Collections.emptyList();

                Component pageTitle = Component.literal((start + 1) + "-" + end);
                List<AbstractConfigListEntry> pageChildren = new ArrayList<>();

                for (int j = 0; j < subEntries.size(); j++) {
                    EditableEntry current = subEntries.get(j);
                    EditableEntry def = (j < subDefaults.size()) ? subDefaults.get(j) : null;
                    if (def == null) {
                        def = new EditableEntry(current.key, new SpawnInfo());
                    }
                    SearchableRowEntry row = buildRow(current, def, i18n);
                    pageChildren.add(row);
                    allRowContainers.add(row);
                }

                SavableSubCategoryEntry page = new SavableSubCategoryEntry(
                        pageTitle,
                        pageChildren,
                        false,
                        saveAllRowsRunnable);
                pageContainers.add(page);
            }
        }

        List<AbstractConfigListEntry> parentChildren = new ArrayList<>(pageContainers);
        SubCategoryListEntry parentEntry = new SubCategoryListEntry(
                Component.translatable(i18n),
                parentChildren,
                false);

        return Collections.singletonList(parentEntry);
    }

    private static void saveAllRows(Field field, Object config,
            List<SearchableRowEntry> allRowContainers) {
        List<EditableEntry> allEntries = new ArrayList<>();
        for (SearchableRowEntry row : allRowContainers) {
            EditableEntry entry = row.getEntry();
            if (entry != null) {
                allEntries.add(entry);
            }
        }
        saveBack(field, config, allEntries);
    }

    private static SearchableRowEntry buildRow(
            EditableEntry currentEntry,
            EditableEntry defaultEntry,
            String i18n) {

        ConfigEntryBuilder.create();
        List<AbstractConfigListEntry<?>> controls = buildSpawnInfoControls(
                currentEntry.spawnInfo,
                defaultEntry.spawnInfo,
                i18n);

        Component title = currentEntry.name != null ? currentEntry.name
                : Component.literal(currentEntry.key != null ? currentEntry.key : "(empty)");

        return new SearchableRowEntry(
                title,
                currentEntry,
                controls,
                false);
    }

    private static List<AbstractConfigListEntry<?>> buildSpawnInfoControls(
            SpawnInfo info,
            SpawnInfo defaultInfo,
            String i18n) {
        List<AbstractConfigListEntry<?>> list = new ArrayList<>();
        ConfigEntryBuilder eb = ConfigEntryBuilder.create();

        for (Field field : SpawnInfo.class.getDeclaredFields()) {
            field.setAccessible(true);
            String fieldName = field.getName();
            String translationKey = i18n + "." + fieldName;

            try {
                Class<?> type = field.getType();
                Object currentValue = field.get(info);
                Object defaultValue = field.get(defaultInfo);

                AbstractConfigListEntry<?> control = createControlForField(
                        eb, translationKey, type, currentValue, defaultValue, field, info);
                if (control != null) {
                    list.add(control);
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private static AbstractConfigListEntry<?> createControlForField(
            ConfigEntryBuilder eb,
            String translationKey,
            Class<?> type,
            Object currentValue,
            Object defaultValue,
            Field field,
            SpawnInfo info) {

        Component label = Component.translatable(translationKey);

        if (type == int.class || type == Integer.class) {
            int val = currentValue != null ? (int) currentValue : 0;
            int def = defaultValue != null ? (int) defaultValue : 0;
            return eb.startIntField(label, val)
                    .setDefaultValue(def)
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
            long def = defaultValue != null ? (long) defaultValue : 0L;
            return eb.startLongField(label, val)
                    .setDefaultValue(def)
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
            float def = defaultValue != null ? (float) defaultValue : 0f;
            return eb.startFloatField(label, val)
                    .setDefaultValue(def)
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
            double def = defaultValue != null ? (double) defaultValue : 0.0;
            return eb.startDoubleField(label, val)
                    .setDefaultValue(def)
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
            boolean def = defaultValue != null && (boolean) defaultValue;
            return eb.startBooleanToggle(label, val)
                    .setDefaultValue(def)
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
            String def = defaultValue != null ? (String) defaultValue : "";
            return eb.startStrField(label, val)
                    .setDefaultValue(def)
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
                    List<String> defaultList = (List<String>) defaultValue;
                    if (defaultList == null) {
                        defaultList = new ArrayList<>();
                    }
                    List<String> finalCurrentList = currentList;
                    List<String> finalDefaultList = defaultList;
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
                            () -> new ArrayList<>(finalDefaultList),
                            Component.translatable("text.cloth-config.reset_value"),
                            false,
                            true,
                            false);
                }
            }
        }
        // fallback
        String val = currentValue != null ? currentValue.toString() : "";
        String def = defaultValue != null ? defaultValue.toString() : "";
        return eb.startStrField(label, val)
                .setDefaultValue(def)
                .setSaveConsumer(v -> {
                    try {
                        field.set(info, v);
                    } catch (IllegalAccessException ignored) {
                    }
                })
                .build();
    }

    // ---------- 工具 ----------
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
        ArrayList<Entry<ResourceLocation, SpawnInfo>> resu = new ArrayList<>(map.entrySet());
        Collator collator = Collator.getInstance();
        resu.sort((a1, b1) -> {
            var a = a1.getKey();
            var b = b1.getKey();
            if (a.getNamespace().equals(b.getNamespace())) {
                String r_a = RoleUtils.getRoleName(a).getString();
                String r_b = RoleUtils.getRoleName(b).getString();
                return collator.compare(r_a, r_b);
            } else {
                String nameSpaceA = a.getNamespace();
                String nameSpaceB = b.getNamespace();
                return collator.compare(nameSpaceA, nameSpaceB);
            }
        });
        return resu.stream()
                .map(e -> new EditableEntry(e.getKey().toString(), cloneSpawnInfo(e.getValue()),
                        getKeyName(e.getKey(), type)))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static Component getKeyName(ResourceLocation key, int type) {
        if (type == 1) {
            return RoleUtils.getRoleName(key).append(Component.translatable(" (%s)", key.toString()));
        } else if (type == 2) {
            return RoleUtils.getModifierName(key).append(Component.translatable(" (%s)", key.toString()));
        }
        return Component.literal(key.toString());
    }

    private static SpawnInfo cloneSpawnInfo(SpawnInfo original) {
        if (original == null)
            return new SpawnInfo();
        SpawnInfo copy = new SpawnInfo(
                original.minEnabledPlayer,
                original.maxEnabledPlayer,
                original.enableChance,
                original.maxSpawn);
        if (original.map != null) {
            copy.map = new ArrayList<>(original.map);
        }
        return copy;
    }

    private static void saveBack(Field field, Object config, List<EditableEntry> updated) {
        RoleSpawnInfoEntries obj = (RoleSpawnInfoEntries) getFieldValue(field, config);
        if (obj == null) {
            obj = new RoleSpawnInfoEntries();
            setFieldValue(field, config, obj);
        }
        int originalType = obj.type;

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
        obj.type = originalType;
    }
}