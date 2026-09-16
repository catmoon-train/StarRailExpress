/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.custommodifier;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个自定义修饰符的完整配置数据模型（Gson + {@code @SerializedName} 持久化）。
 *
 * <p>
 * 与自定义职业（{@link io.wifi.starrailexpress.customrole.CustomRoleData}）结构对齐，
 * 但单独保存在 {@code sre_custom_modifiers.json} 里。
 */
public class CustomModifierData {

    /** 自定义修饰符统一命名空间。 */
    public static final String NAMESPACE = "custommodifier";

    // ==================== 基础 ====================
    @SerializedName("englishId")
    public String englishId = "";

    @SerializedName("displayName")
    public String displayName = "";

    @SerializedName("description")
    public String description = "";

    /**
     * 自定义标签（可多个）。
     *
     * <p>
     * 加载时逐条挂到运行时修饰符的 flags 上（{@code FlagUtils.applyCustomFlags}），介绍页的分类筛选
     * 据此过滤；{@code inner.*} 前缀的标签仍然具备其语义（例如 {@code inner.hidden}）。
     * 没有对应翻译的标签显示成「大写下划线转空格」的原文。
     */
    @SerializedName("tags")
    public List<String> tags = new ArrayList<>();

    @SerializedName("colorR")
    public int colorR = 255;
    @SerializedName("colorG")
    public int colorG = 255;
    @SerializedName("colorB")
    public int colorB = 255;

    /** 是否为隐藏修饰符（普通玩家不可视）。 */
    @SerializedName("hidden")
    public boolean hidden = false;

    /**
     * 仅标记使用：该修饰符只作为标记，不拥有任何触发条件与触发内容。
     *
     * <p>
     * 为 true 时编辑器会隐藏「触发条件 / 触发内容」两个页签，运行时也完全跳过条件判定与触发内容。
     */
    @SerializedName("markerOnly")
    public boolean markerOnly = false;

    // ==================== 关联设置（仅作用于介绍页面） ====================
    /** 双向关联职业（职业 id）。 */
    @SerializedName("bothRelatedRoles")
    public List<String> bothRelatedRoles = new ArrayList<>();
    /** 单向关联职业（职业 id）。 */
    @SerializedName("relatedRoles")
    public List<String> relatedRoles = new ArrayList<>();
    /** 单向移除关联职业（职业 id）。 */
    @SerializedName("removeRelatedRoles")
    public List<String> removeRelatedRoles = new ArrayList<>();
    /** 双向关联修饰符（修饰符 id）。 */
    @SerializedName("bothRelatedModifiers")
    public List<String> bothRelatedModifiers = new ArrayList<>();
    /** 单向关联修饰符（修饰符 id）。 */
    @SerializedName("relatedModifiers")
    public List<String> relatedModifiers = new ArrayList<>();
    /** 单向移除关联修饰符（修饰符 id）。 */
    @SerializedName("removeRelatedModifiers")
    public List<String> removeRelatedModifiers = new ArrayList<>();

    // ==================== 生成设置 ====================
    @SerializedName("defaultMax")
    public int defaultMax = 1;

    @SerializedName("defaultEnableChance")
    public int defaultEnableChance = 10000;

    @SerializedName("enableNeededPlayerCount")
    public int enableNeededPlayerCount = -1;

    @SerializedName("enableMaxPlayerCount")
    public int enableMaxPlayerCount = -1;

    /** 限定地图（地图 id）。为空表示不限制。 */
    @SerializedName("spawnMaps")
    public List<String> spawnMaps = new ArrayList<>();

    // ==================== 生成限制 ====================
    /** 不给特定阵营刷新（{@link io.wifi.starrailexpress.api.RoleTeam} 名称）。 */
    @SerializedName("cannotAppliedToTeams")
    public List<String> cannotAppliedToTeams = new ArrayList<>();
    /** 仅给特定阵营刷新（{@link io.wifi.starrailexpress.api.RoleTeam} 名称）。 */
    @SerializedName("canOnlyAppliedToTeams")
    public List<String> canOnlyAppliedToTeams = new ArrayList<>();
    /** 不作用于特定职业（职业 id）。 */
    @SerializedName("cannotBeAppliedTo")
    public List<String> cannotBeAppliedTo = new ArrayList<>();
    /** 仅作用于特定职业（职业 id）。 */
    @SerializedName("canOnlyBeAppliedTo")
    public List<String> canOnlyBeAppliedTo = new ArrayList<>();

