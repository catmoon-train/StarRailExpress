/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.util;

import io.wifi.starrailexpress.cca.SREPlayerMinigameTaskComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.ShopEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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

    /**
     * 支持售货机与职业商店使用（职业商店按条目货币校验/扣款/显示，默认金币）！！！
     * 
     * @param stack
     * @param price
     * @param type
     * @param currency
     * @param weight
     */
    public ShopEntry(ItemStack stack, int price, dev.doctor4t.wathe.util.ShopEntry.Type type, Currency currency) {
        this(stack, price, type, currency, 1);
    }

    /**
     * 支持售货机与职业商店使用（职业商店按条目货币校验/扣款/显示，默认金币）！！！
     * 
     * @param stack
     * @param price
     * @param type
     * @param currency
     * @param weight
     */
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
        if (!ShopEvents.ALLOW_BUY.invoker().allow(player, this)) return false;
        if (this.stack().isEmpty())
            return false;
        return true;
    }

    /**
     * 仅支持售货机使用！！！
     * 
     * @param stack
     * @param price
     * @param type
     * @param currency
     * @param weight
     */
    public enum Currency {
        MONEY("money", "gui.vendingmachine.money_display", "sre.custom_role.shop.currency.money", 0xFFFFD700) {
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

            @Override
            public String iconText() {
                return "\uF6A8";
            }
        },
        MINIGAME_TOKEN("minigame_token", "gui.vendingmachine.minigame_token_display",
                "sre.custom_role.shop.currency.minigame_token", 0xFF7CFCA0) {
            @Override
            public int getBalance(@NotNull Player player) {
                return SREPlayerMinigameTaskComponent.KEY.get(player).getTokens();
            }

            @Override
            public String iconText() {
                return "\uF6A9";
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
        private final String displayNameKey;
        private final int color;

        Currency(String serializedName, String priceTranslationKey, String displayNameKey, int color) {
            this.serializedName = serializedName;
            this.priceTranslationKey = priceTranslationKey;
            this.displayNameKey = displayNameKey;
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

        /** 货币名字的翻译键（不含图标，用于切换货币等纯文本场合）。 */
        public String displayNameKey() {
            return this.displayNameKey;
        }

        /**
         * 「数量 + 货币图标」的文本片段，例如 {@code 120} 后跟一枚金币图标。
         * <p>
         * 图标码位由 {@link #iconText()} 硬编码提供，语言文件里只留 {@code %s} 占位，
         * 避免翻译工具把私有使用区字形改写成乱码方块。
         */
        public Component amountText(int amount) {
            return Component.literal(Integer.toString(amount)).append(this.iconText());
        }

        /**
         * 用本货币的价格翻译键格式化数量。
         * <p>
         * 价格翻译键的文案只包含 {@code %s}（数量），图标一律由代码追加，
         * 语言文件无法修改货币字形。
         */
        public MutableComponent formatAmount(int amount) {
            return Component.translatable(this.priceTranslationKey(), this.amountText(amount));
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

        /**
         * 货币图标字形（Unicode 私有使用区码位）。
         * <p>
         * 必须与 {@code assets/minecraft/font/default.json} 中注册的字符一致。
         * 该码位刻意写死在代码里，不允许由语言文件提供。
         */
        public abstract String iconText();
    }
}
