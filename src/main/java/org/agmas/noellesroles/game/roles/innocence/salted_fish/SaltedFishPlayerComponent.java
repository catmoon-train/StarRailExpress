package org.agmas.noellesroles.game.roles.innocence.salted_fish;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class SaltedFishPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<SaltedFishPlayerComponent> KEY = ModComponents.SALTED_FISH;
    public static final ResourceLocation SKILL_ID = Noellesroles.id("salted_fish_sunbathe");

    public static final int ACTIVE_TICKS = 80 * 20;
    public static final int COOLDOWN_TICKS = 40 * 20;
    public static final int SIDE_INTERVAL_TICKS = 20 * 20;
    public static final int FLIP_TICKS = 20;

    private final Player player;
    public int activeTicks;
    public int cooldownTicks;
    public int flipTicks;
    public int side;
    public int previousSide;
    public float sunYaw;

    public SaltedFishPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer target) {
        return target.level() == player.level();
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public void init() {
        activeTicks = 0;
        cooldownTicks = 0;
        flipTicks = 0;
        side = 0;
        previousSide = 0;
        sunYaw = 0.0f;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    public boolean isActive() {
        return activeTicks > 0;
    }

    public boolean useSkill(ServerPlayer sp) {
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        if (!gameWorld.isRole(sp, ModRoles.SALTED_FISH)) {
            return false;
        }
        if (activeTicks > 0) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.salted_fish.already_active")
                    .withStyle(ChatFormatting.YELLOW), true);
            return false;
        }
        if (cooldownTicks > 0) {
            sp.displayClientMessage(Component.translatable("message.sre.skill.cooldown",
                    String.format("%.1f", cooldownTicks / 20.0F)).withStyle(ChatFormatting.RED), true);
            return false;
        }

        activeTicks = ACTIVE_TICKS;
        flipTicks = 0;
        side = 0;
        previousSide = 0;
        updateSunYaw(sp.serverLevel());
        faceSun(sp, true);
        applyRestraints();
        sync();
        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.0f, 0.75f);
        sp.displayClientMessage(Component.translatable("message.noellesroles.salted_fish.start")
                .withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        if (!gameWorld.isRunning() || !gameWorld.isRole(sp, ModRoles.SALTED_FISH)
                || !GameUtils.isPlayerAliveAndSurvival(sp)) {
            if (activeTicks > 0) {
                activeTicks = 0;
                flipTicks = 0;
                sync();
            }
            tickCooldown();
            return;
        }

        if (activeTicks > 0) {
            tickActive(sp);
        } else {
            tickCooldown();
        }
    }

    private void tickActive(ServerPlayer sp) {
        int elapsed = ACTIVE_TICKS - activeTicks;
        if (elapsed > 0 && elapsed % SIDE_INTERVAL_TICKS == 0) {
            startFlip(sp);
        }

        updateSunYaw(sp.serverLevel());
        faceSun(sp, elapsed % 10 == 0);
        applyRestraints();
        stopHorizontalMotion(sp);

        activeTicks--;
        if (flipTicks > 0) {
            flipTicks--;
        }

        if (activeTicks <= 0) {
            finishActive(sp);
            return;
        }

        if (elapsed % 20 == 0 || flipTicks == FLIP_TICKS - 1 || flipTicks == 0) {
            sync();
        }
    }

    private void tickCooldown() {
        if (cooldownTicks <= 0) {
            return;
        }
        cooldownTicks--;
        if (cooldownTicks == 0 || cooldownTicks % 20 == 0) {
            sync();
        }
    }

    private void startFlip(ServerPlayer sp) {
        previousSide = side;
        side = 1 - side;
        flipTicks = FLIP_TICKS;
        Vec3 motion = new Vec3(0.0D, 0.42D, 0.0D);
        sp.setDeltaMovement(motion);
        sp.hurtMarked = true;
        sp.connection.send(new ClientboundSetEntityMotionPacket(sp.getId(), motion));
        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.SLIME_BLOCK_STEP, SoundSource.PLAYERS, 1.0f,
                1.35f);
    }

    private void finishActive(ServerPlayer sp) {
        activeTicks = 0;
        flipTicks = 0;
        cooldownTicks = COOLDOWN_TICKS;
        stopHorizontalMotion(sp);
        SREAbilityPlayerComponent.KEY.get(sp).setSkillCooldown(SKILL_ID, COOLDOWN_TICKS);
        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 0.8f, 0.9f);
        sp.displayClientMessage(Component.translatable("message.noellesroles.salted_fish.end")
                .withStyle(ChatFormatting.AQUA), true);
        sync();
    }

    private void applyRestraints() {
        player.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, 10, 0, false, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.TURN_BANED, 10, 0, false, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.USED_BANED, 10, 0, false, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.INVENTORY_BANED, 10, 0, false, false, false));
    }

    private void stopHorizontalMotion(ServerPlayer sp) {
        Vec3 current = sp.getDeltaMovement();
        Vec3 stopped = new Vec3(0.0D, current.y, 0.0D);
        sp.setDeltaMovement(stopped);
        sp.hurtMarked = true;
    }

    private void updateSunYaw(ServerLevel level) {
        long time = Math.floorMod(level.getDayTime(), 24000L);
        sunYaw = Mth.wrapDegrees((time / 24000.0f) * 360.0f - 90.0f);
    }

    private void faceSun(ServerPlayer sp, boolean forcePacket) {
        sp.setYRot(sunYaw);
        sp.setYHeadRot(sunYaw);
        sp.setYBodyRot(sunYaw);
        sp.setXRot(-80.0f);
        if (forcePacket) {
            sp.connection.teleport(sp.getX(), sp.getY(), sp.getZ(), sunYaw, -80.0f);
        }
    }

    public float getRenderRoll(float partialTick) {
        float from = sideToRoll(previousSide);
        float to = sideToRoll(side);
        if (flipTicks <= 0) {
            return to;
        }
        if (previousSide == 0 && side == 1) {
            to += 360.0f;
        } else if (previousSide == 1 && side == 0) {
            from += 360.0f;
            to += 360.0f;
        }
        float progress = Mth.clamp((FLIP_TICKS - flipTicks + partialTick) / (float) FLIP_TICKS, 0.0f, 1.0f);
        return Mth.lerp(progress, from, to);
    }

    public float getRenderBounce(float partialTick) {
        if (flipTicks <= 0) {
            return 0.0f;
        }
        float progress = Mth.clamp((FLIP_TICKS - flipTicks + partialTick) / (float) FLIP_TICKS, 0.0f, 1.0f);
        return Mth.sin(progress * Mth.PI) * 0.45f;
    }

    private static float sideToRoll(int side) {
        return side == 0 ? 90.0f : -90.0f;
    }

    @Override
    public void clientTick() {
        if (activeTicks > 0) {
            activeTicks--;
        }
        if (cooldownTicks > 0) {
            cooldownTicks--;
        }
        if (flipTicks > 0) {
            flipTicks--;
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("activeTicks", activeTicks);
        tag.putInt("cooldownTicks", cooldownTicks);
        tag.putInt("flipTicks", flipTicks);
        tag.putInt("side", side);
        tag.putInt("previousSide", previousSide);
        tag.putFloat("sunYaw", sunYaw);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        activeTicks = tag.getInt("activeTicks");
        cooldownTicks = tag.getInt("cooldownTicks");
        flipTicks = tag.getInt("flipTicks");
        side = tag.getInt("side");
        previousSide = tag.getInt("previousSide");
        sunYaw = tag.getFloat("sunYaw");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
