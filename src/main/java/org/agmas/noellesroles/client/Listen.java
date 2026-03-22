package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.client.SREClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class Listen {
    public static final List<SoundInfo> soundInfos = new ArrayList<>();

    // 缓存
    public static final Stack<SoundInfo> soundInfoPool = new Stack<>();

    public static final ResourceLocation ECHO_TEX =
            ResourceLocation.tryBuild(Noellesroles.MOD_ID, "textures/gui/sound_gui.png");

    public static boolean listening = false;
    public static boolean inListen = false;
    public static long startListenTime = 0;

    public static final int FRAME_WIDTH = 32;
    public static final int FRAME_HEIGHT = 32;
    public static final int FRAME_COUNT = 5;
    public static final int FRAME_DURATION = 10; // 每帧持续 tick 数

    public static int tick = 0;
    public static void registerEvents() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) {
                listening = false;
                inListen = false;
                return;
            }
            final var gameWorldComponent = SREClient.gameComponent;
            if (gameWorldComponent==null ){
                return;
            }
            if (!gameWorldComponent.isRole(mc.player, ModRoles.DETECTIVE)){
                return;
            }
            LocalPlayer player = mc.player;

            if (player.isCrouching()) {
                if (!listening) {
                    startListenTime = mc.level.getGameTime();
                    listening = true;
                }

                if (mc.level.getGameTime() - startListenTime >= 15) {
                    inListen = true;
                }
            } else {
                listening = false;
                inListen = false;
            }
        });
    }





    public static Vector3f worldToScreen(double worldX, double worldY, double worldZ) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();

        // ====== 1. 世界坐标 → 相机局部坐标 ======
        Vec3 cam = camera.getPosition();

        float x = (float)(worldX - cam.x);
        float y = (float)(worldY - cam.y);
        float z = (float)(worldZ - cam.z);

        // ====== 2. 相机旋转（四元数） ======
        Quaternionf rotation = camera.rotation().conjugate(new Quaternionf());

        Vector3f local = new Vector3f(x, y, z);
        rotation.transform(local); // 应用相机旋转

        // ====== 3. 获取 Projection 矩阵 ======
        Matrix4f projection = mc.gameRenderer.getProjectionMatrix(mc.options.fov().get());

        // 推入裁剪空间
        Vector4f clip = new Vector4f(local.x, local.y, local.z, 1.0f);
        clip.mul(projection);

        // 在背后（W < 0） → 不可见
        if (clip.w <= 0.0f) {
            return new Vector3f(Float.NaN, Float.NaN, -1);
        }

        // ====== 4. 归一化坐标 (NDC) ======
        clip.div(clip.w);

        // ====== 5. NDC → 屏幕坐标 ======
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        float sx = (clip.x * 0.5f + 0.5f) * sw;
        float sy = (clip.y * -0.5f + 0.5f) * sh;

        return new Vector3f(sx, sy, clip.z);
    }

   public static class SoundInfo {
        public long time;
        public Vec3 pos;
    }
}
