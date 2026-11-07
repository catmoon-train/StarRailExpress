package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.entity.YouluSmokeBallEntity;
import org.agmas.noellesroles.content.entity.YouluSmokeWaveEntity;
import org.agmas.noellesroles.role.ModRoles;

/** 幽露实体烟的客户端视野判定，不依赖药水刷新、粒子数量或 HUD 开关。 */
@Environment(EnvType.CLIENT)
public final class YouluSmokeClient {
    private YouluSmokeClient() {
    }

    /** 只有当前职业为幽露的玩家可以看穿幽露烟雾。 */
    public static boolean canSeeThroughSmoke() {
        Minecraft client = Minecraft.getInstance();
        return client.player != null && client.level != null
                && SREGameWorldComponent.KEY.get(client.level).isRole(client.player, ModRoles.YOULU);
    }

    /** 按实际相机位置判定，离开烟雾或实体消失后立即恢复视野。 */
    public static boolean isCameraInsideSmoke() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || canSeeThroughSmoke()) return false;
        Vec3 eye = client.gameRenderer.getMainCamera().getPosition();
        // 实体自身碰撞箱很小，不能用碰撞箱查询代替可配置的烟雾半径。
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity.isRemoved()) continue;
            if (entity instanceof YouluSmokeBallEntity ball && !ball.isBarbarianSmoke() && ball.contains(eye)) {
                return true;
            }
            if (entity instanceof YouluSmokeWaveEntity wave
                    && eye.distanceToSqr(wave.position()) <= (double) wave.getRadius() * wave.getRadius()) {
                return true;
            }
        }
        return false;
    }
}
