package io.wifi.starrailexpress;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public record DeathInfo(Player victim,@Nullable Player killer, ResourceLocation deathReason) {

}
