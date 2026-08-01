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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.content.block.scene.SceneGateBlock;
import org.agmas.noellesroles.init.ModSceneBlocks;
import org.agmas.noellesroles.scene.SceneEventManager;

/**
 * 场景大门方块实体：每 10 tick 将开合状态与破坏任务状态同步。
 */
public class SceneGateBlockEntity extends BlockEntity {

    public SceneGateBlockEntity(BlockPos pos, BlockState state) {
        super(ModSceneBlocks.SCENE_GATE_ENTITY, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SceneGateBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel) || level.getGameTime() % 10 != 0) {
            return;
        }
        // 破坏任务激活 或 收到红石信号 时落下封路（红石便于地图直接触发/测试）
        boolean wantClosed = SceneEventManager.isSabotageActive(serverLevel) || serverLevel.hasNeighborSignal(pos);
        if (wantClosed != state.getValue(SceneGateBlock.CLOSED)) {
            serverLevel.setBlock(pos, state.setValue(SceneGateBlock.CLOSED, wantClosed), Block.UPDATE_ALL);
            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.4, 0.5, 0.4, 0.1);
            serverLevel.playSound(null, pos,
                    wantClosed ? SoundEvents.IRON_DOOR_CLOSE : SoundEvents.IRON_DOOR_OPEN,
                    SoundSource.BLOCKS, 1.2F, wantClosed ? 0.7F : 1.0F);
        }
    }
}
