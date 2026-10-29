package org.agmas.noellesroles.game.modes.echo.economy;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.game.modes.echo.inventory.EchoBagService;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoMatch;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 对局商店：卡包购买与售货机。
 *
 * <p>两类交易都只做「校验 → 扣款 → 交付」：
 * <ul>
 *   <li>卡包：扣款后直接把对应品质的物资包塞进六格背包，抽卡仍走原来的三选一；</li>
 *   <li>售货机：固定商品，每名玩家每台机器每件商品只能买一次（{@code soldOut} 去重）。</li>
 * </ul>
 *
 * <p>所有失败都不扣款、不发货；状态只保存在本局会话里，跨局自然清空。
 */
public final class EchoShopService {
    /** 售货机固定商品。 */
    public enum VendingGood {
        /** 一瓶治疗（回复 3 HP）。 */
        MEDKIT("medkit", 30),
        /** 一次性加速（短时移动加速）。 */
        STIM("stim", 25),
        /** 一枚信标助推卡。 */
        BEACON_BOOST("beacon_boost", 40),
        /** 一枚稳流阀卡。 */
        STEADY_FLOW("steady_flow", 40),
        /** 一个稀有物资包。 */
        RARE_PACK("rare_pack", 55);

        private final String id;
        private final int price;

        VendingGood(String id, int price) { this.id = id; this.price = price; }

        public String id() { return id; }
        public int price() { return price; }
        public String nameKey() { return "echo.shop.good." + id + ".name"; }
        public String descriptionKey() { return "echo.shop.good." + id + ".desc"; }
    }

    private final EchoMatch match;
    private final ServerLevel level;
    private final EchoEconomy economy;
    /** 已售出的商品：{@code machinePos|goodId|playerUuid}。 */
    private final Set<String> soldOut = new HashSet<>();
    /** 每名玩家最近一次购买 tick，用于限频，避免脚本刷单。 */
    private final Map<UUID, Long> lastPurchase = new HashMap<>();

    public EchoShopService(EchoMatch match, ServerLevel level, EchoEconomy economy) {
        this.match = match;
        this.level = level;
        this.economy = economy;
    }

    /** 商店是否可用：只在作业与撤离阶段开放，倒地/受控不能买。 */
    private boolean canShop(ServerPlayer player) {
        return match.present(player) && match.standing(player)
                && !match.bags().busy(player.getUUID())
                && match.phase().acceptsActions()
                && !io.wifi.starrailexpress.cca.SREGameTimeComponent.KEY.get(level).isTimeFrozen();
    }

    /** 同一玩家两次购买至少间隔 10 tick，防止同一 tick 内连续下单。 */
    private boolean rateLimited(ServerPlayer player, long now) {
        long last = lastPurchase.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2);
        return now - last < 10;
    }

    /**
     * 购买卡包。
     *
     * @return 是否成功；失败时已经给出原因，且不扣款
     */
    public boolean buyPack(ServerPlayer player, EchoPackOffer offer) {
        long now = match.matchTick();
        if (offer == null || !canShop(player)) return false;
        if (rateLimited(player, now)) return false;
        if (!EchoEconomy.spend(player, offer.price())) {
            player.displayClientMessage(net.minecraft.network.chat.Component
                    .translatable("echo.shop.no_coins", offer.price()), true);
            return false;
        }
        lastPurchase.put(player.getUUID(), now);
        match.bags().grant(player, offer.quality(), "shop:" + offer.id() + ":" + now);
        player.displayClientMessage(net.minecraft.network.chat.Component
                .translatable("echo.shop.bought", net.minecraft.network.chat.Component.translatable(offer.nameKey())), true);
        return true;
    }

    /**
     * 在售货机购买固定商品。每名玩家每台机器每件商品只能买一次。
     *
     * @param machine 售货机方块坐标
     */
    public boolean buyGood(ServerPlayer player, BlockPos machine, VendingGood good) {
        long now = match.matchTick();
        if (good == null || machine == null || !canShop(player)) return false;
        if (rateLimited(player, now)) return false;
        String key = machine.asLong() + "|" + good.id() + "|" + player.getUUID();
        if (soldOut.contains(key)) {
            player.displayClientMessage(net.minecraft.network.chat.Component
                    .translatable("echo.shop.sold_out"), true);
            return false;
        }
        if (!EchoEconomy.spend(player, good.price())) {
            player.displayClientMessage(net.minecraft.network.chat.Component
                    .translatable("echo.shop.no_coins", good.price()), true);
            return false;
        }
        // 先登记再交付：交付失败时回滚，保证不会「扣了钱没东西」。
        soldOut.add(key);
        lastPurchase.put(player.getUUID(), now);
        if (!deliver(player, good)) {
            soldOut.remove(key);
            net.minecraft.world.item.ItemStack refund = null;
            io.wifi.starrailexpress.data.PlayerSkinEconomyManager.addCoinNum(player, good.price());
            player.displayClientMessage(net.minecraft.network.chat.Component
                    .translatable("echo.shop.delivery_failed"), true);
            return false;
        }
        player.displayClientMessage(net.minecraft.network.chat.Component
                .translatable("echo.shop.bought", net.minecraft.network.chat.Component.translatable(good.nameKey())), true);
        return true;
    }

    /** 商品交付：卡牌进六格，物资类直接生效。 */
    private boolean deliver(ServerPlayer player, VendingGood good) {
        return switch (good) {
            case MEDKIT -> heal(player);
            case STIM -> match.cards().state(player.getUUID()).boost(
                    new org.agmas.noellesroles.game.modes.echo.cards.EchoEffectState.Boost(
                            "shop_stim", 2, match.matchTick() + 120, 0.20D), match.matchTick());
            case BEACON_BOOST -> grantCard(player, "beacon_boost");
            case STEADY_FLOW -> grantCard(player, "steady_flow");
            case RARE_PACK -> {
                match.bags().grant(player,
                        org.agmas.noellesroles.game.modes.echo.cards.EchoDraftService.PackQuality.RARE,
                        "shop:rare:" + match.matchTick());
                yield true;
            }
        };
    }

    /** 直接发放一张指定卡牌（绕过抽包），满包时落地。 */
    private boolean grantCard(ServerPlayer player, String shortId) {
        var bag = match.bags();
        return bag.grantCard(player, "noellesroles:echo/" + shortId, "shop:" + shortId);
    }

    /** 回复到满血（不超过上限）；已倒地不能使用。 */
    private boolean heal(ServerPlayer player) {
        var state = match.state(player.getUUID());
        if (state == null || !match.life(player.getUUID()).standing()) return false;
        state.heal(3F);
        match.combat().projectPublic(player);
        return true;
    }

    public boolean soldOut(BlockPos machine, VendingGood good, UUID player) {
        return soldOut.contains(machine.asLong() + "|" + good.id() + "|" + player);
    }

    public void clear() {
        soldOut.clear();
        lastPurchase.clear();
    }
}
