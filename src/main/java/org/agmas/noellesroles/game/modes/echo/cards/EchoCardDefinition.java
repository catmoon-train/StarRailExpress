package org.agmas.noellesroles.game.modes.echo.cards;

import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;

import java.util.*;

/** 纯数据卡牌定义；effectHandlerId只是后续效果服务的稳定路由键，不代表已执行效果。 */
public record EchoCardDefinition(String id, EchoFaction drawFaction, Set<EchoFaction> allowedFactions,
                                  Series series, Tier tier, Rarity rarity, int weight, Activation activation,
                                  Target target, String effectHandlerId, Map<String, Double> params) {
    public enum Series { SILENCE, DECOY, MOBILITY, LOGISTICS, COUNTER, INTEL, ACCESS, TRACKING, SABOTAGE, HUNT, TRADE, DISGUISE, SELF_PRESERVE, SALVAGE, FORGERY }
    public enum Tier { BASE, UPGRADED }
    public enum Rarity { COMMON, RARE, EPIC }
    public enum Activation { ACTIVE, PASSIVE, KEY }
    public enum Target { SELF, GROUND, GROUND_PAIR, PLAYER, BEACON, CORE_GATE, DIRECTION }

    public EchoCardDefinition {
        if (id == null || !id.matches("noellesroles:echo/[a-z_]+") || weight <= 0) throw new IllegalArgumentException("无效卡ID或权重");
        Objects.requireNonNull(drawFaction); Objects.requireNonNull(series); Objects.requireNonNull(tier);
        Objects.requireNonNull(rarity); Objects.requireNonNull(activation); Objects.requireNonNull(target);
        Objects.requireNonNull(effectHandlerId);
        allowedFactions = Set.copyOf(allowedFactions);
        params = Map.copyOf(params);
        if (allowedFactions.isEmpty() || params.values().stream().anyMatch(v -> !Double.isFinite(v))) throw new IllegalArgumentException("无效卡牌参数");
    }

    public String recipeGroup() { return (series == Series.ACCESS ? "SHARED" : drawFaction.name()) + ":" + series.name(); }
    public String shortId() { return id.substring("noellesroles:echo/".length()); }
    public String nameKey() { return "echo.card."+shortId()+".name"; }
    public String descriptionKey() { return "echo.card."+shortId()+".desc"; }
    public String icon() { return "noellesroles:textures/gui/echo/cards/"+shortId()+".png"; }
    public boolean usableBy(EchoFaction faction) { return allowedFactions.contains(faction); }
    public boolean isKey() { return activation == Activation.KEY; }
}
