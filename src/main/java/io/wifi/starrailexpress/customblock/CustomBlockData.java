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

package io.wifi.starrailexpress.customblock;

import com.google.gson.annotations.SerializedName;
import io.wifi.starrailexpress.api.RoleTeam;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个「自定义方块」的完整配置数据（Gson + {@code @SerializedName} 持久化）。
 *
 * <p>
 * 与 {@link io.wifi.starrailexpress.customitem.CustomItemData} 结构对齐，单独保存在
 * {@code sre_custom_blocks.json}。所有自定义方块本质上都是同一个注册方块
 * （{@code starrailexpress:custom_block}）加上方块实体里记录的自定义 id，
 * 本类描述的就是「那份方块数据」。
 *
 * <p>
 * 外观有两种来源（按优先级）：{@link #packTexturePath 资源包贴图} 优先，
 * 其次是 {@link #inheritBlock 继承方块}（模型 / 碰撞 / 音效都跟随它）；
 * 两者都没有时客户端画紫黑棋盘占位。
 *
 * <p>
 * <b>交互是「事件列表」</b>：一个方块可以挂任意多个 {@link BlockEvent}，
 * 每个事件自带类型、指令、冷却与生效条件（见 {@link BlockEventType}）。
 *
 * <p>
 * 时间类字段单位统一为 <b>tick</b>（20 tick = 1 秒）。
 */
public class CustomBlockData {

    /** 自定义方块统一命名空间（仅用于展示 / 标识）。 */
    public static final String NAMESPACE = "customblock";

    /** 靠近事件允许的最大半径（格）。 */
    public static final double MAX_PROXIMITY_RADIUS = 32.0D;

    /** 单个方块允许配置的事件数量上限（防止配置写成无上限的死循环）。 */
    public static final int MAX_EVENTS = 32;

    // ==================== 基础数据 ====================

    /** 方块编号（英文，供指令 {@code /sre:give block <id>} 获取）。 */
    @SerializedName("id")
    public String id = "";

    /** 方块显示名称（物品栏里的名字）。 */
    @SerializedName("displayName")
    public String displayName = "";

    /** 方块物品的 tooltip（每行一条）。 */
    @SerializedName("tooltip")
    public List<String> tooltip = new ArrayList<>();

    // ==================== 外观 ====================

    /** 继承方块：填写方块 id（如 {@code minecraft:oak_planks}），模型 / 碰撞形状 / 音效都跟随它。 */
    @SerializedName("inheritBlock")
    public String inheritBlock = "";

    /** 资源包贴图路径：把方块渲染成整方块贴图，与 {@link #inheritBlock} 冲突时优先本项。 */
    @SerializedName("packTexturePath")
    public String packTexturePath = "";

    /** 发光等级 0~15（0 = 不发光）。 */
    @SerializedName("lightLevel")
    public int lightLevel = 0;

    /**
     * 发光是否受「关灯」事件影响（参考列车灯 {@code TrainLightBlock}）。
     *
     * <p>
     * 开启后这个方块会被登记进关灯点位：关灯期间亮度归零并带闪烁，关灯结束后恢复；
     * 关闭则亮度恒定，关灯不影响它。默认关闭（保持加这个选项之前的行为）。
     */
    @SerializedName("lightAffectedByBlackout")
    public boolean lightAffectedByBlackout = false;

    // ==================== 朝向 ====================

    /** 是否按放置者朝向旋转（4 向水平朝向）。 */
    @SerializedName("rotate")
    public boolean rotate = true;

    // ==================== 方块属性 ====================

    /** 是否无碰撞：开启后玩家 / 生物可以直接穿过。 */
    @SerializedName("noCollision")
    public boolean noCollision = false;

    /** 是否屏蔽天空光：开启后像不透明方块一样，下方不会被天空照亮。 */
    @SerializedName("blocksSkylight")
    public boolean blocksSkylight = false;

    // ==================== 交互事件 ====================

    /** 该方块挂载的全部交互事件（可多个，同类型也可以有多个）。 */
    @SerializedName("events")
    public List<BlockEvent> events = new ArrayList<>();

    /**
     * 按类型筛出事件。
     *
     * <p>
     * 事件数量很少（上限 {@link #MAX_EVENTS}），直接遍历即可；这里返回的列表
     * 只在「触发时」使用，不做缓存，避免配置改动后残留旧引用。
     */
    public List<BlockEvent> eventsOf(BlockEventType type) {
        List<BlockEvent> matched = new ArrayList<>();
        if (events == null) {
            return matched;
        }
        for (BlockEvent event : events) {
            if (event != null && event.type() == type && event.hasCommands()) {
                matched.add(event);
            }
        }
        return matched;
    }

    /** 是否配置了指定类型的事件。 */
    public boolean hasEventOf(BlockEventType type) {
        if (events == null) {
            return false;
        }
        for (BlockEvent event : events) {
            if (event != null && event.type() == type && event.hasCommands()) {
                return true;
            }
        }
        return false;
    }

    /** 是否配置了靠近事件（靠近事件是唯一需要服务端做位置扫描的类型）。 */
    public boolean hasProximityEvent() {
        return hasEventOf(BlockEventType.PROXIMITY);
    }

    /** 该方块配置的事件里出现的最大靠近半径（没有靠近事件返回 0）。 */
    public double maxProximityRadius() {
        double max = 0.0D;
        if (events == null) {
            return max;
        }
        for (BlockEvent event : events) {
            if (event != null && event.type() == BlockEventType.PROXIMITY) {
                max = Math.max(max, event.radius);
            }
        }
        return max;
    }

    // ==================== 工具方法 ====================

    /** 完整的展示用标识：{@code customblock:<id>}。 */
    public String getFullIdentifier() {
        return NAMESPACE + ":" + id;
    }

    /**
     * 数值收敛与空值兜底：把配置里可能出现的非法值（负冷却、过大半径、越界亮度等）
     * 收敛到合法区间，并保证所有列表非 null。反序列化后与保存前都调用一次。
     */
    public void sanitize() {
        id = id == null ? "" : id.trim().toLowerCase();
        displayName = displayName == null ? "" : displayName;
        tooltip = safeList(tooltip);
        inheritBlock = inheritBlock == null ? "" : inheritBlock.trim();
        packTexturePath = packTexturePath == null ? "" : packTexturePath.trim();

        lightLevel = clamp(lightLevel, 0, 15);

        if (events == null) {
            events = new ArrayList<>();
        }
        if (events.size() > MAX_EVENTS) {
            events = new ArrayList<>(events.subList(0, MAX_EVENTS));
        }
        for (BlockEvent event : events) {
            if (event != null) {
                event.sanitize();
            }
        }
    }

    private static List<String> safeList(List<String> list) {
        return list == null ? new ArrayList<>() : list;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // ==================== 事件 ====================

    /**
     * 交互事件的类型。
     *
     * <p>
     * 新增类型时只需：在枚举里加一项、在 {@link CustomBlockRuntime} 里加对应触发点
     * （右键 = {@code useWithoutItem}、踩踏 = {@code stepOn}、靠近 = 服务端位置扫描），
     * 编辑器与序列化不需要改动（类型是轮回按钮，字段按类型显示）。
     */
    public enum BlockEventType {
        /** 右键点击方块时触发。 */
        RIGHT_CLICK,
        /** 玩家踩上方块时触发。 */
        STEP,
        /** 玩家进入半径范围内时触发（需要服务端扫描，是唯一有持续开销的类型）。 */
        PROXIMITY;

        /** 该类型是否使用「半径」字段。 */
        public boolean usesRadius() {
            return this == PROXIMITY;
        }

        /** 该类型是否使用「仅潜行时触发」字段。 */
        public boolean usesSneakOnly() {
            return this == RIGHT_CLICK;
        }

        /** 该类型是否使用「触发后消耗方块」字段。 */
        public boolean usesConsume() {
            return this == RIGHT_CLICK;
        }

        /** 该类型是否使用「每个玩家只触发一次」字段。 */
        public boolean usesOncePerPlayer() {
            return this == STEP || this == PROXIMITY;
        }

        /** 该类型是否需要服务端持续扫描（性能敏感）。 */
        public boolean needsScanning() {
            return this == PROXIMITY;
        }

        /** 按名称解析类型（未知 / 空返回右键，保证旧配置不会因为写错类型就整个失效）。 */
        public static BlockEventType byName(String name) {
            if (name != null) {
                for (BlockEventType type : values()) {
                    if (type.name().equalsIgnoreCase(name.trim())) {
                        return type;
                    }
                }
            }
            return RIGHT_CLICK;
        }
    }

    /**
     * 一个交互事件：类型 + 指令 + 冷却 + 生效条件。
     *
     * <p>
     * 字段按类型取用（见 {@link BlockEventType} 的 {@code usesXxx}），不适用的字段在界面上
     * 不显示、运行时也不读，配置里留着不影响行为。
     */
    public static class BlockEvent {

        /** 事件类型（{@link BlockEventType} 名称）。 */
        @SerializedName("type")
        public String type = BlockEventType.RIGHT_CLICK.name();

        /** 触发时执行的指令列表（{@code <player>} = 触发的玩家）。 */
        @SerializedName("commands")
        public List<String> commands = new ArrayList<>();

        /** 触发后的冷却（tick，0 = 无冷却）。 */
        @SerializedName("cooldownTicks")
        public int cooldownTicks = 0;

        /** 每个玩家是否只触发一次（踩踏 / 靠近；需要重进世界或重载才重置）。 */
        @SerializedName("oncePerPlayer")
        public boolean oncePerPlayer = false;

        /** 是否只在潜行时触发（右键）。 */
        @SerializedName("sneakOnly")
        public boolean sneakOnly = false;

        /** 触发后是否消耗（移除）该方块（右键）。 */
        @SerializedName("consumeBlock")
        public boolean consumeBlock = false;

        /** 触发半径（格；靠近类型使用）。 */
        @SerializedName("radius")
        public double radius = 4.0D;

        /** 是否只在游戏进行中（非准备阶段 / 非会议）触发。 */
        @SerializedName("gameRunningOnly")
        public boolean gameRunningOnly = false;

        /** 允许触发的职业 id 列表（空 = 不限职业；支持 {@code ns:path} 与纯 path）。 */
        @SerializedName("requiredRoles")
        public List<String> requiredRoles = new ArrayList<>();

        /** 允许触发的阵营列表（{@link RoleTeam} 名称；空 = 不限阵营）。 */
        @SerializedName("requiredTeams")
        public List<String> requiredTeams = new ArrayList<>();

        public BlockEventType type() {
            return BlockEventType.byName(type);
        }

        public void setType(BlockEventType value) {
            this.type = value.name();
        }

        /** 是否配置了至少一条非空指令。 */
        public boolean hasCommands() {
            if (commands == null || commands.isEmpty()) {
                return false;
            }
            for (String command : commands) {
                if (command != null && !command.isBlank()) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 是否配置了有效的职业限定。
         *
         * <p>
         * 空串不算条件：编辑器里每行一个输入框，空行会被写进配置（例如
         * {@code "requiredRoles": [""]}）。若把空串当成一个「限定的职业」，条件就永远不成立、
         * 事件静默失效——这是最容易踩的坑。
         */
        public boolean hasRoleCondition() {
            if (requiredRoles == null) {
                return false;
            }
            for (String role : requiredRoles) {
                if (role != null && !role.isBlank()) {
                    return true;
                }
            }
            return false;
        }

        /** 是否配置了任何条件（无条件的事件走最快路径）。 */
        public boolean hasConditions() {
            return gameRunningOnly || hasRoleCondition() || !teams().isEmpty();
        }

        /** 配置里填的阵营（解析失败的名字会被忽略）。 */
        public List<RoleTeam> teams() {
            List<RoleTeam> teams = new ArrayList<>();
            if (requiredTeams == null) {
                return teams;
            }
            for (String name : requiredTeams) {
                if (name == null || name.isBlank()) {
                    continue;
                }
                try {
                    teams.add(RoleTeam.valueOf(name.trim().toUpperCase()));
                } catch (IllegalArgumentException ignored) {
                    // 配置里写了不存在的阵营名：忽略
                }
            }
            return teams;
        }

        public void sanitize() {
            type = type().name();
            commands = safeList(commands);
            requiredRoles = safeList(requiredRoles);
            requiredTeams = safeList(requiredTeams);
            // 去掉空行占位（编辑器每个列表都会留一个空输入框），否则会变成永远不成立的条件
            requiredRoles.removeIf(role -> role == null || role.isBlank());
            requiredTeams.removeIf(team -> team == null || team.isBlank());
            cooldownTicks = clamp(cooldownTicks, 0, 20 * 60 * 10);
            radius = clampDouble(radius, 0.0D, MAX_PROXIMITY_RADIUS);
            if (type() == BlockEventType.PROXIMITY && radius < 1.0D) {
                radius = 1.0D;
            }
        }
    }
}
