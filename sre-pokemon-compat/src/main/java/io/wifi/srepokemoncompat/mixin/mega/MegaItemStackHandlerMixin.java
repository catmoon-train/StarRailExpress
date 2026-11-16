package io.wifi.srepokemoncompat.mixin.mega;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** 低于 Mega 默认优先级，在插件 postApply 中精确保护其两个已合并的 useOnEntity 处理器。 */
@Pseudo
@Mixin(value = ItemStack.class, priority = 500)
public abstract class MegaItemStackHandlerMixin { }
