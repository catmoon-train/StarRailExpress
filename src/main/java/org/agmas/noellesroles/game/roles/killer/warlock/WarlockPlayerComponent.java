package org.agmas.noellesroles.game.roles.killer.warlock;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.data.PlayerEconomyManager;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 咒术师（Warlock）玩家组件 —— 重做版。
 *
 * 核心循环：<b>窃取发肤 → 蚀骨之咒 / 领域展开</b>
 * <ul>
 * <li><b>窃取发肤</b>（G）：近身从目标身上悄悄取得一份「咒物」（头发 / 血肉），
 * 每名玩家整局只能被窃取一次。目标只会收到一句模糊的寒意提示。</li>
 * <li><b>蚀骨之咒</b>（V 切换）：消耗一份咒物诅咒其主人 —— 目标反胃、短暂迟缓，
 * 周身萦绕灵魂颗粒；若诅咒未解时目标死亡（不限死因），咒术师收取咒酬。</li>
 * <li><b>领域展开·灰髓之境</b>（潜行+技能键）：消耗至多 {@value WarlockDomainManager#MAX_VICTIMS}
 * 份咒物，将其主人连同自己一并拉入灰雾领域（高空异空间，见
 * {@link WarlockDomainManager}），域内自由猎杀；杀死咒术师即可立刻破界而出。</li>
 * </ul>
 * 技能冷却统一交给 {@code RoleSkill}（见 {@code ModRolesInitialEventRegister}），
 * 组件只负责咒物 / 诅咒 / 领域状态与同步。
 */
public class WarlockPlayerComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<WarlockPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "warlock"),
            WarlockPlayerComponent.class);

    /** 窃取发肤的最大距离。 */
    public static final double STEAL_RANGE = 4.0D;
    /** 蚀骨之咒持续时间（tick）。 */
    public static final int CURSE_DURATION_TICKS = 45 * 20;
    /** 蚀咒目标自动搜索半径（未瞄准咒物主人时取最近者）。 */
    public static final double CURSE_AUTO_RANGE = 40.0D;
    /** 被诅咒者死亡时咒术师获得的咒酬。 */
    public static final int CURSE_REWARD_COINS = 40;

    static {
        // 被诅咒者死亡（不限死因）→ 咒术师收取咒酬
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> rewardCurseOnDeath(victim));
        OnPlayerDeath.EVENT.register((victim, deathReason) -> rewardCurseOnDeath(victim));
    }

    private final Player player;
    /** 已窃取咒物的玩家（发肤主人）。 */
    public final Set<UUID> essences = new LinkedHashSet<>();
    /** 已被窃取过的玩家（包括咒物已消耗的），保证每人整局只能被窃取一次。 */
    public final Set<UUID> everStolen = new LinkedHashSet<>();
    @Nullable
    public UUID curseTarget;
    public long curseEndTick;
    /** 领域是否展开（由 {@link WarlockDomainManager} 维护，同步给 HUD）。 */
    public boolean domainOpen;
    public long domainEndTick;

    public WarlockPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        essences.clear();
        everStolen.clear();
        curseTarget = null;
        curseEndTick = 0;
        domainOpen = false;
        domainEndTick = 0;
        sync();
    }

    @Override
    public void clear() {
        if (player instanceof ServerPlayer sp) {
            WarlockDomainManager.forceEnd(sp.getUUID(), sp.server);
        }
        init();
    }

    public void sync() {
        KEY.sync(player);
    }

    public boolean isActiveWarlock() {
        if (player == null || player.level().isClientSide())
            return false;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        return gameWorld.isRole(player, ModRoles.WARLOCK);
    }

    // ── 技能一：窃取发肤 ─────────────────────────────────────────

    public boolean trySteal(@Nullable ServerPlayer target) {
        if (!(player instanceof ServerPlayer sp) || !isActiveWarlock())
            return false;
        if (!io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(sp))
            return false;
        if (target == null || target == sp
                || !io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(target)) {
            fail(sp, "message.noellesroles.warlock.steal_no_target");
            return false;
        }
        if (sp.distanceTo(target) > STEAL_RANGE) {
            fail(sp, "message.noellesroles.warlock.steal_too_far");
            return false;
        }
        if (everStolen.contains(target.getUUID())) {
            fail(sp, "message.noellesroles.warlock.steal_already");
            return false;
        }

        essences.add(target.getUUID());
        everStolen.add(target.getUUID());
        sync();

        ServerLevel level = sp.serverLevel();
        level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + 1.0D, target.getZ(),
                6, 0.25D, 0.4D, 0.25D, 0.01D);
        level.playSound(null, target.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS,
                0.4F, 0.6F);

        sp.displayClientMessage(Component
                .translatable("message.noellesroles.warlock.stolen", target.getName().getString())
                .withStyle(ChatFormatting.DARK_PURPLE), true);
        // 目标只得到一句模糊的寒意提示，不暴露咒术师
        target.displayClientMessage(Component
                .translatable("message.noellesroles.warlock.steal_victim_hint")
                .withStyle(ChatFormatting.DARK_GRAY), true);
        return true;
    }

    // ── 技能二：蚀骨之咒 ─────────────────────────────────────────

    public boolean tryCurse(@Nullable ServerPlayer crosshair) {
        if (!(player instanceof ServerPlayer sp) || !isActiveWarlock())
            return false;
        if (!io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(sp))
            return false;
        if (essences.isEmpty()) {
            fail(sp, "message.noellesroles.warlock.no_essence");
            return false;
        }

        ServerPlayer target = null;
        if (crosshair != null && essences.contains(crosshair.getUUID())
                && io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(crosshair)) {
            target = crosshair;
        } else {
            double best = CURSE_AUTO_RANGE * CURSE_AUTO_RANGE;
            for (UUID uuid : essences) {
                ServerPlayer candidate = sp.server.getPlayerList().getPlayer(uuid);
                if (candidate == null
                        || !io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(candidate))
                    continue;
                double dist = sp.distanceToSqr(candidate);
                if (dist < best) {
                    best = dist;
                    target = candidate;
                }
            }
        }
        if (target == null) {
            fail(sp, "message.noellesroles.warlock.curse_no_target");
            return false;
        }

        essences.remove(target.getUUID());
        curseTarget = target.getUUID();
        curseEndTick = sp.level().getGameTime() + CURSE_DURATION_TICKS;
        sync();

        target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, CURSE_DURATION_TICKS, 0, false, false, false));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 8 * 20, 0, false, false, false));
        target.serverLevel().playSound(null, target.blockPosition(), SoundEvents.WARDEN_HEARTBEAT,
                SoundSource.PLAYERS, 1.2F, 0.7F);
        target.displayClientMessage(Component
                .translatable("message.noellesroles.warlock.curse_victim_hint")
                .withStyle(ChatFormatting.DARK_PURPLE), true);
        sp.displayClientMessage(Component
                .translatable("message.noellesroles.warlock.cursed", target.getName().getString())
                .withStyle(ChatFormatting.DARK_PURPLE), true);
        return true;
    }

    // ── 技能三：领域展开 ─────────────────────────────────────────

    public boolean tryOpenDomain() {
        if (!(player instanceof ServerPlayer sp) || !isActiveWarlock())
            return false;
        if (!io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(sp))
            return false;
        return WarlockDomainManager.open(sp, this);
    }

    // ── Tick / 事件 ────────────────────────────────────────────

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp) || !isActiveWarlock())
            return;
        long gameTime = sp.level().getGameTime();

        if (curseTarget != null) {
            if (gameTime >= curseEndTick) {
                curseTarget = null;
                curseEndTick = 0;
                sync();
            } else if (gameTime % 15 == 0) {
                ServerPlayer target = sp.server.getPlayerList().getPlayer(curseTarget);
                if (target != null
                        && io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(target)) {
                    target.serverLevel().sendParticles(ParticleTypes.SOUL,
                            target.getX(), target.getY() + 0.9D, target.getZ(), 2, 0.2D, 0.35D, 0.2D, 0.005D);
                }
            }
        }
    }

    private static void rewardCurseOnDeath(Player victim) {
        if (!(victim instanceof ServerPlayer sv))
            return;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sv.level());
        for (ServerPlayer candidate : sv.server.getPlayerList().getPlayers()) {
            if (!gameWorld.isRole(candidate, ModRoles.WARLOCK))
                continue;
            WarlockPlayerComponent comp = KEY.maybeGet(candidate).orElse(null);
            if (comp == null || comp.curseTarget == null || !comp.curseTarget.equals(sv.getUUID()))
                continue;
            if (sv.level().getGameTime() >= comp.curseEndTick)
                continue;
            comp.curseTarget = null;
            comp.curseEndTick = 0;
            PlayerEconomyManager.addCoinNum(candidate, CURSE_REWARD_COINS);
            candidate.displayClientMessage(Component
                    .translatable("message.noellesroles.warlock.curse_reward", CURSE_REWARD_COINS)
                    .withStyle(ChatFormatting.GOLD), true);
            comp.sync();
        }
    }

    private static void fail(ServerPlayer sp, String key) {
        sp.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
    }

    // ── 同步 ────────────────────────────────────────────────────

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        ListTag list = new ListTag();
        for (UUID uuid : essences) {
            list.add(StringTag.valueOf(uuid.toString()));
        }
        tag.put("essences", list);
        tag.putBoolean("domainOpen", domainOpen);
        tag.putLong("domainEndTick", domainEndTick);
        tag.putLong("curseEndTick", curseEndTick);
        if (curseTarget != null)
            tag.putString("curseTarget", curseTarget.toString());
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        essences.clear();
        for (Tag entry : tag.getList("essences", Tag.TAG_STRING)) {
            essences.add(UUID.fromString(entry.getAsString()));
        }
        domainOpen = tag.getBoolean("domainOpen");
        domainEndTick = tag.getLong("domainEndTick");
        curseEndTick = tag.getLong("curseEndTick");
        curseTarget = tag.contains("curseTarget") ? UUID.fromString(tag.getString("curseTarget")) : null;
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider lookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider lookup) {
    }
}
