package org.agmas.noellesroles.client.map;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/** 动画元数据与实际图片尺寸不一致时的取色边界回归测试。 */
class AreaMapPaletteTest {
    @Test void singleFrameImageIgnoresStaleAnimationIndices() {
        assertFrame(16, 16, 16, 16, IntStream.of(1, 2, 3), 0, 0, 16, 16);
    }

    @Test void selectsFirstValidFrameRatherThanFirstMetadataEntry() {
        assertFrame(32, 32, 16, 16, IntStream.of(-1, Integer.MAX_VALUE, 3, 0), 16, 16, 16, 16);
    }

    @Test void preservesHorizontalAndVerticalAnimationFrames() {
        assertFrame(16, 64, 16, 16, IntStream.of(2, 0), 0, 32, 16, 16);
        assertFrame(64, 16, 16, 16, IntStream.of(3, 0), 48, 0, 16, 16);
    }

    @Test void oversizedFrameDimensionsAreClippedToActualImage() {
        assertFrame(8, 4, 16, 16, IntStream.of(1), 0, 0, 8, 4);
    }

    @Test void emptyMetadataFallsBackToFirstFrameAndIgnoresPartialEdgeFrames() {
        assertFrame(35, 19, 16, 16, IntStream.empty(), 0, 0, 16, 16);
        assertFrame(35, 19, 16, 16, IntStream.of(2, 1), 16, 0, 16, 16);
    }

    private static void assertFrame(int imageWidth, int imageHeight, int frameWidth, int frameHeight,
                                    IntStream frames, int x, int y, int width, int height) {
        var frame = AreaMapPalette.sampleFrame(imageWidth, imageHeight, frameWidth, frameHeight, frames);
        assertEquals(new AreaMapPalette.SampleFrame(x, y, width, height), frame);
        int nx = Math.min(16, frame.width()), nz = Math.min(16, frame.height());
        for (int z = 0; z < nz; z++) {
            for (int sx = 0; sx < nx; sx++) {
                int px = frame.x() + sx * frame.width() / nx;
                int py = frame.y() + z * frame.height() / nz;
                assertTrue(px >= 0 && px < imageWidth, "采样列必须在实际图片内");
                assertTrue(py >= 0 && py < imageHeight, "采样行必须在实际图片内");
            }
        }
    }
}
