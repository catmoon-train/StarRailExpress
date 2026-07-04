package io.wifi.starrailexpress.content.item.component;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Component.Serializer;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.BookContent;

import org.jetbrains.annotations.Nullable;

public record SREWrittenBookContent(Filterable<String> title, String author,
        List<Filterable<Component>> pages, boolean resolved) implements BookContent<Component, SREWrittenBookContent> {
    public static final SREWrittenBookContent EMPTY = new SREWrittenBookContent(Filterable.passThrough(""), "",
            List.of(), true);
    public static final int PAGE_LENGTH = 32767;
    public static final int TITLE_MAX_LENGTH = 64;
    public static final Codec<Component> CONTENT_CODEC = ComponentSerialization.flatCodec(32767);
    public static final Codec<List<Filterable<Component>>> PAGES_CODEC;
    public static final Codec<SREWrittenBookContent> CODEC;
    public static final StreamCodec<RegistryFriendlyByteBuf, SREWrittenBookContent> STREAM_CODEC;

    private static Codec<Filterable<Component>> pageCodec(Codec<Component> codec) {
        return Filterable.codec(codec);
    }

    public static Codec<List<Filterable<Component>>> pagesCodec(Codec<Component> codec) {
        return pageCodec(codec).listOf();
    }

    @Nullable
    public SREWrittenBookContent resolve(CommandSourceStack commandSourceStack, @Nullable Player player) {
        if (this.resolved) {
            return null;
        } else {
            ImmutableList.Builder<Filterable<Component>> builder = ImmutableList
                    .builderWithExpectedSize(this.pages.size());

            for (Filterable<Component> filterable : this.pages) {
                Optional<Filterable<Component>> optional = resolvePage(commandSourceStack, player, filterable);
                if (optional.isEmpty()) {
                    return null;
                }

                builder.add(optional.get());
            }

            return new SREWrittenBookContent(this.title, this.author, builder.build(), true);
        }
    }

    public SREWrittenBookContent markResolved() {
        return new SREWrittenBookContent(this.title, this.author, this.pages, true);
    }

    private static Optional<Filterable<Component>> resolvePage(CommandSourceStack commandSourceStack,
            @Nullable Player player, Filterable<Component> filterable) {
        return filterable.resolve((component) -> {
            try {
                Component component2 = ComponentUtils.updateForEntity(commandSourceStack, component, player, 0);
                return isPageTooLarge(component2, commandSourceStack.registryAccess()) ? Optional.empty()
                        : Optional.of(component2);
            } catch (Exception var4) {
                return Optional.of(component);
            }
        });
    }

    private static boolean isPageTooLarge(Component component, HolderLookup.Provider provider) {
        return Serializer.toJson(component, provider).length() > PAGE_LENGTH;
    }

    public List<Component> getPages(boolean bl) {
        return Lists.transform(this.pages, (filterable) -> (Component) filterable.get(bl));
    }

    public SREWrittenBookContent withReplacedPages(List<Filterable<Component>> list) {
        return new SREWrittenBookContent(this.title, this.author, list, false);
    }

    static {
        PAGES_CODEC = pagesCodec(CONTENT_CODEC);
        CODEC = RecordCodecBuilder.create((instance) -> instance
                .group(Filterable.codec(Codec.string(0, TITLE_MAX_LENGTH)).fieldOf("title")
                        .forGetter(SREWrittenBookContent::title),
                        Codec.STRING.fieldOf("author").forGetter(SREWrittenBookContent::author),
                        PAGES_CODEC.optionalFieldOf("pages", List.of()).forGetter(SREWrittenBookContent::pages),
                        Codec.BOOL.optionalFieldOf("resolved", false).forGetter(SREWrittenBookContent::resolved))
                .apply(instance, SREWrittenBookContent::new));
        STREAM_CODEC = StreamCodec.composite(Filterable.streamCodec(ByteBufCodecs.stringUtf8(TITLE_MAX_LENGTH)),
                SREWrittenBookContent::title, ByteBufCodecs.STRING_UTF8, SREWrittenBookContent::author,
                Filterable.streamCodec(ComponentSerialization.STREAM_CODEC).apply(ByteBufCodecs.list()),
                SREWrittenBookContent::pages, ByteBufCodecs.BOOL, SREWrittenBookContent::resolved,
                SREWrittenBookContent::new);
    }
}
