package org.agmas.noellesroles.role_data.killer;

import org.agmas.noellesroles.game.command_block.FogGuestRole;
import org.agmas.noellesroles.game.command_block.LegendFx;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** 雾客：藏雾期间不能出手。近处有人看着你，雾就碎。 */
public class FogGuestRoleData extends SimpleRoleData {
    private boolean hidden;
    private long hiddenUntil;

    public FogGuestRoleData(RoleDataContext context) {
        super(context);
    }

    public boolean isHidden() {
        return hidden && player.level().getGameTime() < hiddenUntil;
    }

    public boolean hide(long now) {
        hidden = true;
        hiddenUntil = now + FogGuestRole.HIDE_TICKS;
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, FogGuestRole.HIDE_TICKS, 0, false, false, true));
        if (player.level() instanceof ServerLevel level) {
            LegendFx.ring(level, player.position(), 1.6F, LegendFx.FOG);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMBIENT_CAVE.value(),
                SoundSource.PLAYERS, 0.6F, 0.5F);
        if (player instanceof ServerPlayer self) {
            self.displayClientMessage(Component.translatable("message.noellesroles.fog_guest.hidden")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        return true;
    }

    @Override
    public void serverTick() {
        if (!hidden || !(player instanceof ServerPlayer self)) {
            return;
        }
        long now = self.level().getGameTime();
        if (now >= hiddenUntil || !GameUtils.isPlayerAliveAndSurvival(self)) {
            clearHide();
            return;
        }
        for (ServerPlayer other : self.serverLevel().players()) {
            if (other == self || !GameUtils.isPlayerAliveAndSurvival(other)) {
                continue;
            }
            if (FogGuestRole.watched(other, self)) {
                shatter(other);
                return;
            }
        }
    }

    private void shatter(ServerPlayer watcher) {
        clearHide();
        player.removeEffect(MobEffects.INVISIBILITY);
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 0));
        if (player.level() instanceof ServerLevel level) {
            LegendFx.ring(level, player.position(), 1.2F, LegendFx.FOG);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GLASS_BREAK,
                SoundSource.PLAYERS, 0.7F, 1.4F);
        if (player instanceof ServerPlayer self) {
            self.displayClientMessage(Component.translatable("message.noellesroles.fog_guest.shattered",
                    watcher.getDisplayName()).withStyle(ChatFormatting.WHITE), true);
        }
    }

    private void clearHide() {
        hidden = false;
        hiddenUntil = 0;
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putBoolean("Hidden", hidden);
        tag.putLong("HiddenUntil", hiddenUntil);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        hidden = tag.getBoolean("Hidden");
        hiddenUntil = tag.getLong("HiddenUntil");
    }
}
