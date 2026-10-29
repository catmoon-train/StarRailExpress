package org.agmas.noellesroles.game.modes.echo.classes;

import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 模式职业：每阵营三个，按「战斗 / 牵制 / 循环」三种定位覆盖。
 *
 * <p>职业只提供身份、定位、技能映射与数值锚点；所有效果由
 * {@link EchoClassState}（纯状态机）与 {@code EchoClassService}（世界接线）承担。
 * 这样职业可以被纯单元测试覆盖，不需要启动服务端。
 *
 * <p>与旧 {@code EchoHunterClass}（裂爪/织网）的区别：职业不再依赖「专长」，
 * 也不再依赖锁定值；选到哪个职业就直接获得该职业的攻击方式、被动与主动技能。
 */
public enum EchoClass {
    /** 追捕 · 战斗：三连斩 + 破防处决。 */
    RAVAGER(EchoFaction.INTERCEPTOR, Role.BRAWLER),
    /** 追捕 · 牵制：投掷封锁钉，限制走位与救人。 */
    WARDEN(EchoFaction.INTERCEPTOR, Role.CONTROLLER),
    /** 追捕 · 循环：击倒后获得追击加速，滚雪球。 */
    STALKER(EchoFaction.INTERCEPTOR, Role.CHASER),

    /** 逃脱 · 战斗：翻越与扛伤，能把一次击倒拖成两次。 */
    VANGUARD(EchoFaction.CARRIER, Role.BRAWLER),
    /** 逃脱 · 牵制：布置干扰装置，拖慢追捕者的推进。 */
    TRAPPER(EchoFaction.CARRIER, Role.CONTROLLER),
    /** 逃脱 · 循环：修机与搬运效率最高，越到后期越强。 */
    HAULER(EchoFaction.CARRIER, Role.CHASER);

    /** 职业定位：决定它在对局里负责哪一段节奏。 */
    public enum Role {
        /** 战斗：正面换血、打断、处决。 */
        BRAWLER,
        /** 牵制：区域控制、拖时间、限制对手走位。 */
        CONTROLLER,
        /** 循环：资源与节奏，靠持续收益滚雪球。 */
        CHASER
    }

    private final EchoFaction faction;
    private final Role role;

    EchoClass(EchoFaction faction, Role role) { this.faction = faction; this.role = role; }

    public EchoFaction faction() { return faction; }
    public Role role() { return role; }
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String nameKey() { return "echo.class." + id() + ".name"; }
    public String roleKey() { return "echo.class." + id() + ".role"; }
    public String descriptionKey() { return "echo.class." + id() + ".desc"; }
    public String passiveKey() { return "echo.class." + id() + ".passive"; }
    public String skillKey() { return "echo.class." + id() + ".skill"; }
    public String icon() { return "noellesroles:textures/gui/echo/classes/" + id() + ".png"; }

    public static List<EchoClass> of(EchoFaction faction) {
        return Arrays.stream(values()).filter(c -> c.faction == faction).toList();
    }

    public static Optional<EchoClass> byId(String id) {
        if (id == null) return Optional.empty();
        String normalized = id.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(c -> c.id().equals(normalized)).findFirst();
    }

    /**
     * 按出场序号轮流分配，保证同一阵营的多名玩家拿到不同职业。
     * 下标越界或负数都安全回绕。
     */
    public static EchoClass defaultFor(EchoFaction faction, int index) {
        List<EchoClass> pool = of(faction);
        if (pool.isEmpty()) return HAULER;
        return pool.get(Math.floorMod(index, pool.size()));
    }
}
