package io.wifi.starrailexpress.client.gui;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class WelcomeLayoutTest {
    @Test
    void longRoleGoalsStayCenteredWithoutMovingTheEarlierParagraphs() {
        for (int height : new int[] {240, 360, 480, 720, 1080}) {
            for (int lines : new int[] {1, 2, 5, 12}) {
                var l = WelcomeLayout.of(height, 48, 36, lines * 18);
                assertEquals(height / 2.0F, l.top() + l.contentHeight() * l.scale() / 2, 0.001F);
                assertTrue(l.top() >= 35.99F);
                assertTrue(l.top() + l.contentHeight() * l.scale() <= height - 35.99F);
                assertTrue(l.ruleY() < l.premiseY() && l.premiseY() + 36 < l.goalY());
            }
        }
    }
}
