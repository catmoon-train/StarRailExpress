package org.agmas.noellesroles.game.roles.killer.clone_doctor;

import java.awt.Color;
import java.util.OptionalInt;

/** 只替换已获准显示的轮廓颜色，不新增透视权限；五秒循环一次彩虹。 */
public final class CloneDoctorOutlineColor {
    private CloneDoctorOutlineColor() { }

    public static int rainbow(long millis) {
        float hue = Math.floorMod(millis, 5000L) / 5000.0F;
        return Color.HSBtoRGB(hue, 0.85F, 1.0F);
    }

    public static OptionalInt recolor(OptionalInt permittedColor, boolean clonedPerson, long millis) {
        if (permittedColor.isEmpty() || !clonedPerson) return permittedColor;
        return OptionalInt.of(rainbow(millis));
    }
}
