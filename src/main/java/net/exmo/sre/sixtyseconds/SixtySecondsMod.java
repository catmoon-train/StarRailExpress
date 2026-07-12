package net.exmo.sre.sixtyseconds;

import io.wifi.StarRailExpressID;
import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * 末日60秒模式的引导装配：通过公开入口 {@link SREGameModes#registerGameMode} 注册模式，
 * <b>不改动</b> {@code io.wifi} 内任何文件；仅需在 {@code Noellesroles.onInitialize} 里调用一次 {@link #init()}
 * （与 {@code GooseDuckMod.init()} 一致）。
 */
public final class SixtySecondsMod {
    /** 模式 ID：{@code sre:sixty_seconds}，可用 {@code /tmm:start sre:sixty_seconds} 启动。 */
    public static final ResourceLocation MODE_ID = StarRailExpressID.shortId("sixty_seconds");

    /** 注册后的模式实例（init 后非空）。 */
    public static GameMode MODE;

    /** 本模式是否正在进行（供无世界上下文的 mixin 判断，如食物不可堆叠）。开局置 true，结束置 false。 */
    public static volatile boolean RUNNING = false;

    private SixtySecondsMod() {
    }

    public static void init() {
        MODE = SREGameModes.registerGameMode(new SixtySecondsGameMode(MODE_ID));
        net.exmo.sre.sixtyseconds.logic.SixtySecondsHealthSystem.register();
        net.exmo.sre.sixtyseconds.logic.SixtySecondsMonsterSystem.registerEvents();
    }

    /** 当前世界是否正在运行本模式。 */
    public static boolean isActive(Level level) {
        return MODE != null && SREGameWorldComponent.KEY.get(level).getGameMode() == MODE;
    }
}
