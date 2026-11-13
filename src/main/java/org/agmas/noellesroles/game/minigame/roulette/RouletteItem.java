package org.agmas.noellesroles.game.minigame.roulette;

/** 仅描述赌桌道具，不加载物品注册表，便于独立测试规则。 */
public enum RouletteItem {
    MAGNIFYING_GLASS("magnifying_glass"), BEER("beer"), CIGARETTE("cigarette"),
    HANDSAW("handsaw"), HANDCUFFS("handcuffs"), INVERTER("inverter"),
    TELEPHONE("telephone"), EXPIRED_MEDICINE("expired_medicine"), ADRENALINE("adrenaline");

    private final String id;
    RouletteItem(String id) { this.id = id; }
    public String id() { return id; }
    public String itemId() { return "roulette_" + id; }
    public String titleKey() { return "item.noellesroles." + itemId(); }
    public String descriptionKey() { return "roulette.item." + id; }
    public static RouletteItem byOrdinal(int id) {
        return id >= 0 && id < values().length ? values()[id] : null;
    }
}
