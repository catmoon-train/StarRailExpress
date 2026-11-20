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

package io.wifi.starrailexpress.client.ponder;

import com.mojang.authlib.GameProfile;
import net.createmod.catnip.levelWrappers.WrappedClientLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 在思索场景里创建玩家模型。
 *
 * <p>与 PondererLib 的 {@code PonderActions.createPlayer} 行为一致：
 * 未指定身份时优先用当前观看者，其次用固定默认身份。
 */
public final class PonderPlayerFactory {

    /** 没有观看者时使用的占位身份。 */
    public static final GameProfile DEFAULT_PROFILE = new GameProfile(
            UUID.fromString("00000000-0000-0000-0000-000000000001"), "Player");

    private PonderPlayerFactory() {
    }

    /** 用给定身份创建玩家模型；{@code profile} 为 null 时使用观看者身份。 */
    public static RemotePlayer createPlayer(Level level, @Nullable GameProfile profile) {
        if (level == null) {
            return null;
        }
        GameProfile resolved = profile;
        if (resolved == null) {
            var localPlayer = Minecraft.getInstance().player;
            resolved = localPlayer == null ? DEFAULT_PROFILE : localPlayer.getGameProfile();
        }
        RemotePlayer player = new RemotePlayer(WrappedClientLevel.of(level), resolved);
        player.setOnGround(true);
        player.noPhysics = true;
        return player;
    }
}
