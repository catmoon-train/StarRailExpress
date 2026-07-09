package net.exmo.sre.gooseduck.role;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.awt.Color;

/**
 * 鹅鸭杀模式职业注册：鹅（好人 / 乘客阵营）与鸭（伪装在鹅群中的杀手）。
 * <p>
 * 参照修机模式（{@code RepairRoles}）的写法，用 {@link TMMRoles#registerRole} 注册，
 * 基类 {@link GooseDuckRole} 已把它们标记为「其他模式职业」，因此不会污染谋杀职业池。
 */
public final class GooseDuckRoles {
    public static final ResourceLocation GOOSE_ID = Noellesroles.id("goose");
    public static final ResourceLocation DUCK_ID = Noellesroles.id("duck");

    /** 鹅：好人阵营，完成小游戏任务或投票放逐所有鸭即获胜。 */
    public static final SRERole GOOSE = TMMRoles.registerRole(new GooseDuckRole(
            GOOSE_ID, new Color(240, 240, 240).getRGB(), true, false,
            SRERole.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false).setCanBeRandomedByOtherRoles(false).setDefaultMax(0));

    /** 鸭：伪装杀手，可击杀鹅、破坏（关灯）扰乱任务，需在被投票放逐前消灭鹅。 */
    public static final SRERole DUCK = TMMRoles.registerRole(new GooseDuckRole(
            DUCK_ID, new Color(210, 170, 40).getRGB(), false, true,
            SRERole.MoodType.FAKE, Integer.MAX_VALUE, true).setCanBeRandomedByOtherRoles(false).setDefaultMax(0));

    private GooseDuckRoles() {
    }

    /** 触发类加载以确保职业在注册表中就位（由 {@code GooseDuckMod.init} 调用）。 */
    public static void init() {
    }
}
