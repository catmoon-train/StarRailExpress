package org.agmas.noellesroles.game.roles.neutral.raven;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.SREItemUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.*;

/**
 * Independent neutral role: gains hunting charges from nearby mood recovery.
 */
public final class RavenPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<RavenPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            Noellesroles.id("raven"), RavenPlayerComponent.class);

    public static final int MAX_CHARGES = 5;
    private static final int HUNT_TICKS = 120 * 20;
    private static final int COOLDOWN_TICKS = 60 * 20;
    public static final double CHARGE_RADIUS = 8.0;
    public static final float TASK_COMPLETE_PROGRESS = 0.5f;
    private static final double MOOD_RADIUS_SQR = CHARGE_RADIUS * CHARGE_RADIUS;

    private final Player player;
    private final Map<UUID, Float> observedMood = new HashMap<>();
    public int charges;
    public int cooldownTicks;
    public int huntTicks;
    public int kills;
    public int requiredKills;
    public float moodProgress;
    public float moodProgressThreshold;
    public ResourceLocation targetRoleId;
    public UUID bodyUuid;
    private Vec3 bodyPosition = Vec3.ZERO;
    private float bodyYaw;
    private float bodyPitch;

    public RavenPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer target) {
        return true;
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public void init() {
        charges = 0;
        cooldownTicks = 0;
        huntTicks = 0;
        kills = 0;
        requiredKills = 0;
        moodProgress = 0;
        moodProgressThreshold = 1f;
        targetRoleId = null;
        bodyUuid = null;
        observedMood.clear();
        sync();
    }

    @Override
    public void clear() {
        endHunt(false);
        init();
    }

    public boolean isHunting() {
        return huntTicks > 0;
    }

    @Override
    public void clientTick() {
        if (cooldownTicks > 0)
            cooldownTicks--;
        if (huntTicks > 0) {
            huntTicks--;
        }
    }

    @Override
    public void serverTick() {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());

        // If the player still has hunt state but is no longer a RAVEN
        // (e.g. role was changed mid-hunt), clean up immediately.
        if (!game.isRole(player, ModRoles.RAVEN)) {
            if (isHunting() || bodyUuid != null) {
                endHunt(false);
            }
            return;
        }
        if (!game.isRunning() || !GameUtils.isPlayerAliveAndSurvival(player)) {
            // 渡鸦死亡时清除傀儡本体
            if (!GameUtils.isPlayerAliveAndSurvival(player) && (isHunting() || bodyUuid != null)) {
                endHunt(false);
            }
            return;
        }

        int totalPlayers = game.getPlayerCount();
        requiredKills = Math.max(2, (totalPlayers + 11) / 8);
        moodProgressThreshold = getChargeThreshold(totalPlayers);
        boolean changed = observeNearbyMood(totalPlayers);
        if (cooldownTicks > 0)
            cooldownTicks--;
        if (huntTicks > 0) {
            huntTicks--;
            if (!hasLivingTargetRole(game))
                chooseTargetRole(game);
            if (huntTicks <= 0)
                endHunt(true);
            changed = true;
        }
        if (changed || player.tickCount % 200 == 0)
            sync();
    }

    private boolean observeNearbyMood(int totalPlayers) {
        boolean changed = false;
        float threshold = getChargeThreshold(totalPlayers);
        for (Player nearby : player.level().players()) {
            if (nearby == player || nearby.distanceToSqr(player) > MOOD_RADIUS_SQR
                    || !GameUtils.isPlayerAliveAndSurvival(nearby))
                continue;
            float now = SREPlayerMoodComponent.KEY.get(nearby).getMood();
            Float before = observedMood.put(nearby.getUUID(), now);
            if (before != null && now > before && charges < MAX_CHARGES) {
                changed |= addChargeProgress(now - before, threshold);
            }
        }
        observedMood.keySet().removeIf(id -> player.level().getPlayerByUUID(id) == null);
        return changed;
    }

    private float getChargeThreshold(int totalPlayers) {
        return Math.max(1f, totalPlayers / 6f - 1.75f);
    }

    private boolean addChargeProgress(float amount, float threshold) {
        if (amount <= 0 || charges >= MAX_CHARGES)
            return false;
        moodProgress += amount;
        while (moodProgress >= threshold && charges < MAX_CHARGES) {
            moodProgress -= threshold;
            charges++;
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.raven.charge", charges, MAX_CHARGES)
                                .withStyle(ChatFormatting.DARK_PURPLE),
                        true);
            }
        }
        return true;
    }

    public void onNearbyTaskComplete() {
        if (!(player instanceof ServerPlayer))
            return;
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (!game.isRunning() || !game.isRole(player, ModRoles.RAVEN))
            return;
        moodProgressThreshold = getChargeThreshold(game.getPlayerCount());
        if (addChargeProgress(TASK_COMPLETE_PROGRESS, moodProgressThreshold))
            sync();
    }

    public boolean useAbility() {
        if (!(player instanceof ServerPlayer serverPlayer) || isHunting() || cooldownTicks > 0 || charges <= 0)
            return false;
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (!game.isSkillAvailable || !game.isRunning())
            return false;
        if (!chooseTargetRole(game)) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.raven.no_target").withStyle(ChatFormatting.RED), true);
            return false;
        }
        charges--;
        huntTicks = HUNT_TICKS;
        bodyPosition = player.position();
        bodyYaw = player.getYRot();
        bodyPitch = player.getXRot();
        createBody(serverPlayer);
        giveHuntItems(serverPlayer);
        applyHuntEffects();
        sync();
        return true;
    }

    private boolean chooseTargetRole(SREGameWorldComponent game) {
        List<SRERole> roles = new ArrayList<>();
        for (Player candidate : player.level().players()) {
            if (candidate == player || !GameUtils.isPlayerAliveAndSurvival(candidate))
                continue;
            SRERole role = game.getRole(candidate);
            if (role != null && roles.stream().noneMatch(existing -> existing.identifier().equals(role.identifier())))
                roles.add(role);
        }
        if (roles.isEmpty())
            return false;
        targetRoleId = roles.get(player.getRandom().nextInt(roles.size())).identifier();
        return true;
    }

    private boolean hasLivingTargetRole(SREGameWorldComponent game) {
        if (targetRoleId == null)
            return false;
        return player.level().players().stream().anyMatch(candidate -> {
            SRERole role = game.getRole(candidate);
            return candidate != player && GameUtils.isPlayerAliveAndSurvival(candidate)
                    && role != null && targetRoleId.equals(role.identifier());
        });
    }

    private void createBody(ServerPlayer serverPlayer) {
        PuppeteerBodyEntity body = new PuppeteerBodyEntity(ModEntities.PUPPETEER_BODY, serverPlayer.serverLevel());
        body.setPos(bodyPosition);
        body.setYRot(bodyYaw);
        body.setXRot(bodyPitch);
        body.setOwner(serverPlayer);
        serverPlayer.serverLevel().addFreshEntity(body);
        bodyUuid = body.getUUID();
    }

    private void giveHuntItems(ServerPlayer player) {
        player.getInventory().add(TMMItems.KNIFE.getDefaultInstance());
        player.getInventory().add(TMMItems.LOCKPICK.getDefaultInstance());
    }

    private void applyHuntEffects() {
        player.addEffect(new MobEffectInstance(ModEffects.DISGUISE, HUNT_TICKS + 6 * 20, 3, false, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.VOICE_SILENCE, HUNT_TICKS, 0, false, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.NO_COLLIDE, HUNT_TICKS, 0, false, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.INVINCIBLE, HUNT_TICKS, 0, false, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.CHAT_BAN, HUNT_TICKS, 0, false, false, false));
    }

    public boolean canKill(Player victim) {
        if (!isHunting() || targetRoleId == null)
            return false;
        SRERole role = SREGameWorldComponent.KEY.get(player.level()).getRole(victim);
        return role != null && targetRoleId.equals(role.identifier());
    }

    public void onTargetKilled(Player victim) {
        if (!canKill(victim))
            return;
        kills++;
        // 全场播放烈风死亡音效
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                    SoundEvents.BREEZE_DEATH, SoundSource.MASTER, 1.0F, 1.0F);
        }
        // 击杀正确目标时，充能次数+1（不超过上限）
        if (charges < MAX_CHARGES) {
            charges++;
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.raven.charge", charges, MAX_CHARGES)
                                .withStyle(ChatFormatting.DARK_PURPLE),
                        true);
            }
        }
        if (kills >= requiredKills && player.level() instanceof ServerLevel level) {
            RoleUtils.customWinnerWin(level, GameUtils.WinStatus.CUSTOM, ModRoles.RAVEN_ID.getPath(),
                    OptionalInt.of(ModRoles.RAVEN.color()));
        }
        sync();
    }

    public void onBodyDeath(Player killer, ResourceLocation reason) {
        if (!isHunting() || !(player instanceof ServerPlayer serverPlayer))
            return;
        endHunt(false);
        GameUtils.forceKillPlayer(serverPlayer, true, killer, reason);
    }

    /**
     * 狩猎期间按技能键主动返回本体，返还35%冷却。
     */
    public void returnFromHunt() {
        if (!isHunting() || !(player instanceof ServerPlayer serverPlayer))
            return;
        endHunt(false);
        cooldownTicks = (int) (COOLDOWN_TICKS * 0.35);
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.raven.return_body").withStyle(ChatFormatting.GOLD),
                true);
        sync();
    }

    public void endHunt(boolean applyCooldown) {
        if (player instanceof ServerPlayer serverPlayer && bodyUuid != null) {
            // Clear effects before teleport so the client never sees disguised skin at body
            // pos
            serverPlayer.removeEffect(ModEffects.DISGUISE);
            serverPlayer.removeEffect(ModEffects.VOICE_SILENCE);
            serverPlayer.removeEffect(ModEffects.NO_COLLIDE);
            serverPlayer.removeEffect(ModEffects.INVINCIBLE);
            serverPlayer.removeEffect(ModEffects.CHAT_BAN);

            // Remove knife and lockpick from all inventory slots.
            SREItemUtils.clearItem(serverPlayer,
                    stack -> stack.is(TMMItems.KNIFE) || stack.is(TMMItems.LOCKPICK));

            removeBody(serverPlayer.serverLevel());
            // 如果游戏已结束，不要传送到本体傀儡位置——正常游戏结束流程会统一传送到大厅
            SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
            if (game.isRunning()) {
                serverPlayer.teleportTo(serverPlayer.serverLevel(), bodyPosition.x, bodyPosition.y, bodyPosition.z,
                        bodyYaw, bodyPitch);
            }
        }
        if (applyCooldown)
            cooldownTicks = COOLDOWN_TICKS;
        huntTicks = 0;
        targetRoleId = null;
        bodyUuid = null;
        sync();
    }

    public static void onLastStand(ServerLevel world) {
        for (ServerPlayer serverPlayer : world.players()) {
            RavenPlayerComponent raven = ModComponents.RAVEN.get(serverPlayer);
            if (raven != null && raven.isHunting()) {
                raven.charges = Math.min(MAX_CHARGES, raven.charges + 1);
                raven.endHunt(false);
            }
        }
    }

    private void removeBody(ServerLevel level) {
        if (bodyUuid == null)
            return;
        Entity entity = level.getEntity(bodyUuid);
        if (entity != null)
            entity.discard();
    }

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        buf.writeVarInt(charges);
        buf.writeVarInt(cooldownTicks);
        buf.writeVarInt(huntTicks);
        buf.writeVarInt(kills);
        buf.writeVarInt(requiredKills);
        buf.writeFloat(moodProgress);
        buf.writeFloat(moodProgressThreshold);
        boolean hasTarget = targetRoleId != null;
        buf.writeBoolean(hasTarget);
        if (hasTarget) buf.writeUtf(targetRoleId.toString());
    }

    @Override
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        charges = buf.readVarInt();
        cooldownTicks = buf.readVarInt();
        huntTicks = buf.readVarInt();
        kills = buf.readVarInt();
        requiredKills = buf.readVarInt();
        moodProgress = buf.readFloat();
        moodProgressThreshold = buf.readFloat();
        targetRoleId = buf.readBoolean() ? ResourceLocation.tryParse(buf.readUtf()) : null;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider provider) {
        // 使用 writeSyncPacket/applySyncPacket 紧凑二进制格式
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider provider) {
        // 使用 writeSyncPacket/applySyncPacket 紧凑二进制格式
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
    }
}
