package net.exmo.sre.sixtyseconds.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * 被雇佣的军人跟随雇主（仿原版 {@code FollowOwnerGoal}，去掉传送/坐下那套狼的逻辑）：
 * 距离 &gt; {@code startDistance} 开始跟，走到 &le; {@code stopDistance} 停。
 * 仅在 {@link SixtySecondsNpcEntity#isHired()} 且雇主在线同世界时启用。
 */
public class NpcFollowPlayerGoal extends Goal {
    private final SixtySecondsNpcEntity npc;
    private final PathNavigation navigation;
    private final double speedModifier;
    private final float startDistanceSqr;
    private final float stopDistanceSqr;
    private Player owner;
    /** 重新寻路节流（每 10 tick 一次，别每 tick 重算路径）。 */
    private int repathCooldown;

    public NpcFollowPlayerGoal(SixtySecondsNpcEntity npc, double speedModifier,
            float stopDistance, float startDistance) {
        this.npc = npc;
        this.navigation = npc.getNavigation();
        this.speedModifier = speedModifier;
        this.stopDistanceSqr = stopDistance * stopDistance;
        this.startDistanceSqr = startDistance * startDistance;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!npc.isHired()) {
            return false;
        }
        Player employer = employer();
        if (employer == null || employer.isSpectator()) {
            return false;
        }
        // 已经在打架就别跟了，让 MeleeAttackGoal 接管
        if (npc.getTarget() != null && npc.getTarget().isAlive()) {
            return false;
        }
        if (npc.distanceToSqr(employer) < startDistanceSqr) {
            return false;
        }
        this.owner = employer;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (!npc.isHired() || owner == null || navigation.isDone()) {
            return false;
        }
        LivingEntity target = npc.getTarget();
        if (target != null && target.isAlive()) {
            return false;
        }
        return npc.distanceToSqr(owner) > stopDistanceSqr;
    }

    @Override
    public void start() {
        repathCooldown = 0;
    }

    @Override
    public void stop() {
        owner = null;
        navigation.stop();
    }

    @Override
    public void tick() {
        if (owner == null) {
            return;
        }
        npc.getLookControl().setLookAt(owner, 10.0F, npc.getMaxHeadXRot());
        if (--repathCooldown > 0) {
            return;
        }
        repathCooldown = 10;
        navigation.moveTo(owner, speedModifier);
    }

    /** 雇主必须与 NPC 同世界（跨维度的雇佣直接判失效，交给 tick 里的到期逻辑收尾）。 */
    private Player employer() {
        java.util.UUID id = npc.getHiredBy();
        if (id == null) {
            return null;
        }
        Player player = npc.level().getPlayerByUUID(id);
        return player != null && player.isAlive() ? player : null;
    }
}
