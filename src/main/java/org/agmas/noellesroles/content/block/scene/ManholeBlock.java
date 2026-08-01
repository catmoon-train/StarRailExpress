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

package org.agmas.noellesroles.content.block.scene;

import com.mojang.serialization.MapCodec;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SRERoleWorldComponent;
import io.wifi.starrailexpress.content.block.api.TaskInstinctShowableInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.agmas.noellesroles.content.block_entity.scene.ManholeBlockEntity;
import org.agmas.noellesroles.init.ModSceneBlocks;
import org.agmas.noellesroles.scene.ManholeRegistry;
import org.agmas.noellesroles.scene.SceneRoleAccess;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 井盖：仅中立/杀手（或特定职业）可使用。右键沿视线方向传送到另一个井盖处出来。
 * 中立/杀手的任务透视可看到全图井盖。在井盖上停留超过 10 秒会窒息死亡。
 * 离开井盖后1分钟内无法再次进入。
 * 使用原版铁块贴图作为井盖外观。
 */
public class ManholeBlock extends BaseEntityBlock implements TaskInstinctShowableInterface {

    public static final int TASK_INSTINCT_ID = 25;
    /** 传送的最大水平距离。 */
    public static final double TRAVEL_RANGE = 48.0;
    /** 离开井盖后的冷却时间（1分钟） */
    private static final long MANHOLE_COOLDOWN_TICKS = 60 * 20;
    private static final Map<UUID, Long> manholeCooldownUntil = new HashMap<>();
    /** 右键确认窗口：2 秒（40 tick），超时需重新右键井盖 */
    private static final long MANHOLE_CONFIRM_TICKS = 40;
    /** 右键井盖后等待按“祷告/加入塔罗会”键传送的待定目标（含过期时间） */
    private static final Map<UUID, PendingManholeTeleport> pendingManholeTarget = new HashMap<>();

    /** 待传送目标：目标井盖坐标 + 过期游戏时刻 */
    private record PendingManholeTeleport(BlockPos target, long expireTick) {
    }

