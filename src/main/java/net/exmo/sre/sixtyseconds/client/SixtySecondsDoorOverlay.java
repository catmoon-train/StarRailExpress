package net.exmo.sre.sixtyseconds.client;

import io.wifi.starrailexpress.client.SREClient;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.content.block.ShelterDoorBlock;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.agmas.noellesroles.client.TaskBlockOverlayRenderer;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * 避难所门<b>任务点式高亮</b>：60s 模式进行中，给玩家附近的所有 {@link ShelterDoorBlock}
 * 画穿墙金色描边（复用 {@code TaskBlockOverlayRenderer.renderBlockOverlay} 的任务点渲染，不改该类）。
 * 门是本模式的交互中枢（存物资/探索/返回/事件/拜访都在门上），常亮引导；
 * 搜索区/别队门同样高亮，因为所有玩家都会对门操作。
 * <p>门位置每秒<b>客户端本地扫描</b>一次（半径 {@link #RADIUS_H}，零同步、零服务端开销；门在建图后不变）。
 */
public final class SixtySecondsDoorOverlay {
    private static final int SCAN_INTERVAL_TICKS = 20;
    private static final int RADIUS_H = 24;
    private static final int RADIUS_V = 10;
    /** 金色（与 ui_style 的 GOLD 0xFFD4AF37 一致）。 */
    private static final Color COLOR = new Color(212, 175, 55);

    private static List<BlockPos> doors = List.of();
    private static long lastScanGameTime = Long.MIN_VALUE;

    private SixtySecondsDoorOverlay() {
    }

    /** 挂在 WorldRenderEvents.AFTER_TRANSLUCENT（与 TaskBlockOverlayRenderer 同一挂点）。 */
    public static void render(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (SREClient.gameComponent == null || !SREClient.gameComponent.isRunning()
                || SixtySecondsMod.MODE == null
                || SREClient.gameComponent.getGameMode() != SixtySecondsMod.MODE) {
            if (!doors.isEmpty()) {
                doors = List.of();
                lastScanGameTime = Long.MIN_VALUE;
            }
            return;
        }
        long now = mc.level.getGameTime();
        if (now - lastScanGameTime >= SCAN_INTERVAL_TICKS || now < lastScanGameTime) {
            doors = scan(mc);
            lastScanGameTime = now;
        }
        for (BlockPos pos : doors) {
            TaskBlockOverlayRenderer.renderBlockOverlay(context, pos, COLOR, 0.9F, true, 0F);
        }
    }

    private static List<BlockPos> scan(Minecraft mc) {
        List<BlockPos> found = new ArrayList<>();
        BlockPos center = mc.player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-RADIUS_H, -RADIUS_V, -RADIUS_H),
                center.offset(RADIUS_H, RADIUS_V, RADIUS_H))) {
            if (mc.level.getBlockState(pos).getBlock() instanceof ShelterDoorBlock) {
                found.add(pos.immutable());
            }
        }
        return found;
    }
}
