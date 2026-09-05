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

package org.agmas.noellesroles.content.item;

import io.wifi.StarRailExpressID;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.particle.HandParticle;
import io.wifi.starrailexpress.client.render.TMMRenderLayers;
import io.wifi.starrailexpress.compat.CrosshairaddonsCompat;
import io.wifi.starrailexpress.content.item.SkinableItem;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.DropRevolverWhenDead;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMSounds;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 零一五 - 三发手枪
 *
 * 右键开枪，可连续开三枪（命中或落空都消耗一发）
 * 一枪命中给予3秒缓慢2
 * 同一玩家被命中两次则造成击杀
 * 打完三发后进入15秒冷却，射程30格
 */
public class ZeroOneFiveGunItem extends SkinableItem implements DropRevolverWhenDead {

    /** 弹匣容量 */
    private static final int MAX_SHOTS = 3;
    /** 第一次命中标记的持续时间（刻） = 3秒 */
    private static final int HIT_MARK_DURATION = 3 * 20;
    /** 射程30格 */
    private static final float RANGE = 30.0f;
    /** 冷却时间（刻） = 15秒 */
    private static final int COOLDOWN = 15 * 20;

    /** 记录每个玩家被零一五命中的目标 <攻击者UUID, <目标UUID, 剩余标记时间>> */
    private static final Map<UUID, Map<UUID, Integer>> HIT_MARKS = new HashMap<>();

    /** 服务端已消耗弹数（冷却期间会清空） */
    private static final Map<UUID, Integer> SERVER_SHOTS_FIRED = new HashMap<>();
    /** 客户端已消耗弹数（与服务端分表，避免单人模式双计） */
    private static final Map<UUID, Integer> CLIENT_SHOTS_FIRED = new HashMap<>();

    public ZeroOneFiveGunItem(Item.Properties settings) {
        super(settings);
    }

    /** 注册服务端tick事件 */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ZeroOneFiveGunItem::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        tickCleanup();
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user,
            @NotNull InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        if (user.isSpectator() || user.hasEffect(ModEffects.USED_BANED)) {
            return InteractionResultHolder.fail(stack);
        }
        if (user.getCooldowns().isOnCooldown(this)) {
            shotMap(world.isClientSide).remove(user.getUUID());
            return InteractionResultHolder.fail(stack);
        }

        if (world.isClientSide) {
            SREGameWorldComponent gameComponent = SREClient.gameComponent;
            if (gameComponent != null) {
                SRERole role = gameComponent.getRole(user);
                if (role != null && !role.onUseGun(user)) {
                    return InteractionResultHolder.fail(stack);
                }
            }

            if (!tryConsumeShot(user)) {
                return InteractionResultHolder.fail(stack);
            }

            HitResult collision = getGunTarget(user);
            if (collision instanceof EntityHitResult entityHitResult) {
                Entity target = entityHitResult.getEntity();
                ClientPlayNetworking.send(new ZeroOneFiveShootPayload(target.getId()));
                CrosshairaddonsCompat.arrowHit();
            } else {
                ClientPlayNetworking.send(new ZeroOneFiveShootPayload(-1));
            }

            user.setXRot(user.getXRot() - 4.0F);
            spawnHandParticle();

            world.playSound(user, user.getX(), user.getY(), user.getZ(),
                    TMMSounds.ITEM_REVOLVER_SHOOT, SoundSource.PLAYERS, 1.0F, 1.0F);
        } else {
            SREGameWorldComponent gameComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY.get(world);
            SRERole role = gameComponent.getRole(user);
            if (role != null && !role.onUseGun(user)) {
                return InteractionResultHolder.fail(stack);
            }
        }

        return InteractionResultHolder.consume(stack);
    }

    private static Map<UUID, Integer> shotMap(boolean clientSide) {
        return clientSide ? CLIENT_SHOTS_FIRED : SERVER_SHOTS_FIRED;
    }

    /**
     * 消耗一发。打完三发后进入冷却。命中与落空都消耗。
     *
     * @return 是否成功开枪
     */
    public static boolean tryConsumeShot(Player player) {
        Map<UUID, Integer> shots = shotMap(player.level().isClientSide);
        UUID uuid = player.getUUID();
        if (player.getCooldowns().isOnCooldown(ModItems.ZERO_ONE_FIVE_GUN)) {
            shots.remove(uuid);
            return false;
        }

        int used = shots.getOrDefault(uuid, 0) + 1;
        if (used >= MAX_SHOTS) {
            shots.remove(uuid);
            player.getCooldowns().addCooldown(ModItems.ZERO_ONE_FIVE_GUN, COOLDOWN);
        } else {
            shots.put(uuid, used);
        }
        return true;
    }

    /**
     * 处理命中逻辑
     */
    public static void onHit(ServerPlayer shooter, ServerPlayer target) {
        UUID shooterUUID = shooter.getUUID();
        UUID targetUUID = target.getUUID();

        Map<UUID, Integer> shooterMarks = HIT_MARKS.computeIfAbsent(shooterUUID, k -> new HashMap<>());

        if (shooterMarks.containsKey(targetUUID)) {
            GameUtils.killPlayer(target, true, shooter, GameConstants.DeathReasons.ZERO_ONE_FIVE);
            shooterMarks.remove(targetUUID);
        } else {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, HIT_MARK_DURATION, 1, false, false));
            target.serverLevel().sendParticles(
                    ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                    target.getX(), target.getY() + 1, target.getZ(),
                    5, 0.2, 0.2, 0.2, 0.35);
            shooterMarks.put(targetUUID, HIT_MARK_DURATION);
        }
    }

    public static int getCooldown() {
        return COOLDOWN;
    }

    /**
     * 清理过期标记（每刻调用）
     */
    public static void tickCleanup() {
        HIT_MARKS.entrySet().removeIf(entry -> {
            entry.getValue().entrySet().removeIf(mark -> {
                int remaining = mark.getValue() - 1;
                if (remaining <= 0) {
                    return true;
                }
                mark.setValue(remaining);
                return false;
            });
            return entry.getValue().isEmpty();
        });
    }

    /**
     * 清理玩家数据
     */
    public static void clearPlayerData(UUID playerUUID) {
        HIT_MARKS.remove(playerUUID);
        SERVER_SHOTS_FIRED.remove(playerUUID);
        CLIENT_SHOTS_FIRED.remove(playerUUID);
        for (Map<UUID, Integer> marks : HIT_MARKS.values()) {
            marks.remove(playerUUID);
        }
    }

    public static HitResult getGunTarget(Player user) {
        return ProjectileUtil.getHitResultOnViewVector(user, entity -> {
            if (entity instanceof Player player) {
                return GameUtils.isPlayerAliveAndSurvival(player);
            }
            return false;
        }, RANGE);
    }

    public static void spawnHandParticle() {
        HandParticle handParticle = (new HandParticle())
                .setTexture(StarRailExpressID.watheId("textures/particle/gunshot.png"))
                .setPos(0.1F, 0.275F, -0.2F).setMaxAge(3.0F).setSize(0.5F).setVelocity(0.0F, 0.0F, 0.0F)
                .setLight(15, 15).setAlpha(new float[] { 1.0F, 0.1F }).setRenderLayer(TMMRenderLayers::additive);
        SREClient.handParticleManager.spawn(handParticle);
    }

    @Override
    public String getItemSkinType() {
        return "revolver"; // 沿用一次性手枪的材质
    }
}
