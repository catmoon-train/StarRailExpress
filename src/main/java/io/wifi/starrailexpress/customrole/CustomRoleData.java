package io.wifi.starrailexpress.customrole;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/**
 * 单个自定义职业的完整配置数据模型
 */
public class CustomRoleData {
    // ============ 职业基础定义 ============
    @SerializedName("englishId")
    public String englishId = "";

    @SerializedName("displayName")
    public String displayName = "";

    @SerializedName("goals")
    public String goals = "";

    @SerializedName("description")
    public String description = "";

    @SerializedName("initialEffects")
    public List<EffectEntry> initialEffects = new ArrayList<>();

    @SerializedName("colorR")
    public int colorR = 255;
    @SerializedName("colorG")
    public int colorG = 255;
    @SerializedName("colorB")
    public int colorB = 255;

    @SerializedName("isInnocent")
    public boolean isInnocent = true;

    @SerializedName("canUseKiller")
    public boolean canUseKiller = false;

    @SerializedName("moodType")
    public String moodType = "REAL";

    @SerializedName("sprintMultiplier")
    public double sprintMultiplier = 1.0;

    @SerializedName("infiniteSprint")
    public boolean infiniteSprint = false;

    @SerializedName("canSeeTime")
    public boolean canSeeTime = false;

    // ============ 职业高级定义 ============
    @SerializedName("canSeeCoin")
    public boolean canSeeCoin = true;

    @SerializedName("canUseInstinct")
    public boolean canUseInstinct = false;

    @SerializedName("ableToPickUpRevolver")
    public Boolean ableToPickUpRevolver = null;

    @SerializedName("setNeutrals")
    public Boolean setNeutrals = null;

    @SerializedName("setNeutralForKiller")
    public Boolean setNeutralForKiller = null;

    @SerializedName("setVigilanteTeam")
    public Boolean setVigilanteTeam = null;

    @SerializedName("canSeeTeammateKiller")
    public Boolean canSeeTeammateKiller = null;

    @SerializedName("occupiedRoleCount")
    public int occupiedRoleCount = 1;

    @SerializedName("maxCount")
    public int maxCount = 1;

    @SerializedName("canAutoAddMoney")
    public Boolean canAutoAddMoney = null;

    @SerializedName("canBeRandomedByOtherRoles")
    public boolean canBeRandomedByOtherRoles = true;

    @SerializedName("canIgnoreBlackout")
    public Boolean canIgnoreBlackout = null;

    @SerializedName("canSeeBodyItems")
    public Boolean canSeeBodyItems = null;

    @SerializedName("canSeeBodyRoleInfo")
    public Boolean canSeeBodyRoleInfo = null;

    @SerializedName("canSeeBodyDeathReason")
    public Boolean canSeeBodyDeathReason = null;

    @SerializedName("canSeeBodyKiller")
    public Boolean canSeeBodyKiller = null;

    // ============ 职业通用属性补全（原 srerole 暴露、工具此前缺失） ============
    @SerializedName("neutralForInnocent")
    public Boolean neutralForInnocent = null;

    @SerializedName("canSeeBodyName")
    public Boolean canSeeBodyName = null;

    @SerializedName("canUseSkillWhileSpectator")
    public Boolean canUseSkillWhileSpectator = null;

    @SerializedName("mafiaTeam")
    public Boolean mafiaTeam = null;

    @SerializedName("canBePoisoned")
    public Boolean canBePoisoned = null;

    @SerializedName("hiddenForRoleRotation")
    public Boolean hiddenForRoleRotation = null;

    @SerializedName("specialMapRole")
    public String specialMapRole = "ALL";

    @SerializedName("specialVigilante")
    public Boolean specialVigilante = null;

    @SerializedName("refreshableSpecialVigilante")
    public Boolean refreshableSpecialVigilante = null;

    @SerializedName("refreshableSpecialVigilanteChance")
    public int refreshableSpecialVigilanteChance = 0;

    @SerializedName("canJumpManhole")
    public Boolean canJumpManhole = null;

    @SerializedName("canAcrossFog")
    public Boolean canAcrossFog = null;

    @SerializedName("canUseSabotage")
    public Boolean canUseSabotage = null;

    @SerializedName("defaultEnableMaxPlayerCount")
    public int defaultEnableMaxPlayerCount = -1;

    // ============ 独立胜利选项 (仅中立 && !setNeutralForKiller 时可用) ============
    @SerializedName("enableCustomWin")
    public boolean enableCustomWin = false;

    @SerializedName("customWinTitle")
    public String customWinTitle = "";

    @SerializedName("customWinSubtitle")
    public String customWinSubtitle = "";

    @SerializedName("customWinSurviveToLast")
    public boolean customWinSurviveToLast = false;

    @SerializedName("customWinLastAlive")
    public boolean customWinLastAlive = false;

    @SerializedName("customWinLastWithRoles")
    public List<String> customWinLastWithRoles = new ArrayList<>();

    @SerializedName("customWinTagSleep")
    public String customWinTagSleep = "";

    @SerializedName("customWinHeldItem")
    public String customWinHeldItem = "";

    // ============ 职业能力选项 ============
    @SerializedName("initialItems")
    public List<InitialItemEntry> initialItems = new ArrayList<>();

