package org.agmas.noellesroles.game.halloween;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HalloweenLinkRulesTest {
    static final class Stack { int count, damage; Stack(int count) { this.count = count; } }

    @Test void mirrorOverwritesAllSlotsAndSharesCountAndDurability() {
        var source = new ArrayList<Stack>();
        var target = new ArrayList<Stack>();
        for (int i = 0; i < 41; i++) { source.add(new Stack(i + 1)); target.add(new Stack(99)); }
        assertTrue(HalloweenLinkRules.mirrorSlots(source, target));
        for (int i = 0; i < 41; i++) assertSame(source.get(i), target.get(i));
        target.get(0).count--;
        target.get(40).damage++;
        assertEquals(0, source.get(0).count);
        assertEquals(1, source.get(40).damage);
        assertFalse(HalloweenLinkRules.mirrorSlots(source, target));
        Stack replacement = new Stack(7);
        source.set(38, replacement);
        assertTrue(HalloweenLinkRules.mirrorSlots(source, target));
        assertSame(replacement, target.get(38));
    }

    @Test void detachingMirrorNeverClearsOwnerObjectsOrRestoresOverwrittenItems() {
        Stack item = new Stack(5), old = new Stack(99);
        var source = new ArrayList<>(List.of(item));
        var target = new ArrayList<>(List.of(old));
        HalloweenLinkRules.mirrorSlots(source, target);
        HalloweenLinkRules.detachSlots(target, null);
        assertNull(target.getFirst());
        assertSame(item, source.getFirst());
        assertEquals(5, item.count);
        assertFalse(target.contains(old));
    }

    @Test void productionIsOneWayWithoutRewardCopiesAndCleansBorrowerOnEnd() throws Exception {
        var root = java.nio.file.Path.of("src/main/java/org/agmas/noellesroles");
        String links = java.nio.file.Files.readString(root.resolve("game/halloween/HalloweenSoulLinks.java"));
        String items = java.nio.file.Files.readString(root.resolve("game/halloween/HalloweenLinkItems.java"));
        assertTrue(links.contains("HalloweenLinkItems.sync(sender, recipient)"));
        assertTrue(links.contains("HalloweenLinkItems.sync(a, b)"));
        assertTrue(links.contains("HalloweenLinkItems.clear(borrower)"));
        assertTrue(links.contains("body.setCorpseInventoryFromPlayerInventory(null)"));
        assertTrue(items.contains("source.compartments"));
        assertTrue(items.contains("HalloweenLinkRules.mirrorSlots"));
        assertTrue(items.contains("HalloweenLinkRules.detachSlots"));
        assertFalse(items.contains("onRewardStack"));
        assertFalse(items.contains(".copy()"));
        assertFalse(items.contains("prepareNewRewardCopy"));
        assertTrue(items.contains("USING.remove()"));
        for (String name : List.of("Inventory", "Player", "Menu", "Give", "Use", "Living", "Equipment", "Container"))
            assertTrue(java.nio.file.Files.exists(root.resolve("mixin/halloween/HalloweenLink" + name + "Mixin.java")));
        String inventory = java.nio.file.Files.readString(root.resolve("mixin/halloween/HalloweenLinkInventoryMixin.java"));
        assertTrue(inventory.contains("method = \"save\""));
        assertTrue(inventory.contains("method = \"dropAll\""));
        String actions = java.nio.file.Files.readString(root.resolve("mixin/halloween/HalloweenPlayerActionsMixin.java"));
        assertTrue(actions.contains("case DROP_ITEM, DROP_ALL_ITEMS, SWAP_ITEM_WITH_OFFHAND"));
        String use = java.nio.file.Files.readString(root.resolve("mixin/halloween/HalloweenLinkUseMixin.java"));
        assertTrue(use.contains("player.server.isSameThread()"));
        assertTrue(use.contains("HalloweenLinkItems.use(player"));
    }

    @Test void shieldPoolAddsWithoutDuplicatingOrOverflowing() {
        assertEquals(5, HalloweenLinkRules.sharedArmor(2, 3));
        assertEquals(3, HalloweenLinkRules.sharedArmor(-1, 3));
        assertEquals(Integer.MAX_VALUE, HalloweenLinkRules.sharedArmor(Integer.MAX_VALUE, 1));
    }
    @Test void consumeUsesOwnLayersBeforePartner() {
        assertEquals(2, HalloweenLinkRules.consumeOwn(2, 5));
        assertEquals(1, HalloweenLinkRules.consumeOwn(4, 1));
        assertEquals(0, HalloweenLinkRules.consumeOwn(4, -1));
    }
    @Test void tenBlocksIsInclusiveAndInvalidCoordinatesNeverPass() {
        assertTrue(HalloweenLinkRules.within(100, 10));
        assertFalse(HalloweenLinkRules.within(100.001, 10));
        assertFalse(HalloweenLinkRules.within(Double.NaN, 10));
        assertFalse(HalloweenLinkRules.within(1, Double.POSITIVE_INFINITY));
        assertFalse(HalloweenLinkRules.within(0, 0));
    }
}
