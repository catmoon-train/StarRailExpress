package org.agmas.noellesroles.modifier;

import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;

import net.minecraft.resources.ResourceLocation;

public class AnimeModifiers {
    public static final String NAMESPACE = "anime";

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }
    
    // public static SREModifier RE_LIFE_IN_A_DIFFERENT_WORLD_FROM_ZERO = HMLModifiers.register(null);
    public static void init() {
    }
}
