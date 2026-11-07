package io.wifi.starrailexpress.event;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 商店及余额的通用扩展点；具体职业、修饰符和活动规则由业务模块一次注册。 */
public final class ShopEvents {
    private ShopEvents() {
    }

    /** 条目基础购买资格检查之前执行，首个 false 短路，不绕过条目自身的约束。 */
    public static final Event<AllowBuy> ALLOW_BUY = EventFactory.createArrayBacked(AllowBuy.class,
            ShopEvents::allowBuyInvoker);

    /** 默认商店列表解析完成后顺序转换；监听器不得修改职业提供的共享列表。 */
    public static final Event<ModifyEntries> MODIFY_ENTRIES = EventFactory.createArrayBacked(ModifyEntries.class,
            ShopEvents::entriesInvoker);

    /** addToBalance 的原始增量，在收入抑制、接管与账本之前顺序转换。 */
    public static final Event<ModifyIncome> MODIFY_INCOME = EventFactory.createArrayBacked(ModifyIncome.class,
            ShopEvents::incomeInvoker);

    /** setBalance 入口通知，早于收入抑制或自定义余额接管导致的提前返回。 */
    public static final Event<BeforeSetBalance> BEFORE_SET_BALANCE = EventFactory.createArrayBacked(
            BeforeSetBalance.class, listeners -> (shop, amount) -> {
                for (BeforeSetBalance listener : listeners) listener.beforeSet(shop, amount);
            });

    /**
     * 收入抑制之后尝试接管余额操作，首个 true 短路并跳过默认账本/写入。
     * addToBalance 传增量，setBalance 传绝对目标值，保持各入口原有语义。
     */
    public static final Event<HandleBalance> HANDLE_BALANCE = EventFactory.createArrayBacked(HandleBalance.class,
            ShopEvents::balanceInvoker);

    /** 未被接管的金币增量，在 setBalance 之前通知。 */
    public static final Event<BalanceDelta> BALANCE_DELTA = EventFactory.createArrayBacked(BalanceDelta.class,
            listeners -> (player, amount) -> {
                for (BalanceDelta listener : listeners) listener.onDelta(player, amount);
            });

    /** 成功购买扣款前，传入实际货币与实际价格，不改变扣款结果。 */
    public static final Event<BeforePayment> BEFORE_PAYMENT = EventFactory.createArrayBacked(BeforePayment.class,
            listeners -> (player, entry, currency, price) -> {
                for (BeforePayment listener : listeners) listener.beforePayment(player, entry, currency, price);
            });

    /** 成功购买的回放参数已冻结、尚未写入回放，保留原来的商店索引。 */
    public static final Event<PurchaseSuccess> PURCHASE_SUCCESS = EventFactory.createArrayBacked(PurchaseSuccess.class,
            listeners -> (shop, index) -> {
                for (PurchaseSuccess listener : listeners) listener.onSuccess(shop, index);
            });

    static AllowBuy allowBuyInvoker(AllowBuy[] listeners) {
        return (player, entry) -> {
            for (AllowBuy listener : listeners) if (!listener.allow(player, entry)) return false;
            return true;
        };
    }

    static ModifyEntries entriesInvoker(ModifyEntries[] listeners) {
        return (role, player, entries) -> {
            List<ShopEntry> result = entries;
            for (ModifyEntries listener : listeners) result = listener.modify(role, player, result);
            return result;
        };
    }

    static ModifyIncome incomeInvoker(ModifyIncome[] listeners) {
        return (player, amount) -> {
            int result = amount;
            for (ModifyIncome listener : listeners) result = listener.modify(player, result);
            return result;
        };
    }

    static HandleBalance balanceInvoker(HandleBalance[] listeners) {
        return (shop, amount) -> {
            for (HandleBalance listener : listeners) if (listener.handle(shop, amount)) return true;
            return false;
        };
    }

    @FunctionalInterface
    public interface AllowBuy {
        boolean allow(Player player, ShopEntry entry);
    }

    @FunctionalInterface
    public interface ModifyEntries {
        List<ShopEntry> modify(SRERole role, @Nullable Player player, List<ShopEntry> entries);
    }

    @FunctionalInterface
    public interface ModifyIncome {
        int modify(Player player, int amount);
    }

    @FunctionalInterface
    public interface BeforeSetBalance {
        void beforeSet(SREPlayerShopComponent shop, int amount);
    }

    @FunctionalInterface
    public interface HandleBalance {
        boolean handle(SREPlayerShopComponent shop, int amount);
    }

    @FunctionalInterface
    public interface BalanceDelta {
        void onDelta(Player player, int amount);
    }

    @FunctionalInterface
    public interface BeforePayment {
        void beforePayment(Player player, ShopEntry entry, ShopEntry.Currency currency, int price);
    }

    @FunctionalInterface
    public interface PurchaseSuccess {
        void onSuccess(SREPlayerShopComponent shop, int index);
    }
}
