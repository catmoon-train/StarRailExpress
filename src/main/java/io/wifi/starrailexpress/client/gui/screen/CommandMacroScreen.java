package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.client.commandmacro.CommandMacroExecutor;
import io.wifi.starrailexpress.client.commandmacro.CommandMacroStorage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CommandMacroScreen extends Screen {
    private List<CommandMacroStorage.MacroScript> scripts;
    private String selectedGroup = "全部";

    public CommandMacroScreen() {
        super(Component.literal("快捷指令脚本"));
        this.scripts = CommandMacroStorage.load();
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        Set<String> groups = new LinkedHashSet<>();
        groups.add("全部");
        for (CommandMacroStorage.MacroScript script : scripts) {
            groups.add(script.group == null || script.group.isBlank() ? "默认分组" : script.group);
        }

        addRenderableWidget(CycleButton.builder((String s) -> Component.literal("分组: " + s))
                .withValues(new ArrayList<>(groups))
                .withInitialValue(selectedGroup)
                .create(20, 20, 170, 20, Component.literal("分组"), (b, val) -> {
                    selectedGroup = val;
                    init();
                }));

        addRenderableWidget(Button.builder(Component.literal("新建脚本"), b -> {
            CommandMacroStorage.MacroScript script = CommandMacroStorage.createDefaultScript();
            scripts.add(script);
            CommandMacroStorage.save(scripts);
            this.minecraft.setScreen(new CommandMacroEditorScreen(this, scripts, script));
        }).bounds(width - 110, 20, 90, 20).build());

        List<CommandMacroStorage.MacroScript> filtered = scripts.stream()
                .filter(s -> "全部".equals(selectedGroup) || selectedGroup.equals(s.group))
                .sorted(Comparator.comparing(s -> s.name))
                .toList();

        int y = 60;
        for (CommandMacroStorage.MacroScript script : filtered) {
            int rowY = y;
            addRenderableWidget(Button.builder(Component.literal(script.name + " [" + script.group + "]"),
                    b -> CommandMacroExecutor.execute(script)).bounds(20, rowY, 240, 20).build());
            addRenderableWidget(Button.builder(Component.literal("编辑"),
                    b -> this.minecraft.setScreen(new CommandMacroEditorScreen(this, scripts, script)))
                    .bounds(270, rowY, 50, 20).build());
            addRenderableWidget(Button.builder(Component.literal("执行"),
                    b -> CommandMacroExecutor.execute(script)).bounds(325, rowY, 50, 20).build());
            addRenderableWidget(Button.builder(Component.literal("删除"), b -> {
                scripts.remove(script);
                CommandMacroStorage.save(scripts);
                init();
            }).bounds(380, rowY, 50, 20).build());
            y += 24;
            if (y > height - 30) break;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 6, 0xFFFFFF);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}
