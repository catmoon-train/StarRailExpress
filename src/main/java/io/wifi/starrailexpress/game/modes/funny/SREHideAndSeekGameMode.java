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

package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.game.utils.RoleInstance;
import io.wifi.starrailexpress.network.original.AnnounceWelcomePayload;
import io.wifi.starrailexpress.progression.ProgressionDataManager;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.Level;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.RoleWeightedUtil;
import org.agmas.harpymodloader.commands.RoleCountManager;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.events.OnGamePlayerRolesConfirm;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.harpymodloader.modded_murder.RoleAssignmentPool;
import org.agmas.harpymodloader.modded_murder.ForceTeamInfo.ForceTeamType;
import org.agmas.noellesroles.commands.BroadcastCommand;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class SREHideAndSeekGameMode extends SREMurderGameMode {

    /** 剩余最后 1 分钟进入「终局阶段」：全体获得无限体力与发光 */
    public static final int FINAL_PHASE_SECONDS = 60;

    /** 寻找者被击杀后被控（无法移动 / 无法使用物品）的时长（tick） */
    public static final int SEEKER_STUN_DURATION = 6 * 20;

    /** 寻找者受击后再次受击的冷却时间（tick），冷却内不再重复触发控制 */
    public static final long SEEKER_HIT_COOLDOWN = 15L * 20L;

    /** 躲藏者开局的隐身药水时长（tick） */
    private static final int HIDER_INVISIBILITY_DURATION = 10 * 20;

    /** 速度 2 药水时长（tick） */
    private static final int SPEED_POTION_DURATION = 5 * 20;

    /** 躲藏者开局烟花火箭数量 */
    private static final int HIDER_FIREWORK_COUNT = 64;

    /** 寻找者开局获得的速度 2 药水数量 */
    private static final int SEEKER_SPEED_POTION_COUNT = 2;

    /** 寻找者受击冷却：玩家 UUID -> 上次受击的世界时间 */
    private final Map<UUID, Long> seekerHitTime = new HashMap<>();

    /** 是否已进入「最后 1 分钟」的终局阶段（避免每 tick 重复提示） */
    private boolean finalPhaseAnnounced = false;

    public SREHideAndSeekGameMode(ResourceLocation identifier) {
        super(identifier);
    }

    @Override
    public boolean shouldRecordPlayerStats() {
        return false;
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        Harpymodloader.setRoleMaximum(ModRoles.MAGICIAN_ID, 0);
        assignRole(serverWorld, gameWorldComponent,
                players);

        // 固定时长，由配置项 hideAndSeekGameTime 决定（单位：秒，默认 4 分钟）
        SREGameTimeComponent.KEY.get(serverWorld)
                .setResetTime(SREConfig.instance().hideAndSeekGameTime * 20);
        SREGameTimeComponent.KEY.get(serverWorld).reset();
        seekerHitTime.clear();
        finalPhaseAnnounced = false;

        AttributeModifier hiderModifier = new AttributeModifier(
                SRE.wifiId("hide_and_seek"), SREConfig.instance().hideAndSeekHiderScale,
                AttributeModifier.Operation.ADD_VALUE);
        for (ServerPlayer p : players) {
            if (!gameWorldComponent.isKillerTeam(p)) {
                Objects.requireNonNull(p.getAttribute(Attributes.SCALE)).addPermanentModifier(hiderModifier);
            }
        }

        giveStartingItems(gameWorldComponent, players);
    }

    /**
     * 开局发放道具。
     *
     * <p>躲藏者：1 瓶 10 秒隐身、1 瓶 5 秒速度 2、1 组（64 个）小型球状烟花火箭；
     * <p>寻找者：2 瓶 5 秒速度 2。
     */
    private void giveStartingItems(SREGameWorldComponent gameWorldComponent, List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            if (gameWorldComponent.isKillerTeam(player)) {
                for (int i = 0; i < SEEKER_SPEED_POTION_COUNT; i++) {
                    player.addItem(createPotion(MobEffects.MOVEMENT_SPEED, SPEED_POTION_DURATION, 1));
                }
            } else {
                player.addItem(createPotion(MobEffects.INVISIBILITY, HIDER_INVISIBILITY_DURATION, 0));
                player.addItem(createPotion(MobEffects.MOVEMENT_SPEED, SPEED_POTION_DURATION, 1));
                player.addItem(createSmallBallFirework(HIDER_FIREWORK_COUNT));
            }
        }
    }

    /** 构造药水物品（效果与缩放说明中一致，不显示粒子与图标，避免遮挡 HUD） */
    private static ItemStack createPotion(Holder<MobEffect> effect, int duration, int amplifier) {
        ItemStack potion = Items.POTION.getDefaultInstance();
        potion.set(DataComponents.POTION_CONTENTS,
                new PotionContents(Optional.empty(), Optional.of(16185078),
                        List.of(ModEffects.of(effect, duration, amplifier, false, false, true))));
        return potion;
    }

    /** 构造小型球状烟花火箭（构造方式与网警商店一致） */
    private static ItemStack createSmallBallFirework(int count) {
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET, count);
        rocket.set(DataComponents.FIREWORKS, new Fireworks(3, List.of(
                new FireworkExplosion(FireworkExplosion.Shape.SMALL_BALL,
                        new it.unimi.dsi.fastutil.ints.IntArrayList(new int[] { 0xFFFFFF }),
                        new it.unimi.dsi.fastutil.ints.IntArrayList(), false, false))));
        return rocket;
    }

    public static void assignRole(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        Map<Player, SRERole> roleAssignments = assignRolesToPlayers(serverWorld, players);
        OnGamePlayerRolesConfirm.EVENT.invoker().beforeAssignRole(serverWorld, roleAssignments);
        // 计算有特殊角色的玩家数量（用于AnnounceWelcomePayload）
        long killCount = roleAssignments.values().stream()
                .filter(role -> role != null && role != TMMRoles.CIVILIAN && role.canUseKiller())
                .count();
        // 统一应用角色分配并触发相应事件
        for (Map.Entry<Player, SRERole> entry : roleAssignments.entrySet()) {
            final var key = entry.getKey();
            final var value = entry.getValue();
            if (value != null) {
                gameWorldComponent.addRole(key, value, false);
                Harpymodloader.LOGGER.debug("Assigned role " + value.getIdentifier() + " to " + key.getName());
                if (value.canUseKiller()) {
                    SREPlayerShopComponent playerShopComponent = SREPlayerShopComponent.KEY.get(key);
                    if (playerShopComponent.balance < GameConstants.getMoneyStart())
                        playerShopComponent.setBalance(GameConstants.getMoneyStart());
                }
            } else {
                // 如果没有分配角色，则分配默认平民角色
                gameWorldComponent.addRole(key, TMMRoles.CIVILIAN, false);
                Harpymodloader.LOGGER
                        .debug("Assigned role " + TMMRoles.CIVILIAN.getIdentifier() + " to " + key.getName());
            }
        }

        gameWorldComponent.syncRoles();
        // 同步职业

        for (ServerPlayer player : players) {
            var role = gameWorldComponent.getRole(player);
            var roleType = PlayerRoleWeightManager.getRoleType(role);
            PlayerRoleWeightManager.addWeight(player, roleType, 1);
            // PlayerRoleWeightManager.
            ServerPlayNetworking.send(player,
                    new AnnounceWelcomePayload(role.getIdentifier().toString(), (int) killCount,
                            (int) (players.size() - killCount)));
            ModdedRoleAssigned.EVENT.invoker().assignModdedRole(player, role);
            if (roleType == 4) {
                player.addEffect(new MobEffectInstance(
                        ModEffects.MOVE_BANED,
                        (int) (30 * 20), // 持续时间（tick）
                        0, // 等级（0 = 速度 I）
                        false, // ambient（环境效果，如信标）
                        true, // showParticles（显示粒子）
                        true // showIcon（显示图标）
                ));
                player.addEffect(new MobEffectInstance(
                        ModEffects.USED_BANED,
                        (int) (30 * 20), // 持续时间（tick）
                        0, // 等级（0 = 速度 I）
                        false, // ambient（环境效果，如信标）
                        true, // showParticles（显示粒子）
                        true // showIcon（显示图标）
                ));
                player.addEffect(new MobEffectInstance(
                        ModEffects.SKILL_BANED,
                        (int) (30 * 20), // 持续时间（tick）
                        0, // 等级（0 = 速度 I）
                        false, // ambient（环境效果，如信标）
                        true, // showParticles（显示粒子）
                        true // showIcon（显示图标）
                ));

                player.addEffect(new MobEffectInstance(
                        ModEffects.TURN_BANED,
                        (int) (30 * 20), // 持续时间（tick）
                        0, // 等级（0 = 速度 I）
                        false, // ambient（环境效果，如信标）
                        true, // showParticles（显示粒子）
                        true // showIcon（显示图标）
                ));

                player.addEffect(new MobEffectInstance(
                        ModEffects.BLACK_MONITOR,
                        (int) (30 * 20), // 持续时间（tick）
                        0, // 等级（0 = 速度 I）
                        false, // ambient（环境效果，如信标）
                        true, // showParticles（显示粒子）
                        true // showIcon（显示图标）
                ));
            }
        }
        // 分配修饰符（修饰符放在职业分配后）
        Harpymodloader.FORCED_MODDED_ROLE.clear();
        PlayerRoleWeightManager.ForcePlayerTeam.clear();
    }

    private static Map<Player, SRERole> assignRolesToPlayers(ServerLevel serverWorld, List<ServerPlayer> players) {
        Map<Player, SRERole> roleAssignments = new HashMap<>();
        for (Player player : players) {
            roleAssignments.put(player, null);
        }

        // 第一步：处理强制分配的角色
        Map<UUID, SRERole> forcedRoles = new HashMap<>(Harpymodloader.FORCED_MODDED_ROLE);
        int killerCount = RoleCountManager.getKillerCount(players.size());
        int vigilanteCount = RoleCountManager.getVigilanteCount(players.size());
        int neutralsCount = 0;

        // 处理强制分配的角色，减少对应角色类型的数量需求
        for (Map.Entry<UUID, SRERole> entry : forcedRoles.entrySet()) {
            Player player = serverWorld.getPlayerByUUID(entry.getKey());
            if (player != null) {
                SRERole role = entry.getValue();
                if (role != null) {
                    roleAssignments.put(player, role);

                    // 根据角色类型减少对应的数量需求
                    if (role.canUseKiller()) {
                        killerCount--;
                    } else if (role.isVigilanteTeam()) {
                        vigilanteCount--;
                    } else if (!role.isInnocent()) {
                        neutralsCount--;
                    }
                }
            }
        }

        // 确保数量不为负数
        killerCount = Math.max(0, killerCount);
        vigilanteCount = Math.max(0, vigilanteCount);

        RoleAssignmentPool killerPool = RoleAssignmentPool.createUnlimited("Killer",
                role -> role.identifier() == SpecialGameModeRoles.SEEKER.identifier());
        RoleAssignmentPool vigilantePool = RoleAssignmentPool.create("Vigilante",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        role.isVigilanteTeam() && !role.isOtherModeRole());
        // 中立池
        RoleAssignmentPool neutralsPool = RoleAssignmentPool.create("Neutrals",
                role -> (!Harpymodloader.VANNILA_ROLES.contains(role) &&
                        !role.isOtherModeRole() &&
                        ((!role.canUseKiller() &&
                                !role.isInnocent()) || role.isNeutrals())
                        &&
                        role != TMMRoles.CIVILIAN));
        // 平民池（只包含真正的"平民"角色，例如医生等）
        RoleAssignmentPool civilianPool = RoleAssignmentPool.create("Civilian",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        !role.isOtherModeRole() &&
                        !role.isVigilanteTeam() &&
                        !role.canUseKiller() &&
                        !role.isNeutrals() &&
                        role.isInnocent() &&
                        role != TMMRoles.CIVILIAN);

        List<RoleInstance> expandedRoles = getAllRoles(killerCount, vigilanteCount, neutralsCount, players.size(),
                forcedRoles.size(), killerPool,
                neutralsPool, vigilantePool, civilianPool, false);

        RandomSource random = serverWorld.random;
        // 第五步：为未分配的玩家分配角色
        List<ServerPlayer> unassignedPlayers = new ArrayList<>();
        for (ServerPlayer player : players) {
            if (roleAssignments.get(player) == null) {
                unassignedPlayers.add(player);
            }
        }
        // 保底
        for (var p : players) {
            if (PlayerRoleWeightManager.ForcePlayerTeam.containsKey(p.getUUID()))
                continue;
            var manager = PlayerRoleWeightManager.playerWeights.get(p.getUUID());
            if (manager != null) {
                if (manager.getStreakCount() >= random.nextInt(4, 7)) {
                    int highestWeightType = PlayerRoleWeightManager.getHighestScoredType(p.getUUID());
                    if (highestWeightType == manager.getLastAssignedFactionGroup())
                        continue;
                    PlayerRoleWeightManager.forceTeam(p.getUUID(), highestWeightType, ForceTeamType.ROLE_WEIGHTS);
                }
            }
        }
        // 创建权重分布用于分配展开后的角色
        List<Map.Entry<RoleInstance, Float>> roleWeights = new ArrayList<>();

        for (var role : expandedRoles) {
            roleWeights.add(new AbstractMap.SimpleEntry<>(role,
                    HarpyModLoaderConfig.HANDLER.instance().roleWeights.getOrDefault(role.role().identifier(), 1f)));
        }
        Collections.shuffle(roleWeights);
        final var collect = roleWeights
                .stream()
                .collect(Collectors.toMap(
                        a -> a.getKey(),
                        a -> a.getValue(),
                        (existing, replacement) -> existing, // 如果键重复，保留第一个值
                        LinkedHashMap::new));
        var hashMap = new LinkedHashMap<>(collect);
        {
            var roleSelectors = new HashMap<Integer, RoleWeightedUtil>();
            {
                var roleIdToRoleMaps = new HashMap<Integer, HashMap<RoleInstance, Float>>();
                for (var entry : hashMap.entrySet()) {
                    var role = entry.getKey();
                    Float roleWeight = entry.getValue();
                    int roleType = PlayerRoleWeightManager.getRoleType(role.role());
                    if (!roleIdToRoleMaps.containsKey(roleType)) {
                        roleIdToRoleMaps.put(roleType, new HashMap<>());
                    }
                    roleIdToRoleMaps.get(roleType).put(role, roleWeight);
                }
                for (var entry : roleIdToRoleMaps.entrySet()) {
                    roleSelectors.putIfAbsent(entry.getKey(), new RoleWeightedUtil(entry.getValue()));
                }
            }
            {
                // 分配forceTeam
                for (var entry : PlayerRoleWeightManager.ForcePlayerTeam.entrySet()) {
                    UUID playerUid = entry.getKey();
                    var selectedPlayer = unassignedPlayers.stream().filter((p) -> p.getUUID().equals(playerUid))
                            .findFirst().orElse(null);
                    if (selectedPlayer == null)
                        continue;
                    var t = entry.getValue();
                    int roleType = t.roleType();
                    var roleSelector = roleSelectors.get(roleType);
                    if (roleSelector == null)
                        continue;
                    RoleInstance roleInstant = roleSelector.selectRandomKeyBasedOnWeightsAndRemoved();
                    SRERole selectedRole = null;
                    if (roleInstant != null) {
                        hashMap.remove(roleInstant);
                        selectedRole = roleInstant.role();
                        roleAssignments.put(selectedPlayer, selectedRole);
                        unassignedPlayers.remove(selectedPlayer);
                        Harpymodloader.LOGGER.debug(
                                "Assign player [{}] to {} ({})",
                                playerUid, selectedRole.getIdentifier().toString(),
                                roleType);
                    } else {
                        PlayerRoleWeightManager.boostKillerSideAfterForceFailure(playerUid);
                        Harpymodloader.LOGGER.warn(
                                "Couldn't force player [{}]'s role to {} because there are no roles available for him.",
                                playerUid,
                                roleType);
                        FactionCardType cardType = FactionCardType.fromRoleType(roleType);
                        if (cardType != FactionCardType.NONE && t.type() == ForceTeamType.CARD) {
                            ProgressionDataManager.addFactionCard(selectedPlayer, cardType, 1);
                            BroadcastCommand.BroadcastMessage(selectedPlayer,
                                    Component.translatable("message.sre.pass.faction.assign_failed")
                                            .withStyle(ChatFormatting.RED));
                        }
                    }
                }
            }
        }
        RoleWeightedUtil roleSelector = new RoleWeightedUtil(hashMap);
        // 分配展开后的角色给未分配的玩家

        Collections.shuffle(unassignedPlayers);
        while (unassignedPlayers.size() > 0 && roleSelector.size() > 0) {
            RoleInstance roleInstant = roleSelector.selectRandomKeyBasedOnWeightsAndRemoved();
            SRERole selectedRole = null;
            if (roleInstant != null) {
                selectedRole = roleInstant.role();
            }
            if (selectedRole != null) {
                int selectedRoleType = PlayerRoleWeightManager.getRoleType(selectedRole);
                Player selectedPlayer = pickPlayerWithProgressBias(serverWorld, unassignedPlayers, selectedRoleType);
                if (selectedPlayer != null) {
                    unassignedPlayers.remove(selectedPlayer);
                    roleAssignments.put(selectedPlayer, selectedRole);
                    ProgressionDataManager.onRoleAssigned(selectedPlayer, selectedRole);
                }
            }
        }
        for (var up : unassignedPlayers) {
            // 职业不够分配平民
            roleAssignments.put(up, TMMRoles.CIVILIAN);
            ProgressionDataManager.onRoleAssigned(up, TMMRoles.CIVILIAN);
        }
        return roleAssignments;
    }

    /**
     * 阻止躲藏者之间/谋杀者之间互相伤害，以及谋杀者死亡后返回出生点。
     * 
     * @param victim      受害者
     * @param spawnBody   生成尸体
     * @param _killer     杀手（为空认为无杀手）
     * @param deathReason 死亡原因
     * @param forceDeath  强制死亡
     */
    public void killPlayer(Player victim, boolean spawnBody, @Nullable Player _killer,
            ResourceLocation deathReason, boolean forceDeath) {
        Level level = victim.level();
        var gameWorldComponent = SREGameWorldComponent.KEY.get(level);
        if (_killer != null) {
            if (gameWorldComponent.getRoleType(victim) == gameWorldComponent.getRoleType(_killer)) {
                return;
            }
            if (gameWorldComponent.isKillerTeam(victim) && gameWorldComponent.isKillerTeam(_killer))
                return;
            if (!gameWorldComponent.isKillerTeam(victim) && !gameWorldComponent.isKillerTeam(_killer))
                return;
        }
        if (gameWorldComponent.isKillerTeam(victim)) {
            GameUtils.teleportBackToRoom(victim);
            UUID killerUuid = null;
            if (_killer != null) {
                killerUuid = _killer.getUUID();
            }
            SRE.REPLAY_MANAGER.recordPlayerKill(killerUuid, victim.getUUID(), deathReason);
            SRE.REPLAY_MANAGER.recordPlayerNotKilled(
                    _killer,
                    victim,
                    deathReason);
            // 寻找者不会死亡，但会被短暂控制；15 秒内再次受击不重复触发
            applySeekerStun(victim, level);
            return;
        }
        super.killPlayer(victim, spawnBody, _killer, deathReason, forceDeath);
    }

    /**
     * 寻找者被击杀后的控制：6 秒内无法移动且无法使用物品。
     *
     * <p>受击后 15 秒内的再次受击不会重新施加控制，避免被连续集火锁死。
     */
    private void applySeekerStun(Player victim, Level level) {
        if (!(victim instanceof ServerPlayer seeker)) {
            return;
        }
        long currentTick = level.getGameTime();
        Long lastHit = seekerHitTime.get(seeker.getUUID());
        if (lastHit != null && currentTick - lastHit < SEEKER_HIT_COOLDOWN) {
            // 冷却中：仅保留原有的传送与扣时，不再叠加控制
            return;
        }
        seekerHitTime.put(seeker.getUUID(), currentTick);
        seeker.addEffect(ModEffects.of(ModEffects.MOVE_BANED, SEEKER_STUN_DURATION, 0, false, true, true));
        seeker.addEffect(ModEffects.of(ModEffects.USED_BANED, SEEKER_STUN_DURATION, 0, false, true, true));
    }

    @Override
    public void addKillRewardTime(SREGameTimeComponent gameTimeComponent) {
        // 寻找者击杀躲藏者不增加时间
    }

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        super.tickServerGameLoop(serverWorld, gameWorldComponent);
        if (gameWorldComponent.getGameStatus() != SREGameWorldComponent.GameStatus.ACTIVE) {
            return;
        }
        SREGameTimeComponent timeComponent = SREGameTimeComponent.KEY.get(serverWorld);
        if (!timeComponent.hasTime() || timeComponent.getTime() > FINAL_PHASE_SECONDS * 20) {
            return;
        }
        // 终局阶段：全体无限体力 + 发光。以较短时长续期，避免覆盖其它来源的同类效果
        for (ServerPlayer player : serverWorld.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                continue;
            }
            player.addEffect(ModEffects.of(ModEffects.INFINITE_STAMINA, 100, 0, true, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, true, false, true));
        }
        if (!finalPhaseAnnounced) {
            finalPhaseAnnounced = true;
            for (ServerPlayer player : serverWorld.players()) {
                player.sendSystemMessage(
                        Component.translatable("message.gamemode.hide_and_seek.final_phase")
                                .withStyle(ChatFormatting.GOLD));
            }
        }
    }

    public static final int WARNING_DISTANCE = 8;

    @Override
    public boolean canAllPeopleSeeTime() {
        return true;
    }

    @Override
    public void tickClientGameLoop(Level world) {
        super.tickClientGameLoop(world);
        if (world.getGameTime() % 5 != 0 || SREClient.cached_player == null) {
            return;
        }
        var self = SREClient.cached_player;
        boolean isKillerTeam = SREClient.gameComponent.isKillerTeam(self);
        // 统计以自己为中心、半径 WARNING_DISTANCE 格内的敌对方（寻找者 / 躲藏者）人数
        long nearbyCount = world.players().stream()
                .filter(p -> p != self)
                .filter(p -> p.distanceToSqr(self) <= WARNING_DISTANCE * WARNING_DISTANCE)
                .filter(p -> SREClient.gameComponent.isKillerTeam(p) != isKillerTeam)
                .count();
        if (nearbyCount > 0) {
            self.displayClientMessage(Component
                    .translatable("gui.sre.gamemode.hide_and_seek", WARNING_DISTANCE, (int) nearbyCount,
                            isKillerTeam ? Component.translatable("gui.sre.gamemode.hider")
                                    : Component.translatable("gui.sre.gamemode.seeker"))
                    .withStyle(ChatFormatting.GOLD), true);
        }
    }
}
