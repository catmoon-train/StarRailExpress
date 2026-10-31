package org.agmas.noellesroles.game.halloween.online;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class OnlinePumpkinCopyTest {
    @Test void realTimeBorrowingNeverCreatesAnAdditionalIncomeOrResetsOriginalReceipts() throws Exception {
        Path root = Path.of("src/main/java/org/agmas/noellesroles/game/halloween");
        String items = Files.readString(root.resolve("HalloweenLinkItems.java"));
        assertFalse(items.contains("prepareNewRewardCopy"));
        assertFalse(items.contains("onRewardStack"));
        assertFalse(items.contains("CUSTOM_DATA"));
        String handler = Files.readString(root.resolve("pumpkin/PumpkinSpiritHandler.java"));
        assertTrue(handler.contains("HalloweenSoulLinks.isItemBorrower(recipient)) return"));
        String income = Files.readString(root.resolve("online/OnlinePumpkinIncome.java"));
        assertFalse(income.contains("clearCopiedReceipt"));
    }
}
