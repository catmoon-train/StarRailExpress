package io.wifi.starrailexpress.util;

import io.wifi.starrailexpress.cca.SREPlayerMinigameTaskComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class ShopEntry extends dev.doctor4t.wathe.util.ShopEntry {
    private final Currency currency;
    private final int weight;
    private Component failedMessage = null;

    public ShopEntry(ItemStack stack, int price, dev.doctor4t.wathe.util.ShopEntry.Type type) {
        this(stack, price, type, Currency.MONEY);
    }

    public ShopEntry(ItemStack stack, int price, dev.doctor4t.wathe.util.ShopEntry.Type type, Currency currency) {
        this(stack, price, type, currency, 1);
    }

    public ShopEntry(ItemStack stack, int price, dev.doctor4t.wathe.util.ShopEntry.Type type, Currency currency,
            int weight) {
        super(stack, price, type);
        this.currency = currency == null ? Currency.MONEY : currency;
        this.weight = Math.max(1, weight);
    }

    public Currency currency() {
        return this.currency;
    }

    public int weight() {
        return this.weight;
    }

    public void setFailedMessage(Component message) {
        this.failedMessage = message;
    }

    public Component getFailedMessage() {
        return this.failedMessage;
    }

    public boolean hasEnoughCurrency(@NotNull Player player) {
        return this.currency.getBalance(player) >= this.price();
    }

    public void spendCurrency(@NotNull Player player) {
        this.currency.add(player, -this.price());
    }

    public boolean isSafeTime(@NotNull Player player) {
        return player.hasEffect(ModEffects.SAFE_TIME);
    }

    public boolean canDisplay(@NotNull Player player) {
        return true;
    }

    @Override
    public boolean onBuy(@NotNull Player player) {
        return RoleUtils.insertStackInFreeSlot(player, this.stack().copy());
    }

    @Override
    public ItemStack stack() {
        return super.stack();
    }

    public boolean canBuy(@NotNull Player player) {
        if (this.stack().isEmpty())
            return false;
        return true;
    }

    public enum Currency {
        MONEY("money", "gui.vendingmachine.money_display", 0xFFFFD700) {
            @Override
            public int getBalance(@NotNull Player player) {
                return SREPlayerShopComponent.KEY.get(player).balance;
            }

            @Override
            public void add(@NotNull Player player, int amount) {
                SREPlayerShopComponent.KEY.get(player).addToBalance(amount);
            }

            @Override
            public ItemStack iconStack() {
                return Items.GOLD_INGOT.getDefaultInstance();
            }
        },
        MINIGAME_TOKEN("minigame_token", "gui.vendingmachine.minigame_token_display", 0xFF7CFCA0) {
            @Override
            public int getBalance(@NotNull Player player) {
                return SREPlayerMinigameTaskComponent.KEY.get(player).getTokens();
            }

            @Override
            public void add(@NotNull Player player, int amount) {
                SREPlayerMinigameTaskComponent.KEY.get(player).addTokens(amount);
            }

            @Override
            public ItemStack iconStack() {
                return Items.EMERALD.getDefaultInstance();
            }
        };

        private final String serializedName;
        private final String priceTranslationKey;
        private final int color;

        Currency(String serializedName, String priceTranslationKey, int color) {
            this.serializedName = serializedName;
            this.priceTranslationKey = priceTranslationKey;
            this.color = color;
        }

        public abstract int getBalance(@NotNull Player player);

        public abstract void add(@NotNull Player player, int amount);

        /** 获取该货币对应的图标 ItemStack（用于 GUI 渲染）。 */
        public abstract ItemStack iconStack();

        public String serializedName() {
            return this.serializedName;
        }

        public String priceTranslationKey() {
            return this.priceTranslationKey;
        }

        public int color() {
            return this.color;
        }

        public static Currency fromSerializedName(String name) {
            if (name == null || name.isBlank()) {
                return MONEY;
            }
            String normalized = name.toLowerCase();
            if (normalized.equals("token") || normalized.equals("tokens") || normalized.equals("minigame")) {
                return MINIGAME_TOKEN;
            }
            for (Currency currency : values()) {
                if (currency.serializedName.equals(normalized)) {
                    return currency;
                }
            }
            return MONEY;
        }

        public static List<String> serializedNames() {
            return Arrays.stream(values()).map(Currency::serializedName).toList();
        }
    }
}
