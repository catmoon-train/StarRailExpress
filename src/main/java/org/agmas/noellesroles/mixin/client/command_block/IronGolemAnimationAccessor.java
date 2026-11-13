package org.agmas.noellesroles.mixin.client.command_block;

import net.minecraft.world.entity.animal.IronGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 为渲染临时铁傀儡按游戏刻推进攻击动作。 */
@Mixin(IronGolem.class)
public interface IronGolemAnimationAccessor {
    @Accessor("attackAnimationTick")
    void mustard$setAttackAnimationTick(int ticks);
}
