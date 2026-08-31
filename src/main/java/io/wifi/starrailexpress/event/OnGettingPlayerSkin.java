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

package io.wifi.starrailexpress.event;

import io.wifi.starrailexpress.client.util.SREClientUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.PlayerSkin.Model;
import net.minecraft.resources.ResourceLocation;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

import org.agmas.noellesroles.init.ModEffects;

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

        public final PlayerSkin playerSkin;
        public final int type;

        public static PlayerSkinResult alexSlim() {
            return new PlayerSkinResult(ResourceLocation.withDefaultNamespace("textures/entity/player/slim/alex.png"),
                    true);
        }

        public static PlayerSkinResult steveWide() {
            return new PlayerSkinResult(ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png"),
                    false);
        }

        public PlayerSkinResult(PlayerSkin playerSkin) {
            this.type = 2;
            this.playerSkin = playerSkin;
        }

        /**
         * 替换玩家的皮肤（不更换模型，只更换材质）
         * 
         * @param playerSkin
         * @return
         */
        public static PlayerSkinResult texture(ResourceLocation texture, boolean isSlim) {
            return new PlayerSkinResult(texture, isSlim);
        }

        /**
         * 替换玩家的皮肤（包括wide/slim模型）
         * 
         * @param playerSkin
         * @return
         */
        public static PlayerSkinResult playerSkin(PlayerSkin playerSkin) {
            return new PlayerSkinResult(playerSkin);
        }

        /**
         * 替换玩家的皮肤（包括wide/slim模型）
         * 
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
            this.type = 1;
            this.playerSkin = new PlayerSkin(texture, null, null, null, isSlim ? Model.SLIM : Model.WIDE, true);
        }

        public PlayerSkinResult original() {
            return DEFAULT;
        }

        public PlayerSkinResult skip() {
            return SKIP;
        }

        private PlayerSkinResult(ResourceLocation texture, int type, boolean isSlim) {
            this.type = type;
            this.playerSkin = new PlayerSkin(texture, null, null, null, isSlim ? Model.SLIM : Model.WIDE, true);
        }
    }

    /**
     * 获取玩家皮肤事件。
     * 但当玩家拥有 TRUE_SKIN 状态效果将会显示真容。
     * <br/>
     * 请注意！如果您在此event中想要获取皮肤，请不要使用 {@code player.getSkin();}这将会导致崩溃！<br/>
     * 替代方案：请使用 {@link SREClientUtils#getPlayerOriginalSkin} 来获取玩家的原始皮肤！
     */
    Event<OnGettingPlayerSkin> EVENT = createArrayBacked(OnGettingPlayerSkin.class,
            listeners -> (player, originalSkin) -> {
                if (player.hasEffect(ModEffects.TRUE_SKIN)) {
                    return null;
                }
                for (OnGettingPlayerSkin listener : listeners) {
                    var a = listener.onGetSkin(player, originalSkin);
                    if (a != null && a != PlayerSkinResult.SKIP) {
                        return a;
                    }
                }
                return null;
            });

    PlayerSkinResult onGetSkin(AbstractClientPlayer abstractClientPlayerEntity, PlayerSkin originalSkin);
}