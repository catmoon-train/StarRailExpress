package org.agmas.noellesroles.game.halloween.ambience;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/** 万圣节环境音的公共注册；不加载任何客户端类。 */
public final class HalloweenAmbience {
    public static final ResourceLocation TOWN_MUSIC_ID = ResourceLocation.fromNamespaceAndPath("halloween", "music.town");
    public static final SoundEvent TOWN_MUSIC = SoundEvent.createVariableRangeEvent(TOWN_MUSIC_ID);
    private static boolean registered;

    private HalloweenAmbience() {}

    public static void register() {
        if (registered) return;
        Registry.register(BuiltInRegistries.SOUND_EVENT, TOWN_MUSIC_ID, TOWN_MUSIC);
        registered = true;
    }
}
