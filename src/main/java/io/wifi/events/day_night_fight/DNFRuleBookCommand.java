package io.wifi.events.day_night_fight;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DNFRuleBookCommand {
    private static final List<String> ROLES = List.of(
            "common", "killer", "poisoner", "chef", "soldier", "maniac", "psychologist", "locksmith", "civilian");

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tmm:dnf_rules")
                .executes(ctx -> giveCurrentRoleBook(ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("all").executes(ctx -> giveRuleBook(ctx, "all")))
                .then(Commands.literal("killer").executes(ctx -> giveRuleBook(ctx, "killer")))
                .then(Commands.literal("poisoner").executes(ctx -> giveRuleBook(ctx, "poisoner")))
                .then(Commands.literal("chef").executes(ctx -> giveRuleBook(ctx, "chef")))
                .then(Commands.literal("soldier").executes(ctx -> giveRuleBook(ctx, "soldier")))
                .then(Commands.literal("maniac").executes(ctx -> giveRuleBook(ctx, "maniac")))
                .then(Commands.literal("psychologist").executes(ctx -> giveRuleBook(ctx, "psychologist")))
                .then(Commands.literal("locksmith").executes(ctx -> giveRuleBook(ctx, "locksmith")))
                .then(Commands.literal("civilian").executes(ctx -> giveRuleBook(ctx, "civilian"))));
    }

    private static int giveCurrentRoleBook(ServerPlayer player) {
        String role = "common";
        var playerRole = SREGameWorldComponent.KEY.get(player.level()).getRole(player);
        if (playerRole != null) {
            String path = playerRole.identifier().getPath();
            if (path.startsWith("dnf_")) {
                role = path.substring("dnf_".length());
            }
        }
        DNFItems.giveOrDrop(player, buildBook(role));
        player.displayClientMessage(Component.translatable("message.dnf.rules.given"), true);
        return 1;
    }

    private static int giveRuleBook(CommandContext<CommandSourceStack> ctx, String role)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        DNFItems.giveOrDrop(player, buildBook(role));
        player.displayClientMessage(Component.translatable("message.dnf.rules.given"), true);
        return 1;
    }

    public static ItemStack buildBook(String role) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        var pages = new ArrayList<Filterable<Component>>();
        if ("all".equals(role)) {
            for (String roleKey : ROLES) {
                addRolePages(pages, roleKey);
            }
        } else {
            addRolePages(pages, "common");
            if (!"common".equals(role)) {
                addRolePages(pages, role);
            }
        }
        String title = Component.translatable("book.dnf.rules.title").getString();
        book.set(DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(new Filterable<>(title, Optional.of(title)), "DNF", 1, pages, true));
        return book;
    }

    private static void addRolePages(ArrayList<Filterable<Component>> pages, String role) {
        int page = 1;
        while (true) {
            String key = "book.dnf.rules." + role + "." + page;
            Component component = Component.translatable(key);
            if (component.getString().equals(key)) {
                return;
            }
            pages.add(new Filterable<>(component, Optional.of(component)));
            page++;
        }
    }
}
