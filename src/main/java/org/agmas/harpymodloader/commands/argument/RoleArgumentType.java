package org.agmas.harpymodloader.commands.argument;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.agmas.harpymodloader.Harpymodloader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class RoleArgumentType implements ArgumentType<SRERole> {
    public static final DynamicCommandExceptionType ROLE_EMPTY = new DynamicCommandExceptionType(input -> Component.translatable("argument.harpymodloader.role.notfound", input));
    public static final DynamicCommandExceptionType ROLE_MULTIPLE = new DynamicCommandExceptionType(input -> Component.translatable("argument.harpymodloader.role.found-multiple", input));
    private static final Collection<String> EXAMPLES = Arrays.asList("foo", "foo:bar", "012");

    private final boolean skipVanilla;

    public RoleArgumentType(final boolean skipVanilla) {
        this.skipVanilla = skipVanilla;
    }

    public static RoleArgumentType skipVanilla() {
        return new RoleArgumentType(true);
    }

    public static RoleArgumentType create() {
        return new RoleArgumentType(false);
    }

    public static RoleArgumentType create(boolean skipVanilla) {
        return new RoleArgumentType(skipVanilla);
    }

    public static SRERole getRole(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, SRERole.class);
    }

    @Override
    public SRERole parse(final StringReader reader) throws CommandSyntaxException {
        ResourceLocation roleId = null;
        String input = new StringReader(reader).readString();
        try {
            roleId = ResourceLocation.read(reader);
        } catch (CommandSyntaxException ignored) {
        }
        List<SRERole> matchRoles = new ArrayList<>();
        for (final SRERole role : TMMRoles.ROLES.values()) {
            if (skipVanilla && Harpymodloader.VANNILA_ROLES.contains(role)) {
                continue;
            }
            if (role.identifier().equals(roleId) || role.identifier().getPath().startsWith(input)) {
                matchRoles.add(role);
            }
        }
        if (matchRoles.isEmpty()) {
            throw ROLE_EMPTY.create(input);
        }
        if (matchRoles.size() > 1) {
            throw ROLE_MULTIPLE.create(input);
        }
        return matchRoles.getFirst();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(final CommandContext<S> context, final SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestResource(
                TMMRoles.ROLES.values().stream().filter(role -> !skipVanilla || !Harpymodloader.VANNILA_ROLES.contains(role)),
                builder,
                SRERole::identifier,
                Harpymodloader::getRoleName
        );
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    public static class Serializer implements ArgumentTypeInfo<RoleArgumentType, Serializer.Properties> {

        @Override
        public Properties deserializeFromNetwork(final FriendlyByteBuf buf) {
            return new Properties(buf.readBoolean());
        }

        public class Properties implements ArgumentTypeInfo.Template<RoleArgumentType> {
            private final boolean skipVanilla;

            public Properties(final boolean skipVanilla) {
                this.skipVanilla = skipVanilla;
            }

            @Override
            public RoleArgumentType instantiate(final CommandBuildContext commandRegistryAccess) {
                return new RoleArgumentType(skipVanilla);
            }

            @Override
            public ArgumentTypeInfo<RoleArgumentType, ?> type() {
                return Serializer.this;
            }
        }

        @Override
        public void serializeToJson(Properties template, JsonObject jsonObject) {
            jsonObject.addProperty("skipVanilla", template.skipVanilla);
        }

        @Override
        public void serializeToNetwork(Properties template, FriendlyByteBuf friendlyByteBuf) {
            friendlyByteBuf.writeBoolean(template.skipVanilla);
        }

        @Override
        public Properties unpack(RoleArgumentType argumentType) {
            return new Properties(argumentType.skipVanilla);
        }
    }
}