    // --- 旧版直觉系统（已废弃，保留向下兼容；优先使用 instinctModes） ---
    @SerializedName("instinctSameColorFrame")
    public boolean instinctSameColorFrame = false;

    @SerializedName("instinctMaxRange")
    public String instinctMaxRange = "*";

    @SerializedName("instinctUnlimitedTeammate")
    public boolean instinctUnlimitedTeammate = false;

    @SerializedName("instinctNightVision")
    public Boolean instinctNightVision = null;

    // --- 新版直觉系统：支持多种模式切换 ---
    // 每个模式包含「看别人」和「被别人看」两套配置，外加范围和队友限制
    @SerializedName("instinctModes")
    public List<InstinctModeData> instinctModes = new ArrayList<>();

    @SerializedName("enableAbility")
    public boolean enableAbility = false;

    @SerializedName("abilitySkillCommands")
    public List<String> abilitySkillCommands = new ArrayList<>();

    @SerializedName("abilityCooldownSeconds")
    public int abilityCooldownSeconds = 30;

    @SerializedName("abilityInitialCooldownSeconds")
    public int abilityInitialCooldownSeconds = 0;

    @SerializedName("abilityDelayedCommands")
    public List<String> abilityDelayedCommands = new ArrayList<>();

    @SerializedName("abilityDelaySeconds")
    public int abilityDelaySeconds = 0;

    // ============ 游戏结束自动执行指令 ============
    @SerializedName("gameEndCommands")
    public List<String> gameEndCommands = new ArrayList<>();

    // ============ 生成选项 ============
    @SerializedName("twoWayOpposingJobs")
    public List<String> twoWayOpposingJobs = new ArrayList<>();

    @SerializedName("opposingJobs")
    public List<String> opposingJobs = new ArrayList<>();

    @SerializedName("bindWithRoles")
    public List<String> bindWithRoles = new ArrayList<>();

    @SerializedName("mapRestrictedTo")
    public List<String> mapRestrictedTo = new ArrayList<>();

    @SerializedName("enableChance")
    public int enableChance = 100;

    @SerializedName("useRareChance")
    public boolean useRareChance = false;

    @SerializedName("enableRareChance")
    public int enableRareChance = 100;

    @SerializedName("enableNeededPlayerCount")
    public int enableNeededPlayerCount = -1;

    // ============ 商店选项 ============
    @SerializedName("shopEntries")
    public List<ShopEntryData> shopEntries = new ArrayList<>();

    // ============ 内部类 ============

    public static class EffectEntry {
        @SerializedName("effectId")
        public String effectId = "";
        @SerializedName("amplifier")
        public int amplifier = 0; // 0=等级I, 1=等级II...

        public EffectEntry() {}
        public EffectEntry(String id, int amp) { effectId = id; amplifier = amp; }
    }

    public static class InitialItemEntry {
        @SerializedName("itemId")
        public String itemId = "";
        @SerializedName("count")
        public int count = 1;
    }

    /**
     * 单个直觉模式的配置数据。
     * 每个模式定义了「看别人」（seeing）和「被别人看」（beSeen）的高亮类型，
     * 分别对应 {@code SRERole.setInstinctType} 和 {@code SRERole.setBeSeenInstinctType}。
     * 类型字符串见 {@link io.wifi.starrailexpress.api.InstinctType#valueOf(String)}，
     * 额外支持 {@code CUSTOM(0xAARRGGBB)} 形式的自定义颜色。
     */
    public static class InstinctModeData {
        /** 直觉关闭时，该职业看别人的高亮类型（如 DEFAULT/NONE/KILLER_INSTINCT/OBSERVER_ROLE_COLOR/TARGET_ROLE_COLOR/CUSTOM(0x...)） */
        @SerializedName("seeingOff")
        public String seeingOff = "DEFAULT";

        /** 直觉开启时，该职业看别人的高亮类型 */
        @SerializedName("seeingOn")
        public String seeingOn = "DEFAULT";

        /** 别人直觉关闭时，看到该职业的高亮类型 */
        @SerializedName("beSeenOff")
        public String beSeenOff = "DEFAULT";

        /** 别人直觉开启时，看到该职业的高亮类型 */
        @SerializedName("beSeenOn")
        public String beSeenOn = "DEFAULT";

        /** 最大透视距离（格数），"*" 表示无限 */
        @SerializedName("maxRange")
        public String maxRange = "*";

        /** 同阵营队友无视距离限制 */
        @SerializedName("unlimitedTeammate")
        public boolean unlimitedTeammate = false;
    }

    public static class ShopEntryData {
        @SerializedName("type")
        public String type = "item";

        @SerializedName("itemId")
        public String itemId = "";

        @SerializedName("price")
        public int price = 0;

        @SerializedName("cooldownSeconds")
        public int cooldownSeconds = 0;

        @SerializedName("allowDuplicate")
        public boolean allowDuplicate = true; // item类型专用：是否允许快捷栏已有该物品时继续购买

        // 自定义类型专用
        @SerializedName("displayName")
        public String displayName = "";

        @SerializedName("commands")
        public List<String> commands = new ArrayList<>();
    }

    public String getFullIdentifier() {
        return "customrole:" + englishId;
    }
}
