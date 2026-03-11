package org.agmas.noellesroles.presets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 预设数据结构
 */
public class Preset {
    private String description;
    private RoleSettings roles;
    private ModifierSettings modifiers;

    public Preset() {
        this.roles = new RoleSettings();
        this.modifiers = new ModifierSettings();
    }

    public Preset(String description) {
        this();
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public RoleSettings getRoles() {
        return roles;
    }

    public void setRoles(RoleSettings roles) {
        this.roles = roles;
    }

    public ModifierSettings getModifiers() {
        return modifiers;
    }

    public void setModifiers(ModifierSettings modifiers) {
        this.modifiers = modifiers;
    }

    /**
     * 角色设置
     */
    public static class RoleSettings {
        private List<String> enabled = new ArrayList<>();
        private List<String> disabled = new ArrayList<>();

        public List<String> getEnabled() {
            return enabled;
        }

        public void setEnabled(List<String> enabled) {
            this.enabled = enabled;
        }

        public List<String> getDisabled() {
            return disabled;
        }

        public void setDisabled(List<String> disabled) {
            this.disabled = disabled;
        }
    }

    /**
     * 修饰符设置
     */
    public static class ModifierSettings {
        private List<String> enabled = new ArrayList<>();
        private List<String> disabled = new ArrayList<>();

        public List<String> getEnabled() {
            return enabled;
        }

        public void setEnabled(List<String> enabled) {
            this.enabled = enabled;
        }

        public List<String> getDisabled() {
            return disabled;
        }

        public void setDisabled(List<String> disabled) {
            this.disabled = disabled;
        }
    }

    /**
     * 预设管理器
     */
    public static class PresetManager {
        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
        private static final File PRESETS_DIR = new File("config/noellesroles/presets");
        private static final File CUSTOM_PRESETS_FILE = new File(PRESETS_DIR, "custom_presets.json");

        private static Map<String, Preset> defaultPresets = new HashMap<>();
        private static Map<String, Preset> customPresets = new HashMap<>();

        /**
         * 加载所有预设
         */
        public static void loadPresets() {
            loadDefaultPresets();
            loadCustomPresets();
        }

        /**
         * 加载默认预设
         */
        private static void loadDefaultPresets() {
            try {
                // 首先尝试从config目录加载（允许用户覆盖默认预设）
                File configPresetsFile = new File("config/noellesroles/presets.json");
                if (configPresetsFile.exists()) {
                    FileReader reader = new FileReader(configPresetsFile);
                    defaultPresets = GSON.fromJson(reader, new TypeToken<Map<String, Preset>>(){}.getType());
                    reader.close();
                } else {
                    // 从资源文件加载默认预设
                    try (java.io.InputStream inputStream = Preset.class.getResourceAsStream("/assets/noellesroles/presets.json")) {
                        if (inputStream != null) {
                            java.io.InputStreamReader reader = new java.io.InputStreamReader(inputStream);
                            defaultPresets = GSON.fromJson(reader, new TypeToken<Map<String, Preset>>(){}.getType());
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * 加载自定义预设
         */
        private static void loadCustomPresets() {
            try {
                if (!PRESETS_DIR.exists()) {
                    PRESETS_DIR.mkdirs();
                }

                if (CUSTOM_PRESETS_FILE.exists()) {
                    FileReader reader = new FileReader(CUSTOM_PRESETS_FILE);
                    customPresets = GSON.fromJson(reader, new TypeToken<Map<String, Preset>>(){}.getType());
                    reader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * 保存自定义预设
         */
        public static void saveCustomPresets() {
            try {
                if (!PRESETS_DIR.exists()) {
                    PRESETS_DIR.mkdirs();
                }

                FileWriter writer = new FileWriter(CUSTOM_PRESETS_FILE);
                GSON.toJson(customPresets, writer);
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * 获取预设（包括默认和自定义）
         */
        public static Preset getPreset(String name) {
            Preset preset = customPresets.get(name);
            if (preset == null) {
                preset = defaultPresets.get(name);
            }
            return preset;
        }

        /**
         * 获取所有预设名称
         */
        public static List<String> getAllPresetNames() {
            List<String> names = new ArrayList<>();
            names.addAll(defaultPresets.keySet());
            names.addAll(customPresets.keySet());
            return names;
        }

        /**
         * 获取所有预设及其描述
         */
        public static Map<String, String> getAllPresetDescriptions() {
            Map<String, String> descriptions = new HashMap<>();
            for (Map.Entry<String, Preset> entry : defaultPresets.entrySet()) {
                descriptions.put(entry.getKey(), entry.getValue().getDescription());
            }
            for (Map.Entry<String, Preset> entry : customPresets.entrySet()) {
                descriptions.put(entry.getKey() + " (自定义)", entry.getValue().getDescription());
            }
            return descriptions;
        }

        /**
         * 检查是否为默认预设
         */
        public static boolean isDefaultPreset(String name) {
            return defaultPresets.containsKey(name);
        }

        /**
         * 添加自定义预设
         */
        public static void addCustomPreset(String name, Preset preset) {
            customPresets.put(name, preset);
            saveCustomPresets();
        }

        /**
         * 删除自定义预设
         */
        public static void removeCustomPreset(String name) {
            customPresets.remove(name);
            saveCustomPresets();
        }
    }
}
