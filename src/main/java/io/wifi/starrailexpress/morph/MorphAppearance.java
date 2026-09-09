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

package io.wifi.starrailexpress.morph;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * 统一变形外观。
 * <ul>
 * <li>{@link Type#NONE}：未变形</li>
 * <li>{@link Type#PLAYER}：复制指定玩家的皮肤 / 帽子 / 名牌 / 玩偶</li>
 * <li>{@link Type#TEXTURE}：使用指定贴图（无真实玩家可复制，帽子/名牌/玩偶按隐藏处理）</li>
 * </ul>
 */
public final class MorphAppearance {

    public enum Type {
        NONE,
        PLAYER,
        TEXTURE
    }

    public static final MorphAppearance NONE = new MorphAppearance(Type.NONE, null, null, false);

    private final Type type;
    private final @Nullable UUID targetPlayer;
    private final @Nullable ResourceLocation texture;
    private final boolean slim;

    private MorphAppearance(Type type, @Nullable UUID targetPlayer, @Nullable ResourceLocation texture, boolean slim) {
        this.type = type;
        this.targetPlayer = targetPlayer;
        this.texture = texture;
        this.slim = slim;
    }

    public static MorphAppearance ofPlayer(UUID targetPlayer) {
        if (targetPlayer == null) {
            return NONE;
        }
        return new MorphAppearance(Type.PLAYER, targetPlayer, null, false);
    }

    public static MorphAppearance ofTexture(ResourceLocation texture, boolean slim) {
        if (texture == null) {
            return NONE;
        }
        return new MorphAppearance(Type.TEXTURE, null, texture, slim);
    }

    public Type type() {
        return type;
    }

    public boolean isNone() {
        return type == Type.NONE;
    }

    public boolean isPlayer() {
        return type == Type.PLAYER && targetPlayer != null;
    }

    public boolean isTexture() {
        return type == Type.TEXTURE && texture != null;
    }

    public @Nullable UUID targetPlayer() {
        return targetPlayer;
    }

    public @Nullable ResourceLocation texture() {
        return texture;
    }

    public boolean slim() {
        return slim;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeByte(type.ordinal());
        if (type == Type.PLAYER) {
            buf.writeUUID(targetPlayer);
        } else if (type == Type.TEXTURE) {
            buf.writeResourceLocation(texture);
            buf.writeBoolean(slim);
        }
    }

    public static MorphAppearance read(FriendlyByteBuf buf) {
        int ordinal = buf.readUnsignedByte();
        Type[] values = Type.values();
        Type type = ordinal >= 0 && ordinal < values.length ? values[ordinal] : Type.NONE;
        return switch (type) {
            case PLAYER -> ofPlayer(buf.readUUID());
            case TEXTURE -> ofTexture(buf.readResourceLocation(), buf.readBoolean());
            case NONE -> NONE;
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MorphAppearance that)) {
            return false;
        }
        return slim == that.slim && type == that.type
                && Objects.equals(targetPlayer, that.targetPlayer)
                && Objects.equals(texture, that.texture);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, targetPlayer, texture, slim);
    }

    @Override
    public String toString() {
        return switch (type) {
            case NONE -> "MorphAppearance.NONE";
            case PLAYER -> "MorphAppearance.player(" + targetPlayer + ")";
            case TEXTURE -> "MorphAppearance.texture(" + texture + ", slim=" + slim + ")";
        };
    }
}
