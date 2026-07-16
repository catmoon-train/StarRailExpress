package net.exmo.sre.sixtyseconds.content.block_entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 哨戒炮方块实体：本身不存数据（射击结算在 {@code SixtySecondsPveSystem}，纯服务端），
 * 只为客户端 BER（{@code SixtySecondsTurretRenderer}）提供渲染状态缓存——
 * 炮头当前朝向/目标朝向/上次扫描时间都存在这里，由渲染器每帧读写（仅客户端使用）。
 */
public class SixtySecondsTurretBlockEntity extends BlockEntity {

    // ── 以下字段仅客户端渲染器读写（不落盘、不同步）────────────────────
    /** 炮头当前渲染朝向（度）。 */
    public float clientYaw;
    /** 目标朝向（度），渲染器逐帧向它平滑逼近。 */
    public float clientTargetYaw;
    /** 上次目标扫描的 gameTime（每 10 tick 扫一次附近的怪）。 */
    public long clientLastScan = Long.MIN_VALUE;
    /** 当前是否锁定了目标（决定炮头是追踪还是慢速巡逻旋转）。 */
    public boolean clientHasTarget;
    /** 锁定中的目标缓存（帧间读实时位置，扫描间隔内不换目标）。 */
    public net.minecraft.world.entity.LivingEntity clientTarget;

    public SixtySecondsTurretBlockEntity(BlockPos pos, BlockState state) {
        super(org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_TURRET_ENTITY, pos, state);
    }
}