    // ==================== 互斥修饰符 ====================
    /**
     * 双向互斥修饰符（修饰符 id）：双方不会出现在同一名玩家身上。
     *
     * <p>
     * 与自定义职业的 {@code twoWayOpposingJobs}（双向互斥职业）对应，
     * 生效方式见 {@link SREModifier#addTwoWayOpposingModifier}。
     */
    @SerializedName("twoWayOpposingModifiers")
    public List<String> twoWayOpposingModifiers = new ArrayList<>();
    /**
     * 单向互斥修饰符（修饰符 id）：本修饰符不会被分配给已持有这些修饰符的玩家。
     *
     * <p>
     * 与自定义职业的 {@code opposingJobs}（单向互斥职业）对应，
     * 生效方式见 {@link SREModifier#addOpposingModifier}。
     */
    @SerializedName("opposingModifiers")
    public List<String> opposingModifiers = new ArrayList<>();

    // ==================== 触发组（每组 = 一份条件 + 一份内容） ====================
    /**
     * 多个「触发条件 → 触发内容」组：每组各自判条件、各自执行自己的指令 / 效果 / 属性。
     *
     * <p>
     * 空列表时回退到下面那几个旧的顶层字段（{@link #effectiveGroups()} 会就地迁移成一个组），
     * 所以老 JSON 不需要手工改。组类型见 {@link TriggerGroupData#isGlobal()}：全局组无条件、
     * 拥有该修饰符就持续生效（药水效果常驻、属性常驻，指令只在获得时执行一次）；
     * 条件组要有条件之后才会判定触发。
     */
    @SerializedName("groups")
    public List<TriggerGroupData> groups = new ArrayList<>();

    // ==================== 旧版单组字段（仅用于读取老 JSON） ====================
    @Deprecated
    @SerializedName("conditions")
    public List<ConditionData> conditions = new ArrayList<>();

    /** 条件触发后是否自动移除该修饰符。全局触发时不生效。 */
    @Deprecated
    @SerializedName("removeModifierOnTrigger")
    public boolean removeModifierOnTrigger = false;

    /** 执行指令（可用 &lt;player&gt; 代表拥有该修饰符的玩家）。 */
    @Deprecated
    @SerializedName("commands")
    public List<String> commands = new ArrayList<>();

    /** 给予药水效果。 */
    @Deprecated
    @SerializedName("effects")
    public List<EffectData> effects = new ArrayList<>();

    /** 玩家属性（仅全局触发时生效）。 */
    @Deprecated
    @SerializedName("attributes")
    public List<AttributeData> attributes = new ArrayList<>();

    /**
     * 取触发组（必要时把旧版顶层字段迁移成一个组）。
     *
     * <p>
     * 就地迁移：迁移后旧字段被清空，保存时写出的就是 {@code groups}。因为 Gson 是先构造对象再填字段，
     * 所以迁移不能放在构造函数里，只能用这种「首次读取时迁移」的写法（幂等，重复调用无副作用）。
     */
    public List<TriggerGroupData> effectiveGroups() {
        if (groups == null) {
            groups = new ArrayList<>();
        }
        if (!groups.isEmpty()) {
            return groups;
        }
        boolean hasLegacy = (conditions != null && !conditions.isEmpty())
                || (removeModifierOnTrigger)
                || (commands != null && !commands.isEmpty())
                || (effects != null && !effects.isEmpty())
                || (attributes != null && !attributes.isEmpty());
        if (!hasLegacy) {
            return groups;
        }
        TriggerGroupData legacy = new TriggerGroupData();
        legacy.conditions = conditions == null ? new ArrayList<>() : conditions;
        legacy.globalMode = legacy.conditions.isEmpty();
        legacy.removeModifierOnTrigger = removeModifierOnTrigger;
        legacy.commands = commands == null ? new ArrayList<>() : commands;
        legacy.effects = effects == null ? new ArrayList<>() : effects;
        legacy.attributes = attributes == null ? new ArrayList<>() : attributes;
        groups.add(legacy);
        conditions = new ArrayList<>();
        removeModifierOnTrigger = false;
        commands = new ArrayList<>();
        effects = new ArrayList<>();
        attributes = new ArrayList<>();
        return groups;
    }

    /** 触发组数量（迁移后）。 */
    public int groupCount() {
        return effectiveGroups().size();
    }

