package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.SREClientConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.packet.CommandBlockImpactS2CPacket;

/**
 * 命令方块掉血的客户端反馈：一声玻璃破碎 + 一记短促的镜头震动。
 *
 * <p>震动幅度固定、时长很短（约 0.5 秒），跟飞机事件的持续震颤区分开：这里只是「震一下」。
 * 镜头由一个 tick 倒数的计数驱动，由 {@code CameraMixin} 在相机 setup 后叠加偏移。</p>
 */
public final class CommandBlockImpactClient {
    /** 一次震动的总时长（tick）。 */
    private static final int SHAKE_TICKS = 10;
    private static int tremorTicks;
    private static int tremorDuration;
    private static int tremorStrength = 1;

    private CommandBlockImpactClient() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CommandBlockImpactS2CPacket.ID,
                (packet, context) -> context.client().execute(() -> play(packet.strength())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (tremorTicks > 0) {
                tremorTicks--;
            }
        });
    }

    public static boolean isTremorActive() {
        return tremorTicks > 0;
    }

    public static void clear() {
        tremorTicks = 0;
        tremorDuration = 0;
        tremorStrength = 1;
    }

    /**
     * 由 {@code CameraMixin} 在相机 setup 完成后调用。
     *
     * @param tickDelta 当前帧的部分 tick
     * @return 要叠加到相机上的偏移；没有震动时返回 {@link CameraJitter#NONE}
     */
    public static CameraJitter computeCamera(float tickDelta) {
        if (tremorTicks <= 0 || SREClientConfig.instance().disableScreenShake) {
            return CameraJitter.NONE;
        }
        float t = tremorDuration - tremorTicks + tickDelta;
        // 前紧后松：开头最明显，结尾自然衰减。
        float fade = Mth.clamp(tremorTicks / (float) Math.max(1, tremorDuration), 0.0F, 1.0F);
        float intensity = fade * (tremorStrength >= 2 ? 1.0F : 0.6F);
        float yaw = Mth.sin(t * 2.35F) * 0.85F * intensity;
        float pitch = Mth.cos(t * 2.9F) * 0.62F * intensity;
        double x = Mth.sin(t * 3.1F) * 0.022D * intensity;
        double y = Mth.cos(t * 2.6F) * 0.016D * intensity;
        return new CameraJitter(yaw, pitch, x, y);
    }

    public record CameraJitter(float yaw, float pitch, double x, double y) {
        static final CameraJitter NONE = new CameraJitter(0, 0, 0, 0);
    }

    private static void play(int strength) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        tremorStrength = Math.max(1, strength);
        tremorDuration = SHAKE_TICKS + (tremorStrength >= 2 ? 4 : 0);
        tremorTicks = tremorDuration;
        // 玻璃先碎，再叠一层低频回响，避免只有一声“叮”的塑料感。
        player.playNotifySound(SoundEvents.GLASS_BREAK, SoundSource.AMBIENT,
                tremorStrength >= 2 ? 1.0F : 0.72F, 0.72F);
        player.playNotifySound(SoundEvents.GLASS_BREAK, SoundSource.AMBIENT,
                tremorStrength >= 2 ? 0.85F : 0.5F, 1.05F);
        if (tremorStrength >= 2) {
            player.playNotifySound(SoundEvents.STONE_BREAK, SoundSource.AMBIENT, 1.0F, 0.45F);
        }
    }
}
