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

package org.agmas.noellesroles.scene;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.block_entity.scene.LoopingMirrorBlockEntity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * 上下循环运行时：实体穿过顶/底面时按高度回绕，速度与朝向不变。
 */
public final class VerticalLoopingMirrorManager {
    private static final Set<RelativeMovement> RELATIVE_ALL = EnumSet.of(
            RelativeMovement.X, RelativeMovement.Y, RelativeMovement.Z,
            RelativeMovement.X_ROT, RelativeMovement.Y_ROT);

    private VerticalLoopingMirrorManager() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(VerticalLoopingMirrorManager::tick);
    }

    public static void add(ServerLevel level, VerticalLoopingMirrorLoop loop) {
        VerticalLoopingMirrorSavedData.get(level).add(loop);
    }

    public static void addAndBind(ServerLevel level, VerticalLoopingMirrorLoop loop) {
        LoopingMirrorSavedData.get(level).removeContaining(loop.controller());
        add(level, loop);
        writeToBlockEntity(level, loop);
    }

    public static boolean removeContaining(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        VerticalLoopingMirrorSavedData data = VerticalLoopingMirrorSavedData.get(serverLevel);
        List<VerticalLoopingMirrorLoop> removed = new ArrayList<>();
        for (VerticalLoopingMirrorLoop loop : List.copyOf(data.loops())) {
            if (loop.contains(pos)) {
                removed.add(loop);
            }
        }
        if (removed.isEmpty()) {
            return false;
        }
        data.removeContaining(pos);
        for (VerticalLoopingMirrorLoop loop : removed) {
            clearBlockEntity(serverLevel, loop);
        }
        return true;
    }

    public static void writeToBlockEntity(ServerLevel level, VerticalLoopingMirrorLoop loop) {
        if (level.getBlockEntity(loop.controller()) instanceof LoopingMirrorBlockEntity be) {
            be.setVerticalLoop(loop);
        }
    }

    public static void clearBlockEntity(ServerLevel level, VerticalLoopingMirrorLoop loop) {
        if (level.getBlockEntity(loop.controller()) instanceof LoopingMirrorBlockEntity be) {
            be.clearVerticalLoop();
        }
    }

    private static void tick(ServerLevel level) {
        List<VerticalLoopingMirrorLoop> loops = VerticalLoopingMirrorSavedData.get(level).loops();
        if (loops.isEmpty()) {
            return;
        }
        Set<Entity> wrapped = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (VerticalLoopingMirrorLoop loop : loops) {
            AABB search = loop.searchBox();
            List<Entity> entities = level.getEntities((Entity) null, search,
                    entity -> entity != null && !entity.isRemoved());
            for (Entity entity : entities) {
                Entity root = entity.getRootVehicle();
                if (!wrapped.add(root)) {
                    continue;
                }
                wrap(root, loop);
            }
        }
    }

    private static void wrap(Entity root, VerticalLoopingMirrorLoop loop) {
        if (root instanceof ServerPlayer player && player.isSleeping()) {
            return;
        }
        Vec3 prev = new Vec3(root.xo, root.yo, root.zo);
        Vec3 curr = root.position();
        int direction = loop.crossed(prev, curr);
        if (direction == 0) {
            return;
        }
        Vec3 mapped = loop.wrap(curr, direction);
        Vec3 delta = mapped.subtract(curr);
        if (delta.lengthSqr() < 1.0E-8D) {
            return;
        }
        if (root instanceof ServerPlayer player && !player.isPassenger()) {
            player.teleportTo(player.serverLevel(), delta.x, delta.y, delta.z, RELATIVE_ALL, 0.0F, 0.0F);
            player.hurtMarked = true;
            player.setOldPosAndRot();
            return;
        }
        root.teleportTo(mapped.x, mapped.y, mapped.z);
        root.setOldPosAndRot();
        root.xo = mapped.x;
        root.yo = mapped.y;
        root.zo = mapped.z;
        if (root instanceof ServerPlayer player) {
            player.connection.teleport(mapped.x, mapped.y, mapped.z, root.getYRot(), root.getXRot());
            player.hurtMarked = true;
        }
    }
}
