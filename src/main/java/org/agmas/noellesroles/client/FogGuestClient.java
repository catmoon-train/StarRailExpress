package org.agmas.noellesroles.client;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.command_block.FogGuestZoneEntity;

/** 缓存可见雾区；从雾外不渲染雾内实体，连名称、装备与发光轮廓一起遮挡。 */
public final class FogGuestClient {
    private static final List<FogGuestZoneEntity> ZONES = new ArrayList<>();
    private FogGuestClient() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ZONES.clear();
            if (client.level == null) return;
            for (Entity entity : client.level.entitiesForRendering()) {
                if (entity instanceof FogGuestZoneEntity zone && !zone.isRemoved()) ZONES.add(zone);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ZONES.clear());
    }

    public static boolean hidden(Entity entity) {
        if (ZONES.isEmpty() || entity instanceof FogGuestZoneEntity) return false;
        Minecraft client = Minecraft.getInstance();
        Vec3 camera = client.gameRenderer.getMainCamera().getPosition();
        for (FogGuestZoneEntity zone : ZONES) {
            if (!zone.isRemoved() && zone.level() == entity.level()
                    && !zone.contains(camera) && zone.contains(entity.position())) return true;
        }
        return false;
    }
}
