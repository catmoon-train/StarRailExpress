package org.agmas.harpymodloader.commands.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;

public class ModifierArgumentType implements ArgumentType<SREModifier> {
    public static final DynamicCommandExceptionType MODIFIER_EMPTY = new DynamicCommandExceptionType(input -> Component.translatable("argument.harpymodloader.modifier.notfound", input));
    public static final DynamicCommandExceptionType MODIFIER_MULTIPLE = new DynamicCommandExceptionType(input -> Component.translatable("argument.harpymodloader.modifier.found-multiple", input));
    private static final Collection<String> EXAMPLES = Arrays.asList("foo", "foo:bar", "012");

    public static ModifierArgumentType create() {
        return new ModifierArgumentType();
    }

    public static SREModifier getModifier(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, SREModifier.class);
    }

    @Override
    public SREModifier parse(final StringReader reader) throws CommandSyntaxException {
        String input = new StringReader(reader).readString();
        ResourceLocation modifierId = null;
        try {
            modifierId = ResourceLocation.read(reader);
        } catch (CommandSyntaxException ignored) {
        }
        List<SREModifier> result = new ArrayList<>();
        for (final SREModifier modifier : HMLModifiers.MODIFIERS) {
            if (modifier.identifier().equals(modifierId) || modifier.identifier().getPath().startsWith(input)) {
                result.add(modifier);
            }
        }
        if (result.isEmpty()) {
            throw MODIFIER_EMPTY.create(input);
        }
        if (result.size() > 1) {
            throw MODIFIER_MULTIPLE.create(input);
        }
        return result.getFirst();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(final CommandContext<S> context, final SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestResource(HMLModifiers.MODIFIERS.stream(), builder, SREModifier::identifier, SREModifier::getName);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
