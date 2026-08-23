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
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.EarlyKillPlayer;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LeatherPigRoleData extends SimpleRoleData {

    public static final ResourceLocation SKILL_ID = Noellesroles.id("leather_pig_frenzy");

    /**
     * 伪装期间玩家的真实眼高（= 猪实体的眼高）。
     *
     * <p>
     * 改的是眼高而不是相机位置：相机、准星射线、枪械命中判定全都读 {@code getEyeY()}，
     * 只挪相机会让画面与实际命中点错开 0.75 格。见 LeatherPigEyeHeightMixin。
     */
    public static final float PIG_EYE_HEIGHT = EntityType.PIG.getDimensions().eyeHeight();

    public static final int FRENZY_COST = 150;
    public static final int FRENZY_TICKS = 30 * 20;
    public static final int COOLDOWN_SECONDS = 45;
    /** 疯魔模式直觉高亮范围（格） */
    public static final double INSTINCT_RANGE = 40.0;
    /** 追杀心跳音效间隔（tick） */
    private static final int HEARTBEAT_INTERVAL = 40;
    /** 伪装状态补发间隔（tick） */
    private static final int DISGUISE_RESYNC_INTERVAL = 20;
    /** 伪装翻转后的补发窗口（tick）：窗口内每秒补发一次，之后不再广播 */
    private static final int DISGUISE_RESYNC_WINDOW = 100;
    /** 疯魔倒计时漂移校正间隔（tick），只发给本人 */
    private static final int FRENZY_RESYNC_INTERVAL = 200;

    /** 疯魔模式期间推开路径上玩家的判定范围（格） */
    private static final double PUSH_RANGE = 2;
    /** 推开力度（水平） */
    private static final double PUSH_STRENGTH = 0.9;
    /** 推开后的死亡归因窗口（tick）：被推玩家在此窗口内因环境死亡则归因给皮革噶的 */
    private static final int PUSH_ATTRIBUTION_TICKS = 100;

    /**
     * 被皮革噶的推开的玩家登记表：受害者 UUID -&gt; (皮革噶的 UUID, 归因过期游戏刻)。
     * 用于将"被推致死"的平民归因给皮革噶的，从而复用小脑惩罚管线。
     */
    private static final Map<UUID, PushRecord> PUSHED = new HashMap<>();

    private record PushRecord(UUID pusher, long expiryGameTime) {
    }

    /** 是否以猪的形象示人（角色分配且存活期间为 true，同步给所有客户端） */
    public boolean disguised = true;
    /** 疯魔模式剩余时间（tick） */
    public int frenzyTicks = 0;
    /** 伪装翻转后的剩余补发时间（tick），仅服务端使用，不参与同步 */
    private int disguiseResyncTicks = 0;

    public LeatherPigRoleData(RoleDataContext context) {
        super(context);
    }

    /**
     * 只同步给本人：疯魔倒计时只有本人关心（见 {@link #writeToSyncNbtWithPlayer}），
     * 伪装状态没变时不必对全场广播。
     */
    private void syncSelf() {
        if (player instanceof ServerPlayer sp) {
            syncTo(sp);
        }
    }

    @Override
    public void init() {
        // 因为init reset仅给对应玩家同步，此处给其他玩家同步。
        setDisguised(false);
    }

    @Override
    public void initOnClient() {
        // 因为init reset仅给对应玩家同步，此处给其他玩家同步。
        setDisguised(false);
    }

    @Override
    public void clear() {
        init();
    }

    public boolean isDisguised() {
        return disguised;
    }

    /** 伪装状态翻转时必须 refreshDimensions()，否则 Entity 缓存的 eyeHeight 不会重算。 */
    private void setDisguised(boolean value) {
        if (disguised == value) {
            return;
        }
        disguised = value;
        player.refreshDimensions();
    }

    /** 供 mixin 在无组件实例时查询：该玩家当前是否顶着猪的皮。 */
    public static boolean isDisguised(Player player) {
        LeatherPigRoleData component = RoleData.getNullable(LeatherPigRoleData.class, player);
        return component != null && component.disguised;
    }

    public boolean isFrenzyActive() {
        return frenzyTicks > 0;
    }

    public boolean useSkill(ServerPlayer sp) {
        if (sp.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        if (!gameWorld.isRole(sp, ModRoles.LEATHER_PIG)) {
            return false;
        }
        if (frenzyTicks > 0) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.leather_pig.already_active")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(sp);
        if (shop.balance < FRENZY_COST) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.insufficient_funds_money", FRENZY_COST)
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        shop.addToBalance(-FRENZY_COST);
        frenzyTicks = FRENZY_TICKS;
        syncSelf();
        // 神秘追杀音效：自定义追杀 BGM（资源包提供）+ 原版守卫者诅咒兜底

        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS,
                0.7f, 0.8f);
        sp.displayClientMessage(Component.translatable("message.noellesroles.leather_pig.frenzy_start")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        return true;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        boolean shouldDisguise = gameWorld.isRunning() && GameUtils.isPlayerAliveAndSurvival(sp);
        if (shouldDisguise != disguised) {
            setDisguised(shouldDisguise);
            disguiseResyncTicks = DISGUISE_RESYNC_WINDOW;
            sync();
        } else if (disguiseResyncTicks > 0) {
            // 翻转恰好落在开局传送/重生的窗口里时，客户端可能还没有对应实体，
            // CCA 会直接丢弃这个组件包。翻转后的 5 秒窗口内每秒补发一次兜底；
            // 窗口过后交给 CCA「开始追踪实体时自动同步」收尾，不再全场每秒广播。
            disguiseResyncTicks--;
            if (disguiseResyncTicks % DISGUISE_RESYNC_INTERVAL == 0) {
                sync();
            }
        }

        if (frenzyTicks <= 0) {
            return;
        }
        if (!shouldDisguise) {
            frenzyTicks = 0;
            syncSelf();
            return;
        }

        frenzyTicks--;
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 10, 2, true, false, true));
        sp.addEffect(new MobEffectInstance(ModEffects.INFINITE_STAMINA, 10, 2, true, false, true));
        // 疯魔冲锋：持续推开路径上的邻近玩家
        pushPlayersOnPath(sp);

        int elapsed = FRENZY_TICKS - frenzyTicks;
        if (elapsed % HEARTBEAT_INTERVAL == 0) {
            sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS,
                    1.0f, 1.1f);
        }

        sp.serverLevel().sendParticles(ParticleTypes.CLOUD, sp.getX(), sp.getY() + 0.25, sp.getZ(), 2, 0.1, 0.1, 0.1,
                0.1);
        if (frenzyTicks == 0) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.leather_pig.frenzy_end")
                    .withStyle(ChatFormatting.AQUA), true);
            syncSelf();
            return;
        }
        // 客户端在 clientTick 里自行倒计时，这里只做低频漂移校正，且只发给本人
        if (frenzyTicks % FRENZY_RESYNC_INTERVAL == 0) {
            syncSelf();
        }
    }

    @Override
    public void clientTick() {
        if (frenzyTicks > 0) {
            frenzyTicks--;
        }
    }

    /**
     * 疯魔模式期间持续推开路径上的邻近玩家，并登记以便环境致死时归因小脑惩罚。
     */
    private void pushPlayersOnPath(ServerPlayer sp) {
        long now = sp.level().getGameTime();
        for (ServerPlayer target : sp.serverLevel().getEntitiesOfClass(ServerPlayer.class,
                sp.getBoundingBox().inflate(PUSH_RANGE),
                p -> p != sp && GameUtils.isPlayerAliveAndSurvival(p))) {
            double dx = target.getX() - sp.getX();
            double dz = target.getZ() - sp.getZ();
            double lenSq = dx * dx + dz * dz;
            if (lenSq < 1.0e-4) {
                // 位置重叠时，用皮革噶的自身朝向作为推开方向
                float yawRad = sp.getYRot() * Mth.DEG_TO_RAD;
                dx = -Mth.sin(yawRad);
                dz = Mth.cos(yawRad);
                lenSq = 1.0;
            }
            double inv = 1.0 / Math.sqrt(lenSq);
            // 覆盖水平速度为推开方向（避免每 tick 叠加导致失控）；仅在着地时给一次小跳，避免持续升空
            double vy = target.onGround() ? 0.42 : target.getDeltaMovement().y;
            target.setDeltaMovement(dx * inv * PUSH_STRENGTH, vy, dz * inv * PUSH_STRENGTH);
            target.hurtMarked = true;
            PUSHED.put(target.getUUID(), new PushRecord(sp.getUUID(), now + PUSH_ATTRIBUTION_TICKS));
        }
    }

    /**
     * 注册皮革噶的相关事件：将"被推致死"的平民归因给皮革噶的。
     * 在 {@code ModRolesInitialEventRegister.register()} 中调用一次。
     *
     * <p>
     * 被推玩家若在归因窗口内因无归属的环境死亡（坠车/摔落/岩浆/溺水等）而死，
     * 且其为平民（乘客阵营），则将皮革噶的判定为真正击杀者。随后 {@code killPlayer}
     * 管线会触发 {@code OnTeammateKilledTeammate(innocent, innocent)} →
     * {@code XiaoNaoHandler} 施加小脑惩罚。
     */
    public static void registerEvents() {
        EarlyKillPlayer.FIND_KILLER_EVENT.register((victim, originalKiller, reason, force) -> {
            // 仅认领无归属的环境死亡
            if (force)
                return null;
            if (originalKiller != null) {
                return null;
            }
            if (!(victim instanceof ServerPlayer serverVictim)) {
                return null;
            }
            PushRecord record = PUSHED.get(serverVictim.getUUID());
            if (record == null) {
                return null;
            }
            if (serverVictim.level().getGameTime() > record.expiryGameTime()) {
                PUSHED.remove(serverVictim.getUUID());
                return null;
            }
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverVictim.level());
            SRERole victimRole = gameWorld.getRole(serverVictim);
            // 仅当目标为平民（乘客阵营/好人）时才归因并触发小脑惩罚
            if (victimRole == null || !victimRole.isInnocent()) {
                return null;
            }
            Player pusher = serverVictim.level().getPlayerByUUID(record.pusher());
            if (!(pusher instanceof ServerPlayer)) {
                return null;
            }
            if (!gameWorld.isRole(pusher, ModRoles.LEATHER_PIG) || !GameUtils.isPlayerAliveAndSurvival(pusher)) {
                return null;
            }
            PUSHED.remove(serverVictim.getUUID());
            return pusher;
        });
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) {
        // 伪装状态必须同步给所有客户端，否则其他玩家看不到猪模型（此前只有本人能看到自己的猪模型）。
        return true;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("disguised", disguised);
        tag.putInt("frenzyTicks", frenzyTicks);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        setDisguised(tag.getBoolean("disguised"));
        frenzyTicks = tag.getInt("frenzyTicks");
    }
}
