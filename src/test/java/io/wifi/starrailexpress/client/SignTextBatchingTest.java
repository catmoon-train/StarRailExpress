package io.wifi.starrailexpress.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.wifi.starrailexpress.compat.WorldTextBatchingSource;
import io.wifi.starrailexpress.mixin.compat.iris.IrisWorldTextBatchingSourceMixin;
import io.wifi.utils.client.betterrender.TextBatchingBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SignTextBatchingTest {
    private static class OrdinarySource implements MultiBufferSource {
        @Override
        public VertexConsumer getBuffer(RenderType type) {
            throw new AssertionError("选择缓冲时不应创建顶点或提交绘制");
        }
    }

    private static class BatchedSource extends OrdinarySource implements WorldTextBatchingSource { }

    @Test
    void ordinarySourcesKeepExistingPerSignFallback() {
        assertSame(TextBatchingBuffer.SIGN_TEXT, TextBatchingBuffer.signTextSource(new OrdinarySource()));
        assertNotSame(TextBatchingBuffer.CHAT, TextBatchingBuffer.signTextSource(new OrdinarySource()));
    }

    @Test
    void markedWorldSourceIsUsedWithoutPerSignBufferOrDraw() {
        BatchedSource source = new BatchedSource();
        for (int signFace = 0; signFace < 100; signFace++) {
            assertSame(source, TextBatchingBuffer.signTextSource(source));
        }
    }

    @Test
    void worldPassesDoNotRetainPreviousSource() {
        BatchedSource first = new BatchedSource();
        BatchedSource second = new BatchedSource();
        assertSame(first, TextBatchingBuffer.signTextSource(first));
        assertSame(second, TextBatchingBuffer.signTextSource(second));
        assertSame(TextBatchingBuffer.SIGN_TEXT, TextBatchingBuffer.signTextSource(new OrdinarySource()));
        assertSame(first, TextBatchingBuffer.signTextSource(first));
    }

    @Test
    void optionalIrisMixinOnlyAddsBatchingCapability() {
        assertTrue(WorldTextBatchingSource.class.isAssignableFrom(IrisWorldTextBatchingSourceMixin.class));
        assertEquals(0, IrisWorldTextBatchingSourceMixin.class.getDeclaredMethods().length);
        assertEquals(0, IrisWorldTextBatchingSourceMixin.class.getDeclaredFields().length);
    }
}
