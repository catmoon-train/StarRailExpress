package org.agmas.noellesroles.role_data.killer;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** 白瞳：眼睛会自己亮一下，藏不住。 */
public class WhiteEyesRoleData extends SimpleRoleData {
    public static final int FLARE_INTERVAL = 12 * 20;
    public static final int FLARE_TICKS = 2 * 20;

    private long nextFlare;

    public WhiteEyesRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public void init() {
        if (player instanceof ServerPlayer sp && !sp.level().isClientSide()) {
            nextFlare = sp.serverLevel().getGameTime() + FLARE_INTERVAL;
        }
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer self) || !GameUtils.isPlayerAliveAndSurvival(self)) {
            return;
        }
        long now = self.serverLevel().getGameTime();
        if (nextFlare == 0L) {
            nextFlare = now + FLARE_INTERVAL;
        }
        if (now < nextFlare) {
            return;
        }
        nextFlare = now + FLARE_INTERVAL;
        self.addEffect(new MobEffectInstance(MobEffects.GLOWING, FLARE_TICKS, 0, false, false, true));
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putLong("flare", nextFlare);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        nextFlare = tag.getLong("flare");
    }
}
