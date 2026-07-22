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
import org.agmas.noellesroles.game.roles.killer.dream.DreamPlayerComponent;
import org.agmas.noellesroles.init.ModEffects;

/**
 * Dream（梦魇）客户端逻辑。
 *
 * <ul>
 * <li><b>颤抖</b>（{@link ModEffects#TREMBLE}）：准星/视角每 tick 缓慢随机漂移
 * —— 纯本地视角偏移，多频正弦叠加产生"手抖"般的缓慢游走，不发包。</li>
 * <li><b>虚拟血量条</b>：准星指向玩家时，若其受过伤（虚拟血量未满，见
 * {@link DreamHealthComponent}），在其名字下方绘制红色血条与数值；
 * 通过 {@code OnRenderRoleName.RENDER_PLAYER_EXTRA} 事件挂入，未受伤不显示。</li>
 * <li><b>追杀音乐谓词</b>：{@link #isAnyDreamBerserk()} 供
 * {@code NoellesrolesClientAmbientSounds} 驱动
 * {@code NRSounds.MANHUNT_CHASE}。</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public class DreamClientHandler {
    /** 血条宽度（RoleNameRenderer 的 0.6 缩放坐标系内）。 */
    private static final int BAR_WIDTH = 60;

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

        // 虚拟血量条：受伤后才显示
        OnRenderRoleName.RENDER_PLAYER_EXTRA.register((self, target, context, tickCounter, renderer) -> {
            if (self == null || target == null || self.level() == null) {
                return;
            }
            long gameTime = self.level().getGameTime();
            DreamHealthComponent health = DreamHealthComponent.KEY.get(target);
            if (!health.shouldShowBar(gameTime)) {
                return;
            }
            int current = health.getEffectiveHealth(gameTime);
            int max = DreamHealthComponent.maxHealth();
            float ratio = Mth.clamp(current / (float) max, 0f, 1f);

            context.pose().translate(0, 20, 0);
            int y = 0;
            int half = BAR_WIDTH / 2;
            context.fill(-half - 1, y - 1, half + 1, y + 4, 0xAA000000);
            context.fill(-half, y, -half + (int) (BAR_WIDTH * ratio), y + 3, 0xFFD32F2F);
            Component text = Component.literal(current + " / " + max);
            context.drawString(renderer, text, -renderer.width(text) / 2, y + 6, 0xFFFF6B6B);
            context.pose().translate(0, 16, 0);
        });
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
                    && DreamPlayerComponent.isBerserk(p)) {
                return true;
            }
        }
        return false;
    }
}
