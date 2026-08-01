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

package org.agmas.noellesroles.content.block_entity.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.init.ModSceneBlocks;
import org.agmas.noellesroles.scene.SceneTaskManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 作物方块实体：检测在其上蹦跶（向上速度）的玩家并上报场景任务进度，带去抖动。
 */
public class CropBlockEntity extends BlockEntity {

    private static final int DEBOUNCE = 10;
    private final Map<UUID, Long> lastBounce = new HashMap<>();

    public CropBlockEntity(BlockPos pos, BlockState state) {
        super(ModSceneBlocks.CROP_ENTITY, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CropBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        AABB top = new AABB(pos.above()).inflate(0.1, 0.2, 0.1);
        List<Player> players = serverLevel.getEntitiesOfClass(Player.class, top,
                p -> p.isAlive() && !p.isSpectator() && p.getDeltaMovement().y > 0.2);
        long now = serverLevel.getGameTime();
        for (Player p : players) {
            Long last = be.lastBounce.get(p.getUUID());
            if (last != null && now - last < DEBOUNCE) {
                continue;
            }
            be.lastBounce.put(p.getUUID(), now);
            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 8, 0.3, 0.1, 0.3, 0.05);
            if (p instanceof ServerPlayer sp) {
                SceneTaskManager.reportCropBounce(sp);
            }
        }
    }
}
