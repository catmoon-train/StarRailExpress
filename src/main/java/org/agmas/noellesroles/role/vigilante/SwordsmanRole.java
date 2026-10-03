package org.agmas.noellesroles.role.vigilante;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.agmas.noellesroles.game.roles.killer.dream.DreamHealthComponent;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role_data.vigilante.SwordsmanRoleData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 剑客（Swordsman）—— 警长阵营特殊警。
 *
 * <ul>
 * <li>开启了 {@code canUseSpVanillaWeapon}：可用武士刀削他人虚拟血量（死因 katana）；</li>
 * <li>开局自带一把武士刀；</li>
 * <li>技能「淬血」：扣除自身<b>虚拟血量上限</b> 50% 的血量（不会把虚拟血量扣到 1 以下，
 * 虚拟血量只剩 1 点时无法使用），15 秒内手上武士刀附带附魔光效、
 * 虚拟血量伤害 ×2，并获得速度 II + 急迫 II；</li>
 * <li>商店：回复虚拟血量（200 金币，虚拟血量满时不可购买）、
 * 锻刀（100 金币，回复手上武士刀 3 点耐久，耐久满时不可购买）。</li>
 * </ul>
 *
 * <p>职业相关规则全部集中在本类，商店条目也在这里定义（不写进 RoleShopHandler）。
 */
public class SwordsmanRole extends NormalRole {

    /** 「回复虚拟血量」售价。 */
    public static final int RESTORE_HEALTH_PRICE = 200;
    /** 「锻刀」售价。 */
    public static final int FORGE_PRICE = 100;
    /** 「锻刀」每次回复的耐久点数。 */
    public static final int FORGE_AMOUNT = 3;
    /** 淬血持续时间：15 秒。 */
    public static final int QUXUE_DURATION_TICKS = 15 * 20;
    /** 淬血冷却：15 秒（与持续时间一致，避免无限续）。 */
    public static final int QUXUE_COOLDOWN_TICKS = 15 * 20;
    /** 淬血期间的虚拟血量伤害倍率。 */
    public static final int QUXUE_DAMAGE_MULTIPLIER = 2;
    /** 淬血扣除的自身虚拟血量百分比（<b>按虚拟血量上限计算</b>）。 */
    private static final int QUXUE_SELF_COST_PERCENT = 50;
    /** 虚拟血量只剩这么多点时无法使用淬血。 */
    private static final int QUXUE_MIN_HEALTH = 1;

    public SwordsmanRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            SRERole.MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    /** 开局自带一把武士刀。 */
    @Override
    public List<ItemStack> getDefaultItems() {
        return List.of(new ItemStack(ModItems.KATANA));
    }

    // ───────────────────────── 技能：淬血 ─────────────────────────

