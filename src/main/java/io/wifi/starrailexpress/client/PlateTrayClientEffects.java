package io.wifi.starrailexpress.client;

import io.wifi.starrailexpress.content.block_entity.PlateTrayBlockEntity;
import io.wifi.starrailexpress.event.CanSeePoison;
import io.wifi.starrailexpress.event.ItemGameplayEvents;
import io.wifi.starrailexpress.index.TMMParticles;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;

/** 托盘默认粒子监听器；职业覆盖由更早的事件阶段决定。 */
@Environment(EnvType.CLIENT)
public final class PlateTrayClientEffects {
    private PlateTrayClientEffects() {
    }

    public static void register() {
        ItemGameplayEvents.PLATE_TRAY_CLIENT_TICK.register((world, pos, state, tray) -> {
            renderBeverageParticles(world, pos, tray);
            return false;
        });
    }

    private static void renderBeverageParticles(Level world, BlockPos pos, PlateTrayBlockEntity tray) {
        // 保留旧判断：必须同时有毒和护盾，而不是任意一项存在就显示。
        boolean hasShield = tray.getArmorer() != null || tray.getWeakArmorer() != null
                || tray.getVirtualArmorer() != null;
        if ((!SREClient.isKiller() && !CanSeePoison.EVENT.invoker().visible(Minecraft.getInstance().player))
                || tray.getPoisoner() == null || !hasShield) {
            return;
        }
        if (world.getRandom().nextIntBetweenInclusive(0, 20) < 17) {
            return;
        }
        if (hasShield) {
            world.addParticle(ParticleTypes.EFFECT, pos.getX() + 0.5f, pos.getY(), pos.getZ() + 0.5f,
                    0f, 0.05f, 0f);
        }
        if (tray.getPoisoner() != null) {
            world.addParticle(TMMParticles.POISON, pos.getX() + 0.5f, pos.getY(), pos.getZ() + 0.5f,
                    0f, 0.05f, 0f);
        }
    }
}
