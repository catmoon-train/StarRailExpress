package org.agmas.noellesroles.content.block_entity.scene;

import java.util.List;

import org.agmas.noellesroles.content.block.scene.IncineratorBlock;
import org.agmas.noellesroles.init.ModSceneBlocks;

import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 焚化炉方块实体：炉口冒火；将被击退（朝炉口高速）的邻近玩家吸入并烧死。
 */
public class IncineratorBlockEntity extends BlockEntity {

    /** 判定为"被击退"的最小朝向炉口速度。普通行走约 0.1，击退约 0.35~0.9。 */
    private static final double KNOCKBACK_THRESHOLD = 0.22;
    /** 上一次受到攻击的超时时间（tick），超过此时间则视为无伤害来源。 */
    private static final long LAST_HURT_TIMEOUT = 60;

    public IncineratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModSceneBlocks.INCINERATOR_ENTITY, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, IncineratorBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Direction facing = state.getValue(IncineratorBlock.FACING);
        BlockPos mouth = pos.relative(facing);
        Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

        // 炉口火焰
        serverLevel.sendParticles(ParticleTypes.FLAME,
                mouth.getX() + 0.5, mouth.getY() + 0.3, mouth.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.01);

        // 炉口内的玩家：烧死
        List<Player> inMouth = serverLevel.getEntitiesOfClass(Player.class, new AABB(mouth),
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator());
        for (Player p : inMouth) {
            p.setRemainingFireTicks(100);
            serverLevel.sendParticles(ParticleTypes.LAVA, p.getX(), p.getY() + 0.5, p.getZ(), 15, 0.3, 0.5, 0.3, 0.05);
            serverLevel.playSound(null, mouth, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 1.2F, 0.7F);

            // 追溯伤害来源：检测推入焚化炉的凶手
            Player killer = null;
            LivingEntity lastHurt = p.getLastHurtByMob();
            if (lastHurt instanceof Player lastHurtPlayer
                    && serverLevel.getGameTime() - p.getLastHurtByMobTimestamp() < LAST_HURT_TIMEOUT) {
                killer = lastHurtPlayer;
            }
            GameUtils.forceKillPlayer(p, true, killer, GameConstants.DeathReasons.INCINERATOR_PUSHED);
        }

        // 炉口前 2 格内、被击退（朝炉口高速）的玩家：吸入
        AABB front = new AABB(mouth).expandTowards(facing.getStepX(), 0, facing.getStepZ()).inflate(0.4, 0.2, 0.4);
        List<Player> nearby = serverLevel.getEntitiesOfClass(Player.class, front,
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator());
        for (Player p : nearby) {
            Vec3 toMouth = new Vec3(center.x - p.getX(), 0, center.z - p.getZ());
            if (toMouth.lengthSqr() < 1.0e-3) {
                continue;
            }
            Vec3 dir = toMouth.normalize();
            double inward = p.getDeltaMovement().x * dir.x + p.getDeltaMovement().z * dir.z;
            if (inward > KNOCKBACK_THRESHOLD) {
                p.setDeltaMovement(dir.x * 0.55, p.getDeltaMovement().y, dir.z * 0.55);
                p.hurtMarked = true;
            }
        }
    }
}
