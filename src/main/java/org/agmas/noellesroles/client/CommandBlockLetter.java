package org.agmas.noellesroles.client;

import java.util.List;

import org.agmas.noellesroles.game.command_block.CommandBlockRules;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.client.gui.screen.NewspaperScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/**
 * 命令方块局的开局信。正文按 {@code docs/命令方块杀手事件.md} 写，
 * 职业名点开介绍页。事件没出现时不拦截，仍走原来的地图信。
 */
public final class CommandBlockLetter {
    private static final String[] EVENTS = {
            "blackout", "chaos", "static", "rewind", "hush", "coins", "trace", "jam", "inject"
    };

    private CommandBlockLetter() {
    }

    public static boolean tryOpen() {
        if (!CommandBlockProgressClient.isShowing()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        minecraft.setScreen(new NewspaperScreen(
                List.of(body()),
                Component.translatable("letter.noellesroles.command_block.title"),
                Component.translatable("letter.noellesroles.command_block.subtitle"),
                0f));
        return true;
    }

    private static MutableComponent body() {
        MutableComponent body = Component.literal("");
        body.append(Component.translatable("letter.noellesroles.command_block.intro", link(ModRoles.ABNORMAL)));
        body.append("\n\n");
        heading(body, "letter.noellesroles.command_block.open");
        body.append(Component.translatable("letter.noellesroles.command_block.open.body",
                link(ModRoles.ABNORMAL), link(ModRoles.ABNORMAL), link(ModRoles.ABNORMAL)));
        body.append("\n\n");
        heading(body, "letter.noellesroles.command_block.crack");
        body.append(Component.translatable("letter.noellesroles.command_block.crack.body"));
        body.append("\n\n");
        heading(body, "letter.noellesroles.command_block.end");
        body.append(Component.translatable("letter.noellesroles.command_block.end.body"));
        body.append("\n\n");
        heading(body, "letter.noellesroles.command_block.pool");
        body.append(Component.translatable("letter.noellesroles.command_block.pool.lead", link(ModRoles.DREAM)));
        body.append("\n");
        for (SRERole role : CommandBlockRules.pool()) {
            appendRole(body, role);
        }
        body.append("\n");
        heading(body, "letter.noellesroles.command_block.event");
        body.append(Component.translatable("letter.noellesroles.command_block.event.lead", link(ModRoles.ABNORMAL)));
        body.append("\n");
        for (String event : EVENTS) {
            body.append(Component.translatable("letter.noellesroles.command_block.event." + event));
            body.append("\n");
        }
        body.append("\n");
        body.append(Component.translatable("letter.noellesroles.command_block.event.note"));
        body.append("\n\n");
        body.append(Component.translatable("letter.noellesroles.command_block.click_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        return body;
    }

    private static void heading(MutableComponent body, String key) {
        body.append(Component.translatable(key).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
        body.append("\n");
    }

    private static void appendRole(MutableComponent body, SRERole role) {
        body.append(link(role));
        body.append("\n");
    }

    private static MutableComponent link(SRERole role) {
        MutableComponent name = RoleUtils.getRoleName(role);
        if (role == null) {
            return name;
        }
        return name.withStyle(style -> style
                .withUnderlined(true)
                .withColor(ChatFormatting.DARK_RED)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("letter.noellesroles.command_block.role_hover")
                                .withStyle(ChatFormatting.GREEN)))
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        "/sre:client screen role_introduction " + role.identifier().getPath())));
    }
}
