package io.wifi.starrailexpress.event;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 获取玩家皮肤事件。
 */
@Environment(EnvType.CLIENT)
public interface OnGettingPlayerSkin {
    public static class PlayerSkinResult {
        public static PlayerSkinResult DEFAULT = new PlayerSkinResult(null, 0, false);
        public static PlayerSkinResult SKIP = new PlayerSkinResult(null, -1, false);

        public final ResourceLocation texture;
        public final PlayerSkin playerSkin;
        public final int type;
        public final boolean isSlim;

        public static PlayerSkinResult alexSlim() {
            return new PlayerSkinResult(ResourceLocation.withDefaultNamespace("textures/entity/player/slim/alex.png"),
                    true);
        }

        public static PlayerSkinResult steveWide() {
            return new PlayerSkinResult(ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png"),
                    false);
        }

        public PlayerSkinResult(PlayerSkin playerSkin) {
            this.texture = null;
            this.type = 2;
            this.playerSkin = playerSkin;
            this.isSlim = false;
        }

        public static PlayerSkinResult texture(ResourceLocation texture, boolean isSlim) {
            return new PlayerSkinResult(texture, isSlim);
        }

        public static PlayerSkinResult playerSkin(PlayerSkin playerSkin) {
            return new PlayerSkinResult(playerSkin);
        }

        public PlayerSkinResult(ResourceLocation texture, boolean isSlim) {
            this.texture = texture;
            this.type = 1;
            this.isSlim = isSlim;
            this.playerSkin = null;
        }

        public PlayerSkinResult original() {
            return DEFAULT;
        }

        public PlayerSkinResult skip() {
            return SKIP;
        }

        private PlayerSkinResult(ResourceLocation texture, int type, boolean isSlim) {
            this.texture = texture;
            this.type = type;
            this.playerSkin = null;
            this.isSlim = isSlim;
        }
    }

    /**
     * 获取玩家皮肤事件。
     */
    Event<OnGettingPlayerSkin> EVENT = createArrayBacked(OnGettingPlayerSkin.class,
            listeners -> (player) -> {
                for (OnGettingPlayerSkin listener : listeners) {
                    var a = listener.onGetSkin(player);
                    if (a != null && a != PlayerSkinResult.SKIP) {
                        return a;
                    }
                }
                return null;
            });

    PlayerSkinResult onGetSkin(AbstractClientPlayer abstractClientPlayerEntity);
}