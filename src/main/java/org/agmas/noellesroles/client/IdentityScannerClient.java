package org.agmas.noellesroles.client;

import org.agmas.noellesroles.client.screen.IdentityScannerScreen;
import org.agmas.noellesroles.packet.IdentityScannerStateS2CPacket;
import org.agmas.noellesroles.game.scanner.IdentityScannerAccess;
import io.wifi.starrailexpress.client.SREClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** 扫描仪统一客户端入口；同步阵营使用限制，不新增按键。 */
public final class IdentityScannerClient {
    private static BlockPos pendingOpen;
    private static int pendingTicks;
    private IdentityScannerClient() {
    }

    public static void register() {
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide && level.getBlockState(hit.getBlockPos())
                    .is(org.agmas.noellesroles.init.ModBlocks.IDENTITY_SCANNER)) {
                pendingOpen = hit.getBlockPos().immutable();
                pendingTicks = 40;
            }
            return net.minecraft.world.InteractionResult.PASS;
        });
        ClientPlayNetworking.registerGlobalReceiver(IdentityScannerStateS2CPacket.ID,
                (packet, context) -> context.client().execute(() -> {
                    Minecraft client = context.client();
                    if (!canView(client, packet.pos())) {
                        return;
                    }
                    if (client.screen instanceof IdentityScannerScreen screen) {
                        if (screen.pos().equals(packet.pos()) && screen.isValid(client)) {
                            screen.updateState(packet);
                        }
                    } else if (pendingTicks > 0 && packet.pos().equals(pendingOpen)) {
                        pendingOpen = null;
                        pendingTicks = 0;
                        client.setScreen(new IdentityScannerScreen(packet, client.level));
                    }
                }));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (pendingTicks > 0) pendingTicks--;
            if (client.level == null || pendingTicks == 0) pendingOpen = null;
            if (client.screen instanceof IdentityScannerScreen screen) {
                if (!screen.isValid(client)) {
                    client.setScreen(null);
                } else {
                    screen.clientTick();
                }
            }
        });
    }

    public static boolean canView(Minecraft client, BlockPos pos) {
        return client.player != null && client.level != null && SREClient.gameComponent != null
                && SREClient.gameComponent.isRunning() && IdentityScannerAccess.canUse(client.player)
                && client.level.getBlockState(pos).is(org.agmas.noellesroles.init.ModBlocks.IDENTITY_SCANNER)
                && client.player.distanceToSqr(Vec3.atCenterOf(pos)) <= 36.0;
    }
}
