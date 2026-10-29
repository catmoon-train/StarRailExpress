package org.agmas.noellesroles.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * 恐惧魔王的细手皮只走 Psycho 皮肤。这里不再按杀戮形态贴皮，避免狂暴结束后皮肤卡住。
 */
@Environment(EnvType.CLIENT)
public final class DreadlordSkinHandler {
    private DreadlordSkinHandler() {
    }

    public static void register() {
    }
}
