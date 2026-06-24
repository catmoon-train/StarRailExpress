package org.agmas.noellesroles.content.entity;

import java.util.Comparator;
import java.util.Optional;

import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class MummyEntity extends Husk {
    private static final int MAX_AGE = 15 * 20;
    private static final double HOME_RADIUS = 4.0D;

    private BlockPos home = BlockPos.ZERO;
    private int age;

    public MummyEntity(EntityType<? extends Husk> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public boolean shouldDespawnInPeaceful() {
        return false;
    }

    public void setHome(BlockPos home) {
        this.home = home.immutable();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        age++;
        if (age > MAX_AGE || distanceToHomeSqr() > (HOME_RADIUS + 1.0D) * (HOME_RADIUS + 1.0D)) {
            discard();
            return;
        }

        Optional<ServerPlayer> target = serverLevel.players().stream()
                .filter(p -> p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE)
                .filter(GameUtils::isPlayerAliveAndSurvival)
                .filter(p -> p.distanceToSqr(Vec3.atCenterOf(home)) <= HOME_RADIUS * HOME_RADIUS)
                .min(Comparator.comparingDouble(this::distanceToSqr));
        if (target.isPresent()) {
            getNavigation().moveTo(target.get(), 1.05D);
            setTarget(target.get());
        } else {
            setTarget(null);
            getNavigation().stop();
        }

        AABB touchBox = getBoundingBox().inflate(0.18D);
        for (ServerPlayer player : serverLevel.getEntitiesOfClass(ServerPlayer.class, touchBox,
                p -> p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE && GameUtils.isPlayerAliveAndSurvival(p))) {
            GameUtils.forceKillPlayer(player, true, null, GameConstants.DeathReasons.ANCIENT_BITE);
        }
    }

    private double distanceToHomeSqr() {
        return Vec3.atCenterOf(home).distanceToSqr(position());
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("HomeX", home.getX());
        tag.putInt("HomeY", home.getY());
        tag.putInt("HomeZ", home.getZ());
        tag.putInt("Age", age);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        home = new BlockPos(tag.getInt("HomeX"), tag.getInt("HomeY"), tag.getInt("HomeZ"));
        age = tag.getInt("Age");
    }
}
