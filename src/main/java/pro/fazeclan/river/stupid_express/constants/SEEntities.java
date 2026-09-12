/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pro.fazeclan.river.stupid_express.constants;

import dev.doctor4t.ratatouille.util.registrar.EntityTypeRegistrar;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.modifier.twin_children.TwinChildrenSeatEntity;

public final class SEEntities {
    private static final EntityTypeRegistrar REGISTRAR = new EntityTypeRegistrar(StupidExpress.MOD_ID);

    public static final EntityType<TwinChildrenSeatEntity> TWIN_CHILDREN_SEAT = REGISTRAR.create(
            "twin_children_seat",
            EntityType.Builder.of(TwinChildrenSeatEntity::new, MobCategory.MISC)
                    .sized(0.35F, 0.1F)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .noSummon()
                    .noSave()
                    .fireImmune());

    private SEEntities() {
    }

    public static void init() {
        REGISTRAR.registerEntries();
    }
}
