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

    // ==================== 触发条件（为空 = 全局触发） ====================
    @SerializedName("conditions")
    public List<ConditionData> conditions = new ArrayList<>();

    /** 条件触发后是否自动移除该修饰符。全局触发时不生效。 */
    @SerializedName("removeModifierOnTrigger")
    public boolean removeModifierOnTrigger = false;

    // ==================== 触发内容 ====================
    /** 执行指令（可用 &lt;player&gt; 代表拥有该修饰符的玩家）。 */
    @SerializedName("commands")
    public List<String> commands = new ArrayList<>();

    /** 给予药水效果。 */
    @SerializedName("effects")
    public List<EffectData> effects = new ArrayList<>();

    /** 玩家属性（仅全局触发时生效）。 */
    @SerializedName("attributes")
    public List<AttributeData> attributes = new ArrayList<>();

    /** 没有任何条件 = 全局触发：拥有该修饰符即持续生效。 */
    public boolean isGlobalTrigger() {
        return conditions == null || conditions.isEmpty();
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
        DEATH_COUNTDOWN
    }
}
