package org.agmas.harpymodloader.modifiers;

import java.util.ArrayList;

public class HMLModifiers {

    public static final ArrayList<SREModifier> MODIFIERS = new ArrayList<>();
    public static void init() {}

    public static SREModifier registerModifier(SREModifier modifier) {
        MODIFIERS.add(modifier);
        return modifier;
    }
}
