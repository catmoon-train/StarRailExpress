package io.wifi.starrailexpress.content.vote.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import io.wifi.starrailexpress.client.gui.screen.mapui.MapVoteLayout;

class VoteFlowLayoutTest {
    @Test
    void staysInsideScreenAtCommonWindowSizesAndGuiScales() {
        for (int[] window : new int[][] {{854, 480}, {1280, 720}, {1920, 1080}}) {
            for (int scale = 1; scale <= 4; scale++) {
                int width = (int) Math.ceil(window[0] / (double) scale);
                int height = (int) Math.ceil(window[1] / (double) scale);
                if (width < 320 || height < 240) continue; // Minecraft's minimum GUI size.
                var b = VoteFlowFrame.layout(width, height);
                assertTrue(b.x() >= 8 && b.y() >= 8
                        && b.x() + b.w() <= width - 8 && b.y() + b.h() <= height - 8,
                        "Departure board overflows " + width + "x" + height + ": " + b);
            }
        }
    }

    @Test
    void destinationDetailsAndControlsNeverShareTheirVerticalSpace() {
        for (int width : new int[] {320, 427, 640, 854, 1280, 1920}) {
            for (int height : new int[] {240, 270, 360, 480, 720, 1080}) {
                var l = MapVoteLayout.of(width, height);
                assertTrue(l.contentHeight() >= 80, l.toString());
                assertTrue(l.contentY() + l.contentHeight() < l.routeY());
                assertTrue(l.routeY() + l.routeHeight() < l.footerY());
                assertTrue(l.footerY() + 22 <= height - 8);
                assertTrue(l.routeLeft() + l.stationWidth() <= l.routeRight());
                assertTrue(l.buttonX() >= l.contentX());
            }
        }
    }
}
