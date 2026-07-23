package io.wifi.starrailexpress.api;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;

/**
 * 表示本能类型，支持预定义常量与自定义颜色值（仅 CUSTOM 系列有颜色）。
 * 提供类似枚举的 values()、valueOf() 以及快捷获取方法。
 * <p>
 * 使用示例：
 * 
 * <pre>
 * // 使用预定义常量
 * InstinctType type = InstinctType.defaultLogic();
 * if (type.isDefault()) { ... }
 *
 * // 使用固定颜色
 * InstinctType custom = InstinctType.custom(0xFFFF0000);
 * if (custom.isCustom()) {
 *     int color = custom.getColor(); // 返回固定颜色
 * }
 *
 * // 使用动态颜色函数
 * InstinctType dynamic = InstinctType.customWithFunction((self, target, selfRole, targetRole) -> {
 *     // 根据上下文计算颜色
 *     return selfRole.isAlive() ? 0xFF00FF00 : 0xFFFF0000;
 * });
 * int color = dynamic.getColor(playerSelf, playerTarget, selfRole, targetRole);
 * </pre>
 */
public final class InstinctType {

    /**
     * 颜色计算函数接口。
     * 用于 {@link #CUSTOM_WITH_FUNCTION} 类型，根据上下文动态计算颜色值。
     */
    @FunctionalInterface
    public interface ColorFunction {
        /**
         * 根据上下文计算颜色值。
         *
         * @param self       当前玩家（主动方）
         * @param target     目标玩家（被动方）
         * @param selfRole   当前玩家角色
         * @param targetRole 目标玩家角色
         * @return ARGB 颜色值（int）
         */
        InstinctType getInstinct(Player self, Player target, SRERole selfRole, @Nullable SRERole targetRole);
    }

    private enum Kind {
        DEFAULT,
        NONE,
        KILLER_INSTINCT,
        OBSERVER_ROLE_COLOR,
        TARGET_ROLE_COLOR,
        CUSTOM,
        CUSTOM_WITH_FUNCTION
    }

    private final Kind kind;
    private final int fixedColor; // 仅当 kind == CUSTOM 时有效
    private final ColorFunction colorFunction; // 仅当 kind == CUSTOM_WITH_FUNCTION 时有效

    private InstinctType(Kind kind, int fixedColor, ColorFunction colorFunction) {
        this.kind = kind;
        this.fixedColor = fixedColor;
        this.colorFunction = colorFunction;
    }

    // ---------- 预定义常量 ----------
    public static final InstinctType DEFAULT = new InstinctType(Kind.DEFAULT, 0, null);
    public static final InstinctType NONE = new InstinctType(Kind.NONE, 0, null);
    public static final InstinctType KILLER_INSTINCT = new InstinctType(Kind.KILLER_INSTINCT, 0, null);
    public static final InstinctType OBSERVER_ROLE_COLOR = new InstinctType(Kind.OBSERVER_ROLE_COLOR, 0, null);
    public static final InstinctType TARGET_ROLE_COLOR = new InstinctType(Kind.TARGET_ROLE_COLOR, 0, null);

    // ---------- 静态快捷获取方法 ----------
    public static InstinctType defaultLogic() {
        return DEFAULT;
    }

    public static InstinctType none() {
        return NONE;
    }

    public static InstinctType killerInstinct() {
        return KILLER_INSTINCT;
    }

    public static InstinctType observerRoleColor() {
        return OBSERVER_ROLE_COLOR;
    }

    public static InstinctType targetRoleColor() {
        return TARGET_ROLE_COLOR;
    }

    // ---------- 工厂方法：固定颜色（CUSTOM） ----------
    public static InstinctType custom(int color) {
        return new InstinctType(Kind.CUSTOM, color, null);
    }

    // ---------- 工厂方法：动态颜色函数（CUSTOM_WITH_FUNCTION） ----------
    public static InstinctType customWithFunction(ColorFunction function) {
        Objects.requireNonNull(function, "colorFunction must not be null");
        return new InstinctType(Kind.CUSTOM_WITH_FUNCTION, 0, function);
    }

    // ---------- 获取颜色（无参数，仅适用于固定颜色 CUSTOM） ----------
    /**
     * 获取固定颜色值（仅当类型为 {@code CUSTOM} 时有效）。
     *
     * @return 固定颜色值
     * @throws IllegalStateException 如果当前类型不是 {@code CUSTOM}
     */
    public int getColor() {
        if (kind != Kind.CUSTOM) {
            throw new IllegalStateException(
                    "getColor() can only be called when kind is CUSTOM. But actual kind: " + kind);
        }
        return fixedColor;
    }

