package org.agmas.noellesroles.init.events;

import io.wifi.starrailexpress.api.RoleTeam;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.content.item.KnifeItem;
import io.wifi.starrailexpress.event.ItemTransferEvents;
import io.wifi.starrailexpress.event.ShopEvents;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.events.OnShopPurchase;
import org.agmas.noellesroles.game.economy.MoneyFlow;
import org.agmas.noellesroles.game.halloween.HalloweenItems;
import org.agmas.noellesroles.game.halloween.HalloweenRuntime;
import org.agmas.noellesroles.game.halloween.HalloweenSoulLinks;
import org.agmas.noellesroles.game.modifier.taxed.TaxedModifier;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.InsaneKillerRoleData;
import org.agmas.noellesroles.role_data.killer.WizardRoleData;

import java.util.ArrayList;
import java.util.List;

/** 职业、修饰符及活动对通用商店的扩展，集中一次注册而不向核心组件嵌入规则。 */
public final class ShopRoleEventHandlers {
    private static boolean registered;

    private ShopRoleEventHandlers() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        ShopEvents.ALLOW_BUY.register(ShopRoleEventHandlers::allowBuy);
        ShopEvents.MODIFY_ENTRIES.register(ShopRoleEventHandlers::extendHalloweenShop);
        ShopEvents.MODIFY_INCOME.register(ShopRoleEventHandlers::applyIncomeTax);
        ShopEvents.BEFORE_SET_BALANCE.register(ShopRoleEventHandlers::updateBalancePresentation);
        ShopEvents.HANDLE_BALANCE.register(ShopRoleEventHandlers::handleNoCoinBalance);
        // 已知来源先认领，未分类余额增量在此兜底，不重复记录。
        ShopEvents.BALANCE_DELTA.register(MoneyFlow::onBalanceChange);
        ShopEvents.BEFORE_PAYMENT.register((player, entry, currency, price) -> {
            if (currency == ShopEntry.Currency.MONEY) {
                MoneyFlow.recordExpense(player, MoneyFlow.ExpenseType.SHOP, price, entry.stack().getDisplayName());
            }
        });
        ShopEvents.PURCHASE_SUCCESS.register(ShopRoleEventHandlers::notifyShopPurchase);
        ItemTransferEvents.ALLOW_HOTBAR_INSERT.register((player, stack) -> !HalloweenSoulLinks.isItemBorrower(player));
    }

    private static boolean allowBuy(Player player, ShopEntry entry) {
        if (SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.INSANE_KILLER)) {
            var data = RoleData.getNullable(InsaneKillerRoleData.class, player);
            if (data != null && data.inNearDeath()) return false;
        }
        return true;
    }

    private static List<ShopEntry> extendHalloweenShop(SRERole role, Player player, List<ShopEntry> entries) {
        if (player == null || !HalloweenRuntime.isEnabled(player.level()) || !RoleTeam.KILLER.matches(role)
                || entries.stream().noneMatch(entry -> entry.stack().getItem() instanceof KnifeItem)
                || entries.stream().anyMatch(entry -> entry.stack().is(HalloweenItems.BLOOD_SAW))) return entries;
        // 不修改职业共享的商店列表，只为符合条件的结果创建副本。
        List<ShopEntry> result = new ArrayList<>(entries);
        result.add(new ShopEntry(HalloweenItems.BLOOD_SAW.getDefaultInstance(), 200, ShopEntry.Type.WEAPON) {
            @Override
            public boolean canBuy(Player buyer) {
                var actualRole = SREGameWorldComponent.KEY.get(buyer.level()).getRole(buyer);
                return HalloweenRuntime.isEnabled(buyer.level()) && RoleTeam.KILLER.matches(actualRole)
                        && ShopContent.getShopEntries(actualRole, buyer).stream()
                                .anyMatch(entry -> entry.stack().getItem() instanceof KnifeItem)
                        && super.canBuy(buyer);
            }
        });
        return result;
    }

    private static int applyIncomeTax(Player player, int amount) {
        try {
            if (amount <= 0 || !(player instanceof ServerPlayer sp) || !TaxedModifier.shouldApplyTax(sp)) return amount;
            return TaxedModifier.applyTax(amount);
        } catch (Throwable ignored) {
            // 沿用原税收 hook 的回退范围，不把其它监听器的异常一并吞掉。
            return amount;
        }
    }

    private static void updateBalancePresentation(SREPlayerShopComponent shop, int amount) {
        int displayed = shop.getDisplayedBalance();
        boolean negative = shop.hasNegativeBalanceDisplay();
        if (negative && amount >= 0) {
            displayed = amount;
            negative = false;
        } else if (amount > displayed) {
            displayed = amount;
            negative = false;
        } else if (amount < displayed) {
            negative = true;
        }
        shop.setBalancePresentation(amount, displayed, negative);
    }

    private static boolean handleNoCoinBalance(SREPlayerShopComponent shop, int amount) {
        Player player = shop.getPlayer();
        if (player.level().isClientSide) return false;
        var game = SREGameWorldComponent.KEY.get(player.level());
        if (game == null) return false;
        var role = game.getRole(player);
        if (role == null || !role.hasNoCoinSystem()) return false;
        if (role == ModRoles.WIZARD && amount > 0) {
            var data = RoleData.getNullable(WizardRoleData.class, player);
            if (data != null) data.addMana(amount * NoellesRolesConfig.HANDLER.instance().wizardManaPerCoin);
        }
        if (shop.balance != 0) {
            shop.balance = 0;
            shop.sync();
        }
        return true;
    }

    private static void notifyShopPurchase(SREPlayerShopComponent shop, int index) {
        Player buyer = shop.getPlayer();
        if (!(buyer instanceof ServerPlayer sp) || sp.serverLevel().isClientSide) return;
        try {
            var game = SREGameWorldComponent.KEY.get(sp.level());
            if (game == null) return;
            var role = game.getRole(sp);
            if (role == null || !GameUtils.isPlayerAliveAndSurvival(sp)) return;
            List<ShopEntry> entries = ShopContent.getShopEntries(role.getIdentifier(), buyer);
            if (index >= 0 && index < entries.size()) {
                ShopEntry entry = entries.get(index);
                // 保留既有事件 API：重新查询后的基础价，不替换为本次动态扣款价。
                OnShopPurchase.EVENT.invoker().onPurchase(sp, entry, entry.price());
            }
        } catch (Exception ignored) {
            // 保留原购买通知的异常隔离。
        }
    }
}
