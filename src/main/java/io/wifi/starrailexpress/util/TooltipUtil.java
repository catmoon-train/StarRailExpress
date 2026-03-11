package io.wifi.starrailexpress.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

public class TooltipUtil {
    public static List<Component> sprit(MutableComponent component){
        var strings = component.getString().split("\n");
        List<Component> list = new ArrayList<>();
        for (String string : strings) {
            list.add(Component.literal(string));
        }

        return list;
    }
    public static List<Component> sprit(Component component){
        var strings = component.getString().split("\n");
        List<Component> list = new ArrayList<>();
        for (String string : strings) {
            list.add(Component.literal(string));
        }
        return list;
    }
}