    // ---------- 获取颜色（带上下文，适用于动态函数 CUSTOM_WITH_FUNCTION，也兼容 CUSTOM） ----------
    /**
     * 根据上下文获取颜色值。
     * <ul>
     * <li>若类型为 {@code CUSTOM}，返回固定颜色（忽略上下文参数）。</li>
     * <li>若类型为 {@code CUSTOM_WITH_FUNCTION}，调用函数计算颜色。</li>
     * <li>其他预定义常量抛出 {@link IllegalStateException}。</li>
     * </ul>
     *
     * @param self       当前玩家（主动方）
     * @param target     目标玩家（被动方）
     * @param selfRole   当前玩家角色
     * @param targetRole 目标玩家角色
     * @return 计算得到的颜色值
     * @throws IllegalStateException 如果当前类型不支持颜色（即预定义常量且不是 CUSTOM 系列）
     */
    public InstinctType getTrueInstinct(Player self, Player target, SRERole selfRole, SRERole targetRole) {
        return switch (kind) {
            case CUSTOM_WITH_FUNCTION -> colorFunction.getInstinct(self, target, selfRole, targetRole);
            default -> this;
        };
    }

    // ---------- 类型判断方法 ----------
    public boolean isDefault() {
        return kind == Kind.DEFAULT;
    }

    public boolean isNone() {
        return kind == Kind.NONE;
    }

    public boolean isKillerInstinct() {
        return kind == Kind.KILLER_INSTINCT;
    }

    public boolean isObserverRoleColor() {
        return kind == Kind.OBSERVER_ROLE_COLOR;
    }

    public boolean isTargetRoleColor() {
        return kind == Kind.TARGET_ROLE_COLOR;
    }

    public boolean isCustom() {
        return kind == Kind.CUSTOM;
    }

    public boolean isCustomWithFunction() {
        return kind == Kind.CUSTOM_WITH_FUNCTION;
    }

    // ---------- 类似枚举的 values() 和 valueOf() ----------
    /**
     * 返回所有预定义常量（不包括 CUSTOM 和 CUSTOM_WITH_FUNCTION）。
     *
     * @return 包含 DEFAULT, NONE, KILLER_INSTINCT, OBSERVER_ROLE_COLOR,
     *         TARGET_ROLE_COLOR 的数组
     */
    public static InstinctType[] values() {
        return new InstinctType[] {
                DEFAULT, NONE, KILLER_INSTINCT, OBSERVER_ROLE_COLOR, TARGET_ROLE_COLOR
        };
    }

    /**
     * 根据名称返回对应的预定义常量。
     * <p>
     * 注意：{@code CUSTOM} 和 {@code CUSTOM_WITH_FUNCTION} 不是常量，请使用对应的工厂方法创建。
     *
     * @param name 常量名称（如 "DEFAULT"）
     * @return 对应的常量
     * @throws IllegalArgumentException 如果名称无效，或名称为 "CUSTOM" 或
     *                                  "CUSTOM_WITH_FUNCTION"
     */
    public static InstinctType valueOf(String name) {
        if (name == null) {
            throw new NullPointerException("Name is null");
        }
        return switch (name) {
            case "DEFAULT" -> DEFAULT;
            case "NONE" -> NONE;
            case "KILLER_INSTINCT" -> KILLER_INSTINCT;
            case "OBSERVER_ROLE_COLOR" -> OBSERVER_ROLE_COLOR;
            case "TARGET_ROLE_COLOR" -> TARGET_ROLE_COLOR;
            case "CUSTOM" ->
                throw new IllegalArgumentException("CUSTOM is not a constant. Use custom(int) to create.");
            case "CUSTOM_WITH_FUNCTION" ->
                throw new IllegalArgumentException(
                        "CUSTOM_WITH_FUNCTION is not a constant. Use customWithFunction(ColorFunction) to create.");
            default -> throw new IllegalArgumentException("Unknown constant: " + name);
        };
    }

    // ---------- 获取内部枚举（用于 switch 等场景） ----------
    public Kind getKind() {
        return kind;
    }

    // ---------- 标准重写 ----------
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        InstinctType that = (InstinctType) o;
        return kind == that.kind &&
                fixedColor == that.fixedColor &&
                Objects.equals(colorFunction, that.colorFunction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, fixedColor, colorFunction);
    }

    @Override
    public String toString() {
        return switch (kind) {
            case CUSTOM -> "CUSTOM(" + Integer.toHexString(fixedColor) + ")";
            case CUSTOM_WITH_FUNCTION -> "CUSTOM_WITH_FUNCTION(" + colorFunction + ")";
            default -> kind.name();
        };
    }
}