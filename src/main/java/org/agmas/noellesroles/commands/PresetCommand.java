package org.agmas.noellesroles.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.presets.Preset;

import java.util.List;
import java.util.Map;

/**
 * 预设命令
 * /noellesroles preset apply <name> - 启用指定预设
 * /noellesroles preset list - 列出所有预设
 * /noellesroles preset create <name> <description> - 创建自定义预设
 * /noellesroles preset delete <name> - 删除自定义预设
 * /noellesroles preset save <name> <description> - 保存当前状态为预设
 */
public class PresetCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var presetCommand = Commands.literal("noellesroles")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("preset")
                            .then(registerApplySubcommand())
                            .then(registerListSubcommand())
                            .then(registerCreateSubcommand())
                            .then(registerDeleteSubcommand())
                            .then(registerSaveSubcommand()));
            dispatcher.register(presetCommand);
        });
    }

    private static ArgumentBuilder<CommandSourceStack, ?> registerApplySubcommand() {
        return Commands.literal("apply")
                .then(Commands.argument("name", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            for (String name : Preset.PresetManager.getAllPresetNames()) {
                                builder.suggest(name);
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            String presetName = StringArgumentType.getString(context, "name");
                            return applyPreset(context, presetName);
                        }));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> registerListSubcommand() {
        return Commands.literal("list")
                .executes(context -> {
                    return listPresets(context);
                });
    }

    private static ArgumentBuilder<CommandSourceStack, ?> registerCreateSubcommand() {
        return Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.string())
                        .then(Commands.argument("description", StringArgumentType.greedyString())
                                .executes(context -> {
                                    String name = StringArgumentType.getString(context, "name");
                                    String description = StringArgumentType.getString(context, "description");
                                    return createPreset(context, name, description);
                                })));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> registerDeleteSubcommand() {
        return Commands.literal("delete")
                .then(Commands.argument("name", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            for (String name : Preset.PresetManager.getAllPresetNames()) {
                                if (!Preset.PresetManager.isDefaultPreset(name)) {
                                    builder.suggest(name);
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            return deletePreset(context, name);
                        }));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> registerSaveSubcommand() {
        return Commands.literal("save")
                .then(Commands.argument("name", StringArgumentType.string())
                        .then(Commands.argument("description", StringArgumentType.greedyString())
                                .executes(context -> {
                                    String name = StringArgumentType.getString(context, "name");
                                    String description = StringArgumentType.getString(context, "description");
                                    return savePreset(context, name, description);
                                })));
    }

    private static int applyPreset(CommandContext<CommandSourceStack> context, String presetName) {
        Preset preset = Preset.PresetManager.getPreset(presetName);
        if (preset == null) {
            context.getSource().sendFailure(Component.literal("预设 '" + presetName + "' 不存在"));
            return 0;
        }

        Preset.RoleSettings roleSettings = preset.getRoles();
        Preset.ModifierSettings modifierSettings = preset.getModifiers();

        int rolesApplied = applyRoles(roleSettings);
        int modifiersApplied = applyModifiers(modifierSettings);

        context.getSource().sendSuccess(() -> Component.literal("成功应用预设: " + presetName), true);
        context.getSource().sendSuccess(() -> Component.literal("  - 启用了 " + rolesApplied + " 个职业"), false);
        context.getSource().sendSuccess(() -> Component.literal("  - 禁用了 " + roleSettings.getDisabled().size() + " 个职业"), false);
        context.getSource().sendSuccess(() -> Component.literal("  - 启用了 " + modifiersApplied + " 个修饰符"), false);
        context.getSource().sendSuccess(() -> Component.literal("  - 禁用了 " + modifierSettings.getDisabled().size() + " 个修饰符"), false);
        if (preset.getDescription() != null && !preset.getDescription().isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("  描述: " + preset.getDescription()), false);
        }

        return 1;
    }

    private static int applyRoles(Preset.RoleSettings roleSettings) {
        int applied = 0;
        List<String> enabled = roleSettings.getEnabled();
        List<String> disabled = roleSettings.getDisabled();

        org.agmas.harpymodloader.config.HarpyModLoaderConfig config = 
            org.agmas.harpymodloader.config.HarpyModLoaderConfig.HANDLER.instance();
        
        // 清空disabled列表，重新添加禁用的角色
        config.disabled.clear();
        
        // 将需要禁用的角色添加到disabled列表
        for (String roleId : disabled) {
            config.disabled.add(roleId);
            applied++;
        }
        
        // 确保启用的角色不在disabled列表中
        for (String roleId : enabled) {
            if (config.disabled.contains(roleId)) {
                config.disabled.remove(roleId);
            }
            applied++;
        }
        
        // 保存配置
        org.agmas.harpymodloader.config.HarpyModLoaderConfig.HANDLER.save();

        return applied;
    }

    private static int applyModifiers(Preset.ModifierSettings modifierSettings) {
        int applied = 0;
        List<String> enabled = modifierSettings.getEnabled();
        List<String> disabled = modifierSettings.getDisabled();

        org.agmas.harpymodloader.config.HarpyModLoaderConfig config =
            org.agmas.harpymodloader.config.HarpyModLoaderConfig.HANDLER.instance();

        // 清空disabledModifiers列表，重新添加禁用的修饰符
        config.disabledModifiers.clear();

        // 将需要禁用的修饰符添加到disabledModifiers列表
        for (String modifierId : disabled) {
            config.disabledModifiers.add(modifierId);
            applied++;
        }

        // 确保启用的修饰符不在disabledModifiers列表中
        for (String modifierId : enabled) {
            if (config.disabledModifiers.contains(modifierId)) {
                config.disabledModifiers.remove(modifierId);
            }
            applied++;
        }

        // 保存配置
        org.agmas.harpymodloader.config.HarpyModLoaderConfig.HANDLER.save();

        return applied;
    }

    private static int listPresets(CommandContext<CommandSourceStack> context) {
        Map<String, String> descriptions = Preset.PresetManager.getAllPresetDescriptions();

        if (descriptions.isEmpty()) {
            context.getSource().sendFailure(Component.literal("没有可用的预设"));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("可用的预设:"), false);
        for (Map.Entry<String, String> entry : descriptions.entrySet()) {
            final String key = entry.getKey();
            final String value = entry.getValue();
            String line = "  " + key;
            if (value != null && !value.isEmpty()) {
                line += " - " + value;
            }
            final String finalLine = line;
            context.getSource().sendSuccess(() -> Component.literal(finalLine), false);
        }

        return 1;
    }

    private static int createPreset(CommandContext<CommandSourceStack> context, String name, String description) {
        if (Preset.PresetManager.isDefaultPreset(name)) {
            context.getSource().sendFailure(Component.literal("不能覆盖默认预设 '" + name + "'"));
            return 0;
        }

        Preset preset = new Preset(description);
        Preset.PresetManager.addCustomPreset(name, preset);

        context.getSource().sendSuccess(() -> Component.literal("成功创建自定义预设: " + name), true);
        context.getSource().sendSuccess(() -> Component.literal("  描述: " + description), false);
        context.getSource().sendSuccess(() -> Component.literal("  提示: 使用 /noellesroles preset apply " + name + " 应用此预设"), false);

        return 1;
    }

    private static int deletePreset(CommandContext<CommandSourceStack> context, String name) {
        if (Preset.PresetManager.isDefaultPreset(name)) {
            context.getSource().sendFailure(Component.literal("不能删除默认预设 '" + name + "'"));
            return 0;
        }

        if (Preset.PresetManager.getPreset(name) == null) {
            context.getSource().sendFailure(Component.literal("预设 '" + name + "' 不存在"));
            return 0;
        }

        Preset.PresetManager.removeCustomPreset(name);
        context.getSource().sendSuccess(() -> Component.literal("成功删除自定义预设: " + name), true);

        return 1;
    }

    private static int savePreset(CommandContext<CommandSourceStack> context, String name, String description) {
        if (Preset.PresetManager.isDefaultPreset(name)) {
            context.getSource().sendFailure(Component.literal("不能覆盖默认预设 '" + name + "'"));
            return 0;
        }

        org.agmas.harpymodloader.config.HarpyModLoaderConfig config =
            org.agmas.harpymodloader.config.HarpyModLoaderConfig.HANDLER.instance();

        // 创建角色设置
        Preset.RoleSettings roleSettings = new Preset.RoleSettings();
        java.util.List<String> enabledRoles = new java.util.ArrayList<>();
        java.util.List<String> disabledRoles = new java.util.ArrayList<>();

        // 遍历所有角色
        for (Object roleObj : io.wifi.starrailexpress.api.TMMRoles.ROLES.values()) {
            try {
                // 通过反射获取角色ID
                java.lang.reflect.Method identifierMethod = roleObj.getClass().getMethod("identifier");
                Object identifierObj = identifierMethod.invoke(roleObj);

                String roleId = identifierObj.toString();

                if (config.disabled.contains(roleId)) {
                    disabledRoles.add(roleId);
                } else {
                    enabledRoles.add(roleId);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        roleSettings.setEnabled(enabledRoles);
        roleSettings.setDisabled(disabledRoles);

        // 创建修饰符设置
        Preset.ModifierSettings modifierSettings = new Preset.ModifierSettings();
        java.util.List<String> enabledModifiers = new java.util.ArrayList<>();
        java.util.List<String> disabledModifiers = new java.util.ArrayList<>();

        // 遍历所有修饰符
        for (Object modifierObj : org.agmas.harpymodloader.modifiers.HMLModifiers.MODIFIERS) {
            try {
                // 通过反射获取修饰符ID
                java.lang.reflect.Method identifierMethod = modifierObj.getClass().getMethod("identifier");
                Object identifierObj = identifierMethod.invoke(modifierObj);

                String modifierId = identifierObj.toString();

                if (config.disabledModifiers.contains(modifierId)) {
                    disabledModifiers.add(modifierId);
                } else {
                    enabledModifiers.add(modifierId);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        modifierSettings.setEnabled(enabledModifiers);
        modifierSettings.setDisabled(disabledModifiers);

        // 创建预设并保存
        Preset preset = new Preset(description);
        preset.setRoles(roleSettings);
        preset.setModifiers(modifierSettings);
        Preset.PresetManager.addCustomPreset(name, preset);

        context.getSource().sendSuccess(() -> Component.literal("成功保存当前状态为预设: " + name), true);
        context.getSource().sendSuccess(() -> Component.literal("  - 启用的职业: " + enabledRoles.size()), false);
        context.getSource().sendSuccess(() -> Component.literal("  - 禁用的职业: " + disabledRoles.size()), false);
        context.getSource().sendSuccess(() -> Component.literal("  - 启用的修饰符: " + enabledModifiers.size()), false);
        context.getSource().sendSuccess(() -> Component.literal("  - 禁用的修饰符: " + disabledModifiers.size()), false);
        context.getSource().sendSuccess(() -> Component.literal("  描述: " + description), false);
        context.getSource().sendSuccess(() -> Component.literal("  提示: 使用 /noellesroles preset apply " + name + " 应用此预设"), false);

        return 1;
    }
}
