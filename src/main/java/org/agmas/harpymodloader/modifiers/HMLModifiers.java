package org.agmas.harpymodloader.modifiers;

import java.util.ArrayList;
import java.util.HashSet;

import net.minecraft.resources.ResourceLocation;

public class HMLModifiers {

    public static final ArrayList<SREModifier> MODIFIERS = new ArrayList<>();

    public static SREModifier getModifier(ResourceLocation res) {
        if (res == null)
            return null;
        for (var m : MODIFIERS) {
            if (m.identifier().equals(res))
                return m;
        }
        return null;
    }

    public static void init() {
    }

    public static SREModifier registerModifier(SREModifier modifier, String... flags) {
        return registerModifier(modifier.addFlag(flags));
    }

    public static SREModifier registerModifier(SREModifier modifier) {
        MODIFIERS.add(modifier);
        return modifier;
    }

    public static HashSet<String> getAllFlags() {
        HashSet<String> filters = new HashSet<>();
        for (var it : MODIFIERS) {
            filters.addAll(it.getFlags());
        }
        return filters;
    }
}
