package net.exmo.sre.gooseduck;

import io.wifi.StarRailExpressID;
import io.wifi.starrailexpress.api.SREGameModes;
import net.exmo.sre.gooseduck.role.GooseDuckRoles;
import net.minecraft.resources.ResourceLocation;

/**
 * 鹅鸭杀模式的引导装配：注册职业、游戏模式与鸭的破坏技能。
 * <p>
 * 通过既有的公开入口 {@link SREGameModes#registerGameMode} 注册模式，
 * 因此<b>无需改动</b> {@code io.wifi} 内的任何文件；仅需在 {@code Noellesroles.onInitialize}
 * 里调用一次 {@link #init()}（与 {@code ModEffects.init()} 等子系统装配一致）。
 */
public final class GooseDuckMod {
    /** 模式 ID：{@code canyuesama:goose_duck}，与作者其它模式（修机 / 第四房间）同命名空间。 */
    public static final ResourceLocation GOOSE_DUCK_MODE_ID = StarRailExpressID.canyueId("goose_duck");

    private GooseDuckMod() {
    }

    public static void init() {
        // 确保鹅 / 鸭职业在注册表就位。
        GooseDuckRoles.init();
        // 注册游戏模式（追加进 GAME_MODES 表，可用 /tmm:start canyuesama:goose_duck 启动）。
        SREGameModes.registerGameMode(new GooseGooseDuckGameMode(GOOSE_DUCK_MODE_ID));
        // 注册鸭的主动破坏技能。
        GooseDuckSabotage.register();
    }
}
