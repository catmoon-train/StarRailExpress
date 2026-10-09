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

package org.agmas.noellesroles.game.roles.killer.dream.client;

import io.wifi.starrailexpress.event.client.OnRenderRoleName;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.killer.dream.DreamHealthComponent;
import org.agmas.noellesroles.game.roles.killer.dream.VirtualShieldComponent;
import org.agmas.noellesroles.role_data.killer.DreamRoleData;
import org.agmas.noellesroles.init.ModEffects;

/**
 * Dream（Dream）客户端逻辑。
 *
 * <ul>
 * <li><b>颤抖</b>（{@link ModEffects#TREMBLE}）：准星/视角每 tick 缓慢随机漂移
 * —— 纯本地视角偏移，多频正弦叠加产生"手抖"般的缓慢游走，不发包。</li>
 * <li><b>虚拟血量条</b>：准星指向玩家时，若其受过伤（虚拟血量未满，见
 * {@link DreamHealthComponent}），在其名字下方绘制红色血条与数值；
 * 通过 {@code OnRenderRoleName.RENDER_PLAYER_EXTRA} 事件挂入，未受伤不显示。</li>
 * <li><b>虚拟护盾条</b>：目标身上有虚拟护盾（见 {@link VirtualShieldComponent}）时，
 * 在虚拟血量条<b>所处的同一个位置</b>绘制淡灰色护盾条，并因此不再绘制虚拟血量条；护盾为 0 时不渲染。
 * 可见性门禁与虚拟血量完全一致（护士 / {@code canUseSpVanillaWeapon}）。</li>
 * <li><b>追杀音乐谓词</b>：{@link #isAnyDreamBerserk()} 供
 * {@code NoellesrolesClientAmbientSounds} 驱动
 * {@code NRSounds.MANHUNT_CHASE}。</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public class DreamClientHandler {
    /** 血条宽度（RoleNameRenderer 的 0.6 缩放坐标系内）。 */
    private static final int BAR_WIDTH = 60;
    /** 虚拟护盾条的填充色（淡灰），边框与数值沿用虚拟血量条的样式。 */
    private static final int SHIELD_BAR_COLOR = 0xFFD0D0D0;
    private static final int SHIELD_BAR_TEXT_COLOR = 0xFFE6E6E6;
    /**
     * 血条 / 护盾条的纵向位置（外层已做过一次 {@code translate(0,20,0)}，所以 y=0 正好是角色名字下方的条）。
     *
     * <p>虚拟护盾与虚拟血量<b>共用同一个位置</b>：有护盾时就只画护盾条、不再画虚拟血量条
     * ——同位置后画的红色血量条会把灰色护盾条整个盖住，护盾等于看不见。
     */
    private static final int BAR_Y = 0;

    public static void register() {
        // 颤抖：视角缓慢漂移
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            var player = client.player;
            if (player == null || client.isPaused() || !player.isAlive()
                    || !player.hasEffect(ModEffects.TREMBLE)) {
                return;
            }
            float t = player.tickCount;
            // 多频正弦叠加：缓慢、无规律但连续的漂移
            float yawDrift = (Mth.sin(t * 0.11f) + 0.6f * Mth.sin(t * 0.23f + 1.7f)) * 0.35f;
            float pitchDrift = (Mth.cos(t * 0.13f + 0.5f) + 0.6f * Mth.sin(t * 0.19f)) * 0.22f;
            // Entity.turn 内部会 ×0.15，这里除回去
            player.turn(yawDrift / 0.15f, pitchDrift / 0.15f);
        });

        // 虚拟护盾条 + 虚拟血量条；只有护士与开了 canUseSpVanillaWeapon 的职业能看到，平民不可见
        OnRenderRoleName.RENDER_PLAYER_EXTRA.register((self, target, context, tickCounter, renderer) -> {
            if (self == null || target == null || self.level() == null) {
                return;
            }
            // 可见性门禁：观察者必须是护士，或开启了 canUseSpVanillaWeapon 的职业
            if (!canViewerSeeVirtualHealth(self)) {
                return;
            }
            long gameTime = self.level().getGameTime();
            DreamHealthComponent health = DreamHealthComponent.KEY.get(target);
            int shield = VirtualShieldComponent.KEY.get(target).currentShield();

            // 往下让开角色名字：护盾条与虚拟血量条都在这个坐标系里画
            context.pose().translate(0, 20, 0);

            // ① 虚拟护盾条：与虚拟血量条<b>同一个位置</b>；数值为 0 时完全不渲染。
            //    有护盾就直接return，不再画虚拟血量条——同一个位置两条会互相盖住。
            if (shield > 0) {
                drawShieldBar(context, renderer, shield);
                return;
            }

            boolean showHealth = isViewerNurse(self) || health.shouldShowBar(gameTime);
            if (!showHealth) {
                return;
            }
            int current = health.getEffectiveHealth(gameTime);
            int max = DreamHealthComponent.maxHealth();
            float ratio = Mth.clamp(current / (float) max, 0f, 1f);

            int y = BAR_Y;
            int half = BAR_WIDTH / 2;
            context.fill(-half - 1, y - 1, half + 1, y + 4, 0xAA000000);
            context.fill(-half, y, -half + (int) (BAR_WIDTH * ratio), y + 3, 0xFFD32F2F);
            Component text = Component.literal(current + " / " + max);
            context.drawString(renderer, text, -renderer.width(text) / 2, y + 6, 0xFFFF6B6B);
            context.pose().translate(0, 16, 0);
        });
    }

    /**
     * 画虚拟护盾条（淡灰色），样式与虚拟血量条一致：黑边框 + 填充 + 条形数值文本，
     * 位置也完全一致（同一个 {@link #BAR_Y}）。
     *
     * <p>以护盾上限 {@link VirtualShieldComponent#DEFAULT_SHIELD} 为满条基准；护盾可以超过
     * 上限（例如指令直接 add 了一个很大的值），此时按「满条」画满并把数值原样写出来。
     */
    private static void drawShieldBar(io.wifi.utils.client.betterrender.FakeGuiGraphics context,
            net.minecraft.client.gui.Font renderer, int shield) {
        int max = VirtualShieldComponent.DEFAULT_SHIELD;
        float ratio = max <= 0 ? 1.0F : Mth.clamp(shield / (float) max, 0f, 1f);
        int half = BAR_WIDTH / 2;
        int y = BAR_Y;
        context.fill(-half - 1, y - 1, half + 1, y + 4, 0xAA000000);
        context.fill(-half, y, -half + (int) (BAR_WIDTH * ratio), y + 3, SHIELD_BAR_COLOR);
        Component text = Component.literal(String.valueOf(shield));
        context.drawString(renderer, text, -renderer.width(text) / 2, y + 6, SHIELD_BAR_TEXT_COLOR);
    }

    /** 本地玩家（观察者）是否是护士：护士始终能看到其它玩家的虚拟血量条。 */
    private static boolean isViewerNurse(Player viewer) {
        var gameComponent = io.wifi.starrailexpress.client.SREClient.gameComponent;
        return gameComponent != null && gameComponent.isRole(viewer,
                org.agmas.noellesroles.role.ModRoles.NURSE);
    }

    /**
     * 观察者能否看到其它玩家的虚拟血量条：
     * 护士始终可见；开启了 {@code canUseSpVanillaWeapon} 的职业可见；平民等其余职业不可见。
     */
    private static boolean canViewerSeeVirtualHealth(Player viewer) {
        var gameComponent = io.wifi.starrailexpress.client.SREClient.gameComponent;
        if (gameComponent == null) {
            return false;
        }
        if (isViewerNurse(viewer)) {
            return true;
        }
        io.wifi.starrailexpress.api.SRERole role = gameComponent.getRole(viewer);
        return role != null && role.canUseSpVanillaWeapon();
    }

    /**
     * 客户端可见范围内是否有处于狂暴（疯魔）状态的 Dream（驱动 MANHUNT_CHASE 追杀音乐）。
     * 疯魔状态由 {@code SREPlayerPsychoComponent} 同步，职业信息由 gameComponent 同步。
     */
    public static boolean isAnyDreamBerserk() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null
                || io.wifi.starrailexpress.client.SREClient.gameComponent == null) {
            return false;
        }
        for (Player p : client.level.players()) {
            if (io.wifi.starrailexpress.client.SREClient.gameComponent.isRole(p,
                    org.agmas.noellesroles.role.ModRoles.DREAM)
                    && DreamRoleData.isBerserk(p)) {
                return true;
            }
        }
        return false;
    }
}
