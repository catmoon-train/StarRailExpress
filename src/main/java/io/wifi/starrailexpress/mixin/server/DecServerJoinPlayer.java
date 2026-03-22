package io.wifi.starrailexpress.mixin.server;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.replay.GameReplayManager;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.network.SyncMapConfigPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.ladysnake.cca.api.v3.component.ComponentProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public class DecServerJoinPlayer {

    @Inject(method = "placeNewPlayer", at = @At("TAIL"), cancellable = true)
    public void placeNewPlayer(Connection connection, ServerPlayer serverPlayer,
            CommonListenerCookie commonListenerCookie, CallbackInfo ci) {
        if (SRE.isLobby)
            return;
        GameReplayManager.playerNames.put(serverPlayer.getUUID(), serverPlayer.getScoreboardName());
        final var gameWorldComponent = SREGameWorldComponent.KEY.get(serverPlayer.level());

        // MapVotingComponent mapVotingComponent = MapVotingComponent.KEY.get(serverPlayer.level());
//        if (mapVotingComponent.isVotingActive()){
//            if (TMMConfig.mapRandomCount!=-1){
//            ServerPlayNetworking.send(serverPlayer, new ShowSelectedMapUIPayload(true));
//        }
//            }
        if (gameWorldComponent.isRunning()) {
            if (serverPlayer.level() instanceof ServerLevel serverWorld) {
                AreasWorldComponent areas = AreasWorldComponent.KEY.get(serverWorld);
                AreasWorldComponent.PosWithOrientation spectatorSpawnPos = areas.getSpectatorSpawnPos();
                serverPlayer.teleportTo(serverWorld, spectatorSpawnPos.pos.x(), spectatorSpawnPos.pos.y(),
                        spectatorSpawnPos.pos.z(), spectatorSpawnPos.yaw, spectatorSpawnPos.pitch);
                serverPlayer.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            }
        } else {
            if (serverPlayer.level() instanceof ServerLevel serverWorld) {
                BlockPos spawn = serverWorld.getSharedSpawnPos();
                float angle = serverWorld.getSharedSpawnAngle();
                serverPlayer.teleportTo(serverWorld, spawn.getX(), spawn.getY(),
                        spawn.getZ(), angle, 0);
                for (int i = 0; i < serverPlayer.getInventory().getContainerSize(); i++) {
                    serverPlayer.getInventory().setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                    serverPlayer.containerMenu.broadcastChanges();
                    serverPlayer.inventoryMenu.slotsChanged(serverPlayer.getInventory());
                }
                if (!serverPlayer.isCreative())
                    serverPlayer.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
            }
        }
        SyncMapConfigPayload.sendToPlayer(serverPlayer);
        gameWorldComponent.setSyncRole(true);
        SREGameWorldComponent.KEY.syncWith(serverPlayer, (ComponentProvider) serverPlayer.level());
        gameWorldComponent.setSyncRole(false);
    }
}
