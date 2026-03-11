package org.agmas.harpymodloader.modifiers;

import java.util.ArrayList;

public class HMLModifiers {

    public static final ArrayList<Modifier> MODIFIERS = new ArrayList<>();
    public static void init() {}

    public static Modifier registerModifier(Modifier modifier) {
        MODIFIERS.add(modifier);
        return modifier;
    }
}
