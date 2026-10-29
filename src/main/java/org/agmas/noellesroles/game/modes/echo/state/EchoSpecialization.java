package org.agmas.noellesroles.game.modes.echo.state;

import org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig;

import java.util.*;

/** 阵营内专长：只调数值，不新增身份/按键/胜利条件；每阵营一个默认项。 */
public enum EchoSpecialization {
    PORTER(EchoFaction.CARRIER, true),
    SCOUT(EchoFaction.CARRIER, false),
    HUNTER(EchoFaction.INTERCEPTOR, true),
    SABOTEUR(EchoFaction.INTERCEPTOR, false),
    SMUGGLER(EchoFaction.BROKER, true),
    FIXER(EchoFaction.BROKER, false);

    private final EchoFaction faction;
    private final boolean starter;

    EchoSpecialization(EchoFaction faction, boolean starter) { this.faction = faction; this.starter = starter; }

    public EchoFaction faction() { return faction; }
    public boolean starter() { return starter; }
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String nameKey() { return "echo.spec." + id() + ".name"; }
    public String descriptionKey() { return "echo.spec." + id() + ".desc"; }

    public static List<EchoSpecialization> of(EchoFaction faction) {
        return Arrays.stream(values()).filter(s -> s.faction == faction).toList();
    }
    public static EchoSpecialization starter(EchoFaction faction) {
        if (faction == null) return PORTER;
        return Arrays.stream(values()).filter(s -> s.faction == faction && s.starter).findFirst().orElse(PORTER);
    }

    public static Optional<EchoSpecialization> byId(String id) {
        if (id == null) return Optional.empty();
        for (var spec : values()) if (spec.id().equals(id)) return Optional.of(spec);
        return Optional.empty();
    }

    /** 该专长的货槽加值；非托运者专长返回 0。 */
    public int cargoCapacityBonus(EchoRuleConfig.Specialization rules) {
        return switch (this) {
            case PORTER -> rules.porter().cargoCapacityBonus();
            case SCOUT -> rules.scout().cargoCapacityBonus();
            default -> 0;
        };
    }

    public double cargoPenaltyDelta(EchoRuleConfig.Specialization rules) {
        return this == PORTER ? rules.porter().cargoPenaltyDelta() : 0;
    }

    public double collectSpeed(EchoRuleConfig.Specialization rules) {
        return this == SCOUT ? rules.scout().collectSpeed() : 1;
    }

    public double blackCollectSpeed(EchoRuleConfig.Specialization rules) {
        return this == SMUGGLER ? rules.smuggler().blackCollectSpeed() : 1;
    }

    /** 破坏者让迟滞引导更快；迟滞本身不扣任何已积累进度。 */
    public double stallSpeed(EchoRuleConfig.Specialization rules) {
        return this == SABOTEUR ? rules.saboteur().stallSpeed() : 1;
    }

    public double cutterChargeSpeed(EchoRuleConfig.Specialization rules) {
        return this == HUNTER ? rules.hunter().cutterChargeSpeed() : 1;
    }

    public double cutterCooldownSpeed(EchoRuleConfig.Specialization rules) {
        return switch (this) {
            case HUNTER -> rules.hunter().cutterCooldownSpeed();
            case SABOTEUR -> rules.saboteur().cutterCooldownSpeed();
            default -> 1;
        };
    }

    public int tradeRangeBonus(EchoRuleConfig.Specialization rules) {
        return this == FIXER ? rules.fixer().tradeRangeBonus() : 0;
    }

    public double proposalCooldownSpeed(EchoRuleConfig.Specialization rules) {
        return this == FIXER ? rules.fixer().proposalCooldownSpeed() : 1;
    }

    /** 把专长倍率作用到该成员的战斗规则副本；非截流者专长原样返回。 */
    public EchoRuleConfig.Combat combat(EchoRuleConfig rules) {
        var base = rules.combat();
        double charge = cutterChargeSpeed(rules.specialization());
        double cooldown = cutterCooldownSpeed(rules.specialization());
        if (charge == 1 && cooldown == 1) return base;
        return new EchoRuleConfig.Combat(scaled(base.cutterChargeTicks(), charge), scaled(base.cutterCooldownTicks(), cooldown),
                base.cutterRange(), base.shoveCooldownTicks(), base.shoveControlTicks(), base.controlImmunityTicks(),
                base.bleedTicks(), base.rescueTicks(), base.rescueProtectionTicks(), base.environmentControlTicks(),
                base.environmentProtectionTicks(), base.rescueRange(), base.shoveRange());
    }

    public static int scaled(int ticks, double speed) {
        return Math.max(1, (int) Math.round(ticks * speed));
    }
}
