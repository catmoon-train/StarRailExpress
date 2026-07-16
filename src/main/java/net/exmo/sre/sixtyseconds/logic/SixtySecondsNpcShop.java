package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.cca.SREPlayerMinigameTaskComponent;
import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.SixtySecondsPhase;
import net.exmo.sre.sixtyseconds.entity.SixtySecondsNpcEntity;
import net.exmo.sre.sixtyseconds.network.OpenNpcShopS2CPacket;
import net.exmo.sre.sixtyseconds.shop.SixtySecondsShopStore;
import net.exmo.sre.sixtyseconds.shop.SixtySecondsShopTable;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;

import java.util.ArrayList;
import java.util.List;

/**
 * 商人交易（服务端）：每日库存/价格惰性刷新 + 购买结算。
 * <p>范式：S2C 开屏 → C2S 动作 → 服务端改状态 → <b>重推 S2C</b>（照抄 {@code RepairRoleShopScreen} 链路）。
 * <p>结算货币是游戏币（{@link SREPlayerMinigameTaskComponent}）；余额不足时自动消耗背包里的
 * 实体币 {@code sixty_seconds_coin}（1 枚 = 1 币）补足——仍不足则<b>一分不扣</b>并提示。
 */
public final class SixtySecondsNpcShop {
    private SixtySecondsNpcShop() {
    }

    /**
     * 惰性每日刷新（照抄物资箱 {@code SupplyBoxBlockEntity.ensureDaily}：不做全图扫描，
     * 只在开屏/购买前按需重算）。种子含 NPC UUID → 同日不同商人价格不同、重开屏不抖。
     */
    public static void ensureDaily(ServerLevel level, SixtySecondsNpcEntity npc) {
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        List<SixtySecondsShopTable.Entry> entries = entriesOf(level, npc);
        int[] stock = npc.getStockLeft();
        int[] price = npc.getPriceNow();
        // 表结构变了（管理员增删了商品）→ 整体重建，避免下标错位
        boolean rebuild = stock.length != entries.size() || price.length != entries.size();
        if (!rebuild && npc.getStockDay() == data.dayNumber) {
            return;
        }
        RandomSource random = RandomSource.create(
                npc.getUUID().getLeastSignificantBits() ^ (data.dayNumber * 0x9E3779B9L));
        int[] newStock = new int[entries.size()];
        int[] newPrice = new int[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            SixtySecondsShopTable.Entry entry = entries.get(i);
            int previous = rebuild || i >= stock.length ? entry.stock : stock[i];
            // 首次/重建给满库存，之后每日按 restockPerDay 回补且不超上限
            newStock[i] = rebuild || npc.getStockDay() < 0
                    ? entry.stock
                    : Math.min(entry.stock, previous + entry.restockPerDay);
            float jitter = 1.0F + entry.priceJitter * (random.nextFloat() * 2.0F - 1.0F);
            newPrice[i] = Math.max(1, Math.round(entry.price * jitter));
        }
        npc.setDailyStock(data.dayNumber, newStock, newPrice);
    }

    public static List<SixtySecondsShopTable.Entry> entriesOf(ServerLevel level, SixtySecondsNpcEntity npc) {
        return SixtySecondsShopStore.get(level).entriesOf(npc.getShopProfile());
    }

    /** 开购买屏（先刷新当日库存/价格再下发快照）。 */
    public static void openShop(ServerPlayer player, SixtySecondsNpcEntity npc) {
        ServerLevel level = player.serverLevel();
        ensureDaily(level, npc);
        ServerPlayNetworking.send(player, buildPacket(level, player, npc));
    }

