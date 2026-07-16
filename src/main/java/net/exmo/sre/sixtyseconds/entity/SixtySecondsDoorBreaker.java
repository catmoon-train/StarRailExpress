package net.exmo.sre.sixtyseconds.entity;

/**
 * 夜袭中能砸门/路障的实体（{@link SixtySecondsMonsterEntity} 自研怪 + {@link SixtySecondsNpcEntity} 强盗）。
 * <p>
 * {@code SixtySecondsDefenseSystem.tickAssault} 原先按 {@code instanceof Zombie} 取每秒破门伤害，
 * 导致非僵尸系的强盗（{@code PathfinderMob}）被每 tick 踢出追踪表（刷得出来但不冲门、清晨不消散）。
 * 改按本接口取值后，两类实体走同一条冲门/破门链，新增会砸门的实体只需 implements 本接口。
 */
public interface SixtySecondsDoorBreaker {
    /** 对家门/路障的每秒伤害（由 DefenseSystem 结算）。 */
    int doorDps();
}
