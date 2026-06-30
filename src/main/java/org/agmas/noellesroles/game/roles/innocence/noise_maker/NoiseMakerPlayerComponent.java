package org.agmas.noellesroles.game.roles.innocence.noise_maker;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class NoiseMakerPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<NoiseMakerPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "noise_maker"), NoiseMakerPlayerComponent.class);
    private final Player player;
    public boolean isActive = true;
    public int cooldown = 0;

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        this.isActive = true;
        this.cooldown = 0;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    public NoiseMakerPlayerComponent(Player player) {
        this.player = player;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    public void clientTick() {
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    public void serverTick() {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.NOISEMAKER)) {
            return;
        }
        if (!gameWorld.isRunning()) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        if (player.level().getGameTime() % 20 == 0) {
            sync();
        }
    }

    public void useAbility() {
        // 冷却由 RoleSkill 统一管理

        // player.displayClientMessage(Component.translatable("message.noellesroles.insufficient_funds"),
        // true);
        player.addEffect(
                new MobEffectInstance(MobEffects.LUCK, 120, 0, false, false, false));

        Component msg = Component.translatable("gui.noellesroles.noisemaker.ability").withStyle(ChatFormatting.AQUA,
                ChatFormatting.BOLD);

        if (player instanceof ServerPlayer serverPlayer) {
            ConfigWorldComponent.onPlayerUsedSkill( (ServerPlayer) serverPlayer);
            var gameWorldComponent = SREGameWorldComponent.KEY.get(serverPlayer.level());
            player.level().playSound(null, serverPlayer.blockPosition(), SoundEvents.NOTE_BLOCK_HARP.value(),
                    SoundSource.PLAYERS, 2F, 0F);
            for (ServerPlayer p : serverPlayer.serverLevel().players()) {
                if (p.isSpectator())
                    continue;
                if (p.getUUID().equals(player.getUUID())) {

                } else {
                    if (p.distanceTo(player) <= 15) {
                        if (gameWorldComponent.isRole(p, ModRoles.WIND_YAOSE))
                            continue;
                        p.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0, false, false, false));
                    }
                    p.displayClientMessage(msg, true);
                }
            }
            serverPlayer.displayClientMessage(msg, true);

            // 坚守者式冲击波：眩晕（定身 + 禁止背包 + 禁止使用）并击退正前方扇形内的玩家
            NoellesRolesConfig cfg = NoellesRolesConfig.HANDLER.instance();
            Vec3 lookFlat = new Vec3(serverPlayer.getLookAngle().x, 0, serverPlayer.getLookAngle().z);
            if (lookFlat.lengthSqr() > 1.0e-4) {
                lookFlat = lookFlat.normalize();
                double swRange = cfg.noisemakerShockwaveRange;
                int stunTicks = GameConstants.getInTicks(0, cfg.noisemakerStunSeconds);
                for (Player target : serverPlayer.serverLevel().players()) {
                    if (target.equals(player) || !GameUtils.isPlayerAliveAndSurvival(target))
                        continue;
                    Vec3 to = new Vec3(target.getX() - serverPlayer.getX(), 0, target.getZ() - serverPlayer.getZ());
                    double dist = to.length();
                    if (dist > swRange || dist < 1.0e-4)
                        continue;
                    // 仅作用于正前方（约 ±72° 扇形）
                    if (lookFlat.dot(to.scale(1.0 / dist)) < 0.3D)
                        continue;
                    double strength = cfg.noisemakerShockwaveKnockback;
                    target.push(to.x / dist * strength, 0.42D, to.z / dist * strength);
                    if (target instanceof ServerPlayer stp) {
                        stp.hurtMarked = true;
                        stp.connection.send(new ClientboundSetEntityMotionPacket(stp.getId(), stp.getDeltaMovement()));
                    }
                    target.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, stunTicks, 0, false, true, true));
                    target.addEffect(new MobEffectInstance(ModEffects.INVENTORY_BANED, stunTicks, 0, false, true, true));
                    target.addEffect(new MobEffectInstance(ModEffects.USED_BANED, stunTicks, 0, false, true, true));
                }
            }
            serverPlayer.serverLevel().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                    SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.5F, 1.0F);
        }

        sync();
    }

    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("isActive", this.isActive);
        tag.putInt("cooldown", this.cooldown);
    }

    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.isActive = !tag.contains("isActive") || tag.getBoolean("isActive");
        this.cooldown = tag.getInt("cooldown");
    }

    
    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}