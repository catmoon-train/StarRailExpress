/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package io.wifi.starrailexpress.client;

import io.wifi.starrailexpress.event.AllowItemShowInHand;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.agmas.noellesroles.init.ModItems;

/** 客户端对开局赞助者玩偶的观察者专属显示规则。 */
public final class SponsorIntroClientEvents {
    private SponsorIntroClientEvents() {
    }

    public static void register() {
        AllowItemShowInHand.EVENT.register((holder, stack, mainHand) -> {
            if (!stack.has(SREDataComponentTypes.SPONSOR_INTRO)) {
                return null;
            }
            var localPlayer = Minecraft.getInstance().player;
            if (localPlayer == null || holder.getUUID().equals(localPlayer.getUUID())) {
                return null;
            }

            ItemStack letter = new ItemStack(ModItems.LETTER_ITEM);
            var itemName = stack.get(DataComponents.ITEM_NAME);
            if (itemName != null) {
                letter.set(DataComponents.ITEM_NAME, itemName);
            }
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore != null) {
                letter.set(DataComponents.LORE, lore);
            }
            return letter;
        });
    }
}
