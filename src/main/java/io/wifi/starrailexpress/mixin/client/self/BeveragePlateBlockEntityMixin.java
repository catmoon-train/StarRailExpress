package io.wifi.starrailexpress.mixin.client.self;

import io.wifi.starrailexpress.block_entity.BeveragePlateBlockEntity;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.event.CanSeePoison;
import io.wifi.starrailexpress.index.TMMParticles;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author SkyNotTheLimit
 */
@Environment(EnvType.CLIENT)
@Mixin(BeveragePlateBlockEntity.class)
public class BeveragePlateBlockEntityMixin {

    // haha I love writing extremely cursed mixins
    @Inject(method = "clientTick", at = @At("HEAD"))
    private static void tickWithoutFearOfCrashing(Level world, BlockPos pos, BlockState state, BlockEntity blockEntity,
            CallbackInfo ci) {
        if (!(blockEntity instanceof BeveragePlateBlockEntity tray)) {
            return;
        }
        if ((!SREClient.isKiller() && !CanSeePoison.EVENT.invoker().visible(Minecraft.getInstance().player))
                || tray.getPoisoner() == null || tray.getArmorer() == null) {
            return;
        }
        if (world.getRandom().nextIntBetweenInclusive(0, 20) < 17) {
            return;
        }
        if (tray.getArmorer() != null) {
            world.addParticle(
                    ParticleTypes.EFFECT,
                    pos.getX() + 0.5f,
                    pos.getY(),
                    pos.getZ() + 0.5f,
                    0f, 0.05f, 0f);
        }
        if (tray.getPoisoner() != null) {
            world.addParticle(
                    TMMParticles.POISON,
                    pos.getX() + 0.5f,
                    pos.getY(),
                    pos.getZ() + 0.5f,
                    0f, 0.05f, 0f);
        }
    }
}