    /** 活板门碰撞箱：厚度 3 像素，大小与原版活板门一致 */
    private static final VoxelShape TRAPDOOR_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 3.0, 16.0);

    public ManholeBlock(Properties settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return null;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return TRAPDOOR_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return TRAPDOOR_SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.CONSUME;
        }
        // 所有玩家右键井盖统一播放音效
        serverLevel.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6F, 0.7F);
        var role = SceneRoleAccess.roleOf(player);
        boolean canUse = SceneRoleAccess.canEnterRestricted(player, null)
                || (role != null && role.canJumpManhole());
        // 所有玩家右键井盖都不会直接传送。不能使用井盖的玩家：无任何提示、无反应。
        if (!canUse) {
            return InteractionResult.CONSUME;
        }
        // 以本次右键为准：清除旧的待传送目标
        pendingManholeTarget.remove(player.getUUID());

        // 检查离开井盖后的冷却时间（游戏未开始时不检查冷却）
        boolean gameRunning = SREGameWorldComponent.KEY.get(serverLevel).isRunning();
        if (gameRunning) {
            Long cooldownUntil = manholeCooldownUntil.get(player.getUUID());
            if (cooldownUntil != null && serverLevel.getGameTime() < cooldownUntil) {
                long remainingSec = (cooldownUntil - serverLevel.getGameTime()) / 20;
                sp.displayClientMessage(Component.translatable("message.noellesroles.manhole.cooldown", remainingSec),
                        true);
                return InteractionResult.CONSUME;
            }
            if (cooldownUntil != null) {
                manholeCooldownUntil.remove(player.getUUID());
            }
        }
        BlockPos target = ManholeRegistry.findInLookDirection(serverLevel, player, pos, TRAVEL_RANGE);
        if (target == null) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.manhole.no_exit"), true);
            return InteractionResult.CONSUME;
        }

        // 记录待传送目标（含 2 秒确认窗口），等待玩家按下“祷告/加入塔罗会”键后才真正传送
        pendingManholeTarget.put(player.getUUID(),
                new PendingManholeTeleport(target.immutable(), serverLevel.getGameTime() + MANHOLE_CONFIRM_TICKS));
        // 用 actionbar 提示玩家按对应按键（%s 由代码传入具体按键名）
        sp.displayClientMessage(Component.translatable("message.noellesroles.manhole.press_pray",
                Component.keybind("key.noellesroles.fool_prayer")), true);
        return InteractionResult.CONSUME;
    }

    /**
     * 取出并移除玩家的待传送目标（按“祷告/加入塔罗会”键时调用）。
     * 若已超过 2 秒确认窗口则视为失效，返回 null（需重新右键井盖确认）。
     *
     * @return 目标井盖坐标，没有或已超时则返回 null
     */
    public static BlockPos consumePendingTarget(Player player) {
        PendingManholeTeleport pending = pendingManholeTarget.remove(player.getUUID());
        if (pending == null) {
            return null;
        }
        if (player.level().getGameTime() > pending.expireTick()) {
            return null;
        }
        return pending.target();
    }

    /**
     * 执行井盖传送（在玩家按下“祷告/加入塔罗会”键后调用）。
     * 会再次校验权限与目标是否仍然存在，并设置离开冷却。
     */
    public static void doTeleport(ServerPlayer player, BlockPos target, ServerLevel serverLevel) {
        // 权限二次校验（防止右键后权限变化）
        var role = SceneRoleAccess.roleOf(player);
        if (!SceneRoleAccess.canEnterRestricted(player, null)
                && (role == null || !role.canJumpManhole())) {
            return;
        }
        if (target == null || !ManholeRegistry.all(serverLevel).contains(target)) {
            player.displayClientMessage(Component.translatable("message.noellesroles.manhole.no_exit"), true);
            return;
        }
        // 玩家 2 格内必须仍有井盖方块，防止离开井盖后再按按键传送
        if (!isManholeWithin2Blocks(serverLevel, player.blockPosition())) {
            player.displayClientMessage(Component.translatable("message.noellesroles.manhole.not_near"), true);
            return;
        }

        // 设置冷却时间（离开井盖后1分钟无法再次进入，游戏未开始时不设置）
        boolean gameRunning = SREGameWorldComponent.KEY.get(serverLevel).isRunning();
        if (gameRunning) {
            Long cooldownUntil = manholeCooldownUntil.get(player.getUUID());
            if (cooldownUntil != null && serverLevel.getGameTime() < cooldownUntil) {
                long remainingSec = (cooldownUntil - serverLevel.getGameTime()) / 20;
                player.displayClientMessage(Component.translatable("message.noellesroles.manhole.cooldown", remainingSec),
                        true);
                return;
            }
            manholeCooldownUntil.remove(player.getUUID());
            manholeCooldownUntil.put(player.getUUID(), serverLevel.getGameTime() + MANHOLE_COOLDOWN_TICKS);
        }

        // 起点特效（以玩家当前位置为准）
        double sx = player.getX();
        double sy = player.getY() + 1.0;
        double sz = player.getZ();
        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, sx, sy, sz, 20, 0.3, 0.3, 0.3, 0.02);
        serverLevel.playSound(null, player.blockPosition(), SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE,
                SoundSource.BLOCKS, 0.9F, 1.2F);

        // 传送
        double tx = target.getX() + 0.5;
        double ty = target.getY() + 1.0;
        double tz = target.getZ() + 0.5;
        player.teleportTo(tx, ty, tz);
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 12, 0, false, false, false));

        // 终点特效
        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, tx, ty, tz, 20, 0.3, 0.3, 0.3, 0.02);
        serverLevel.playSound(null, target, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, SoundSource.BLOCKS, 0.9F, 0.9F);
    }

    /**
     * 判断指定位置 2 格（含）范围内是否存在井盖方块。
     */
    private static boolean isManholeWithin2Blocks(ServerLevel level, BlockPos center) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (level.getBlockState(center.offset(dx, dy, dz)).getBlock() instanceof ManholeBlock) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ManholeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state,
            BlockEntityType<T> type) {
        if (world.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModSceneBlocks.MANHOLE_ENTITY,
                (lvl, pos, s, be) -> ManholeBlockEntity.serverTick(lvl, pos, s, be));
    }

    // ── 任务透视：中立/杀手可见全图井盖 ──

    @Override
    public int taskInstinctId() {
        return TASK_INSTINCT_ID;
    }

    @Override
    public boolean shouldRenderTaskInstinct(Level level, BlockState state, BlockPos pos, Player player) {
        SRERole role = SRERoleWorldComponent.KEY.get(player.level()).getRole(player);
        if (role == null) {
            return false;
        }
        // 中立/杀手阵营可见；可跳井盖的职业（含平民阵营中的此类职业）也可透视；
        // 其余纯平民玩家看不到井盖任务点透视。
        return SceneRoleAccess.canEnterRestricted(player, null) || role.canJumpManhole();
    }

    @Override
    public Color taskInstinctRenderColor(BlockState state, BlockPos pos, Player player) {
        return new Color(0x35C7D6);
    }
}
