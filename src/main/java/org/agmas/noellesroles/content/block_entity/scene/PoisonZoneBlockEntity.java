package org.agmas.noellesroles.content.block_entity.scene;

import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import io.wifi.starrailexpress.index.TMMParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.init.ModSceneBlocks;
import org.agmas.noellesroles.scene.SceneEventManager;
import org.agmas.noellesroles.scene.SceneParticles;

import java.util.List;

/**
 * 有毒区域方块实体：持续停留在毒区中 400 tick 后获得 30 秒中毒（potionTicks），
 * 已中毒的玩家在毒区中加速计时器衰减。
 */
public class PoisonZoneBlockEntity extends BlockEntity {

    /** 获得毒药效果的停留时间（20 秒 = 400 tick）。 */
    public static final int DWELL_TICKS = 400;
    /** 中毒持续时间（30 秒 = 600 tick）。 */
    public static final int POISON_TICKS = 600;
    /** 在毒区中中毒计时器的额外衰减速度（每 tick 额外减 N）。 */
    public static final int ZONE_ACCELERATION = 2;
    private static final String CHANNEL = "poison";

    public PoisonZoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModSceneBlocks.POISON_ZONE_ENTITY, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PoisonZoneBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        AABB box = SceneParticles.sceneRegion(pos);
        // 区域内毒气粒子（覆盖 3×3×4 范围）
        if (serverLevel.getGameTime() % 2 == 0) {
            SceneParticles.regionScatter(serverLevel, box, TMMParticles.POISON, 4);
        }
        List<Player> players = serverLevel.getEntitiesOfClass(Player.class, box,
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator());
        for (Player player : players) {
            // 已中毒的玩家在毒区中加速衰减
            SREPlayerPoisonComponent poison = SREPlayerPoisonComponent.KEY.get(player);
            if (poison.getPoisonTicks() > 0) {
                // 加速中毒计时器（每 tick 额外减 ZONE_ACCELERATION）
                poison.poisonTicks = Math.max(0, poison.poisonTicks - ZONE_ACCELERATION);
                poison.sync();
                // 加速提示粒子
                if (serverLevel.getGameTime() % 10 == 0) {
                    SceneParticles.burst(serverLevel, player.position().add(0, 1, 0), TMMParticles.POISON,
                            6, 0.3, 0.02);
                }
                continue;
            }
            // 未中毒的玩家统计停留时间
            int dwell = SceneEventManager.reportDwell(serverLevel, player, CHANNEL);
            if (dwell >= DWELL_TICKS) {
                SceneParticles.burst(serverLevel, player.position().add(0, 1, 0), TMMParticles.POISON,
                        30, 0.5, 0.05);
                SceneEventManager.resetDwell(serverLevel, player, CHANNEL);
                // 施加 30 秒中毒（potionTicks），而非直接杀死
                poison.setPoisonTicks(POISON_TICKS, null);
            } else if (dwell % 20 == 0) {
                // 每秒一次轻微的中毒提示粒子
                SceneParticles.burst(serverLevel, player.position().add(0, 1, 0), TMMParticles.POISON,
                        4, 0.3, 0.02);
            }
        }
    }
}
