package org.agmas.noellesroles.game.modes.echo.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.game.modes.echo.map.EchoMapDraft;
import org.agmas.noellesroles.game.modes.echo.rules.EchoRosterPolicy;

/** 静态命令树，不注册新按键，不以字符串拼接执行管理命令。 */
public final class EchoMapCommand {
    private EchoMapCommand() { }
    public static void initialize() {
        ArgumentTypeRegistry.registerArgumentType(ResourceLocation.fromNamespaceAndPath("noellesroles","echo_token"),EchoTokenArgument.class,SingletonArgumentInfo.contextFree(EchoTokenArgument::token));
        ServerTickEvents.START_SERVER_TICK.register(server -> { EchoMapTools.tick(server); EchoRouteTools.tick(server); org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorGenerator.tick(server); });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server) -> { EchoMapTools.disconnected(handler.player.getUUID()); EchoRouteTools.disconnected(handler.player.getUUID()); });
        ServerWorldEvents.UNLOAD.register((server,level) -> { EchoMapTools.unload(level); EchoRouteTools.unload(level); });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { EchoMapTools.stop(); EchoRouteTools.stop(); });
    }
    private static String arg(CommandContext<CommandSourceStack> context,String name) { return context.getArgument(name,String.class); }
    public static LiteralArgumentBuilder<CommandSourceStack> tree() {
        var map=Commands.literal("map").requires(source -> source.hasPermission(2));
        for(boolean fresh:new boolean[]{true,false}) map.then(Commands.literal(fresh?"new":"edit")
                .then(Commands.argument("mapId",EchoTokenArgument.token()).executes(c -> EchoMapTools.run(c.getSource(),() -> EchoMapTools.begin(c.getSource(),arg(c,"mapId"),fresh)))));
        map.then(Commands.literal("cancel").executes(c -> EchoMapTools.run(c.getSource(),() -> EchoMapTools.cancel(c.getSource()))));
        map.then(Commands.literal("list").executes(c -> EchoMapTools.run(c.getSource(),() -> EchoMapTools.list(c.getSource()))));
        map.then(Commands.literal("mark").then(Commands.argument("kind",EchoTokenArgument.token())
                .suggests((c,b) -> SharedSuggestionProvider.suggest(EchoMapDraft.KINDS,b))
                .then(Commands.argument("id",EchoTokenArgument.token())
                        .executes(c -> EchoMapTools.run(c.getSource(),() -> EchoMapTools.mark(c.getSource(),arg(c,"kind"),arg(c,"id"),6)))
                        .then(Commands.argument("minPlayers",IntegerArgumentType.integer(EchoRosterPolicy.MIN_PLAYERS,EchoRosterPolicy.MAX_PLAYERS))
                                .executes(c -> EchoMapTools.run(c.getSource(),() -> EchoMapTools.mark(c.getSource(),arg(c,"kind"),arg(c,"id"),IntegerArgumentType.getInteger(c,"minPlayers"))))))));
        map.then(Commands.literal("corner")
                .then(Commands.literal("a").executes(c -> EchoMapTools.run(c.getSource(),() -> EchoMapTools.corner(c.getSource(),true))))
                .then(Commands.literal("b").executes(c -> EchoMapTools.run(c.getSource(),() -> EchoMapTools.corner(c.getSource(),false)))));
        map.then(Commands.literal("volume").then(Commands.argument("kind",EchoTokenArgument.token())
                .suggests((c,b) -> SharedSuggestionProvider.suggest(EchoMapDraft.VOLUMES,b))
                .then(Commands.argument("id",EchoTokenArgument.token()).executes(c -> EchoMapTools.run(c.getSource(),() -> EchoMapTools.volume(c.getSource(),arg(c,"kind"),arg(c,"id")))))));
        map.then(Commands.literal("remove").then(Commands.argument("field",EchoTokenArgument.token())
                .suggests((c,b) -> SharedSuggestionProvider.suggest(java.util.List.of("carrierSpawns","interceptorSpawns","brokerSpawns","beacons","crystalNodes","altars","supplyCrates","blackCrystalNodes","blackDropoffs","doors"),b))
                .then(Commands.argument("id",EchoTokenArgument.token()).executes(c -> EchoMapTools.run(c.getSource(),() -> EchoMapTools.remove(c.getSource(),arg(c,"field"),arg(c,"id")))))));
        map.then(Commands.literal("save").then(Commands.argument("mapId",EchoTokenArgument.token())
                .executes(c -> EchoMapTools.run(c.getSource(),() -> EchoMapTools.save(c.getSource(),arg(c,"mapId"),null)))
                .then(Commands.literal("confirm").then(Commands.argument("nonce",EchoTokenArgument.token())
                        .executes(c -> EchoMapTools.run(c.getSource(),() -> EchoMapTools.save(c.getSource(),arg(c,"mapId"),arg(c,"nonce"))))))));
        map.then(Commands.literal("validate")
                .executes(c -> EchoMapTools.run(c.getSource(),() -> EchoMapTools.validate(c.getSource(),null)))
                .then(Commands.argument("mapId",EchoTokenArgument.token()).executes(c -> EchoMapTools.run(c.getSource(),() -> EchoMapTools.validate(c.getSource(),arg(c,"mapId"))))));
        map.then(Commands.literal("preview")
                .executes(c -> EchoMapTools.run(c.getSource(),() -> EchoMapTools.preview(c.getSource(),null)))
                .then(Commands.argument("mapId",EchoTokenArgument.token()).executes(c -> EchoMapTools.run(c.getSource(),() -> EchoMapTools.preview(c.getSource(),arg(c,"mapId"))))));
        map.then(Commands.literal("routes")
                .executes(c -> EchoMapTools.run(c.getSource(),() -> EchoRouteTools.start(c.getSource(),null)))
                .then(Commands.literal("confirm").then(Commands.argument("nonce",EchoTokenArgument.token())
                        .executes(c -> EchoMapTools.run(c.getSource(),() -> org.agmas.noellesroles.game.modes.echo.runtime.EchoRouteAdmission.confirm(c.getSource(),arg(c,"nonce"))))))
                .then(Commands.literal("cancel").executes(c -> EchoMapTools.run(c.getSource(),() -> EchoRouteTools.cancel(c.getSource()))))
                .then(Commands.argument("mapId",EchoTokenArgument.token()).executes(c -> EchoMapTools.run(c.getSource(),() -> EchoRouteTools.start(c.getSource(),arg(c,"mapId"))))));
        map.then(Commands.literal("gate")
                .executes(c -> EchoMapTools.run(c.getSource(), () -> EchoMapTools.gate(c.getSource(), null)))
                .then(Commands.literal("on")
                        .executes(c -> EchoMapTools.run(c.getSource(), () -> EchoMapTools.gate(c.getSource(), true))))
                .then(Commands.literal("off")
                        .executes(c -> EchoMapTools.run(c.getSource(), () -> EchoMapTools.gate(c.getSource(), false)))));
        map.then(Commands.literal("generate")
                .then(Commands.argument("mapId", EchoTokenArgument.token())
                        .executes(c -> EchoMapTools.run(c.getSource(), () -> org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorGenerator.start(c.getSource(), arg(c, "mapId"), false)))
                        .then(Commands.literal("confirm")
                                .executes(c -> EchoMapTools.run(c.getSource(), () -> org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorGenerator.start(c.getSource(), arg(c, "mapId"), true)))))
                .then(Commands.literal("status")
                        .executes(c -> EchoMapTools.run(c.getSource(), () -> org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorGenerator.status(c.getSource()))))
                .then(Commands.literal("cancel")
                        .executes(c -> EchoMapTools.run(c.getSource(), () -> org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorGenerator.cancel(c.getSource())))));
        return map;
    }
}
