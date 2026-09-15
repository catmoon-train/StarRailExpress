/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.client;

import io.wifi.starrailexpress.customitem.CustomThrowableEntity;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;

/**
 * 钳子拆除「自定义列车物品·投掷物」时的进度条 HUD。
 *
 * <p>
 * 数据来自实体的同步字段（{@link CustomThrowableEntity#syncedDefuseTicks()} /
 * {@link CustomThrowableEntity#syncedDefuseTotal()}）：服务端一开始拆除就会同步给所有客户端，
 * 所以自己拆、看别人拆都能看到进度条，不需要额外的网络包。
 */
@Environment(EnvType.CLIENT)
public final class CustomThrowableDefuseHud {

    /** 只显示这个距离内正在被拆的投掷物。 */
    private static final double RANGE = 8.0D;
    private static final int BAR_WIDTH = 130;
    private static final int BAR_HEIGHT = 8;

    private CustomThrowableDefuseHud() {
    }

    public static void register() {
        CommonHudRenderCallback.EVENT.register((graphics, deltaTracker) -> render(graphics));
    }

    private static void render(FakeGuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.options.hideGui) {
            return;
        }
        CustomThrowableEntity target = nearestDefusing(client);
        if (target == null) {
            return;
        }
        int total = Math.max(1, target.syncedDefuseTotal());
        int remaining = Mth.clamp(target.syncedDefuseTicks(), 0, total);
        float progress = Mth.clamp(1.0F - (float) remaining / (float) total, 0.0F, 1.0F);

        int x = (graphics.guiWidth() - BAR_WIDTH) / 2;
        int y = graphics.guiHeight() / 2 + 30;

        graphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, 0xC0000000);
        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xFF3A3A3A);
        graphics.fill(x, y, x + (int) (BAR_WIDTH * progress), y + BAR_HEIGHT, 0xFF66E06B);

        Component text = Component.translatableWithFallback("sre.custom_item.hud.defusing", "拆除中 %.1f 秒",
                (total - remaining) / 20.0F);
        graphics.drawCenteredString(client.font, text, graphics.guiWidth() / 2, y - 12, 0xFFFFFF);
    }

    private static CustomThrowableEntity nearestDefusing(Minecraft client) {
        CustomThrowableEntity found = null;
        double best = RANGE * RANGE;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof CustomThrowableEntity charge) || charge.syncedDefuseTicks() <= 0) {
                continue;
            }
            double distance = entity.distanceToSqr(client.player);
            if (distance <= best) {
                best = distance;
                found = charge;
            }
        }
        return found;
    }
}
