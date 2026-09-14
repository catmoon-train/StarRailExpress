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

package io.wifi.starrailexpress.disguise;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 实体伪装状态：伪装成哪个实体、用什么外观 NBT、眼高是多少。
 * <p>
 * 完全由实体类型 + 外观 NBT 描述，不枚举任何具体实体——任意已注册实体（含其他模组的）都能伪装。
 * {@link #NONE} 表示「未伪装」，同步包用它在增量更新里表达「解除该玩家的伪装」。
 */
public final class EntityDisguiseState {

    /** 未伪装。 */
    public static final EntityDisguiseState NONE = new EntityDisguiseState(null, null, 0.0F);

    private final @Nullable EntityType<?> type;
    private final @Nullable CompoundTag nbt;
    private final float eyeHeight;

    private EntityDisguiseState(@Nullable EntityType<?> type, @Nullable CompoundTag nbt, float eyeHeight) {
        this.type = type;
        this.nbt = nbt;
        this.eyeHeight = eyeHeight;
    }

    /**
     * @param type      伪装成的实体类型
     * @param nbt       外观 NBT（可为 null）；内部会复制一份，调用方后续修改不影响本状态
     * @param eyeHeight 该实体的眼高（由 {@link EntityDisguise#computeEyeHeight} 预先算好，避免逐帧创建实体）
     */
    public static EntityDisguiseState of(@Nullable EntityType<?> type, @Nullable CompoundTag nbt, float eyeHeight) {
        if (type == null) {
            return NONE;
        }
        return new EntityDisguiseState(type, nbt == null ? null : nbt.copy(), eyeHeight);
    }

    public boolean isNone() {
        return type == null;
    }

    /** 伪装成的实体类型；{@link #isNone()} 时为 {@code null}。 */
    public @Nullable EntityType<?> type() {
        return type;
    }

    /**
     * 外观 NBT；{@link #isNone()} 时为 {@code null}。
     * <p>
     * 返回内部实例（不做防御性复制）——客户端只在重建临时实体时读一次，请勿修改。
     */
    public @Nullable CompoundTag nbt() {
        return nbt;
    }

    /** 该实体的眼高；未伪装时为 {@code 0}。 */
    public float eyeHeight() {
        return eyeHeight;
    }

    public void write(FriendlyByteBuf buf) {
        ResourceLocation id = type == null ? null : EntityType.getKey(type);
        if (id == null) {
            buf.writeBoolean(false);
            return;
        }
        buf.writeBoolean(true);
        buf.writeResourceLocation(id);
        buf.writeNbt(nbt);
        buf.writeFloat(eyeHeight);
    }

    public static EntityDisguiseState read(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return NONE;
        }
        ResourceLocation id = buf.readResourceLocation();
        CompoundTag nbt = buf.readNbt();
        float eyeHeight = buf.readFloat();
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            // 服务端认识、客户端不认识（模组实体不同步）时按未伪装处理，而不是崩在渲染里。
            return NONE;
        }
        return of(BuiltInRegistries.ENTITY_TYPE.get(id), nbt, eyeHeight);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EntityDisguiseState that)) {
            return false;
        }
        return Float.compare(eyeHeight, that.eyeHeight) == 0
                && Objects.equals(type, that.type)
                && Objects.equals(nbt, that.nbt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, nbt, eyeHeight);
    }

    @Override
    public String toString() {
        if (isNone()) {
            return "EntityDisguiseState.NONE";
        }
        return "EntityDisguiseState[" + EntityType.getKey(type) + ", eyeHeight=" + eyeHeight
                + (nbt == null || nbt.isEmpty() ? "" : ", nbt=" + nbt) + "]";
    }
}
