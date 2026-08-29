/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.agmas.noellesroles.content.entity;

import io.wifi.starrailexpress.content.entity.no_water_influenced.NoHeavyWaterInfluencedThrowableItemProjectile;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.content.entity.ServerGrenadeAreaManager.Type;
import org.agmas.noellesroles.init.ModItems;

/** 鸟兽兽专属燃烧弹实体：燃烧区域的连续击杀时间缩短 40%。 */
public class NiaoshoushouIncendiaryGrenadeEntity extends IncendiaryGrenadeEntity {
    public NiaoshoushouIncendiaryGrenadeEntity(
            EntityType<? extends NoHeavyWaterInfluencedThrowableItemProjectile> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.NIAOSHOU_SHOU_INCENDIARY_GRENADE;
    }

    @Override
    protected Type getAreaType() {
        return Type.NIAOSHOU_FIRE;
    }
}
