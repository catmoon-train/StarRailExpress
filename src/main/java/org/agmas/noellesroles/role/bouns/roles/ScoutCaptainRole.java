package org.agmas.noellesroles.role.bouns.roles;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.util.PlayerStaminaGetter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.bouns.BounsRoles;

/**
 * 童子军队长（警长阵营）—— PEAK 爬山图专属，复用童子军的攀爬逻辑。
 *
 * <p>在童子军的基础上多了两点：
 * <ul>
 * <li>体力上限是平民的 {@value #STAMINA_MULTIPLIER} 倍（注册时传的 maxSprintTime）；</li>
 * <li>吃东西额外恢复体力：每次吃完一份食物恢复 {@value #EAT_STAMINA_RESTORE}（10*5）点，
 * 由 {@code ScoutCaptainEatMixin} 挂在 {@code Player#eat} 上。</li>
 * </ul>
 */
public class ScoutCaptainRole extends ScoutRole {

    /** 体力上限倍率（平民体力 × 该值）。 */
    public static final int STAMINA_MULTIPLIER = 3;

    /** 吃一次东西恢复的体力（10 * 5）。 */
    public static final float EAT_STAMINA_RESTORE = 50.0F;

    public ScoutCaptainRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    public static boolean isCaptain(Player player) {
        if (player == null || player.level() == null) {
            return false;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        return game != null && game.isRole(player, BounsRoles.SCOUT_CAPTAIN);
    }

    /**
     * 吃东西恢复体力。
     *
     * <p>客户端与服务端都要执行：{@code sprintingTicks} 两端各自模拟、没有常规同步包，
     * 只改服务端的话 HUD 体力条不会动（与 {@code ShilijiaItem} 同样的处理）。
     */
    public static void onEat(Player player) {
        if (!isCaptain(player) || !(player instanceof PlayerStaminaGetter stamina)) {
            return;
        }
        if (ModEffects.hasInfiniteStamina(player)) {
            return;
        }
        float max = ScoutRole.maxStaminaOf(player);
        if (max == Float.MAX_VALUE) {
            return;
        }
        float current = stamina.starrailexpress$getStamina();
        if (current < 0f) {
            current = max;
        }
        float next = Math.min(max, current + EAT_STAMINA_RESTORE);
        if (next > current) {
            stamina.starrailexpress$setStamina(next);
        }
    }

    /** 便于其它系统按职业对象判断 */
    public static boolean isCaptain(SRERole role) {
        return role != null && role.identifier().equals(BounsRoles.SCOUT_CAPTAIN_ID);
    }
}
