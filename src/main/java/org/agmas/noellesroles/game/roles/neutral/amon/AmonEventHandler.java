package org.agmas.noellesroles.game.roles.neutral.amon;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeath;
import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.AllowShootRevolverDrop;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 阿蒙死亡转移事件：受致命伤（护盾后）时，若存在成熟宿主则夺舍续命并取消死亡。
 *
 * <p>在 {@code killPlayer} 的护盾后判定点拦截（{@link AfterShieldAllowPlayerDeath} 总是先于
 * 带击杀者变体触发，故两者注册同一逻辑不会重复夺舍——首个取消即短路）。
 */
public final class AmonEventHandler {

    private AmonEventHandler() {}

    public static void register() {
        AfterShieldAllowPlayerDeath.EVENT.register((player, deathReason) -> allowDeath(player, deathReason));
        AfterShieldAllowPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> allowDeath(player, deathReason));
        // 阿蒙开枪击中时不让枪掉落（其枪为窃取的复制品，保留在手）。
        AllowShootRevolverDrop.EVENT.register((shooter, target) -> {
            if (!(shooter instanceof ServerPlayer amon)) return TrueFalseResult.PASS;
            SREGameWorldComponent game = SREGameWorldComponent.KEY.get(amon.level());
            if (game.isRunning() && game.isRole(amon, ModRoles.AMON)) {
                return TrueFalseResult.FALSE;
            }
            return TrueFalseResult.PASS;
        });
    }

    /** @return true 允许死亡；false 取消死亡（已夺舍续命）。 */
    private static boolean allowDeath(Player player, ResourceLocation deathReason) {
        // 越界/掉线等死因不允许逃死。
        if (deathReason.equals(GameConstants.DeathReasons.FELL_OUT_OF_TRAIN)
                || deathReason.equals(GameConstants.DeathReasons.DISCONNECT)) {
            return true;
        }
        if (!(player instanceof ServerPlayer amon)) return true;
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(amon.level());
        if (!game.isRunning() || !game.isRole(amon, ModRoles.AMON)) return true;

        AmonPlayerComponent comp = AmonPlayerComponent.KEY.get(amon);
        // 终幕：消耗备用能力逃脱续命；否则中途夺舍续命。两者均无可用资源时真正死亡。
        if (comp.finalePhase) {
            if (comp.tryFinaleEscape()) {
                return false;
            }
            // 无备用能力，阿蒙真正死亡：关闭全服终幕表现（状态栏/音乐/滤镜）。
            comp.endFinaleOnDeath();
            return true;
        }
        return !comp.tryDeathTransfer();
    }
}
