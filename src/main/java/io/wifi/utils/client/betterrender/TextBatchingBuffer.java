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

package io.wifi.utils.client.betterrender;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link MultiBufferSource} that routes every glyph into one {@link BufferBuilder}
 * per render type, then uploads each render type through the vanilla
 * {@code RenderType.draw} state setup when {@link #flush()} is called.
 *
 * <p>This removes the per-vertex {@code BufferSource.getBuffer()} lookups (and
 * the immediate endBatch vanilla performs whenever the glyph render type
 * switches) while keeping the rendering output identical.
 *
 * <p>Instances are meant to be reused across a frame or a draw pass:
 * vertices accumulate in per-render-type builders until {@link #flush()} draws
 * everything and releases the temporary buffers.
 */
public class TextBatchingBuffer implements MultiBufferSource {

    /**
     * Buffer shared by sign text rendering: sign text glyphs are batched here
     * per {@code renderSignText} call and flushed at the end of that call while
     * the world's camera model-view matrix is still active.
     */
    public static final TextBatchingBuffer SIGN_TEXT = new TextBatchingBuffer();

    /**
     * Buffer shared by chat text rendering: chat glyphs are batched here during
     * the GUI render and flushed at the very end of the frame (after the open
     * screen renders), keeping the text on top of the chat backdrop and screen.
     */
    public static final TextBatchingBuffer CHAT = new TextBatchingBuffer();

    private final List<Capture> captures = new ArrayList<>(2);
    private RenderType lastType;
    private BufferBuilder lastBuilder;

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        if (renderType == lastType) {
            return lastBuilder;
        }
        for (int i = 0; i < captures.size(); i++) {
            Capture capture = captures.get(i);
            if (capture.renderType == renderType) {
                lastType = renderType;
                lastBuilder = capture.builder;
                return lastBuilder;
            }
        }
        Capture capture = new Capture(renderType);
        captures.add(capture);
        lastType = renderType;
        lastBuilder = capture.builder;
        return lastBuilder;
    }

    /** Draws every captured render type and releases the temporary buffers. */
    public void flush() {
        for (Capture capture : captures) {
            MeshData mesh = capture.builder.build();
            if (mesh != null) {
                capture.renderType.draw(mesh);
                mesh.close();
            }
            capture.close();
        }
        captures.clear();
        lastType = null;
        lastBuilder = null;
    }

    private static final class Capture implements AutoCloseable {
        final RenderType renderType;
        final ByteBufferBuilder bytes;
        final BufferBuilder builder;

        Capture(RenderType renderType) {
            this.renderType = renderType;
            this.bytes = new ByteBufferBuilder(8192);
            this.builder = new BufferBuilder(this.bytes, renderType.mode(), renderType.format());
        }

        @Override
        public void close() {
            this.bytes.close();
        }
    }
}
