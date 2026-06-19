package io.wifi.starrailexpress.event;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.PlayerSkin.Model;
import net.minecraft.resources.ResourceLocation;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

import io.wifi.starrailexpress.client.util.SREClientUtils;

/**
 * 获取玩家皮肤事件。
 * <br/>
 * 请注意！如果您在此event中想要获取皮肤，请不要使用 {@code player.getSkin();}这将会导致崩溃！<br/>
 * 替代方案：请使用 {@link SREClientUtils#getPlayerOriginalSkin} 来获取玩家的原始皮肤！
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

        /**
         * 替换玩家的皮肤（不更换模型，只更换材质）
         * @param playerSkin
         * @return
         */
        public static PlayerSkinResult texture(ResourceLocation texture, boolean isSlim) {
            return new PlayerSkinResult(texture, isSlim);
        }

        /**
         * 替换玩家的皮肤（包括wide/slim模型）
         * @param playerSkin
         * @return
         */
        public static PlayerSkinResult playerSkin(PlayerSkin playerSkin) {
            return new PlayerSkinResult(playerSkin);
        }

        /**
         * 替换玩家的皮肤（包括wide/slim模型）
         * @param playerSkin
         * @return
         */
        public static PlayerSkinResult playerSkin(ResourceLocation texture, Model model) {
            // ResourceLocation texture, @Nullable String textureUrl, @Nullable
            // ResourceLocation capeTexture, @Nullable ResourceLocation elytraTexture, Model
            // model, boolean secure
            return playerSkin(new PlayerSkin(texture, null, null, null, model, true));
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
     * <br/>
     * 请注意！如果您在此event中想要获取皮肤，请不要使用 {@code player.getSkin();}这将会导致崩溃！<br/>
     * 替代方案：请使用 {@link SREClientUtils#getPlayerOriginalSkin} 来获取玩家的原始皮肤！
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