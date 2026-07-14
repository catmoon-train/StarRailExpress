package net.exmo.sre.sixtyseconds.client;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsRecipes;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;

/**
 * 玩家准星瞄准<b>功能方块</b>时，在准星下方显示该方块名字（带半透明底衬）。
 * 覆盖范围：所有 {@code sixty_seconds_*} 功能方块（随时显示）+ 60s 模式下的合成台方块（工作台/熔炉/炼药锅）。
 */
public final class SixtySecondsBlockNameHud {
    private SixtySecondsBlockNameHud() {
    }

    public static void register() {
        CommonHudRenderCallback.EVENT.register((graphics, deltaTracker) -> render(graphics));
    }

    private static void render(FakeGuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockState state = mc.level.getBlockState(bhr.getBlockPos());
        if (!isFunctional(state)) {
            return;
        }
        Component name = state.getBlock().getName();
        // 避难所门已通用化（统一门菜单），不再按用途附加后缀
        int cx = mc.getWindow().getGuiScaledWidth() / 2;
        int y = mc.getWindow().getGuiScaledHeight() / 2 + 10;
        int w = mc.font.width(name);
        int x = cx - w / 2;
        // 半透明底衬 + 名字
        g.fill(x - 4, y - 2, x + w + 4, y + 10, 0x88000000);
        g.fill(x - 4, y - 2, x + w + 4, y - 1, 0x33FFE8C0); // 顶部装饰线
        g.drawString(mc.font, name, x, y, 0xFFFFF4DC);
    }

    private static boolean isFunctional(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if ("noellesroles".equals(id.getNamespace()) && id.getPath().startsWith("sixty_seconds_")) {
            return true;
        }
        // 合成台（原版方块被本模式复用）：仅在 60s 模式激活时提示
        boolean inMode = SREClient.gameComponent != null && SixtySecondsMod.MODE != null
                && SREClient.gameComponent.getGameMode() == SixtySecondsMod.MODE;
        return inMode && SixtySecondsRecipes.stationOf(state) != null;
    }
}
