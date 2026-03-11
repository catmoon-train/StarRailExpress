package io.wifi.starrailexpress.command.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.wifi.starrailexpress.game.MapManager;
import io.wifi.starrailexpress.item.KnifeItem;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MapLoadArgumentType  implements ArgumentType<String> {





    public static String escapeIfRequired(String input) {
        for(char c : input.toCharArray()) {
            if (!StringReader.isAllowedInUnquotedString(c)) {
                return escape(input);
            }
        }

        return input;
    }
    private final StringArgumentType.StringType type;

    private MapLoadArgumentType(StringArgumentType.StringType type) {
        this.type = type;
    }
    public static MapLoadArgumentType word() {
        return new MapLoadArgumentType(StringArgumentType.StringType.SINGLE_WORD);
    }

    public static MapLoadArgumentType string() {
        return new MapLoadArgumentType(StringArgumentType.StringType.QUOTABLE_PHRASE);
    }

    public static MapLoadArgumentType greedyString() {
        return new MapLoadArgumentType(StringArgumentType.StringType.GREEDY_PHRASE);
    }

    public static String getString(CommandContext<?> context, String name) {
        return (String)context.getArgument(name, String.class);
    }
    private static String escape(String input) {
        StringBuilder result = new StringBuilder("\"");

        for(int i = 0; i < input.length(); ++i) {
            char c = input.charAt(i);
            if (c == '\\' || c == '"') {
                result.append('\\');
            }

            result.append(c);
        }

        result.append("\"");
        return result.toString();
    }


    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return context.getSource() instanceof SharedSuggestionProvider
                ? SharedSuggestionProvider.suggest(getExamples(), builder)
                : Suggestions.empty();
    }

    public String parse(StringReader reader) throws CommandSyntaxException {
        if (this.type == StringArgumentType.StringType.GREEDY_PHRASE) {
            String text = reader.getRemaining();
            reader.setCursor(reader.getTotalLength());
            return text;
        } else {
            return this.type == StringArgumentType.StringType.SINGLE_WORD ? reader.readUnquotedString() : reader.readString();
        }
    }


    @Override
    public Collection<String> getExamples() {
        return List.of();
    }
}
