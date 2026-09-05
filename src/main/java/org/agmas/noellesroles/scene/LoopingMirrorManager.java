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
import net.minecraft.util.Mth;
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
 * 循环镜子运行时：实体穿过任一平面时相对传送到另一平面，保留速度并按夹角旋转朝向。
 * 对面场景由客户端生成，服务端不再复制方块。
 */
public final class LoopingMirrorManager {
    private static final Set<RelativeMovement> RELATIVE_ALL = EnumSet.of(
            RelativeMovement.X, RelativeMovement.Y, RelativeMovement.Z,
            RelativeMovement.X_ROT, RelativeMovement.Y_ROT);

    private LoopingMirrorManager() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(LoopingMirrorManager::tick);
    }

    public static void add(ServerLevel level, LoopingMirrorLoop loop) {
        LoopingMirrorSavedData.get(level).add(loop);
    }

    public static void addAndBind(ServerLevel level, LoopingMirrorLoop loop) {
        add(level, loop);
        writeToBlockEntity(level, loop);
    }

    public static boolean removeContaining(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        LoopingMirrorSavedData data = LoopingMirrorSavedData.get(serverLevel);
        List<LoopingMirrorLoop> removed = new ArrayList<>();
        for (LoopingMirrorLoop loop : List.copyOf(data.loops())) {
            if (loop.contains(pos)) {
                removed.add(loop);
            }
        }
        if (removed.isEmpty()) {
            return false;
        }
        data.removeContaining(pos);
        for (LoopingMirrorLoop loop : removed) {
            clearBlockEntity(serverLevel, loop);
        }
        return true;
    }

    public static void writeToBlockEntity(ServerLevel level, LoopingMirrorLoop loop) {
        if (level.getBlockEntity(loop.controller()) instanceof LoopingMirrorBlockEntity be) {
            be.setLoop(loop);
        }
    }

    public static void clearBlockEntity(ServerLevel level, LoopingMirrorLoop loop) {
        if (level.getBlockEntity(loop.controller()) instanceof LoopingMirrorBlockEntity be) {
            be.clearLoop();
        }
    }

    private static void tick(ServerLevel level) {
        List<LoopingMirrorLoop> loops = LoopingMirrorSavedData.get(level).loops();
        if (loops.isEmpty()) {
            return;
        }
        Set<Entity> wrapped = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (LoopingMirrorLoop loop : loops) {
            AABB search = loop.searchBox();
            List<Entity> entities = level.getEntities((Entity) null, search, entity -> entity != null && !entity.isRemoved());
            for (Entity entity : entities) {
                Entity root = entity.getRootVehicle();
                if (!wrapped.add(root)) {
                    continue;
                }
                wrap(root, loop);
            }
        }
    }

    private static void wrap(Entity root, LoopingMirrorLoop loop) {
        if (root instanceof ServerPlayer player && player.isSleeping()) {
            return;
        }
        Vec3 prev = new Vec3(root.xo, root.yo, root.zo);
        Vec3 curr = root.position();
        LoopingMirrorPlane from = loop.crossedFrom(prev, curr);
        if (from == null) {
            return;
        }
        LoopingMirrorPlane to = loop.other(from);
        Vec3 mapped = loop.mapPoint(from, to, curr);
        Vec3 delta = mapped.subtract(curr);
        if (delta.lengthSqr() < 1.0E-8D) {
            return;
        }
        Vec3 velocity = loop.mapVec(from, to, root.getDeltaMovement());
        Vec3 look = loop.mapVec(from, to, root.getLookAngle());
        float yawDelta = Mth.wrapDegrees(loop.yawOf(look) - root.getYRot());
        float pitchDelta = Mth.wrapDegrees(loop.pitchOf(look) - root.getXRot());
        if (root instanceof ServerPlayer player && !player.isPassenger()) {
            player.teleportTo(player.serverLevel(), delta.x, delta.y, delta.z, RELATIVE_ALL, yawDelta, pitchDelta);
            player.setDeltaMovement(velocity);
            player.hurtMarked = true;
            player.setOldPosAndRot();
            return;
        }
        float yRot = root.getYRot() + yawDelta;
        float xRot = root.getXRot() + pitchDelta;
        root.teleportTo(mapped.x, mapped.y, mapped.z);
        root.setYRot(yRot);
        root.setXRot(xRot);
        root.setDeltaMovement(velocity);
        root.setOldPosAndRot();
        root.xo = mapped.x;
        root.yo = mapped.y;
        root.zo = mapped.z;
        if (root instanceof ServerPlayer player) {
            player.connection.teleport(mapped.x, mapped.y, mapped.z, yRot, xRot);
            player.hurtMarked = true;
        }
    }
}
