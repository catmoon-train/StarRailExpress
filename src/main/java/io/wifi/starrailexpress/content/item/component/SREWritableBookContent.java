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
