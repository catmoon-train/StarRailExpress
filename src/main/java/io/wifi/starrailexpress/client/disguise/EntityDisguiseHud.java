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

package io.wifi.starrailexpress.client.disguise;

import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.client.gui.HudMoodRenderer;
import io.wifi.starrailexpress.cca.SREPlayerMinigameTaskComponent;
import io.wifi.starrailexpress.event.client.OnRenderRoleName;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;
import org.agmas.noellesroles.init.ModEffects;

import java.util.List;

/**
 * 伪装状态 HUD。
 * <p>
 * 两个位置：
 * <ul>
 * <li><b>自己看自己</b>：被伪装时在左上角信息行（心情任务 + 小游戏任务）**下方**新起若干行，
 * 显示「当前伪装：xxx」，一行一个来源。</li>
 * <li><b>观察者看别人</b>：火眼金睛持有者、创造 / 旁观者瞄准某名玩家时，在战斗名牌区域多一行
 * 「当前伪装成：xxx」。挂 {@code RENDER_PLAYER_EXTRA} 事件——{@code RoleNameRenderer} 的作者明确
 * 要求不要 mixin 那个类。该行按事件的约定先 {@code translate(0, 12)} 新起一行，结束时再把光标
 * 推到下一行起点，避免与其它监听器（肉汁提示、Dream 血条……）重叠。</li>
 * </ul>
 * 注意：实体伪装对火眼金睛**不**穿透（那只针对皮肤），所以这里照报不误。
 * 开关：{@code SREClientConfig.showDisguiseHud}。
 */
@Environment(EnvType.CLIENT)
public final class EntityDisguiseHud {

    /** 与心情任务行同一起始 x（见 HudMoodRenderer）。 */
    private static final int MOOD_X = 22;
    /** 心情任务行：起始 y 与行距（见 HudMoodRenderer）。 */
    private static final int MOOD_START_Y = 6;
    private static final int MOOD_LINE_H = 10;
    /** 小游戏任务区上方的间距、每行占位、以及本 HUD 与上一块之间留的空隙。 */
    private static final int MINIGAME_GAP = 8;
    private static final int MINIGAME_LINE_H = 12;
    private static final int HUD_MARGIN = 2;

    private static final int COLOR = 0xFFFFD37F;

    private EntityDisguiseHud() {
    }

    public static void register() {
        CommonHudRenderCallback.EVENT.register((context, deltaTracker) -> renderSelfStatus(context));
        OnRenderRoleName.RENDER_PLAYER_EXTRA.register(EntityDisguiseHud::renderAimedTarget);
    }

    /** 自己看自己：左上角信息行下方。 */
    private static void renderSelfStatus(FakeGuiGraphics context) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer self = client.player;
        if (self == null || client.options.hideGui || !SREClientConfig.instance().showDisguiseHud) {
            return;
        }
        List<Component> names = DisguiseStatusResolver.describe(self);
        if (names.isEmpty()) {
            return;
        }
        Font font = client.font;
        int x = MOOD_X;
        int y = topLeftAnchorY(self);
        context.pose().pushPose();
        // 与心情 / 小游戏 HUD 一起被这两个偏移量搬动，免得用户把它们挪走后两者重叠。
        context.pose().translate(SREClientConfig.instance().moodLeftOffset,
                SREClientConfig.instance().moodTopOffset, 0F);
        for (Component name : names) {
            Component line = Component.translatable("hud.sre.entitydisguise.status", name);
            context.drawString(font, line, x, y, COLOR, true);
            y += font.lineHeight + 2;
        }
        context.pose().popPose();
    }

    /**
     * 左上角信息栈的底部：照 {@code MiniGameHudRenderer} 的公式推算（它自己是本地变量 lineY，
     * 没有暴露出来），再多留一点空隙。心情行数来自 {@code HudMoodRenderer.renderers}。
     */
    private static int topLeftAnchorY(LocalPlayer self) {
        int y = MOOD_START_Y + MOOD_LINE_H * HudMoodRenderer.renderers.size() + MINIGAME_GAP;
        SREPlayerMinigameTaskComponent minigame = SREPlayerMinigameTaskComponent.KEY.get(self);
        if (minigame != null) {
            if (minigame.hasPendingTask()) {
                y += MINIGAME_LINE_H;
            }
            if (minigame.hasSabotageTask()) {
                y += MINIGAME_LINE_H;
            }
        }
        return y + HUD_MARGIN;
    }

    /** 观察者看别人：瞄准一名玩家时，在战斗名牌下方加一行。 */
    private static void renderAimedTarget(Player self, Player target, FakeGuiGraphics context,
            DeltaTracker deltaTracker, Font font) {
        if (self == null || target == null || !canSeeDisguiseStatus(self)) {
            return;
        }
        List<Component> names = DisguiseStatusResolver.describe(target);
        if (names.isEmpty()) {
            return;
        }
        for (Component name : names) {
            // 事件约定：先偏移 12 新起一行，文本在 y = 0 处居中绘制。
            context.pose().translate(0F, 12F, 0F);
            Component line = Component.translatable("hud.sre.entitydisguise.aimed", name);
            context.drawString(font, line, -font.width(line) / 2, 0, Mth.color(1.0F, 0.85F, 0.4F) | 0xFF000000);
        }
        // 把光标推到本行下方，避免与后面的监听器（肉汁提示 / Dream 血条……）叠在一起。
        context.pose().translate(0F, font.lineHeight + 2, 0F);
    }

    /** 谁能看到别人的伪装状态：火眼金睛、创造、旁观。 */
    private static boolean canSeeDisguiseStatus(Player self) {
        return self.hasEffect(ModEffects.TRUE_SKIN_OBSERVER) || self.isCreative() || self.isSpectator();
    }
}
