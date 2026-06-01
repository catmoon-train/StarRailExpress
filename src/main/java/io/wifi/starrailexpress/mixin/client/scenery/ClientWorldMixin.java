package io.wifi.starrailexpress.mixin.client.scenery;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.index.TMMBlocks;
import io.wifi.starrailexpress.index.TMMParticles;
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

    // 雪花效果性能优化：节流计数器
    private static int sre_snowFrameCount = 0;
    private static final int SRE_SNOW_UPDATE_INTERVAL = 3; // 每3tick更新一次（约50ms）
    private static final int SRE_SNOW_PARTICLES_PER_TICK = 50; // 减少粒子数量

    @Inject(method = "<init>", at = @At("TAIL"))
    public void tmm$addCustomBlockMarkers(ClientPacketListener networkHandler, ClientLevel.ClientLevelData properties, ResourceKey registryRef, Holder dimensionTypeEntry, int loadDistance, int simulationDistance, Supplier profiler, LevelRenderer worldRenderer, boolean debugWorld, long seed, CallbackInfo ci) {
        MARKER_PARTICLE_ITEMS = new HashSet<>(MARKER_PARTICLE_ITEMS);
        MARKER_PARTICLE_ITEMS.add(TMMBlocks.BARRIER_PANEL.asItem());
        MARKER_PARTICLE_ITEMS.add(TMMBlocks.LIGHT_BARRIER.asItem());
        // 手持实体交互方块/镶板时显示屏障粒子
        MARKER_PARTICLE_ITEMS.add(TMMBlocks.ENTITY_INTERACTION_BLOCK.asItem());
        MARKER_PARTICLE_ITEMS.add(TMMBlocks.ENTITY_INTERACTION_PANEL.asItem());
    }

    @Inject(method = "tick", at = @At("TAIL"))
    public void tmm$addSnowflakes(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        // 第1级过滤：快速检查所有条件（零开销）
        // 雪花效果依赖列车移动、下雪启用和地图配置
        if (!SREClient.isTrainMoving() || 
            !SREClient.getTrainComponent().isSnowing() || 
            SREClient.areaComponent == null || 
            !SREClient.areaComponent.snowEnabled) {
            return;
        }
        
        // 第2级过滤：节流机制，每3tick更新一次（降低CPU负载66%）
        sre_snowFrameCount++;
        if (sre_snowFrameCount % SRE_SNOW_UPDATE_INTERVAL != 0) {
            return;
        }
        
        LocalPlayer player = minecraft.player;
        if (player == null) return;
        
        RandomSource random = player.getRandom();
        Vec3 playerVel = player.getKnownMovement();
        
        // 预计算玩家位置（避免重复调用）
        double playerX = player.getX();
        double playerY = player.getY();
        double playerZ = player.getZ();
        
        // 性能优化：减少粒子数量从200降到50
        for (int i = 0; i < SRE_SNOW_PARTICLES_PER_TICK; i++) {
            // 使用局部变量减少对象创建
            float randX = random.nextFloat();
            float randY = random.nextFloat();
            float randZ = random.nextFloat();
            
            // 计算粒子位置（内联计算，避免创建中间Vec3对象）
            double posX = playerX - 20f + randX + playerVel.x();
            double posY = playerY + (randY * 2 - 1) * 10f + playerVel.y();
            double posZ = playerZ + (randZ * 2 - 1) * 10f + playerVel.z();
            
            // 性能优化：只在部分粒子上检查天空可见性（降低75%的canSeeSky调用）
            boolean shouldCheckSky = (i % 4 == 0);
            
            if (!shouldCheckSky || this.minecraft.level.canSeeSky(BlockPos.containing(posX, posY, posZ))) {
                // 添加雪花粒子
                this.addParticle(
                    TMMParticles.SNOWFLAKE, 
                    posX, posY, posZ, 
                    2 + playerVel.x(), 
                    playerVel.y(), 
                    playerVel.z()
                );
            }
        }
    }
}
