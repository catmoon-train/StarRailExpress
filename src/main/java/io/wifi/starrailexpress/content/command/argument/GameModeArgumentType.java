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

package io.wifi.starrailexpress.content.command.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.api.SREGameModes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GameModeArgumentType implements ArgumentType<ResourceLocation> {
    private static final Collection<String> EXAMPLES = Stream.of(
                    SREGameModes.MURDER)
            .map(key -> key.identifier.toString())
            .collect(Collectors.toList());
    private static final DynamicCommandExceptionType INVALID_GAME_MODE_EXCEPTION = new DynamicCommandExceptionType(
            id -> Component.translatableEscape("argument.game_mode.invalid", id)
    );

    public ResourceLocation parse(StringReader stringReader) throws CommandSyntaxException {
        return ResourceLocation.read(stringReader);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return context.getSource() instanceof SharedSuggestionProvider
                ? SharedSuggestionProvider.suggestResource(SREGameModes.GAME_MODES.keySet().stream().toList(), builder)
                : Suggestions.empty();
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    public static GameModeArgumentType gameMode() {
        return new GameModeArgumentType();
    }

    public static GameMode getGameModeArgument(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        ResourceLocation identifier = context.getArgument(name, ResourceLocation.class);
        GameMode gameMode = SREGameModes.GAME_MODES.get(identifier);
        if (gameMode == null) {
            throw INVALID_GAME_MODE_EXCEPTION.create(identifier);
        } else {
            return gameMode;
        }
    }
}
