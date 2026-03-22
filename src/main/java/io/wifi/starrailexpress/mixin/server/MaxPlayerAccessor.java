package io.wifi.starrailexpress.mixin.server;

import io.wifi.starrailexpress.util.MutableMaxPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PlayerList.class)
public abstract class MaxPlayerAccessor implements MutableMaxPlayer {
    @Shadow
    @Final
    @Mutable
    private int maxPlayers;

    @Override
    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }
}