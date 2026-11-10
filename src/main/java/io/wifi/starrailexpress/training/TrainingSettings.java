package io.wifi.starrailexpress.training;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/** 仅保存在训练存档中的设置，不保存在线假人或待执行任务。 */
public final class TrainingSettings {
    public static final int MAX_ENEMIES = 32;
    public int enemyCount = 5;
    public String myRole = "starrailexpress:civilian";
    public String enemyRole = "starrailexpress:killer";
    public String behavior = "stationary";
    public String equipment = "knife";
    public String area = "range";
    public boolean paused;
    public boolean autoRespawn = true;
    public int respawnDelay = 3;
    public boolean friendlyFirePenalty;
    public boolean unlimitedStamina;
    public boolean speedBoost;
    public boolean noCooldown;
    public List<String> myModifiers = new ArrayList<>();
    public List<String> enemyModifiers = new ArrayList<>();
    public Map<Integer, BotSettings> overrides = new LinkedHashMap<>();

    public void normalize() {
        enemyCount = Math.clamp(enemyCount, 0, MAX_ENEMIES);
        respawnDelay = Math.clamp(respawnDelay, 1, 30);
        behavior = validBehavior(behavior);
        equipment = validEquipment(equipment);
        area = validArea(area);
        if (myRole == null) myRole = "starrailexpress:civilian";
        if (enemyRole == null) enemyRole = "starrailexpress:killer";
        myModifiers = TrainingModifierSelection.normalize(myModifiers);
        enemyModifiers = TrainingModifierSelection.normalize(enemyModifiers);
        if (overrides == null) overrides = new LinkedHashMap<>();
        overrides.entrySet().removeIf(e -> e.getKey() < 0 || e.getKey() >= MAX_ENEMIES || e.getValue() == null);
        overrides.values().forEach(BotSettings::normalize);
    }

    public BotSettings bot(int slot) {
        return overrides.getOrDefault(slot, new BotSettings(enemyRole, behavior, equipment, enemyModifiers));
    }

    public static String validBehavior(String value) {
        return "moving".equals(value) || "combat".equals(value) ? value : "stationary";
    }

    public static String validEquipment(String value) {
        return "gun".equals(value) ? "gun" : "knife";
    }

    public static String validArea(String value) {
        return "arena".equals(value) || "train".equals(value) ? value : "range";
    }

    /** 每个槽位独立覆盖职业与行为。 */
    public static final class BotSettings {
        public String role;
        public String behavior;
        public String equipment;
        public List<String> modifiers = new ArrayList<>();

        public BotSettings() { this("starrailexpress:killer", "stationary", "knife"); }
        public BotSettings(String role, String behavior, String equipment) {
            this(role, behavior, equipment, List.of());
        }
        public BotSettings(String role, String behavior, String equipment, List<String> modifiers) {
            this.role = role;
            this.behavior = behavior;
            this.equipment = equipment;
            this.modifiers = TrainingModifierSelection.normalize(modifiers);
        }
        public void normalize() {
            if (role == null) role = "starrailexpress:killer";
            behavior = validBehavior(behavior);
            equipment = validEquipment(equipment);
            modifiers = TrainingModifierSelection.normalize(modifiers);
        }
    }
}
