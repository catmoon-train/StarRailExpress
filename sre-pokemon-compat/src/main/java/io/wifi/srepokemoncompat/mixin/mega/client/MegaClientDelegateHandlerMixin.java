package io.wifi.srepokemoncompat.mixin.mega.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** 插件只保护 Fabric 的 tick 处理器，保留 common 委托 inject 的形态数据同步。 */
@Pseudo
@Mixin(targets = "com.cobblemon.mod.common.client.entity.PokemonClientDelegate", priority = 500, remap = false)
public abstract class MegaClientDelegateHandlerMixin { }
