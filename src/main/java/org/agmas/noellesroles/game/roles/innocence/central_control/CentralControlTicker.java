package org.agmas.noellesroles.game.roles.innocence.central_control;

import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnGameServerTick;
import io.wifi.starrailexpress.event.OnGameTrueStarted;

/**
 * 中控员的全局事件注册（只在模组初始化时注册一次，不为每个玩家 / 每个职业实例注册）。
 *
 * <p>
 * 只做三件事：
 * <ul>
 * <li>每 tick 推进「红外线扫描仪」的区域发光；</li>
 * <li>开局清空红外摄像头登记表（模块状态由方块实体自己重置，这里只是兜底）；</li>
 * <li>局末同样清空，避免跨局残留点位。</li>
 * </ul>
 */
public final class CentralControlTicker {

    private CentralControlTicker() {
    }

    /** 由 {@code NREventRegister} 统一调用一次。 */
    public static void register() {
        OnGameServerTick.EVENT.register(CentralControlRole::tickInfraredScan);
        OnGameTrueStarted.EVENT.register(level -> CentralControlRole.clearRegistration());
        OnGameEnd.EVENT.register((level, gameWorld) -> CentralControlRole.clearRegistration());
    }
}
