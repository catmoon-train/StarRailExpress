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

package org.agmas.noellesroles.client;

import io.wifi.ConfigCompact.ui.SettingMenuScreen;
import io.wifi.starrailexpress.client.gui.screen.WithParentScreenPauseScreen;
import net.exmo.sre.loading.StarRailExpressTitleScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerLinksScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.social.SocialInteractionsScreen;
import net.minecraft.client.player.LocalPlayer;
import org.agmas.noellesroles.client.screen.FilterSelectionScreen;
import org.agmas.noellesroles.client.screen.RoleIntroduceScreen;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.Nullable;

/**
 * 文盲效果：把客户端即将绘制的码点替换成乱码。
 * {@link #scramble} 每帧在 GameRenderer 开头更新，字形热路径只读这个布尔值。
 * ESC 暂停菜单、职业介绍及其子页面不乱码。
 */
@Environment(EnvType.CLIENT)
public final class IlliterateTextClientHandle {
    private static final char[] CJK = "锟斤拷烫屯嘦孬嫑掱朩圐烎龘靐齉灪龖齾爩龗麤锟斤拷烫屯嘦孬嫑掱朩圐".toCharArray();
    private static final char[] LATIN_UPPER = "BDFGHJKMPQRTVWXY".toCharArray();
    private static final char[] LATIN_LOWER = "bdfghjkmpqrtvwxy".toCharArray();
    private static final char[] DIGITS = "34789347".toCharArray();
    private static final char[] PUNCT = "#%&*?~=+<>/\\|@!^".toCharArray();

    /** 约 20% 的字形会被替换（51/256），其余保持原文。 */
    private static final int SCRAMBLE_THRESHOLD = 51;

    private static boolean hasEffect;

    /** Font 热路径只读：有文盲效果且当前不是系统菜单。 */
    public static boolean scramble;

    static {
        if (CJK.length != 32 || LATIN_UPPER.length != 16 || LATIN_LOWER.length != 16
                || DIGITS.length != 8 || PUNCT.length != 16) {
            throw new IllegalStateException("Illiterate scramble pools must be power-of-two length");
        }
    }

    private IlliterateTextClientHandle() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> refresh(client));
    }

    public static void refresh(Minecraft client) {
        LocalPlayer player = client.player;
        hasEffect = player != null && player.hasEffect(ModEffects.ILLITERATE);
        scramble = hasEffect && !isSystemMenu(client.screen);
    }

    /**
     * ESC 暂停菜单、原版选项树、断开确认，以及暂停菜单里打开的模组设置/角色介绍。
     * 背包、聊天、商店等玩法界面仍会乱码。
     */
    private static boolean isSystemMenu(@Nullable Screen screen) {
        if (screen == null) {
            return false;
        }
        if (screen instanceof PauseScreen
                || screen instanceof OptionsScreen
                || screen instanceof ConfirmScreen
                || screen instanceof ShareToLanScreen
                || screen instanceof TitleScreen
                || screen instanceof DeathScreen
                || screen instanceof DisconnectedScreen
                || screen instanceof GenericMessageScreen
                || screen instanceof StarRailExpressTitleScreen
                || screen instanceof SettingMenuScreen
                || screen instanceof RoleIntroduceScreen
                || screen instanceof FilterSelectionScreen
                || screen instanceof SocialInteractionsScreen
                || screen instanceof ServerLinksScreen) {
            return true;
        }
        Class<?> cls = screen.getClass();
        if (cls.getEnclosingClass() == WithParentScreenPauseScreen.class) {
            return true;
        }
        String name = cls.getName();
        return name.startsWith("net.minecraft.client.gui.screens.options.")
                || name.startsWith("net.minecraft.client.gui.screens.packs.");
    }

    public static int scrambleCodePoint(int index, int codePoint) {
        if (codePoint <= 32 || codePoint == 0x7F || codePoint == 0x00A0 || codePoint == 0x3000) {
            return codePoint;
        }
        int h = index * 0x9E3779B9 ^ codePoint;
        h ^= h >>> 16;
        if ((h & 0xFF) >= SCRAMBLE_THRESHOLD) {
            return codePoint;
        }
        if (codePoint >= 0x4E00 && codePoint <= 0x9FFF) {
            return CJK[h & 31];
        }
        if (codePoint >= 'a' && codePoint <= 'z') {
            return LATIN_LOWER[h & 15];
        }
        if (codePoint >= 'A' && codePoint <= 'Z') {
            return LATIN_UPPER[h & 15];
        }
        if (codePoint >= '0' && codePoint <= '9') {
            return DIGITS[h & 7];
        }
        if (isCjkLike(codePoint) || (codePoint >= 0xFF01 && codePoint <= 0xFFEE)) {
            return CJK[h & 31];
        }
        if (codePoint >= 0x2000 && codePoint <= 0x206F) {
            return codePoint;
        }
        return PUNCT[h & 15];
    }

    private static boolean isCjkLike(int codePoint) {
        return (codePoint >= 0x2E80 && codePoint <= 0x9FFF)
                || (codePoint >= 0xAC00 && codePoint <= 0xD7AF)
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF);
    }
}
