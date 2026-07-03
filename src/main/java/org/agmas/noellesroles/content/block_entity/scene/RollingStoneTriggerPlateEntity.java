package org.agmas.noellesroles.content.block_entity.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.content.block.scene.RollingStoneTriggerPlate;
import org.agmas.noellesroles.init.ModSceneBlocks;
import org.agmas.noellesroles.scene.SceneEventManager;

import java.util.List;

/**
 * 滚石触发板方块实体：踩踏触发冷却 + 玩家检测 + 破坏任务激活时周期性召唤滚石。
 */
public class RollingStoneTriggerPlateEntity extends BlockEntity {

    /** 踩踏触发冷却。 */
    public static final int STEP_COOLDOWN = 40;
    /** 破坏任务期间召唤间隔。 */
    public static final int SABOTAGE_INTERVAL = 100;

    private long lastTrigger = 0L;

    public RollingStoneTriggerPlateEntity(BlockPos pos, BlockState state) {
        super(ModSceneBlocks.ROLLING_STONE_TRIGGER_ENTITY, pos, state);
    }

    /** 踩踏尝试触发，受冷却限制。 */
    public boolean tryTrigger(ServerLevel level) {
        long now = level.getGameTime();
        if (now - lastTrigger < STEP_COOLDOWN) {
            return false;
        }
        lastTrigger = now;
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RollingStoneTriggerPlateEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        long now = serverLevel.getGameTime();
        if (now - be.lastTrigger >= STEP_COOLDOWN && now % 10 == 0) {
            // expandTowards(0, 1, 0) 向上扩展 1 格以检测站在板上的玩家
            List<Player> playersOnPlate = serverLevel.getEntitiesOfClass(Player.class,
                    new AABB(pos).expandTowards(0, 1, 0).inflate(0.2),
                    p -> p.isAlive() && !p.isSpectator());
            if (!playersOnPlate.isEmpty() && be.tryTrigger(serverLevel)) {
                RollingStoneTriggerPlate.spawnStone(serverLevel, pos, state.getValue(RollingStoneTriggerPlate.FACING));
            }
        }
        // 破坏任务期间周期性召唤
        if (SceneEventManager.isSabotageActive(serverLevel)
                && now % SABOTAGE_INTERVAL == 0) {
            RollingStoneTriggerPlate.spawnStone(serverLevel, pos, state.getValue(RollingStoneTriggerPlate.FACING));
        }
    }
}
