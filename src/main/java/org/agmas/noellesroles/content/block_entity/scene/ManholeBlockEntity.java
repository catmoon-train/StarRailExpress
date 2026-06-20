package org.agmas.noellesroles.content.block_entity.scene;

import java.util.List;

import org.agmas.noellesroles.init.ModSceneBlocks;
import org.agmas.noellesroles.scene.ManholeRegistry;
import org.agmas.noellesroles.scene.SceneEventManager;
import org.agmas.noellesroles.scene.SceneParticles;
import org.agmas.noellesroles.scene.SceneRoleAccess;

import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * 井盖方块实体：登记自身位置；统计站在井盖上的可进入玩家的停留时间，超 10 秒窒息死亡。
 */
public class ManholeBlockEntity extends BlockEntity {

    /** 10 秒。 */
    public static final int DEATH_TICKS = 200;
    /** 6 秒开始警告。 */
    public static final int WARN_TICKS = 120;
    private static final String CHANNEL = "manhole";

    public ManholeBlockEntity(BlockPos pos, BlockState state) {
        super(ModSceneBlocks.MANHOLE_ENTITY, pos, state);
    }

    @Override
    public void setRemoved() {
        if (this.level instanceof ServerLevel serverLevel) {
            ManholeRegistry.remove(serverLevel, this.worldPosition);
        }
        super.setRemoved();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ManholeBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // 惰性登记（幂等）
        ManholeRegistry.add(serverLevel, pos);

        AABB top = new AABB(pos.above());
        List<Player> players = serverLevel.getEntitiesOfClass(Player.class, top,
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator()
                        && SceneRoleAccess.canEnterRestricted(p, null));
        for (Player player : players) {
            int dwell = SceneEventManager.reportDwell(serverLevel, player, CHANNEL);
            if (dwell >= DEATH_TICKS) {
                SceneEventManager.resetDwell(serverLevel, player, CHANNEL);
                SceneParticles.burst(serverLevel, player.position().add(0, 0.5, 0), ParticleTypes.BUBBLE_POP,
                        20, 0.3, 0.02);
                GameUtils.forceKillPlayer(player, true, null, GameConstants.DeathReasons.MANHOLE_SUFFOCATION);
            } else if (dwell >= WARN_TICKS && dwell % 10 == 0) {
                SceneParticles.burst(serverLevel, Vec3Center(pos), ParticleTypes.BUBBLE_COLUMN_UP, 6, 0.25, 0.05);
            }
        }
    }

    private static net.minecraft.world.phys.Vec3 Vec3Center(BlockPos pos) {
        return new net.minecraft.world.phys.Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
    }
}
