package org.agmas.noellesroles.game.roles.killer.wraith_assassin;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.AllowPlayerOpenLockedDoor;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.content.item.InferiorLockpickItem;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class WraithAssassinPlayerComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<WraithAssassinPlayerComponent> KEY = ModComponents.WRAITH_ASSASSIN;

    public static final int LOW_SAN_BLUE = 10;
    public static final int LOW_SAN_YELLOW = 30;
    public static final int ASSAULT_COST = 30;
    public static final int WAIL_COST = 100;
    public static final int MANIFEST_COST = 320;
    public static final int MANIFEST_TICKS = 15 * 20;
    public static final int PANIC_TICKS = 4 * 20;
    public static final ResourceLocation DEATH_REASON = Noellesroles.id("wraith_assault");

    private final Player player;
    public int energy;
    public int manifestTicks;
    private int heartbeatTimer;

    public WraithAssassinPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer target) {
        return this.player == target;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void init() {
        energy = 0;
        manifestTicks = 0;
        heartbeatTimer = 0;
        sync();
    }

    @Override
    public void clear() {
        removeWraithEffects();
        init();
    }

    public boolean isManifested() {
        return manifestTicks > 0;
    }

    public boolean isInDimension() {
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(player.level());
        return gw != null && gw.isRole(player, ModRoles.WRAITH_ASSASSIN) && !isManifested();
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(player.level());
        if (gw == null || !gw.isRunning() || !gw.isRole(player, ModRoles.WRAITH_ASSASSIN)) {
            removeWraithEffects();
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            removeWraithEffects();
            return;
        }

        if (manifestTicks > 0) {
            manifestTicks--;
            applyManifestEffects();
            if (manifestTicks == 0) {
                sync();
            }
        } else {
            applyDimensionEffects();
        }
        applyLowSanNearbyPressure(sp);
        heartbeatTimer++;
    }

    private void applyDimensionEffects() {
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, true, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.WRAITH_DIMENSION, 60, 0, true, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.NO_COLLIDE, 60, 0, true, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.FOOTSTEP_VANISH, 60, 0, true, false, false));
        if (player.hasEffect(ModEffects.WRAITH_MANIFEST)) {
            player.removeEffect(ModEffects.WRAITH_MANIFEST);
        }
    }

    private void applyManifestEffects() {
        if (player.hasEffect(MobEffects.INVISIBILITY)) {
            player.removeEffect(MobEffects.INVISIBILITY);
        }
        player.addEffect(new MobEffectInstance(ModEffects.WRAITH_DIMENSION, 60, 0, true, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.WRAITH_MANIFEST, 60, 0, true, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.NO_COLLIDE, 60, 0, true, false, false));
    }

    private void removeWraithEffects() {
        player.removeEffect(MobEffects.INVISIBILITY);
        player.removeEffect(ModEffects.WRAITH_DIMENSION);
        player.removeEffect(ModEffects.WRAITH_MANIFEST);
        player.removeEffect(ModEffects.NO_COLLIDE);
        player.removeEffect(ModEffects.FOOTSTEP_VANISH);
    }

    private void applyLowSanNearbyPressure(ServerPlayer self) {
        ServerLevel level = self.serverLevel();
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(level);
        for (ServerPlayer target : level.players()) {
            if (target == self || !GameUtils.isPlayerAliveAndSurvival(target) || gw.isKillerTeam(target)) {
                continue;
            }
            if (target.distanceToSqr(self) > 8 * 8 || getSan(target) >= LOW_SAN_BLUE) {
                continue;
            }
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false, true));
            if (heartbeatTimer % 20 == 0) {
                target.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 0.8f, 0.8f);
            }
        }
    }

    public boolean addEnergy(int amount) {
        if (amount <= 0) {
            return false;
        }
        energy += amount;
        sync();
        return true;
    }

    private boolean spendEnergy(ServerPlayer player, int amount) {
        if (energy < amount) {
            player.displayClientMessage(Component.translatable("message.noellesroles.wraith_assassin.not_enough_energy",
                    amount, energy).withStyle(ChatFormatting.RED), true);
            return false;
        }
        energy -= amount;
        sync();
        return true;
    }

    public boolean useAssault(ServerPlayer self) {
        if (!spendEnergy(self, ASSAULT_COST)) {
            return false;
        }
        ServerLevel level = self.serverLevel();
        Vec3 start = self.position();
        Vec3 look = self.getLookAngle().normalize();
        double distance = isManifested() ? 10.0D : 6.0D;
        Set<UUID> hit = new HashSet<>();

        for (int i = 1; i <= 12; i++) {
            Vec3 pos = start.add(look.scale(distance * i / 12.0D));
            for (ServerPlayer target : level.getEntitiesOfClass(ServerPlayer.class,
                    new AABB(pos, pos).inflate(0.9D))) {
                if (target == self || hit.contains(target.getUUID()) || !GameUtils.isPlayerAliveAndSurvival(target)) {
                    continue;
                }
                if (!isManifested() && SREGameWorldComponent.KEY.get(level).isKillerTeam(target)) {
                    continue;
                }
                hit.add(target.getUUID());
                assaultHit(self, target);
            }
        }
        Vec3 end = start.add(look.scale(distance));
        self.teleportTo(end.x, end.y, end.z);
        level.playSound(null, self.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.0f, 1.4f);
        return true;
    }

    private void assaultHit(ServerPlayer self, ServerPlayer target) {
        if (getSan(target) < LOW_SAN_BLUE) {
            GameUtils.killPlayer(target, true, self, DEATH_REASON);
        } else {
            addSan(target, -10);
            target.playNotifySound(SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 1.0f, 0.7f);
        }
    }

    public boolean useWail(ServerPlayer self) {
        if (!spendEnergy(self, WAIL_COST)) {
            return false;
        }
        ServerLevel level = self.serverLevel();
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(level);
        for (ServerPlayer target : level.players()) {
            if (target == self || !GameUtils.isPlayerAliveAndSurvival(target) || gw.isKillerTeam(target)
                    || target.distanceToSqr(self) > 8 * 8) {
                continue;
            }
            addSan(target, -10);
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, PANIC_TICKS, 1, false, false, true));
            target.addEffect(new MobEffectInstance(ModEffects.BLACK_MONITOR, PANIC_TICKS, 0, false, false, true));
            target.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.0f, 0.55f);
        }
        level.playSound(null, self.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.2f, 0.7f);
        return true;
    }

    public boolean useManifest(ServerPlayer self) {
        if (!spendEnergy(self, MANIFEST_COST)) {
            return false;
        }
        manifestTicks = MANIFEST_TICKS;
        applyManifestEffects();
        sync();
        self.serverLevel().playSound(null, self.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0f,
                0.85f);
        return true;
    }

    public boolean buyDrain(ServerPlayer self) {
        ServerLevel level = self.serverLevel();
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(level);
        ServerPlayer target = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayer p : level.players()) {
            if (p == self || !GameUtils.isPlayerAliveAndSurvival(p) || gw.isKillerTeam(p)) {
                continue;
            }
            double dist = p.distanceToSqr(self);
            if (dist < best && dist <= 8 * 8 && getSan(p) > 0) {
                best = dist;
                target = p;
            }
        }
        if (target == null) {
            self.displayClientMessage(Component.translatable("message.noellesroles.wraith_assassin.no_drain_target")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        int drained = Math.min(25, getSan(target));
        addSan(target, -drained);
        addEnergy(drained);
        target.playNotifySound(SoundEvents.WARDEN_NEARBY_CLOSEST, SoundSource.HOSTILE, 1.0f, 0.75f);
        self.displayClientMessage(Component.translatable("message.noellesroles.wraith_assassin.drain", drained,
                target.getName()).withStyle(ChatFormatting.DARK_PURPLE), true);
        return true;
    }

    public void playConversionCue(ServerPlayer self) {
        for (ServerPlayer p : self.serverLevel().players()) {
            if (p == self || getSan(p) >= LOW_SAN_YELLOW || p.distanceToSqr(self) > 12 * 12) {
                continue;
            }
            p.connection.send(new ClientboundSoundPacket(
                    SoundEvents.SOUL_ESCAPE,
                    SoundSource.HOSTILE, self.getX(), self.getY(), self.getZ(), 0.9f, 0.65f,
                    self.getRandom().nextLong()));
        }
    }

    public static int getSan(Player p) {
        return Mth.clamp(Math.round(SREPlayerMoodComponent.KEY.get(p).getMood() * 100.0f), 0, 100);
    }

    public static void addSan(Player p, int amount) {
        SREPlayerMoodComponent.KEY.get(p).addMood(amount / 100.0f);
    }

    public static boolean canPerceiveWraith(Player viewer) {
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(viewer.level());
        return gw.isKillerTeam(viewer) || getSan(viewer) < LOW_SAN_YELLOW;
    }

    public static void registerEvents() {
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (killer != null) {
                var comp = KEY.maybeGet(killer).orElse(null);
                if (comp != null && comp.isInDimension()) {
                    return false;
                }
            }
            return true;
        });
        AllowPlayerOpenLockedDoor.EVENT.register(entity -> {
            if (!(entity instanceof Player player)) {
                return false;
            }
            ItemStack stack = player.getMainHandItem();
            if (!stack.is(ModItems.INFERIOR_LOCKPICK)) {
                return false;
            }
            if (player.getCooldowns().isOnCooldown(stack.getItem())) {
                return false;
            }
            if (!player.isCreative()) {
                player.getCooldowns().addCooldown(stack.getItem(), InferiorLockpickItem.COOLDOWN_TICKS);
            }
            player.playNotifySound(SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.5f, 1.8f);
            return true;
        });
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(WraithAssassinPlayerComponent::filterChat);
    }

    private static boolean filterChat(PlayerChatMessage message, ServerPlayer sender,
            net.minecraft.network.chat.ChatType.Bound bound) {
        var comp = KEY.maybeGet(sender).orElse(null);
        if (comp == null || !SREGameWorldComponent.KEY.get(sender.level()).isRole(sender, ModRoles.WRAITH_ASSASSIN)
                || comp.isManifested()) {
            return true;
        }
        Component rendered = Component.literal("<").append(sender.getDisplayName()).append("> ")
                .append(message.decoratedContent());
        for (ServerPlayer receiver : sender.serverLevel().players()) {
            if (receiver == sender || canPerceiveWraith(receiver)) {
                receiver.sendSystemMessage(rendered.copy().withStyle(ChatFormatting.DARK_AQUA));
            }
        }
        return false;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("energy", energy);
        tag.putInt("manifestTicks", manifestTicks);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        energy = tag.getInt("energy");
        manifestTicks = tag.getInt("manifestTicks");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