    /** 所有组都没有条件 = 全局触发：拥有该修饰符即持续生效。 */
    public boolean isGlobalTrigger() {
        List<TriggerGroupData> groups = effectiveGroups();
        if (groups.isEmpty()) {
            return true;
        }
        for (TriggerGroupData group : groups) {
            if (!group.isGlobal()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 一组「触发条件 + 触发内容」。
     *
     * <p>
     * 组内条件按「与 / 或」串联（{@link ConditionData#logic} 表示与下一个条件的关系），
     * 满足时只执行<b>本组</b>的内容。组类型（{@link #isGlobal()}）用 {@link #setGlobal(boolean)} 显式设置：
     * 全局组没有条件、拥有该修饰符就持续生效；条件组再有条件之后才判定。
     */
    public static class TriggerGroupData {
        @SerializedName("conditions")
        public List<ConditionData> conditions = new ArrayList<>();
        @SerializedName("commands")
        public List<String> commands = new ArrayList<>();
        @SerializedName("effects")
        public List<EffectData> effects = new ArrayList<>();
        @SerializedName("attributes")
        public List<AttributeData> attributes = new ArrayList<>();
        /** 本组触发后是否移除该修饰符（全局组不生效）。 */
        @SerializedName("removeModifierOnTrigger")
        public boolean removeModifierOnTrigger = false;
        /**
         * 本组是否为全局组。{@code null} = 没写这个键（老存档），此时按「条件是否为空」推断。
         *
         * <p>
         * 用包装类型而不是 {@code boolean} 是为了区分「显式设成条件组、但还没来得及加条件」与老数据：
         * 前者必须保持条件组（否则编辑器会把「有条件」按钮又变成全局组，用户永远加不上条件）。
         */
        @SerializedName("global")
        public Boolean globalMode = null;

        /** 是否全局组：拥有该修饰符即持续生效（药水效果 / 属性常驻，指令只在获得时执行一次）。 */
        public boolean isGlobal() {
            if (globalMode != null) {
                return globalMode.booleanValue();
            }
            return conditions == null || conditions.isEmpty();
        }

        /** 设置组类型：{@code true} = 全局组（无条件常驻），{@code false} = 条件组。 */
        public void setGlobal(boolean global) {
            this.globalMode = global;
        }
    }

    public String getFullIdentifier() {
        return NAMESPACE + ":" + englishId;
    }

    public int getColor() {
        return 0xFF000000 | (clamp(colorR) << 16) | (clamp(colorG) << 8) | clamp(colorB);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    // ==================== 内部数据类 ====================

    /** 单个触发条件。 */
    public static class ConditionData {
        /** {@link ConditionType} 名称。 */
        @SerializedName("type")
        public String type = ConditionType.TIMER.name();
        /** 数值参数（含义随类型而定）。 */
        @SerializedName("value")
        public double value = 0;
        /** 字符串参数（如物品 id / 效果 id / 任务类型）。 */
        @SerializedName("stringValue")
        public String stringValue = "";
        /** 比较方式（EQUALS / GREATER / LESS / GREATER_EQUAL / LESS_EQUAL）。 */
        @SerializedName("comparison")
        public String comparison = "EQUALS";
        /** 世界时间类型（DAY / NOON / SUNSET / NIGHT / MIDNIGHT）。 */
        @SerializedName("worldTimeType")
        public String worldTimeType = "DAY";
        /** 与「下一个条件」的逻辑关系：AND（与）/ OR（或）。 */
        @SerializedName("logic")
        public String logic = "AND";
        /** 定时概率条件：每多少秒判定一次。 */
        @SerializedName("intervalSeconds")
        public int intervalSeconds = 10;
        /** 定时概率条件：触发概率（万分比，10000 = 必定触发）。 */
        @SerializedName("chance")
        public int chance = 10000;
    }

    /** 一条「给予药水效果」。 */
    public static class EffectData {
        @SerializedName("effectId")
        public String effectId = "";
        /** 0 = 等级 I。 */
        @SerializedName("amplifier")
        public int amplifier = 0;
        /** 持续时间（秒）；全局触发时忽略（持续到失去修饰符）。 */
        @SerializedName("durationSeconds")
        public int durationSeconds = 10;
    }

    /** 一条「玩家属性」。 */
    public static class AttributeData {
        /** 属性 id，如 {@code minecraft:generic.scale} / {@code minecraft:generic.movement_speed}。 */
        @SerializedName("attributeId")
        public String attributeId = "";
        /** 数值（默认按加法 added 计算，见 FatSkinnyModifier 的实现方式）。 */
        @SerializedName("value")
        public double value = 0;
    }

    /**
     * 自定义修饰符可用的触发条件类型。
     *
     * <p>
     * 抄自实体交互方块 {@code EntityInteractionBlockEntity.ConditionType}，并按需求剔除了：
     * 玩家穿越方块、球形范围、直线范围、右键方块、看向方块、站在方块上、特定职业、职业阵营、
     * 范围内实体数量、游戏进行中、需要完成自定义任务、红石信号、玩家拥有某个修饰符。
     * 其中「触发概率」被替换为 {@link #INTERVAL_CHANCE}（每 N 秒以 X 概率触发）。
     */
    public enum ConditionType {
        /** 自动定时：拥有该修饰符后，每 value 秒触发一次。 */
        TIMER,
        /** 时间锚点：**以游戏开始为基准**，第 value 秒（游戏开始后经过的秒数）满足比较时触发一次。 */
        TIME_ANCHOR,
        /** 有特定物品：stringValue = 物品 id。 */
        HAS_ITEM,
        /** 死亡时触发。 */
        DEATH,
        /** 使用物品：stringValue = 物品 id（留空表示任意物品）。 */
        USE_ITEM,
        /** 聊天栏说话：stringValue 为关键字（包含匹配），留空表示任意发言。 */
        SPEAK,
        /** 金币数量：比较 value。 */
        COIN_AMOUNT,
        /** 击杀过玩家：value 为需要达到的击杀数。 */
        HAS_KILLED,
        /** 玩家数量（全局玩家数）：比较 value。 */
        PLAYER_COUNT,
        /** 存活玩家数量：比较 value。 */
        ALIVE_PLAYERS,
        /** 处于潜行状态。 */
        IS_SNEAKING,
        /** 处于疾跑状态。 */
        IS_SPRINTING,
        /** 拥有特定药水效果：stringValue = 效果 id。 */
        HAS_EFFECT,
        /** 定时概率：每 intervalSeconds 秒以 chance（万分比）的概率触发。 */
        INTERVAL_CHANCE,
        /** 世界时间（DAY / NOON / SUNSET / NIGHT / MIDNIGHT）。 */
        WORLD_TIME,
        /** 心情值：比较 value。 */
        MOOD_VALUE,
        /** 是否处于疯狂模式。 */
        IS_PSYCHO,
        /** 是否中毒。 */
        IS_POISONED,
        /** 是否感染。 */
        IS_INFECTED,
        /** 护盾值：比较 value。 */
        ARMOR_AMOUNT,
        /** 是否有任务。 */
        HAS_TASK,
        /** 连续完成任务数：比较 value。 */
        TASK_STREAK,
        /** 当前活跃疯狂玩家数量：比较 value。 */
        PSYCHOS_ACTIVE,
        /** 是否处于关灯状态。 */
        IS_BLACKOUT,
        /** 监控是否失灵。 */
        IS_MONITOR_BROKEN,
        /** 完成特定类型的任务：stringValue = 任务类型。 */
        NEED_TASK_TYPE,
        /** 玩家受到来自玩家的伤害。 */
        PLAYER_DAMAGED_BY_PLAYER,
        /** 玩家受到非玩家来源的伤害。 */
        PLAYER_DAMAGED_BY_NON_PLAYER,
        /** 游戏经过的时间：**以游戏开始为基准**，已过去的秒数（value）比较。 */
        ELAPSED_TIME,
        /** 是否触发过假毒。 */
        FAKE_POISONED,
        /** 是否拥有弱效护盾。 */
        HAS_WEAK_ARMOR,
        /** 被同阵营的玩家击杀时触发（阵营按 {@code RoleUtils.getRoleType} 的大类比较）。 */
        KILLED_BY_SAME_TEAM,
        /** 被特定阵营的玩家击杀时触发：stringValue = {@link io.wifi.starrailexpress.api.RoleTeam} 名称。 */
        KILLED_BY_TEAM,
        /** 被特定职业的玩家击杀时触发：stringValue = 职业 id。 */
        KILLED_BY_ROLE,
        /** 被「拥有特定修饰符」的玩家击杀时触发：stringValue = 修饰符 id。 */
        KILLED_BY_MODIFIER,
        /** 死亡 value 秒后触发，并自动复活玩家（附带倒计时 HUD）。 */
        DEATH_COUNTDOWN_REVIVE,
        /** 死亡 value 秒后触发，不自动复活（附带倒计时 HUD）。 */
        DEATH_COUNTDOWN,
        /** 玩家连续静止不动 value tick（移动会清零计数）。 */
        STAY_STILL
    }
}
