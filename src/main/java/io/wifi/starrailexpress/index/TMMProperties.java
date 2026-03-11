package io.wifi.starrailexpress.index;

import io.wifi.starrailexpress.block.property.CouchArms;
import io.wifi.starrailexpress.block.property.OrnamentShape;
import io.wifi.starrailexpress.block.property.RailingShape;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public interface TMMProperties {
    BooleanProperty ACTIVE = BooleanProperty.create("active"); // whether a block is receiving power from a breaker
    BooleanProperty INTERACTION_COOLDOWN = BooleanProperty.create("interaction_cooldown");
    BooleanProperty LEFT = BooleanProperty.create("left");
    BooleanProperty OPAQUE = BooleanProperty.create("opaque");
    BooleanProperty RIGHT = BooleanProperty.create("right");
    BooleanProperty SUPPORT = BooleanProperty.create("support");
    BooleanProperty TOP = BooleanProperty.create("top");

    EnumProperty<CouchArms> COUCH_ARMS = EnumProperty.create("arms", CouchArms.class);
    EnumProperty<OrnamentShape> ORNAMENT_SHAPE = EnumProperty.create("shape", OrnamentShape.class);
    EnumProperty<RailingShape> RAILING_SHAPE = EnumProperty.create("shape", RailingShape.class);
}
