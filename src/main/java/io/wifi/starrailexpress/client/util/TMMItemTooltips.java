package io.wifi.starrailexpress.client.util;

import dev.doctor4t.ratatouille.util.TextUtils;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.game.KillerKnifeDurability;
import io.wifi.starrailexpress.index.DevItems;
import io.wifi.starrailexpress.index.SREBlocks;
import io.wifi.starrailexpress.index.TMMItems;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;

import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TMMItemTooltips {
    public static final int COOLDOWN_COLOR = 0xC90000;
    public static final int LETTER_COLOR = 0xC5AE8B;
    public static final int REGULAR_TOOLTIP_COLOR = 0x808080;

    public static void addTooltips() {
        ItemTooltipCallback.EVENT.register((itemStack, tooltipContext, tooltipType, tooltipList) -> {
            addCooldownText(TMMItems.KNIFE, tooltipList, itemStack);
            addCooldownText(TMMItems.REVOLVER, tooltipList, itemStack);
            addCooldownText(TMMItems.DERRINGER, tooltipList, itemStack);
            addCooldownText(TMMItems.GRENADE, tooltipList, itemStack);
            addCooldownText(TMMItems.LOCKPICK, tooltipList, itemStack);
            addCooldownText(TMMItems.CROWBAR, tooltipList, itemStack);
            addCooldownText(TMMItems.BODY_BAG, tooltipList, itemStack);
            addCooldownText(TMMItems.PSYCHO_MODE, tooltipList, itemStack);
            addCooldownText(TMMItems.BLACKOUT, tooltipList, itemStack);

            addTooltipForItem(TMMItems.KNIFE, itemStack, tooltipList);
            addKnifeDurabilityHint(itemStack, tooltipList);
            addTooltipForItem(TMMItems.REVOLVER, itemStack, tooltipList);
            addTooltipForItem(TMMItems.DERRINGER, itemStack, tooltipList);
            addTooltipForItem(TMMItems.GRENADE, itemStack, tooltipList);
            addTooltipForItem(TMMItems.PSYCHO_MODE, itemStack, tooltipList);
            addTooltipForItem(TMMItems.POISON_VIAL, itemStack, tooltipList);
            addTooltipForItem(TMMItems.SCORPION, itemStack, tooltipList);
            addTooltipForItem(TMMItems.FIRECRACKER, itemStack, tooltipList);
            addTooltipForItem(TMMItems.LOCKPICK, itemStack, tooltipList);
            addTooltipForItem(TMMItems.CROWBAR, itemStack, tooltipList);
            addTooltipForItem(TMMItems.BODY_BAG, itemStack, tooltipList);
            addTooltipForItem(TMMItems.BLACKOUT, itemStack, tooltipList);
            addTooltipForItem(TMMItems.DISGUISE_1, itemStack, tooltipList);
            addTooltipForItem(TMMItems.DISGUISE_2, itemStack, tooltipList);
            addTooltipForItem(TMMItems.DISGUISE_3, itemStack, tooltipList);
            addTooltipForItem(TMMItems.NOTE, itemStack, tooltipList);
            addTooltipForItem(TMMItems.MONITOR_BROKEN, itemStack, tooltipList);
            addTooltipForItem(TMMItems.SNIPER_RIFLE, itemStack, tooltipList);
            addTooltipForItem(TMMItems.MAGNUM_BULLET, itemStack, tooltipList);
            addTooltipForItem(TMMItems.SCOPE, itemStack, tooltipList);

            addTooltipForItem(SREBlocks.TRAIN_LIGHT.asItem(), itemStack, tooltipList);
            addTooltipForItem(SREBlocks.REMOTE_REDSTONE.asItem(), itemStack, tooltipList);
            addTooltipForItem(DevItems.BINDING_TOOL, itemStack, tooltipList);
            addTooltipForItem(ModItems.WREATH, itemStack, tooltipList);
            addTooltipForItem(ModItems.CALMING_TEA, itemStack, tooltipList);
            addTooltipForItem(ModItems.CHOCOLATE, itemStack, tooltipList);
            addTooltipForItem(ModItems.TALISMAN, itemStack, tooltipList);
            addTooltipForItem(ModItems.ENERGIZING_COFFEE, itemStack, tooltipList);
            addTooltipForItem(ModItems.MINT_CANDIES, itemStack, tooltipList);
            addTooltipForItem(ModItems.DOGSKIN_PLASTER, itemStack, tooltipList);
            addTooltipForItem(ModItems.REIMU_GOHEI, itemStack, tooltipList);
            addTooltipForItem(ModItems.MINI_BAGUALU, itemStack, tooltipList);
        });
    }

    /**
     * 当「有限耐久」模式开启时，为刀追加耐久提醒说明；若该刀已被标记耐久，则同时显示剩余耐久。
     * When limited-durability mode is on, append a durability reminder to the
     * knife; if the stack is a
     * durability-stamped knife, also show the remaining points.
     */
    private static void addKnifeDurabilityHint(@NotNull ItemStack itemStack, List<Component> tooltipList) {
        if (!itemStack.is(TMMItems.KNIFE) || !SREConfig.instance().knifeDurabilityMode) {
            return;
        }
        tooltipList.add(Component.translatable("item.starrailexpress.knife.durability_hint",
                KillerKnifeDurability.MAX_DURABILITY).withStyle(Style.EMPTY.withColor(REGULAR_TOOLTIP_COLOR)));
        if (KillerKnifeDurability.isMarkedKnife(itemStack)) {
            int remaining = Math.max(0, itemStack.getMaxDamage() - itemStack.getDamageValue());
            tooltipList.add(Component.translatable("item.starrailexpress.knife.durability_remaining",
                    remaining, itemStack.getMaxDamage())
                    .withStyle(Style.EMPTY.withColor(remaining > 0 ? REGULAR_TOOLTIP_COLOR : COOLDOWN_COLOR)));
        }
    }

    private static void addTooltipForItem(Item item, @NotNull ItemStack itemStack, List<Component> tooltipList) {
        if (itemStack.is(item)) {
            tooltipList.addAll(TextUtils.getTooltipForItem(item, Style.EMPTY.withColor(REGULAR_TOOLTIP_COLOR)));
        }
    }

    private static void addCooldownText(Item item, List<Component> tooltipList, @NotNull ItemStack itemStack) {
        if (!itemStack.is(item))
            return;
        if (Minecraft.getInstance().player == null)
            return;
        ItemCooldowns itemCooldownManager = Minecraft.getInstance().player.getCooldowns();
        if (itemCooldownManager.isOnCooldown(item)) {
            ItemCooldowns.CooldownInstance knifeEntry = itemCooldownManager.cooldowns.get(item);
            int timeLeft = knifeEntry.endTime - itemCooldownManager.tickCount;
            if (timeLeft > 0) {
                int minutes = (int) Math.floor((double) timeLeft / 1200);
                int seconds = (timeLeft - (minutes * 1200)) / 20;
                String countdown = (minutes > 0 ? minutes + "m" : "") + (seconds > 0 ? seconds + "s" : "");
                tooltipList.add(Component.translatable("tip.cooldown", countdown).withColor(COOLDOWN_COLOR));
            }
        }
    }
}
