package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.client.commandmacro.CommandMacroStorage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class CommandMacroEditorScreen extends Screen {
    private final Screen parent;
    private final List<CommandMacroStorage.MacroScript> scripts;
    private final CommandMacroStorage.MacroScript script;
    private EditBox nameBox;
    private EditBox groupBox;
    private final List<EditBox> commandBoxes = new ArrayList<>();
    private final List<EditBox> delayBoxes = new ArrayList<>();

    public CommandMacroEditorScreen(Screen parent, List<CommandMacroStorage.MacroScript> scripts,
                                  CommandMacroStorage.MacroScript script) {
        super(Component.literal("编辑脚本"));
        this.parent = parent;
        this.scripts = scripts;
        this.script = script;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        commandBoxes.clear();
        delayBoxes.clear();

        nameBox = new EditBox(this.font, 20, 30, 180, 18, Component.literal("脚本名"));
        nameBox.setValue(script.name == null ? "" : script.name);
        addRenderableWidget(nameBox);

        groupBox = new EditBox(this.font, 210, 30, 180, 18, Component.literal("分组"));
        groupBox.setValue(script.group == null ? "默认分组" : script.group);
        addRenderableWidget(groupBox);

        int y = 60;
        for (int i = 0; i < Math.max(1, script.commands.size()); i++) {
            String cmd = i < script.commands.size() ? script.commands.get(i).command : "";
            int delay = i < script.commands.size() ? script.commands.get(i).delayMs : 1000;
            EditBox cmdBox = new EditBox(this.font, 20, y, 300, 18, Component.literal("命令"));
            cmdBox.setValue(cmd == null ? "" : cmd);
            cmdBox.setMaxLength(2000);
            addRenderableWidget(cmdBox);
            commandBoxes.add(cmdBox);

            EditBox delayBox = new EditBox(this.font, 330, y, 60, 18, Component.literal("间隔ms"));
            delayBox.setValue(String.valueOf(delay));
            addRenderableWidget(delayBox);
            delayBoxes.add(delayBox);
            y += 22;
            if (y > height - 60) break;
        }

        addRenderableWidget(Button.builder(Component.literal("+ 添加指令"), b -> {
            script.commands.add(new CommandMacroStorage.MacroCommand("", 1000));
            init();
        }).bounds(20, height - 26, 90, 20).build());

        addRenderableWidget(Button.builder(Component.literal("保存"), b -> {
            applyChanges();
            CommandMacroStorage.save(scripts);
            this.minecraft.setScreen(parent);
        }).bounds(width - 180, height - 26, 70, 20).build());

        addRenderableWidget(Button.builder(Component.literal("取消"), b -> this.minecraft.setScreen(parent))
                .bounds(width - 100, height - 26, 70, 20).build());
    }

    private void applyChanges() {
        script.name = nameBox.getValue().isBlank() ? "未命名脚本" : nameBox.getValue();
        script.group = groupBox.getValue().isBlank() ? "默认分组" : groupBox.getValue();
        script.commands.clear();
        for (int i = 0; i < commandBoxes.size(); i++) {
            String cmd = commandBoxes.get(i).getValue().trim();
            if (cmd.isEmpty()) continue;
            int delay = 0;
            try {
                delay = Integer.parseInt(delayBoxes.get(i).getValue().trim());
            } catch (Exception ignored) {
            }
            script.commands.add(new CommandMacroStorage.MacroCommand(cmd, Math.max(0, delay)));
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawString(this.font, "脚本名称", 20, 20, 0xFFFFFF);
        guiGraphics.drawString(this.font, "分组", 210, 20, 0xFFFFFF);
        guiGraphics.drawString(this.font, "命令(可填 /tp 等)", 20, 50, 0xAAAAAA);
        guiGraphics.drawString(this.font, "间隔ms", 330, 50, 0xAAAAAA);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}
