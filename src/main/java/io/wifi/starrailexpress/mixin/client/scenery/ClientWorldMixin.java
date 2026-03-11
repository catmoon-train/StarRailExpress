package io.wifi.starrailexpress.mixin.client.scenery;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.index.TMMBlocks;
import io.wifi.starrailexpress.index.TMMParticles;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.Vec3;

@Mixin(ClientLevel.class)
public abstract class ClientWorldMixin extends Level {
    protected ClientWorldMixin(WritableLevelData properties, ResourceKey<Level> registryRef, RegistryAccess registryManager, Holder<DimensionType> dimensionEntry, Supplier<ProfilerFiller> profiler, boolean isClient, boolean debugWorld, long biomeAccess, int maxChainedNeighborUpdates) {
        super(properties, registryRef, registryManager, dimensionEntry, profiler, isClient, debugWorld, biomeAccess, maxChainedNeighborUpdates);
    }

    @Shadow
    public abstract void addParticle(ParticleOptions parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ);

    @Shadow
    @Final
    private Minecraft minecraft;

    @Final
    @Shadow
    @Mutable
    private static Set<Item> MARKER_PARTICLE_ITEMS;

    @Inject(method = "<init>", at = @At("TAIL"))
    public void tmm$addCustomBlockMarkers(ClientPacketListener networkHandler, ClientLevel.ClientLevelData properties, ResourceKey registryRef, Holder dimensionTypeEntry, int loadDistance, int simulationDistance, Supplier profiler, LevelRenderer worldRenderer, boolean debugWorld, long seed, CallbackInfo ci) {
        MARKER_PARTICLE_ITEMS = new HashSet<>(MARKER_PARTICLE_ITEMS);
        MARKER_PARTICLE_ITEMS.add(TMMBlocks.BARRIER_PANEL.asItem());
        MARKER_PARTICLE_ITEMS.add(TMMBlocks.LIGHT_BARRIER.asItem());
    }

    @Inject(method = "tick", at = @At("TAIL"))
    public void tmm$addSnowflakes(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        if (SREClient.isTrainMoving() && SREClient.getTrainComponent().isSnowing()) {
            LocalPlayer player = minecraft.player;
            RandomSource random = player.getRandom();
            for (int i = 0; i < 200; i++) {
                Vec3 playerVel = player.getKnownMovement();
                Vec3 pos = new Vec3(player.getX() - 20f + random.nextFloat() + playerVel.x(), player.getY() + (random.nextFloat() * 2 - 1) * 10f + playerVel.y(), player.getZ() + (random.nextFloat() * 2 - 1) * 10f + playerVel.z());
                if (this.minecraft.level.canSeeSky(BlockPos.containing(pos))) {
                    this.addParticle(TMMParticles.SNOWFLAKE, pos.x(), pos.y(), pos.z(), 2 + playerVel.x(), playerVel.y(), playerVel.z());
                }
            }
        }
    }
}
