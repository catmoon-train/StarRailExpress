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

package io.wifi.starrailexpress.index;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import org.agmas.noellesroles.init.ModItems;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 介绍页：全部药水效果 + 本模组药水物品。
 */
public final class TMMDescEffects {
    public static final List<IntroMobEffect> introEffects = new ArrayList<>();
    public static final Set<Item> introPotionItems = new LinkedHashSet<>();

    private static boolean registered;

    private TMMDescEffects() {
    }

    public static void ensureRegistered() {
        if (registered) {
            return;
        }
        registered = true;
        BuiltInRegistries.MOB_EFFECT.holders().forEach(holder -> introEffects.add(new IntroMobEffect(holder)));
        introEffects.sort(Comparator
                .comparingInt((IntroMobEffect effect) -> namespaceOrder(effect.id().getNamespace()))
                .thenComparing(effect -> effect.getDisplayName().getString(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(effect -> effect.id().toString()));

        addPotion(TMMItems.DEFENSE_VIAL);
        addPotion(TMMItems.WEAK_DEFENSE_VIAL);
        addPotion(TMMItems.POISON_VIAL);
        addPotion(ModItems.DELUSION_VIAL);
        addPotion(ModItems.SPELLBREAKER_POTION);
        addPotion(ModItems.WIZARD_POTION);
        addPotion(ModItems.ALCHEMIST_BUFF_POTION);
        addPotion(ModItems.ADRENALINE);
        addPotion(ModItems.ANTIBIOTIC);
        addPotion(ModItems.HEDINGHONG);
        addPotion(ModItems.DOGSKIN_PLASTER);
        addPotion(ModItems.ANTIDOTE);
        addPotion(ModItems.ANTIDOTE_REAGENT);
        addPotion(ModItems.ANGLER_VANILLA_MILK);
        addPotion(ModItems.SANITY_MEDS);
        addPotion(ModItems.PILL);
        addPotion(ModItems.TOXIN);
        addPotion(ModItems.HALLUCINATION_BOTTLE);
        addPotion(ModItems.DREAM_WINE);
    }

    public static boolean isPotionItem(Item item) {
        ensureRegistered();
        return item != null && introPotionItems.contains(item);
    }

    private static void addPotion(Item item) {
        if (item == null) {
            return;
        }
        introPotionItems.add(item);
        TMMDescItems.introItems.add(item);
    }

    private static int namespaceOrder(String namespace) {
        if ("noellesroles".equals(namespace) || "starrailexpress".equals(namespace)
                || "stupid_express".equals(namespace)) {
            return 0;
        }
        if ("minecraft".equals(namespace)) {
            return 1;
        }
        return 2;
    }
}