    /**
     * 「淬血」：扣自身<b>虚拟血量上限</b> 50% 的血量，换取 15 秒的伤害 ×2 + 附魔光效 + 速度 II / 急迫 II。
     *
     * <p>扣减量按虚拟血量上限算而非当前血量，所以血量越低开启越划算；但扣减后
     * 不会低于 1 点，虚拟血量只剩 1 点时无法使用。
     *
     * @return true 才消耗技能冷却
     */
    public static boolean useQuXue(RoleSkillContext context) {
        ServerPlayer player = context.player();
        if (player.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        SwordsmanRoleData data = RoleData.getNullable(SwordsmanRoleData.class, player);
        if (data == null) {
            return false;
        }
        DreamHealthComponent health = DreamHealthComponent.KEY.get(player);
        int current = health.currentHealth();
        if (current <= QUXUE_MIN_HEALTH) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.swordsman.quxue_no_health")
                            .withStyle(ChatFormatting.RED), true);
            return false;
        }
        // 扣除「虚拟血量上限」的 50%（不是当前血量的 50%），但不会把虚拟血量扣到 1 以下
        int cost = DreamHealthComponent.maxHealth() * QUXUE_SELF_COST_PERCENT / 100;
        health.setHealth(Math.max(QUXUE_MIN_HEALTH, current - cost));
        data.activateQuXue(QUXUE_DURATION_TICKS, QUXUE_DAMAGE_MULTIPLIER);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BLAZE_AMBIENT,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    // ───────────────────────── 商店 ─────────────────────────

    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> shop = new ArrayList<>();
        shop.add(createRestoreVirtualHealthEntry());
        shop.add(createForgeKatanaEntry());
        return shop;
    }

    /** 「回复虚拟血量」：图标药水，购买即把虚拟血量回满，虚拟血量已满时不可购买。 */
    private static ShopEntry createRestoreVirtualHealthEntry() {
        return new ShopEntry(namedIcon(Items.POTION.getDefaultInstance(),
                        "message.noellesroles.swordsman.shop_restore_name",
                        "message.noellesroles.swordsman.shop_restore_lore"),
                RESTORE_HEALTH_PRICE, ShopEntry.Type.TOOL) {
            @Override
            public boolean canBuy(@NotNull Player player) {
                if (player instanceof ServerPlayer sp && isVirtualHealthFull(sp)) {
                    this.setFailedMessage(
                            Component.translatable("message.noellesroles.swordsman.shop_health_full"));
                    return false;
                }
                return super.canBuy(player);
            }

            @Override
            public boolean onBuy(@NotNull Player player) {
                return player instanceof ServerPlayer sp && restoreVirtualHealth(sp);
            }
        };
    }

    /** 「锻刀」：图标铁锭，购买回复手上武士刀 3 点耐久，耐久已满时不可购买。 */
    private static ShopEntry createForgeKatanaEntry() {
        return new ShopEntry(namedIcon(Items.IRON_INGOT.getDefaultInstance(),
                        "message.noellesroles.swordsman.shop_forge_name",
                        "message.noellesroles.swordsman.shop_forge_lore"),
                FORGE_PRICE, ShopEntry.Type.TOOL) {
            @Override
            public boolean canBuy(@NotNull Player player) {
                ItemStack katana = findKatana(player);
                if (katana == null) {
                    this.setFailedMessage(
                            Component.translatable("message.noellesroles.swordsman.shop_need_katana"));
                    return false;
                }
                if (isKatanaDurabilityFull(katana)) {
                    this.setFailedMessage(
                            Component.translatable("message.noellesroles.swordsman.shop_katana_full"));
                    return false;
                }
                return super.canBuy(player);
            }

            @Override
            public boolean onBuy(@NotNull Player player) {
                return player instanceof ServerPlayer sp && forgeKatanaDurability(sp) > 0;
            }
        };
    }

    /**
     * 给商店图标换上自定义名称与说明（否则会显示成原版的「水瓶」「铁锭」）。
     *
     * <p>写法同 {@code CavalryRole} 的「突进 III」附魔书图标。
     */
    private static ItemStack namedIcon(ItemStack icon, String nameKey, String loreKey) {
        icon.set(DataComponents.CUSTOM_NAME, Component.translatable(nameKey));
        icon.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable(loreKey))));
        return icon;
    }

    // ───────────────────────── 工具方法（供商店 / 技能复用） ─────────────────────────

    /** 虚拟血量是否已满。 */
    public static boolean isVirtualHealthFull(ServerPlayer player) {
        return DreamHealthComponent.KEY.get(player).currentHealth() >= DreamHealthComponent.maxHealth();
    }

    /** 把虚拟血量回满。 */
    public static boolean restoreVirtualHealth(ServerPlayer player) {
        if (isVirtualHealthFull(player)) {
            return false;
        }
        DreamHealthComponent.KEY.get(player).setHealth(DreamHealthComponent.maxHealth());
        return true;
    }

    /**
     * 锻刀：回复手上武士刀 {@link #FORGE_AMOUNT} 点耐久。
     *
     * <p>锻刀只减少已损耗的耐久值，不会把刀锻毁；耐久已满（损耗 0）时无事可做，
     * 由 {@code canBuy} 提前拦下。
     *
     * @return 实际回复的耐久点数；0 表示没有可锻的武士刀或耐久已满
     */
    public static int forgeKatanaDurability(ServerPlayer player) {
        ItemStack katana = findKatana(player);
        if (katana == null || katana.getMaxDamage() <= 0) {
            return 0;
        }
        int before = katana.getDamageValue();
        if (before <= 0) {
            return 0;
        }
        katana.setDamageValue(Math.max(0, before - FORGE_AMOUNT));
        player.inventoryMenu.broadcastChanges();
        return before - katana.getDamageValue();
    }

    /** 手上（主手优先，其次副手）的武士刀；没有则返回 null。 */
    public static ItemStack findKatana(Player player) {
        ItemStack main = player.getMainHandItem();
        if (!main.isEmpty() && main.is(ModItems.KATANA)) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (!off.isEmpty() && off.is(ModItems.KATANA)) {
            return off;
        }
        return null;
    }

    /** 手上所有的武士刀（主手 + 副手），用于批量切换附魔光效。 */
    public static List<ItemStack> katanaStacks(Player player) {
        List<ItemStack> list = new ArrayList<>(2);
        ItemStack main = player.getMainHandItem();
        if (!main.isEmpty() && main.is(ModItems.KATANA)) {
            list.add(main);
        }
        ItemStack off = player.getOffhandItem();
        if (!off.isEmpty() && off.is(ModItems.KATANA)) {
            list.add(off);
        }
        return list;
    }

    /** 武士刀耐久是否已满。 */
    public static boolean isKatanaDurabilityFull(ItemStack katana) {
        return katana.getMaxDamage() <= 0 || katana.getDamageValue() <= 0;
    }

    /**
     * 给手上武士刀挂上 / 摘掉附魔光效（淬血期间的发光）。
     *
     * <p>用原版 {@code ENCHANTMENT_GLINT_OVERRIDE} 组件，不需要真的加附魔。
     */
    public static void applyQuXueGlint(Player player, boolean glowing) {
        for (ItemStack stack : katanaStacks(player)) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, glowing ? Boolean.TRUE : null);
        }
    }
}
