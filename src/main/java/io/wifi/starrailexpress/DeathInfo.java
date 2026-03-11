package io.wifi.starrailexpress;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public record DeathInfo(Player victim,@Nullable Player killer, ResourceLocation deathReason) {

}
