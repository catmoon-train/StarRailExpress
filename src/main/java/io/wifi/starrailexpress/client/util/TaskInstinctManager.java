package io.wifi.starrailexpress.client.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.agmas.noellesroles.client.screen.FilterSelectionScreen;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class TaskInstinctManager {
    public static HashMap<Integer, Component> TASK_INSTINCTS = defaultTaskInstincts();
    public static HashMap<Integer, Boolean> TASK_STATUS = null;

    private static HashMap<Integer, Boolean> getOrLoadTaskStatus() {
        if (TASK_STATUS == null) {
            TASK_STATUS = new HashMap<>(SREClientConfig.instance().taskStatus);
        }
        return TASK_STATUS;
    }

    public static boolean tryRegisterTaskInstinctType(int type, Component name, boolean defaultKey) {
        if (TASK_INSTINCTS.containsKey(type)) {
            return false;
        }
        TASK_INSTINCTS.put(type, name);
        getOrLoadTaskStatus().put(type, defaultKey);
        return true;
    }

    public static void registerTaskInstinctType(int type, Component name, boolean defaultKey)
            throws IllegalArgumentException {
        if (!tryRegisterTaskInstinctType(type, name, defaultKey)) {
            throw new IllegalArgumentException("Duplicated task instinct task type '" + type + "'!");
        }
    }

    public static boolean isTaskInstinctTypeShowable(int type) {
        return getOrLoadTaskStatus().getOrDefault(type, true);
    }

    static HashMap<Integer, Component> defaultTaskInstincts() {
        HashMap<Integer, Component> result = new HashMap<>();
        result.put(-1, Component.translatable("hud.noellesroles.task_instinct.render.door"));
        result.put(1, Component.translatable("hud.noellesroles.task_instinct.render.task.food"));
        result.put(2, Component.translatable("hud.noellesroles.task_instinct.render.task.drink"));
        result.put(3, Component.translatable("hud.noellesroles.task_instinct.render.task.bathe"));
        result.put(4, Component.translatable("hud.noellesroles.task_instinct.render.task.bed"));
        result.put(5, Component.translatable("hud.noellesroles.task_instinct.render.task.running_machine"));
        result.put(6, Component.translatable("hud.noellesroles.task_instinct.render.task.lecture"));
        result.put(8, Component.translatable("hud.noellesroles.task_instinct.render.task.toilet"));
        result.put(9, Component.translatable("hud.noellesroles.task_instinct.render.task.seat"));
        result.put(10, Component.translatable("hud.noellesroles.task_instinct.render.task.note_block"));
        result.put(11, Component.translatable("hud.noellesroles.task_instinct.render.vending_machine"));
        result.put(16, Component.translatable("hud.noellesroles.task_instinct.render.task.stove"));
        result.put(17, Component.translatable("hud.noellesroles.task_instinct.render.task.dust"));
        result.put(18, Component.translatable("hud.noellesroles.task_instinct.render.task.transport.start"));
        result.put(19, Component.translatable("hud.noellesroles.task_instinct.render.task.transport.end"));
        result.put(20, Component.translatable("hud.noellesroles.task_instinct.render.task.pray"));
        result.put(21, Component.translatable("hud.noellesroles.task_instinct.render.task.bush"));
        result.put(22, Component.translatable("hud.noellesroles.task_instinct.render.task.crop"));
        result.put(23, Component.translatable("hud.noellesroles.task_instinct.render.lottery_machine"));
        return result;
    }

    public static void showTaskInstinctChoices(Screen parent) {
        LinkedHashMap<String, Component> optionMap = new LinkedHashMap<>();
        HashSet<String> defaultOptions = new HashSet<>();
        ArrayList<Map.Entry<Integer, Component>> arr = new ArrayList<>(TASK_INSTINCTS.entrySet());
        arr.sort((a, b) -> {
            return Integer.compare(a.getKey(), b.getKey());
        });
        for (var t : arr) {
            int k = t.getKey();
            String key = String.valueOf(k);
            var name = t.getValue();
            boolean status = getOrLoadTaskStatus().getOrDefault(k, true);
            if (status) {
                defaultOptions.add(key);
            }
            optionMap.put(key, name);
        }
        FilterSelectionScreen screen = FilterSelectionScreen.builder(parent)
                .title(Component.translatable("screen.limited_inventory.menu.task_instinct_choices"))
                .subtitle(Component.translatable("screen.limited_inventory.menu.task_instinct_choices.tip"))
                .options(optionMap).multiSelect(true).defaultSelections(defaultOptions)
                .callback(selected -> {
                    handleSelected(selected);
                })
                .build();
        Minecraft.getInstance().setScreen(screen);
    }

    private static void handleSelected(Set<String> selected) {
        SREClientConfig.instance().taskStatus.clear();
        for (var t : TASK_INSTINCTS.entrySet()) {
            try {
                int k = t.getKey();
                String key = String.valueOf(k);
                if (selected.contains(key)) {
                    SREClientConfig.instance().taskStatus.put(k, true);
                } else {
                    SREClientConfig.instance().taskStatus.put(k, false);
                }
            } catch (Exception e) {
                SRE.LOGGER.error("Error while parse taskinstinct choices.", e);
            }
        }
        SREClientConfig.HANDLER.save();
        TASK_STATUS = new HashMap<>(SREClientConfig.instance().taskStatus);
    }

}
