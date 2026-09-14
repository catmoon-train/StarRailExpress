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
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * 实体类型参数：解析 {@code namespace:path} 并从 {@link BuiltInRegistries#ENTITY_TYPE} 取值。
 * <p>
 * 候选来自实体注册表本身，所以其他模组注册的实体自动可选，无需任何枚举 / 硬编码列表。
 * {@code minecraft:player} 被排除——玩家模型需要 {@code AbstractClientPlayer}，
 * 伪装成别的玩家请用 {@code MorphApi}。
 */
public class EntityTypeArgumentType implements ArgumentType<EntityType<?>> {

    private static final SimpleCommandExceptionType ERROR_UNKNOWN_ENTITY_TYPE = new SimpleCommandExceptionType(
            Component.translatable("commands.sre.entitydisguise.unknown_entity_type"));
    private static final Collection<String> EXAMPLES = List.of("minecraft:cow", "minecraft:sheep",
            "minecraft:zombie");
    private static final ResourceLocation PLAYER_ID = EntityType.getKey(EntityType.PLAYER);

    public static EntityTypeArgumentType entityType() {
        return new EntityTypeArgumentType();
    }

    @SuppressWarnings("unchecked")
    public static EntityType<?> getEntityType(CommandContext<?> context, String name) {
        return (EntityType<?>) context.getArgument(name, EntityType.class);
    }

    @Override
    public EntityType<?> parse(StringReader reader) throws CommandSyntaxException {
        ResourceLocation id = ResourceLocation.read(reader);
        if (PLAYER_ID.equals(id) || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            throw ERROR_UNKNOWN_ENTITY_TYPE.createWithContext(reader);
        }
        return BuiltInRegistries.ENTITY_TYPE.get(id);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof SharedSuggestionProvider)) {
            return Suggestions.empty();
        }
        Stream<ResourceLocation> candidates = BuiltInRegistries.ENTITY_TYPE.keySet().stream()
                .filter(id -> !PLAYER_ID.equals(id));
        return SharedSuggestionProvider.suggestResource(candidates, builder);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
