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

package io.wifi.starrailexpress.content.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.component.BookContent;
import net.minecraft.world.item.component.WritableBookContent;

public record SREWritableBookContent(List<Filterable<String>> pages)
        implements BookContent<String, WritableBookContent> {
    public static final SREWritableBookContent EMPTY = new SREWritableBookContent(List.of());
    public static final int PAGE_EDIT_LENGTH = 4096;
    public static final int MAX_PAGES = 100;
    private static final Codec<Filterable<String>> PAGE_CODEC = Filterable.codec(Codec.string(0, PAGE_EDIT_LENGTH));
    public static final Codec<List<Filterable<String>>> PAGES_CODEC;
    public static final Codec<SREWritableBookContent> CODEC;
    public static final StreamCodec<ByteBuf, SREWritableBookContent> STREAM_CODEC;

    public SREWritableBookContent {
        if (pages.size() > MAX_PAGES) {
            throw new IllegalArgumentException("Got " + pages.size() + " pages, but maximum is " + MAX_PAGES);
        }
    }

    public Stream<String> getPages(boolean bl) {
        return this.pages.stream().map((filterable) -> (String) filterable.get(bl));
    }

    public WritableBookContent withReplacedPages(List<Filterable<String>> list) {
        return new WritableBookContent(list);
    }

    static {
        PAGES_CODEC = PAGE_CODEC.sizeLimitedListOf(MAX_PAGES);
        CODEC = RecordCodecBuilder.create((instance) -> instance
                .group(PAGES_CODEC.optionalFieldOf("pages", List.of()).forGetter(SREWritableBookContent::pages))
                .apply(instance, SREWritableBookContent::new));
        STREAM_CODEC = Filterable.streamCodec(ByteBufCodecs.stringUtf8(PAGE_EDIT_LENGTH))
                .apply(ByteBufCodecs.list(MAX_PAGES))
                .map(SREWritableBookContent::new, SREWritableBookContent::pages);
    }
}
