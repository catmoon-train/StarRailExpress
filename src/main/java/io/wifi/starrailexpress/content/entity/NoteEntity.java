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

package io.wifi.starrailexpress.content.entity;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import io.wifi.starrailexpress.SRE;
import java.util.function.Supplier;

public class NoteEntity extends Entity {
    private static final EntityDataAccessor<Integer> DIRECTION = SynchedEntityData.defineId(NoteEntity.class,
            EntityDataSerializers.INT);

    @Deprecated
    private static final EntityDataAccessor<String> LINE1 = SynchedEntityData.defineId(NoteEntity.class,
            EntityDataSerializers.STRING);
    @Deprecated
    private static final EntityDataAccessor<String> LINE2 = SynchedEntityData.defineId(NoteEntity.class,
            EntityDataSerializers.STRING);
    @Deprecated
    private static final EntityDataAccessor<String> LINE3 = SynchedEntityData.defineId(NoteEntity.class,
            EntityDataSerializers.STRING);
    @Deprecated
    private static final EntityDataAccessor<String> LINE4 = SynchedEntityData.defineId(NoteEntity.class,
            EntityDataSerializers.STRING);

    public final int seed;

    public NoteEntity(EntityType<?> type, Level world) {
        super(type, world);
        this.seed = this.random.nextInt();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();

        Supplier<Float> randomGiver = () -> (random.nextFloat() - .5f) * .2f;
        if (random.nextFloat() < .1f) {
            this.level().addParticle(ParticleTypes.WAX_ON, this.getX() + randomGiver.get(),
                    this.getY() + randomGiver.get() + this.getBbHeight() / 2f, this.getZ() + randomGiver.get(), 0, 0,
                    0);
        }
    }

    public Component[] getLines() {
        return new Component[] {
                stringToComponent(this.entityData.get(LINE1)),
                stringToComponent(this.entityData.get(LINE2)),
                stringToComponent(this.entityData.get(LINE3)),
                stringToComponent(this.entityData.get(LINE4))
        };
    }

    private String componentToString(Component message) {
        try {
            String msg = Component.Serializer.toJson(message, this.registryAccess());
            return "\uE783" + msg;
        } catch (Exception e) {
            SRE.LOGGER.error("[Note Entity] Error while transform Component message to string", e);
        }
        return "";
    }

    private Component stringToComponent(String string) {
        if (string == null)
            return Component.empty();
        if (string.isBlank())
            return Component.empty();
        if (string.startsWith("\uE783")) {

            try {
                String rawJson = string.substring("\uE783".length());
                Component msg = Component.Serializer.fromJson(rawJson, this.registryAccess());
                return msg;
            } catch (Exception e) {
                SRE.LOGGER.error("[Note Entity] Error while transform Component from JSON to Component", e);
            }
        }

        return Component.literal(string);
    }

    public void setLines(String @NotNull [] lines) {
        if (lines.length > 0)
            this.entityData.set(LINE1, lines[0]);
        if (lines.length > 1)
            this.entityData.set(LINE2, lines[1]);
        if (lines.length > 2)
            this.entityData.set(LINE3, lines[2]);
        if (lines.length > 3)
            this.entityData.set(LINE4, lines[3]);
    }

    public void setLines(Component @NotNull [] lines) {
        String[] arr = new String[lines.length];
        for (int i = 0; i < lines.length; i++) {
            arr[i] = componentToString(lines[i]);
        }
        setLines(arr);
    }

    public @NotNull Direction getDirection() {
        return Direction.values()[this.entityData.get(DIRECTION)];
    }

    public void setDirection(@NotNull Direction direction) {
        this.entityData.set(DIRECTION, direction.get3DDataValue());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        builder.define(DIRECTION, Direction.NORTH.get3DDataValue());
        builder.define(LINE1, "");
        builder.define(LINE2, "");
        builder.define(LINE3, "");
        builder.define(LINE4, "");
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag nbt) {
        nbt.putInt("Direction", this.entityData.get(DIRECTION));
        boolean isRaw = false;
        String line1 = (this.entityData.get(LINE1));
        String line2 = (this.entityData.get(LINE2));
        String line3 = (this.entityData.get(LINE3));
        String line4 = (this.entityData.get(LINE4));
        if (isRawText(line1) || isRawText(line2) || isRawText(line3) || isRawText(line4)) {
            isRaw = true;
        }
        nbt.putString("Line1", removeComponentIndex(line1));
        nbt.putString("Line2", removeComponentIndex(line2));
        nbt.putString("Line3", removeComponentIndex(line3));
        nbt.putString("Line4", removeComponentIndex(line4));
        nbt.putBoolean("raw", isRaw);
    }

    private boolean isRawText(String str) {
        if (str.startsWith("\uE783"))
            return false;
        return true;
    }

    private String removeComponentIndex(String string) {
        if (string.startsWith("\uE783")) {
            return string.substring("\uE783".length());
        }
        return string;
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag nbt) {
        if (nbt.contains("Direction"))
            this.entityData.set(DIRECTION, nbt.getInt("Direction"));

        // 兼容旧版本
        boolean isRawText = true;
        if (nbt.contains("raw")) {
            isRawText = nbt.getBoolean("raw");
        }
        {
            if (nbt.contains("Line1")) {
                String str = nbt.getString("Line1");
                if (str != null) {
                    if (!isRawText) {
                        this.entityData.set(LINE1, "\uE783" + str);
                    } else {
                        this.entityData.set(LINE1, str);
                    }
                }
            }

            if (nbt.contains("Line2")) {
                String str = nbt.getString("Line2");
                if (str != null)
                    if (!isRawText) {
                        this.entityData.set(LINE2, "\uE783" + str);
                    } else {
                        this.entityData.set(LINE2, str);
                    }
            }

            if (nbt.contains("Line3")) {
                String str = nbt.getString("Line3");
                if (str != null)
                    if (!isRawText) {
                        this.entityData.set(LINE3, "\uE783" + str);
                    } else {
                        this.entityData.set(LINE3, str);
                    }
            }

            if (nbt.contains("Line4")) {
                String str = nbt.getString("Line4");
                if (str != null)
                    if (!isRawText) {
                        this.entityData.set(LINE4, "\uE783" + str);
                    } else {
                        this.entityData.set(LINE4, str);
                    }
            }
        }
    }
}