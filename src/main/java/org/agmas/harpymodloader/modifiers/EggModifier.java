package org.agmas.harpymodloader.modifiers;

import io.wifi.starrailexpress.api.SRERole;
import net.minecraft.resources.ResourceLocation;
import java.util.HashSet;

public class EggModifier extends SREModifier {

    public EggModifier(ResourceLocation identifier, int color, HashSet<SRERole> cannotBeAppliedTo,
            HashSet<SRERole> canOnlyBeAppliedTo, boolean killerOnly, boolean civilianOnly) {
        super(identifier, color, cannotBeAppliedTo, canOnlyBeAppliedTo, killerOnly, civilianOnly);
        this.addFlag("bouns");
    }
    
}
