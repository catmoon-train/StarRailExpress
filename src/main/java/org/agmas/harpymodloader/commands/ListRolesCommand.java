package org.agmas.harpymodloader.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wifi.ConfigCompact.network.RoleEnableInfoPacket;
import io.wifi.ConfigCompact.ui.RoleManageConfigUI;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.argument.ModifierArgumentType;
import org.agmas.harpymodloader.commands.argument.RoleArgumentType;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class ListRolesCommand {
    public static String roleDetailsCommandRoot = "roleDetails";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("manageRolesUI").requires(source -> source.hasPermission(2))
                .executes((ListRolesCommand::executeManage)));
        dispatcher.register(Commands.literal(roleDetailsCommandRoot)
                .then(Commands.literal("role")
                        .then(Commands.argument("role", RoleArgumentType.create())
                                .executes((ctx) -> showRoleDetails(ctx,
                                        RoleArgumentType.getRole(ctx,
                                                "role")))))
                .then(Commands.literal("modifier")
                        .then(Commands.argument("modifier", ModifierArgumentType.create())
                                .executes((ctx) -> showModifierDetails(ctx,
                                        ModifierArgumentType.getModifier(ctx,
                                                "modifier"))))));
        dispatcher.register(Commands.literal("listRoles")
                .executes(ctx -> ListRolesCommand.showRole(ctx, 1)) // 默认第一页
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> ListRolesCommand.showRole(ctx,
                                IntegerArgumentType.getInteger(ctx, "page")))));
    }

    private static int showRoleDetails(CommandContext<CommandSourceStack> ctx, SRERole role) {
        var message = Component.literal("\n");
        message.append(Component.translatable("commands.listroles.detail.name.role",
                RoleUtils.getRoleOrModifierOrItemNameWithColor(role).withStyle(ChatFormatting.BOLD))
                .withStyle(ChatFormatting.GOLD));
        message.append("\n").append(Component.translatable("commands.listroles.detail.id",
                RoleUtils.getRoleOrModifierOrItemIdentifier(role).toString())
                .withStyle(ChatFormatting.GRAY))
                .withStyle(ChatFormatting.GRAY);

        message.append("\n").append(Component.translatable("commands.listroles.detail.goal",
                getRoleGoal(role).withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.YELLOW));
        ctx.getSource().sendSystemMessage(message);
        message.append("\n").append(Component.translatable("commands.listroles.detail.introduction",
                RoleUtils.getRoleOrModifierOrItemDescription(role)
                        .copy().withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.GREEN));
        if (ctx.getSource().hasPermission(1)) {
            message.append("\n").append(Component.translatable("commands.listroles.detail.spawn_info")
                    .withStyle(ChatFormatting.GOLD).withStyle(st -> {
                        st = st.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/tmm:config spawn_info role " + role.identifier().toString()));
                        return st;
                    }));
        }
        message.append("\n");
        ctx.getSource().sendSystemMessage(message);
        return 1;
    }

    private static int showModifierDetails(CommandContext<CommandSourceStack> ctx, SREModifier modifier) {
        var message = Component.literal("\n");
        message.append(Component.translatable("commands.listroles.detail.name.modifier",
                RoleUtils.getRoleOrModifierOrItemNameWithColor(modifier).withStyle(ChatFormatting.BOLD))
                .withStyle(ChatFormatting.GOLD));

        message.append("\n").append(Component.translatable("commands.listroles.detail.id",
                RoleUtils.getRoleOrModifierOrItemIdentifier(modifier).toString())
                .withStyle(ChatFormatting.GRAY))
                .withStyle(ChatFormatting.GRAY);
        message.append("\n").append(Component.translatable("commands.listroles.detail.introduction",
                RoleUtils.getRoleOrModifierOrItemDescription(modifier)
                        .copy().withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.GREEN));
        if (ctx.getSource().hasPermission(1)) {
            message.append("\n").append(Component.translatable("commands.listroles.detail.spawn_info")
                    .withStyle(ChatFormatting.GOLD).withStyle(st -> {
                        st = st.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/tmm:config spawn_info modifier "
                                        + modifier.identifier().toString()));
                        return st;
                    }));
        }
        message.append("\n");
        ctx.getSource().sendSystemMessage(message);

        return 1;
    }

    private static MutableComponent getRoleGoal(SRERole role) {
        return RoleUtils.getRoleGoal(role);
    }

    public static RoleEnableInfoPacket getRoleAndModifierEnableInfoPacket(boolean openUI) {
        HashMap<ResourceLocation, Boolean> roleInfos = new HashMap<>();
        HashMap<ResourceLocation, Boolean> modifierInfos = new HashMap<>();
        for (var info : TMMRoles.ROLES.keySet()) {
            if (HarpyModLoaderConfig.HANDLER.instance().disabled.contains(info.toString())) {
                roleInfos.put(info, false);
            } else {
                roleInfos.put(info, true);
            }
        }
        for (var info : HMLModifiers.MODIFIERS) {
            if (HarpyModLoaderConfig.HANDLER.instance().disabledModifiers
                    .contains(info.identifier().toString())) {
                modifierInfos.put(info.identifier(), false);
            } else {
                modifierInfos.put(info.identifier(), true);
            }
        }
        return new RoleEnableInfoPacket(
                new RoleManageConfigUI.RoleAndModifierSyncInfo(roleInfos, modifierInfos),
                openUI);
    }

    public static void sendRoleDisableInfoToPlayer(ServerPlayer player, boolean openUI) {
        var packet = getRoleAndModifierEnableInfoPacket(openUI);
        ServerPlayNetworking.send(player,
                packet);
    }

    private static int executeManage(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();

        context.getSource().sendSuccess(
                () -> Component.translatable("Try to open Role Manage UI for %s", player.getName()),
                true);
        sendRoleDisableInfoToPlayer(player, true);
        return 1;
    }

    private static final int PAGE_SIZE = 10;

    private static int showRole(CommandContext<CommandSourceStack> context, int page) {
        if (!Harpymodloader.officialVerify) {
            context.getSource().sendFailure(Component.translatable("game.start_error.credit"));
            return 1;
        }

        CommandSourceStack source = context.getSource();

        // 准备角色列表
        ArrayList<SRERole> roleList = new ArrayList<>(TMMRoles.ROLES.values());
        if (!source.hasPermission(2)) {
            Noellesroles.sortRoles(roleList, false, true);
        }
        // 准备修饰符列表
        List<SREModifier> modifierList = new ArrayList<>(HMLModifiers.MODIFIERS);
        // 构建消息
        MutableComponent message = Component.literal("\n\n\n");

        // 准备总列表：先角色后修饰符
        List<Object> allEntries = new ArrayList<>();
        allEntries.addAll(roleList);
        allEntries.addAll(modifierList);

        int totalPages = (int) Math.ceil((double) allEntries.size() / PAGE_SIZE);
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, allEntries.size());
        List<Object> subList = start < allEntries.size() ? allEntries.subList(start, end)
                : Collections.emptyList();

        if (!subList.isEmpty()) {
            // 显示总标题 + 页码
            message.append(Component.translatable("commands.listroles.all.title")
                    .append(Component.literal(" (")
                            .append(Component.literal(String.valueOf(page)))
                            .append(Component.literal("/"))
                            .append(Component.literal(String.valueOf(totalPages)))
                            .append(Component.literal(")")).withStyle(ChatFormatting.BOLD))
                    .append("\n\n"));

            // 遍历合并后的列表
            for (Object entry : subList) {
                if (entry instanceof SRERole role) {
                    boolean disabled = HarpyModLoaderConfig.HANDLER.instance().getDisabled()
                            .contains(role.identifier().toString());
                    MutableComponent status = createStatus(source, disabled,
                            "/setEnabledRole " + role.identifier() + " " + disabled);
                    message.append(buildElementText(RoleUtils.getRoleOrModifierNameWithColor(role),
                            role.identifier(),
                            status, true));
                } else if (entry instanceof SREModifier modifier) {
                    boolean disabled = HarpyModLoaderConfig.HANDLER.instance().disabledModifiers
                            .contains(modifier.identifier().toString());
                    MutableComponent status = createStatus(source, disabled,
                            "/setEnabledModifier " + modifier.identifier() + " "
                                    + disabled);
                    message.append(
                            buildElementText(
                                    RoleUtils.getRoleOrModifierNameWithColor(
                                            modifier),
                                    modifier.identifier(),
                                    status,
                                    false));
                }
                message.append("\n");
            }
        } else {
            // 没有任何可用条目
            message.append(Component.translatable("commands.listroles.no.entries"));
        }

        // 翻页按钮（使用 totalPages）
        int maxTotalPages = totalPages; // 沿用变量名，兼容后面的翻页按钮代码
        if (maxTotalPages > 0) {
            message.append("\n"); // 与前面内容隔开

            MutableComponent buttons = Component.empty();

            // 首页
            buttons.append(Component.literal("[")
                    .append(Component.translatable("commands.listroles.button.first_page")
                            .withStyle(ChatFormatting.DARK_GREEN))
                    .append(Component.literal("]"))
                    .withStyle(
                            style -> style.withClickEvent(new ClickEvent(
                                    ClickEvent.Action.RUN_COMMAND, "/listRoles 1"))
                                    .withHoverEvent(new HoverEvent(
                                            HoverEvent.Action.SHOW_TEXT,
                                            Component.translatable(
                                                    "commands.listroles.button.hover",
                                                    1)))));

            buttons.append(Component.literal(" "));

            // 上一页
            if (page > 1) {
                buttons.append(Component.literal("[")
                        .append(Component.translatable("commands.listroles.button.prev_page")
                                .withStyle(ChatFormatting.GREEN))
                        .append(Component.literal("]"))
                        .withStyle(style -> style
                                .withClickEvent(
                                        new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                                "/listRoles " + (page
                                                        - 1)))
                                .withHoverEvent(new HoverEvent(
                                        HoverEvent.Action.SHOW_TEXT,
                                        Component.translatable(
                                                "commands.listroles.button.hover",
                                                page - 1)))));
            } else {
                buttons.append(Component.literal("[")
                        .append(Component.translatable("commands.listroles.button.prev_page")
                                .withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("]"))
                        .withStyle(style -> style.withClickEvent(null)));
            }

            buttons.append(Component.literal(" "));

            // 下一页
            if (page < maxTotalPages) {
                buttons.append(Component.literal("[")
                        .append(Component.translatable("commands.listroles.button.next_page")
                                .withStyle(ChatFormatting.GREEN))
                        .append(Component.literal("]"))
                        .withStyle(style -> style
                                .withClickEvent(
                                        new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                                "/listRoles " + (page
                                                        + 1)))
                                .withHoverEvent(new HoverEvent(
                                        HoverEvent.Action.SHOW_TEXT,
                                        Component.translatable(
                                                "commands.listroles.button.hover",
                                                page + 1)))));
            } else {
                buttons.append(Component.literal("[")
                        .append(Component.translatable("commands.listroles.button.next_page")
                                .withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("]"))
                        .withStyle(style -> style.withClickEvent(null)));
            }

            buttons.append(Component.literal(" "));

            // 尾页
            buttons.append(Component.literal("[")
                    .append(Component.translatable("commands.listroles.button.last_page")
                            .withStyle(ChatFormatting.DARK_GREEN))
                    .append(Component.literal("]"))
                    .withStyle(style -> style
                            .withClickEvent(
                                    new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                            "/listRoles " + maxTotalPages))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.translatable(
                                            "commands.listroles.button.hover",
                                            maxTotalPages)))));
            if (source.hasPermission(2)) {
                buttons.append(Component.literal(" [")
                        .append(Component.translatable("commands.listroles.button.manage_all")
                                .withStyle(ChatFormatting.AQUA))
                        .append(Component.literal("]"))
                        .withStyle(style -> style
                                .withClickEvent(
                                        new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                                "/manageRolesUI"))
                                .withHoverEvent(new HoverEvent(
                                        HoverEvent.Action.SHOW_TEXT,
                                        Component.translatable(
                                                "commands.listroles.detail.click_to_show")))));
            }
            message.append(buttons);
        }

        context.getSource().sendSystemMessage(message);
        return 1;
    }

    private static MutableComponent buildElementText(Component name, ResourceLocation identifier, Component status,
            boolean isRole) {
        return Component.empty().append(name)
                .append(" ")
                .append(Component.literal("(" + identifier + ")").withStyle(ChatFormatting.GRAY))
                .append(" ")
                .append(status).withStyle(style -> style
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable(
                                        "commands.listroles.detail.click_to_show")
                                        .withStyle(ChatFormatting.AQUA)))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/" + roleDetailsCommandRoot + " "
                                        + (isRole ? "role" : "modifier") + " "
                                        + identifier)));
    }

    private static MutableComponent createStatus(CommandSourceStack source, boolean disabled, String cmd) {
        String key = disabled ? "disabled" : "enabled";
        return Component.translatable("commands.listroles.status." + key + ".text").withStyle(style -> {
            if (source.hasPermission(2)) {
                return style
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("commands.listroles.status."
                                        + key + ".hover", cmd)))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd));
            } else {
                return style;
            }
        });
    }
}