    private static OpenNpcShopS2CPacket buildPacket(ServerLevel level, ServerPlayer player,
            SixtySecondsNpcEntity npc) {
        List<SixtySecondsShopTable.Entry> entries = entriesOf(level, npc);
        int[] stock = npc.getStockLeft();
        int[] price = npc.getPriceNow();
        List<OpenNpcShopS2CPacket.Row> rows = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            SixtySecondsShopTable.Entry entry = entries.get(i);
            rows.add(new OpenNpcShopS2CPacket.Row(
                    entry.itemId == null ? "" : entry.itemId,
                    Math.max(1, entry.count),
                    i < price.length ? price[i] : entry.price,
                    i < stock.length ? stock[i] : 0));
        }
        return new OpenNpcShopS2CPacket(npc.getId(), npc.getDisplayName().getString(),
                totalFunds(player), rows);
    }

    /**
     * 购买结算。<b>服务端全量重校验</b>（模式激活/白天/存活/是商人/未逃跑/距离/下标/库存），
     * 任一不过静默拒绝——C2S 是可伪造的。
     */
    public static void buy(ServerPlayer player, SixtySecondsNpcEntity npc, int rowIndex, int count) {
        ServerLevel level = player.serverLevel();
        if (!SixtySecondsMod.isActive(level)) {
            return;
        }
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        if (data.phase != SixtySecondsPhase.DAY) {
            return;
        }
        if (!npc.isAlive() || npc.getVariant() != SixtySecondsNpcEntity.Variant.MERCHANT) {
            return;
        }
        if (npc.isFleeing()) {
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.npc.merchant_refuses")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (npc.distanceToSqr(player) > SixtySecondsBalance.NPC_USE_DISTANCE_SQR) {
            return;
        }
        ensureDaily(level, npc);
        List<SixtySecondsShopTable.Entry> entries = entriesOf(level, npc);
        if (rowIndex < 0 || rowIndex >= entries.size()) {
            return;
        }
        count = Mth.clamp(count, 1, 64);
        SixtySecondsShopTable.Entry entry = entries.get(rowIndex);
        int[] stock = npc.getStockLeft();
        int[] price = npc.getPriceNow();
        if (rowIndex >= stock.length || rowIndex >= price.length) {
            return;
        }
        // 库存不够就按剩余量买（而不是整单失败）
        count = Math.min(count, stock[rowIndex]);
        if (count <= 0) {
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.npc.sold_out").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        ItemStack sample = SixtySecondsShopTable.makeStack(entry);
        if (sample.isEmpty()) {
            return; // 管理员配了个无效 itemId：拒绝成交，别扣钱
        }
        int totalPrice = price[rowIndex] * count;
        if (totalFunds(player) < totalPrice) {
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.npc.no_tokens", totalPrice)
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        // 先扣钱再给货：扣款失败（并发/校验漏网）就不该发货
        if (!spend(player, totalPrice)) {
            return;
        }
        stock[rowIndex] -= count;
        for (int i = 0; i < count; i++) {
            ItemStack give = SixtySecondsShopTable.makeStack(entry);
            if (!player.getInventory().add(give)) {
                player.drop(give, false);
            }
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8F, 1.0F);
        player.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.npc.bought",
                sample.getHoverName(), count, totalPrice).withStyle(ChatFormatting.GREEN), true);
        // 重推 S2C：刷新库存/价格/余额显示
        ServerPlayNetworking.send(player, buildPacket(level, player, npc));
    }

    /** 可用资金 = 游戏币余额 + 背包里的实体币枚数（1 枚 = 1 币）。 */
    public static int totalFunds(ServerPlayer player) {
        return SREPlayerMinigameTaskComponent.KEY.get(player).getTokens() + countCoins(player);
    }

    private static int countCoins(ServerPlayer player) {
        int coins = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ModItems.SIXTY_SECONDS_COIN)) {
                coins += stack.getCount();
            }
        }
        return coins;
    }

    /**
     * 扣款：先扣余额，不足的部分再扫背包消耗实体币补足。
     * 调用前须确认 {@link #totalFunds} 足额；不足时返回 false 且<b>不扣任何东西</b>。
     */
    public static boolean spend(ServerPlayer player, int amount) {
        if (amount <= 0) {
            return true;
        }
        SREPlayerMinigameTaskComponent tokens = SREPlayerMinigameTaskComponent.KEY.get(player);
        int balance = tokens.getTokens();
        int fromBalance = Math.min(balance, amount);
        int remaining = amount - fromBalance;
        if (remaining > 0 && countCoins(player) < remaining) {
            return false; // 余额+实体币仍不足：一分不扣
        }
        if (fromBalance > 0) {
            tokens.addTokens(-fromBalance);
        }
        if (remaining > 0) {
            takeCoins(player, remaining);
        }
        return true;
    }

    /** 从背包消耗指定枚数的实体币（调用前须确认足额）。 */
    private static void takeCoins(ServerPlayer player, int amount) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && amount > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(ModItems.SIXTY_SECONDS_COIN)) {
                continue;
            }
            int take = Math.min(amount, stack.getCount());
            stack.shrink(take);
            amount -= take;
        }
    }
}
