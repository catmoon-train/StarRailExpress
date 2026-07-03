package org.agmas.noellesroles.modifier;

import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.modifier.touhou.THMiscModifiers;

public class BounsModifiers {
    public static final String NAMESPACE = "bouns";

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    public static void init() {
        THMiscModifiers.init();
        AnimeModifiers.init();
    }
}
