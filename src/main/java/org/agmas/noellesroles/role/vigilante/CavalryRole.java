package org.agmas.noellesroles.role.vigilante;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.event.OnShieldBroken;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.FunnyItems;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role_data.vigilante.CavalryRoleData;
import org.agmas.noellesroles.spear.SpearCombat;
import org.agmas.noellesroles.spear.SpearConfig;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 骑兵（Cavalry）—— 警长阵营特殊警长，只在有马的地图出现。
 *
 * <ul>
 * <li>体力为平民的 2.5 倍；</li>
 * <li>开局自带一个超级猪马蹄铁；</li>
 * <li>商店：下界合金矛 75 金币（已拥有时不可购买）、突进 III 附魔 200 金币（一局一次，
 * 直接给快捷栏里的矛附魔）、超级猪马蹄铁 75 金币。</li>
 * </ul>
 *
 * <p>职业相关规则全部集中在本类，商店条目也在这里定义（不再写进 RoleShopHandler）。
 */
public class CavalryRole extends NormalRole {

    /** 下界合金矛售价。 */
    public static final int SPEAR_PRICE = 75;
    /** 突进 III 附魔售价。 */
    public static final int LUNGE_PRICE = 200;
    /** 超级猪马蹄铁售价。 */
    public static final int HORSESHOE_PRICE = 75;
    /** 商店附魔的突进等级。 */
    public static final int LUNGE_LEVEL = 3;

    static {
        OnShieldBroken.EVENT.register((victim, killer) -> {
            CavalryRoleData data = RoleData.getNullable(CavalryRoleData.class, victim);
            if (data != null) {
                data.onShieldBrokenWhileMounted();
            }
        });
    }

    public CavalryRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            SRERole.MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    // ───────────────────────── 商店 ─────────────────────────

    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> shop = new ArrayList<>();
        shop.add(createSpearEntry());
        shop.add(createLungeEntry());
        shop.add(createHorseshoeEntry());
        return shop;
    }

    /** 下界合金矛：已拥有时无法购买。 */
    private static ShopEntry createSpearEntry() {
        return new ShopEntry(ModItems.NETHERITE_SPEAR.getDefaultInstance(), SPEAR_PRICE, ShopEntry.Type.WEAPON) {
            @Override
            public boolean canBuy(@NotNull Player player) {
                if (MCItemsUtils.hasItem(player, ModItems.NETHERITE_SPEAR)) {
                    this.setFailedMessage(
                            Component.translatable("message.noellesroles.cavalry.shop_already_owned"));
                    return false;
                }
                return super.canBuy(player);
            }
        };
    }

    /** 突进 III：一局一次，购买即为快捷栏里的下界合金矛附魔。 */
    private static ShopEntry createLungeEntry() {
        return new ShopEntry(lungeBookDisplayStack(), LUNGE_PRICE, ShopEntry.Type.TOOL) {
            @Override
            public boolean canBuy(@NotNull Player player) {
                var data = RoleData.getNullable(CavalryRoleData.class, player);
                if (data != null && data.lungeBought) {
                    this.setFailedMessage(Component.translatable("message.noellesroles.cavalry.shop_lunge_used"));
                    return false;
                }
                ItemStack spear = findSpearInHotbar(player);
                if (spear == null) {
                    this.setFailedMessage(Component.translatable("message.noellesroles.cavalry.shop_need_spear"));
                    return false;
                }
                if (SpearCombat.lungeLevel(spear) >= LUNGE_LEVEL) {
                    this.setFailedMessage(
                            Component.translatable("message.noellesroles.cavalry.shop_already_enchanted"));
                    return false;
                }
                return super.canBuy(player);
            }

            @Override
            public boolean onBuy(@NotNull Player player) {
                ItemStack spear = findSpearInHotbar(player);
                if (spear == null || !applyLunge(player, spear, LUNGE_LEVEL)) {
                    return false;
                }
                var data = RoleData.getNullable(CavalryRoleData.class, player);
                if (data != null) {
                    data.lungeBought = true;
                    data.sync();
                }
                return true;
            }
        };
    }

    /** 超级猪马蹄铁：额外可购买。 */
    private static ShopEntry createHorseshoeEntry() {
        return new ShopEntry(FunnyItems.SUPER_PIG_HORSESHOE.getDefaultInstance(), HORSESHOE_PRICE,
                ShopEntry.Type.TOOL);
    }

    /** 商店里「突进 III」的图标：一本写着突进 III 的附魔书。 */
    private static ItemStack lungeBookDisplayStack() {
        ItemStack book = Items.ENCHANTED_BOOK.getDefaultInstance();
        book.set(DataComponents.CUSTOM_NAME,
                Component.translatable("enchantment.noellesroles.lunge").append(" ")
                        .append(Component.translatable("enchantment.level." + LUNGE_LEVEL)));
        book.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("message.noellesroles.cavalry.shop_lunge_lore"))));
        return book;
    }

    /** 在快捷栏里找下界合金矛。 */
    public static ItemStack findSpearInHotbar(Player player) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (SpearConfig.isSpear(stack)) {
                return stack;
            }
        }
        return null;
    }

    /** 给矛附上「突进」附魔（需要在服务端执行，因为要查附魔注册表）。 */
    public static boolean applyLunge(Player player, ItemStack spear, int level) {
        if (spear == null || spear.isEmpty() || !(player.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        var holder = serverLevel.registryAccess().registryOrThrow(Registries.ENCHANTMENT).holders()
                .filter(h -> h.unwrapKey()
                        .map(key -> key.location().equals(Noellesroles.id(SpearConfig.LUNGE_ENCHANTMENT_PATH)))
                        .orElse(false))
                .findFirst()
                .orElse(null);
        if (holder == null) {
            return false;
        }
        spear.enchant(holder, level);
        return true;
    }

    /** 骑兵护盾只对模组提供的三种坐骑生效。 */
    public static boolean isCavalryMount(Entity vehicle) {
        return vehicle instanceof org.agmas.noellesroles.content.entity.RainbowHorseEntity
                || vehicle instanceof org.agmas.noellesroles.content.entity.CanyuesaHorseEntity
                || vehicle instanceof org.agmas.noellesroles.content.entity.SuperPigHorseEntity;
    }
}
