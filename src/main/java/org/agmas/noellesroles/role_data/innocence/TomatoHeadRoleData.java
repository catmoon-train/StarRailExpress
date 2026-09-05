/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.role_data.innocence;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.IsPlayerPunchable;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.item.TomatoItem;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TomatoHeadRoleData extends SimpleRoleData {

    public static final net.minecraft.resources.ResourceLocation SKILL_ID = Noellesroles.id("tomato_head_transform");

    public static final int SKILL_COST = 100;
    public static final int FORM_SECONDS = 30;
    public static final int TRIP_SECONDS = 3;
    public static final float TOMATO_WIDTH = 0.25F;
    public static final float TOMATO_HEIGHT = 0.25F;
    public static final float TOMATO_EYE_HEIGHT = 0.18F;
    private static final double KNOCKBACK = 1.15;
    private static final int PUNCH_COOLDOWN_TICKS = 8;

    /** 西红柿形态结束时刻（游戏开始后 tick，会议期间会暂停）。 */
    public long tomatoFormEndTick = -1L;
    private final Map<UUID, Long> tripCooldownUntil = new HashMap<>();

    public TomatoHeadRoleData(RoleDataContext context) {
        super(context);
    }

    public static void registerEvents() {
        IsPlayerPunchable.EVENT.register(entity -> {
            if (!(entity instanceof Player target)) {
                return false;
            }
            SREGameWorldComponent game = SREGameWorldComponent.KEY.get(target.level());
            return game != null && game.isRole(target, ModRoles.TOMATO_HEAD);
        });
        AttackEntityCallback.EVENT.register((attacker, level, hand, entity, hitResult) -> {
            if (level.isClientSide) {
                return InteractionResult.PASS;
            }
            if (!(entity instanceof Player victim)) {
                return InteractionResult.PASS;
            }
            if (!GameUtils.isPlayerAliveAndSurvival(attacker) || !GameUtils.isPlayerAliveAndSurvival(victim)) {
                return InteractionResult.PASS;
            }
            SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level);
            if (game == null || !game.isRole(victim, ModRoles.TOMATO_HEAD)) {
                return InteractionResult.PASS;
            }
            if (attacker.hasEffect(ModEffects.SAFE_TIME) || victim.hasEffect(ModEffects.SAFE_TIME)) {
                return InteractionResult.PASS;
            }
            if (attacker.getCooldowns().isOnCooldown(Items.BARRIER)) {
                return InteractionResult.PASS;
            }
            attacker.getCooldowns().addCooldown(Items.BARRIER, PUNCH_COOLDOWN_TICKS);
            Vec3 dir = victim.position().subtract(attacker.position());
            double len = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
            if (len < 1.0e-4) {
                float yaw = attacker.getYRot() * Mth.DEG_TO_RAD;
                dir = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
                len = 1.0;
            }
            victim.knockback(KNOCKBACK, -dir.x / len, -dir.z / len);
            victim.hurtMarked = true;
            return InteractionResult.SUCCESS;
        });
        OnPlayerDeath.EVENT.register((victim, deathReason) -> {
            if (!(victim instanceof ServerPlayer serverVictim) || victim.level().isClientSide()) {
                return;
            }
            SREGameWorldComponent game = SREGameWorldComponent.KEY.get(victim.level());
            if (game == null || !game.isRole(victim, ModRoles.TOMATO_HEAD)) {
                return;
            }
            TomatoHeadRoleData data = RoleData.getNullable(TomatoHeadRoleData.class, victim);
            if (data != null) {
                data.endTomatoForm(serverVictim, false);
            }
            dropTomato(serverVictim);
        });
    }

    public static boolean isTomatoForm(Player player) {
        if (player != null && player.hasEffect(ModEffects.TOMATO_FORM)) {
            return true;
        }
        TomatoHeadRoleData data = RoleData.getNullable(TomatoHeadRoleData.class, player);
        return data != null && data.isTomatoForm();
    }

    public boolean isTomatoForm() {
        if (tomatoFormEndTick < 0) {
            return false;
        }
        return GameUtils.getTicksFromGameStart(player.level()) < tomatoFormEndTick;
    }

    public boolean useTransform(ServerPlayer sp) {
        if (sp.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(sp.level());
        if (game == null || !game.isRole(sp, ModRoles.TOMATO_HEAD)) {
            return false;
        }
        if (isTomatoForm()) {
            return false;
        }
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(sp);
        if (shop.balance < SKILL_COST) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.tomato_head.no_money", SKILL_COST)
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        shop.balance -= SKILL_COST;
        shop.sync();
        tomatoFormEndTick = GameUtils.getTicksFromGameStart(sp.level()) + FORM_SECONDS * 20L;
        tripCooldownUntil.clear();
        applyTomatoFormEffects(sp);
        sp.refreshDimensions();
        sync();
        return true;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(sp.level());
        if (game == null || !game.isRunning() || !game.isRole(sp, ModRoles.TOMATO_HEAD)) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return;
        }
        if (!isTomatoForm()) {
            if (tomatoFormEndTick >= 0) {
                endTomatoForm(sp, true);
            }
            return;
        }
        applyTomatoFormEffects(sp);
        tickStepOn(sp);
    }

    @Override
    public void clear() {
        if (player instanceof ServerPlayer sp) {
            endTomatoForm(sp, false);
        } else {
            tomatoFormEndTick = -1L;
        }
        tripCooldownUntil.clear();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) {
        return true;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putLong("TomatoFormEndTick", tomatoFormEndTick);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        boolean wasForm = isTomatoForm();
        tomatoFormEndTick = tag.getLong("TomatoFormEndTick");
        if (wasForm != isTomatoForm()) {
            player.refreshDimensions();
        }
    }

    private void applyTomatoFormEffects(ServerPlayer sp) {
        int ticks = 10;
        sp.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, ticks, 0, true, false, false));
        sp.addEffect(new MobEffectInstance(ModEffects.USED_BANED, ticks, 0, true, false, false));
        sp.addEffect(new MobEffectInstance(ModEffects.INVENTORY_BANED, ticks, 0, true, false, false));
        sp.addEffect(new MobEffectInstance(ModEffects.NO_COLLIDE, ticks, 0, true, false, false));
        sp.addEffect(new MobEffectInstance(ModEffects.TOMATO_FORM, ticks, 0, true, false, false));
        sp.setDeltaMovement(0.0, Math.min(sp.getDeltaMovement().y, 0.0), 0.0);
    }

    private void endTomatoForm(ServerPlayer sp, boolean announce) {
        boolean wasForm = tomatoFormEndTick >= 0;
        tomatoFormEndTick = -1L;
        tripCooldownUntil.clear();
        if (wasForm) {
            sp.removeEffect(ModEffects.MOVE_BANED);
            sp.removeEffect(ModEffects.USED_BANED);
            sp.removeEffect(ModEffects.INVENTORY_BANED);
            sp.removeEffect(ModEffects.NO_COLLIDE);
            sp.removeEffect(ModEffects.TOMATO_FORM);
            sp.refreshDimensions();
            if (announce && GameUtils.isPlayerAliveAndSurvival(sp)) {
                sp.displayClientMessage(Component.translatable("message.noellesroles.tomato_head.form_end")
                        .withStyle(ChatFormatting.YELLOW), true);
            }
            sync();
        }
    }

    private void tickStepOn(ServerPlayer tomato) {
        long now = GameUtils.getTicksFromGameStart(tomato.level());
        AABB box = tomato.getBoundingBox().inflate(0.15, 0.25, 0.15);
        for (ServerPlayer other : tomato.serverLevel().getEntitiesOfClass(ServerPlayer.class, box.inflate(0.4),
                p -> p != tomato && GameUtils.isPlayerAliveAndSurvival(p))) {
            if (!other.getBoundingBox().intersects(box)) {
                continue;
            }
            Long until = tripCooldownUntil.get(other.getUUID());
            if (until != null && now < until) {
                continue;
            }
            tripPlayer(other);
            tripCooldownUntil.put(other.getUUID(), now + TRIP_SECONDS * 20L);
        }
    }

    public static void tripPlayer(ServerPlayer target) {
        tripPlayer(target, TRIP_SECONDS);
    }

    public static void tripPlayer(ServerPlayer target, int seconds) {
        int ticks = seconds * 20;
        target.addEffect(new MobEffectInstance(ModEffects.SWIM_POSE, ticks, 0, false, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 3, false, false, true));
        target.setPose(net.minecraft.world.entity.Pose.SWIMMING);
        target.displayClientMessage(Component.translatable("message.noellesroles.tomato_head.tripped")
                .withStyle(ChatFormatting.RED), true);
        target.serverLevel().playSound(null, target.getX(), target.getY(), target.getZ(),
                org.agmas.noellesroles.init.NRSounds.TOMATO_SPLAT,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.9F, 1.05F);
    }

    public static void giveTomato(ServerPlayer target) {
        ItemStack stack = new ItemStack(ModItems.TOMATO);
        if (!target.addItem(stack)) {
            target.drop(stack, false);
        }
    }

    public static void applyTomatoSauce(ServerPlayer target) {
        target.addEffect(new MobEffectInstance(ModEffects.TOMATO_SAUCE, 60, 0, false, false, true));
        target.serverLevel().playSound(null, target.getX(), target.getY(), target.getZ(),
                org.agmas.noellesroles.init.NRSounds.TOMATO_SPLAT,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static void dropTomato(ServerPlayer victim) {
        TomatoItem.spawnGroundDrop(victim.serverLevel(), victim.getX(), victim.getY() + 0.2, victim.getZ(),
                Vec3.ZERO, victim);
    }
}
