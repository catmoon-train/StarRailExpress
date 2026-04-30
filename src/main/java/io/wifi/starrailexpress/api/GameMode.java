package io.wifi.starrailexpress.api;

import io.wifi.starrailexpress.DeathInfo;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.replay.GameReplayData;
import io.wifi.starrailexpress.api.replay.GameReplayManager;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import io.wifi.starrailexpress.cca.SREPlayerProgressionComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.cca.SREPlayerStatsComponent;
import io.wifi.starrailexpress.compat.TrainVoicePlugin;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeath;
import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.AllowPlayerDeath;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.AllowSpectatorPlayerInAreas;
import io.wifi.starrailexpress.event.EarlyKillPlayer;
import io.wifi.starrailexpress.event.OnGiveKillerBalance;
import io.wifi.starrailexpress.event.OnKillPlayerTriggered;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.OnPlayerKilledPlayer;
import io.wifi.starrailexpress.event.OnPlayerKilledPlayerIdentifier;
import io.wifi.starrailexpress.event.OnShieldBroken;
import io.wifi.starrailexpress.event.OnTeammateKilledTeammate;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.network.BreakArmorPayload;
import io.wifi.starrailexpress.network.PlayerDeathPayload;
import io.wifi.starrailexpress.network.TriggerScreenEdgeEffectPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.game.roles.Innocent.coroner.BodyDeathReasonComponent;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.jetbrains.annotations.Nullable;

public abstract class GameMode {
    public final ResourceLocation identifier;
    public final int defaultStartTime;
    public final int minPlayerCount;

    /**
     * @param identifier       游戏的id
     * @param defaultStartTime 默认游戏时长（分钟）
     * @param minPlayerCount   最小启动玩家人数
     */
    public GameMode(ResourceLocation identifier, int defaultStartTime, int minPlayerCount) {
        this.identifier = identifier;
        this.defaultStartTime = defaultStartTime;
        this.minPlayerCount = minPlayerCount;
    }

    public void writeToNbt(CompoundTag nbtCompound, HolderLookup.Provider wrapperLookup) {
    }

    public void readFromNbt(CompoundTag nbtCompound, HolderLookup.Provider wrapperLookup) {
    }

    /**
     * 服务端/客户端共通主循环
     * 
     * @return
     */
    public void tickCommonGameLoop(Level level) {
    }

    /**
     * 客户端游戏主循环
     * 
     * @return
     */
    public void tickClientGameLoop(Level level) {
    }

    /**
     * 此模式是否计入玩家数据
     * 
     * @return
     */
    public boolean shouldRecordPlayerStats() {
        return false;
    }

    /**
     * 此模式是否必须有职业（true将导致没职业的玩家变旁观）
     * 
     * @return
     */
    public boolean requiresAssignedRole() {
        return true;
    }

    /**
     * 获胜者是否只有一人
     * 
     * @return
     */
    public boolean onlyOneWinner() {
        return this.isLooseEndMode();
    }

    /**
     * 是否是亡命徒模式
     * 
     * @return
     */
    public boolean isLooseEndMode() {
        return false;
    }

    /**
     * 记录玩家数据
     * 
     * @param serverWorld
     * @param gameComponent
     * @param readyPlayerList
     */
    public void recordPlayerStats(ServerLevel serverWorld, SREGameWorldComponent gameComponent,
            ArrayList<ServerPlayer> readyPlayerList) {
        if (shouldRecordPlayerStats()) {
            GameUtils.recordPlayerStats(serverWorld, gameComponent, readyPlayerList);
        }
    }

    /**
     * 在游戏开始initializeGame后触发，在OnGameTrueStarted前触发
     * 
     * @param serverWorld
     * @param gameComponent
     * @param readyPlayerList
     */
    public void gameTrueStarted(ServerLevel serverWorld, SREGameWorldComponent gameComponent,
            ArrayList<ServerPlayer> readyPlayerList) {
        int SAFE_TIME_COOLDOWN = SREConfig.instance().safeTimeCooldown * 20;
        if (hasSafeTime()) {
            GameUtils.addItemCooldowns(serverWorld, SAFE_TIME_COOLDOWN);
        }
        GameUtils.executeFunction(serverWorld.getServer().createCommandSourceStack(),
                "harpymodloader:start_game_" + AreasWorldComponent.KEY.get(serverWorld).mapName);
    }

    /**
     * 是否启用区域外检测（主要是y轴和水）
     * 
     * @return
     */
    public boolean enforcesPlayAreaElimination() {
        return true;
    }

    /**
     * 服务器游戏主循环
     * 
     * @param serverWorld
     * @param gameWorldComponent
     */

    public abstract void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent);

    /**
     * 初始化游戏，还未正式开始。
     * 
     * @param serverWorld
     * @param gameWorldComponent
     * @param players
     */
    public abstract void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players);

    /**
     * 在initializeGame前执行（baseInitialize）
     * 
     * @param serverWorld
     * @param gameWorldComponent
     * @param players
     */
    public void beforeInitializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        GameUtils.baseInitialize(serverWorld, gameWorldComponent, players);
    }

    /**
     * 游戏结束
     * 
     * @param serverWorld
     * @param gameWorldComponent
     */
    public void finalizeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
    }

    public boolean hasSafeTime() {
        return true;
    }

    public boolean hasMood() {
        return true;
    }

    public Component getName() {
        // 根据游戏模式ID返回本地化的名称
        String gameModeId = this.identifier.getPath();
        return Component.translatableWithFallback("hud.sre.tip.gamemode." + gameModeId, gameModeId);
    }

    /**
     * 在initializeGame后执行
     * 
     * @param serverWorld
     * @param gameComponent
     * @param readyPlayerList
     */
    public void afterInitializeGame(ServerLevel serverWorld, SREGameWorldComponent gameComponent,
            ArrayList<ServerPlayer> readyPlayerList) {
        // 初始化回放管理器,此时角色已经分配完成
        SRE.REPLAY_MANAGER.initializeReplay(readyPlayerList, gameComponent.getRoles());
        // 记录游戏开始事件
        SRE.REPLAY_MANAGER.addEvent(GameReplayData.EventType.GAME_START, null, null, null, null);

        // Update replay with actual roles after assignment
        SRE.REPLAY_MANAGER.updateRolesFromComponent(gameComponent);
    }

    /**
     * 触发游戏结束时触发（并未返回大厅）
     * 
     * @param world
     */
    public void stopGame(ServerLevel world) {
    }

    /**
     * 记录玩家胜利
     * 
     * @param world
     * @param roundEnd
     * @param gameComponent
     */
    public void recordWinStats(ServerLevel world, SREGameRoundEndComponent roundEnd,
            SREGameWorldComponent gameComponent) {
        if (shouldRecordPlayerStats()) {
            GameUtils.recordWinStats(world, roundEnd, gameComponent, this.onlyOneWinner());

        }
    }

    /**
     * 游戏结束后显示replay调用
     * 
     * @param world
     * @param roundEnd
     * @param gameComponent
     */
    public void showReplay(ServerLevel world, SREGameRoundEndComponent roundEnd, SREGameWorldComponent gameComponent) {
        Component text = SRE.REPLAY_MANAGER.generateReplay();
        for (ServerPlayer player : world.players()) {
            GameReplayManager.sendSystemMessage(player, text);
        }
    }

    /**
     * 限制旁观者的游戏区域
     * 
     * @param player
     * @param gameWorldComponent
     * @param areas
     */
    public void limitSpectatorPlayer(ServerPlayer player, SREGameWorldComponent gameWorldComponent,
            AreasWorldComponent areas) {
        if (!AllowSpectatorPlayerInAreas.EVENT.invoker().allowInAreas(player)) {
            GameUtils.limitPlayerToBox(player, areas.getPlayArea());
        }
    }

    /**
     * 尝试杀死玩家时触发（GameUtils.killPlayer传递）
     * 
     * @param victim      受害者
     * @param spawnBody   生成尸体
     * @param _killer     杀手（为空认为无杀手）
     * @param deathReason 死亡原因
     * @param forceDeath  强制死亡
     */
    public void killPlayer(Player victim, boolean spawnBody, @Nullable Player _killer,
            ResourceLocation deathReason, boolean forceDeath) {
        Player trueKiller = EarlyKillPlayer.FIND_KILLER_EVENT.invoker().findTrueKiller(victim, _killer, deathReason);
        Player killer;
        if (trueKiller != null)
            killer = trueKiller;
        else
            killer = _killer;
        _killer = killer;
        OnKillPlayerTriggered.EVENT.invoker().onKillPlayerTriggered(victim, spawnBody, killer, deathReason, forceDeath);
        SREPlayerPsychoComponent component = SREPlayerPsychoComponent.KEY.get(victim);
        if (killer != null && killer instanceof ServerPlayer serverPlayer) {
            final var triggerScreenEdgeEffectPayload = new TriggerScreenEdgeEffectPayload(java.awt.Color.WHITE.getRGB(),
                    600,
                    0.6f);
            ServerPlayNetworking.send(serverPlayer, triggerScreenEdgeEffectPayload);
        }

        boolean canDeath = true;
        Component deathMessageComponent = Component.translatable("message.death_reason.null");
        if (victim instanceof ServerPlayer serverVictim) {
            deathMessageComponent = SRE.REPLAY_MANAGER.recordPlayerKill(killer != null ? killer.getUUID() : null,
                    serverVictim.getUUID(),
                    deathReason);
        }

        // Check if victim has a role assigned - if not, skip role-dependent logic
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(victim.level());
        SRERole role = gameWorldComponent.getRole(victim);
        if (role == null) {
            // Player doesn't have a role (game not started or joined mid-game), don't kill
            // them
            return;
        }
        if (killer != null) {
            if (killer instanceof ServerPlayer spkiller) {
                if (victim instanceof ServerPlayer spvictim) {
                    OnPlayerKilledPlayer.DeathReason eventDeathReason;
                    switch (deathReason.getPath()) {
                        case "derringer_shot":
                        case "revolver_shot":
                        case "gun_shot":
                            eventDeathReason = OnPlayerKilledPlayer.DeathReason.GUN_SHOOT;
                            break;
                        case "knife_stab":
                            eventDeathReason = OnPlayerKilledPlayer.DeathReason.KNIFE;
                            break;
                        case "grenade":
                            eventDeathReason = OnPlayerKilledPlayer.DeathReason.GRENADE;
                            break;
                        case "bat_hit":
                            eventDeathReason = OnPlayerKilledPlayer.DeathReason.BAT;
                            break;
                        case "poison":
                            eventDeathReason = OnPlayerKilledPlayer.DeathReason.POISON;
                            break;
                        case "arrow":
                            eventDeathReason = OnPlayerKilledPlayer.DeathReason.ARROW;
                            break;
                        case "trident":
                            eventDeathReason = OnPlayerKilledPlayer.DeathReason.TRIDENT;
                            break;
                        default:
                            eventDeathReason = OnPlayerKilledPlayer.DeathReason.UNKNOWN;
                    }
                    // LoggerFactory.getLogger("death_Reason").info(deathReason.getPath());
                    OnPlayerKilledPlayer.EVENT.invoker().playerKilled(spvictim, spkiller, eventDeathReason);
                    OnPlayerKilledPlayerIdentifier.EVENT.invoker().playerKilled(spvictim, spkiller, deathReason);
                }
            }
        }
        if (!role.allowDeath(victim, killer, deathReason, spawnBody)) {
            if (!forceDeath) {
                if (victim instanceof ServerPlayer serverVictim) {
                    SRE.REPLAY_MANAGER.recordPlayerNotKilled(
                            killer,
                            serverVictim,
                            deathReason);
                }
                return;
            }
        }
        if (!AllowPlayerDeath.EVENT.invoker().allowDeath(victim, deathReason))
            if (!forceDeath) {
                if (victim instanceof ServerPlayer serverVictim) {
                    SRE.REPLAY_MANAGER.recordPlayerNotKilled(
                            killer,
                            serverVictim,
                            deathReason);
                }
                return;
            }
        if (!AllowPlayerDeathWithKiller.EVENT.invoker().allowDeath(victim, killer, deathReason))
            if (!forceDeath) {
                if (victim instanceof ServerPlayer serverVictim) {
                    SRE.REPLAY_MANAGER.recordPlayerNotKilled(
                            killer,
                            serverVictim,
                            deathReason);
                }
                return;
            }
        if (killer != null) {
            if (killer instanceof ServerPlayer spkiller) {
                SREArmorPlayerComponent bartenderPlayerComponent = SREArmorPlayerComponent.KEY.get(victim);
                if (bartenderPlayerComponent != null) {
                    if (bartenderPlayerComponent.getArmor() > 0) {
                        boolean cantDefend = SRE.canStickArmor.stream().anyMatch((pre) -> {
                            return pre.test(new DeathInfo(victim, killer, deathReason));
                        });
                        if (!cantDefend) {
                            victim.displayClientMessage(Component.translatable("message.bartender.armor_broke_self")
                                    .withStyle(ChatFormatting.YELLOW), true);
                            victim.playNotifySound(TMMSounds.ITEM_PSYCHO_ARMOUR,
                                    SoundSource.MASTER, 5.0F, 1.0F);
                            bartenderPlayerComponent.removeArmor();
                            SRE.REPLAY_MANAGER.breakArmor(victim.getUUID());
                            SRE.REPLAY_MANAGER.recordPlayerNotKilled(
                                    killer,
                                    victim,
                                    deathReason);
                            ServerPlayNetworking.send(spkiller,
                                    new BreakArmorPayload(victim.getX(), victim.getY(), victim.getZ()));
                            OnShieldBroken.EVENT.invoker().onShieldBroken(victim, killer);
                            if (!forceDeath)
                                return;
                        }
                    }
                }
            }
        }

        if (component.getPsychoTicks() > 0) {
            if (!forceDeath && component.getArmour() > 0) {
                component.setArmour(component.getArmour() - 1);
                if (SRE.REPLAY_MANAGER != null) {
                    SRE.REPLAY_MANAGER.breakArmor(victim.getUUID());
                }

                victim.displayClientMessage(Component.translatable("message.bartender.armor_broke_self")
                        .withStyle(ChatFormatting.YELLOW), true);
                SRE.REPLAY_MANAGER.recordPlayerNotKilled(
                        killer,
                        victim,
                        deathReason);
                if (killer instanceof ServerPlayer serverPlayer) {
                    ServerPlayNetworking.send(serverPlayer,
                            new BreakArmorPayload(victim.getX(), victim.getY(), victim.getZ()));
                }
                component.sync();
                victim.playNotifySound(TMMSounds.ITEM_PSYCHO_ARMOUR, SoundSource.MASTER, 5F, 1F);
                victim.displayClientMessage(Component.translatable("message.bartender.armor_broke_self")
                        .withStyle(ChatFormatting.YELLOW), true);

                if (!forceDeath)
                    return;
            } else {
                component.stopPsycho();
                component.sync();
            }
        }
        if (!role.afterShieldAllowDeath(victim, killer, deathReason, spawnBody)) {
            if (!forceDeath) {
                if (victim instanceof ServerPlayer serverVictim) {
                    SRE.REPLAY_MANAGER.recordPlayerNotKilled(
                            killer,
                            serverVictim,
                            deathReason);
                }
                return;
            }
        }
        if (!AfterShieldAllowPlayerDeath.EVENT.invoker().allowDeath(victim, deathReason))
            if (!forceDeath) {
                if (victim instanceof ServerPlayer serverVictim) {
                    SRE.REPLAY_MANAGER.recordPlayerNotKilled(
                            killer,
                            serverVictim,
                            deathReason);
                }
                return;
            }
        if (!AfterShieldAllowPlayerDeathWithKiller.EVENT.invoker().allowDeath(victim, killer, deathReason))
            if (!forceDeath) {
                if (victim instanceof ServerPlayer serverVictim) {
                    SRE.REPLAY_MANAGER.recordPlayerNotKilled(
                            killer,
                            serverVictim,
                            deathReason);
                }
                return;
            } // --- 新增统计数据更新逻辑 (击杀者) ---
        if (killer instanceof ServerPlayer serverKiller) {
            SREPlayerStatsComponent killerStats = SREPlayerStatsComponent.KEY.get(serverKiller);
            if (shouldRecordPlayerStats()) {
                killerStats.incrementTotalKills();
            }

            SREPlayerProgressionComponent.KEY.get(serverKiller).onPlayerKill();

            SRERole killerRole = gameWorldComponent.getRole(serverKiller);
            if (killerRole != null) {
                killerRole.onKill(victim, spawnBody, killer, deathReason);
                if (shouldRecordPlayerStats()) {
                    killerStats.getOrCreateRoleStats(killerRole.identifier()).incrementKillsAsRole();
                    // 更新阵营击杀数
                    if (killerRole.isVigilanteTeam()) {
                        killerStats.incrementTotalSheriffKills();
                    } else if (killerRole.canUseKiller()) {
                        killerStats.incrementTotalKillerKills();
                    } else if (killerRole.isNeutrals()) {
                        killerStats.incrementTotalNeutralKills();
                    } else if (killerRole.isInnocent() && !killerRole.isVigilanteTeam()) {
                        killerStats.incrementTotalCivilianKills();
                    }
                }

                // 检测是否为友军击杀
                if (victim instanceof ServerPlayer serverVictim) {
                    SRERole victimRole = gameWorldComponent.getRole(serverVictim);
                    if (victimRole != null) {
                        boolean isTeamKill = false;
                        // 杀手击杀杀手
                        if (killerRole.canUseKiller() && victimRole.canUseKiller()) {
                            isTeamKill = true;
                            OnTeammateKilledTeammate.EVENT.invoker().playerKilled(serverVictim, serverKiller, false,
                                    deathReason);
                        }
                        // 无辜者击杀无辜者
                        else if (killerRole.isInnocent() && victimRole.isInnocent()) {
                            isTeamKill = true;
                            OnTeammateKilledTeammate.EVENT.invoker().playerKilled(serverVictim, serverKiller, true,
                                    deathReason);
                        }
                        if (isTeamKill) {
                            if (shouldRecordPlayerStats()) {
                                killerStats.incrementTotalTeamKills();
                                killerStats.getOrCreateRoleStats(killerRole.identifier()).incrementTeamKillsAsRole();
                            }
                        }
                    }
                }
            }
        }
        // --- 结束新增统计数据更新逻辑 (击杀者) ---
        canDeath = canDeath || forceDeath;
        // --- 结束新增统计数据更新逻辑 (受害者) ---
        if (canDeath) {
            // --- 新增统计数据更新逻辑 (受害者) ---
            if (victim instanceof ServerPlayer serverVictim) {
                SRERole victimRole = gameWorldComponent.getRole(serverVictim);
                victimRole.onDeath(victim, spawnBody, killer, deathReason);
                SREPlayerStatsComponent victimStats = SREPlayerStatsComponent.KEY.get(serverVictim);
                if (shouldRecordPlayerStats()) {
                    victimStats.incrementTotalDeaths();
                }
                if (victimRole != null) {
                    if (shouldRecordPlayerStats()) {
                        victimStats.getOrCreateRoleStats(victimRole.identifier()).incrementDeathsAsRole();
                        // 更新阵营死亡数
                        if (victimRole.isVigilanteTeam()) {
                            victimStats.incrementTotalSheriffDeaths();
                        } else if (victimRole.canUseKiller()) {
                            victimStats.incrementTotalKillerDeaths();
                        } else if (victimRole.isNeutrals()) {
                            victimStats.incrementTotalNeutralDeaths();
                        } else if (victimRole.isInnocent() && !victimRole.isVigilanteTeam()) {
                            victimStats.incrementTotalCivilianDeaths();
                        }
                    }
                }
                if (spawnBody) {
                    PlayerBodyEntity body = TMMEntities.PLAYER_BODY.create(victim.level());
                    double scale = victim.getAttributeValue(Attributes.SCALE);
                    victim.stopRiding();
                    victim.stopSleeping();
                    body.getAttribute(Attributes.SCALE).setBaseValue(scale);
                    if (body != null) {
                        if (killer != null) {
                            body.setKillerUuid(killer.getUUID());
                        }
                        body.setDeathReason(deathReason.toString());
                        body.setPlayerUuid(victim.getUUID());
                        Vec3 spawnPos = victim.position().add(victim.getLookAngle().normalize().scale(1));
                        body.moveTo(spawnPos.x(), victim.getY(), spawnPos.z(), victim.getYHeadRot(), 0f);
                        body.setYRot(victim.getYHeadRot());
                        body.setYHeadRot(victim.getYHeadRot());
                        victim.level().addFreshEntity(body);
                        if (NoellesRolesConfig.HANDLER.instance().allowDropItems) {
                            body.setCorpseInventoryFromPlayerInventory(victim.getInventory());
                        }
                        {
                            if (role != null) {
                                final var bodyDeathReasonComponent = BodyDeathReasonComponent.KEY.get(body);
                                bodyDeathReasonComponent.playerRole = role.identifier();
                                bodyDeathReasonComponent.sync();
                            }
                        }
                        victimRole.onDeathWithBody(victim, spawnBody, killer, deathReason, body);
                    }
                }
            }

            canDeath = canDeath || forceDeath;
            if (victim instanceof ServerPlayer serverPlayerEntity
                    && GameUtils.isPlayerAliveAndSurvival(serverPlayerEntity)
                    && canDeath) {
                serverPlayerEntity.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                OnPlayerDeath.EVENT.invoker().onPlayerDeath(victim, deathReason);
                // 关闭任务透视发包
                ServerPlayNetworking.send(serverPlayerEntity, new PlayerDeathPayload());
                OnPlayerDeathWithKiller.EVENT.invoker().onPlayerDeath(victim, killer, deathReason);
                SREPlayerPoisonComponent poisonComponent = SREPlayerPoisonComponent.KEY.maybeGet(serverPlayerEntity)
                        .orElse(null);
                SREArmorPlayerComponent bartenderPlayerComponent = SREArmorPlayerComponent.KEY
                        .maybeGet(serverPlayerEntity)
                        .orElse(null);
                // 删除玩家死后中毒
                if (poisonComponent != null) {
                    poisonComponent.clear();
                }
                // 删除玩家死后盾
                if (bartenderPlayerComponent != null) {
                    bartenderPlayerComponent.clear();
                }
                var cantSend = SRE.cantSendReplay.stream().anyMatch((pre) -> {
                    return pre.test(serverPlayerEntity);
                });
                if (!cantSend) {
                    serverPlayerEntity.sendSystemMessage(
                            Component.translatable("message.death_reason.prefix", Component.literal("")
                                    .withStyle(ChatFormatting.LIGHT_PURPLE)
                                    .append(deathMessageComponent))
                                    .withStyle(ChatFormatting.DARK_RED));
                }
            } else {
                return;
            }

            // 杀手击杀获得金钱奖励
            if (killer != null && SREGameWorldComponent.KEY.get(killer.level()).canUseKillerFeatures(killer)) {
                int gift = OnGiveKillerBalance.EVENT.invoker().onGiveKillerBalance(victim, killer, deathReason);
                gift += GameConstants.getMoneyPerKill();
                SREPlayerShopComponent.KEY.get(killer).addToBalance(gift);
            }
            if (killer != null) {
                inventory_label: for (List<ItemStack> list : killer.getInventory().compartments) {
                    for (int i = 0; i < list.size(); i++) {
                        ItemStack stack = list.get(i);
                        if (stack.is(TMMItems.DERRINGER)) {
                            stack.set(SREDataComponentTypes.USED, false);
                            break inventory_label;
                        }
                    }
                }
            }

            SREPlayerMoodComponent.KEY.get(victim).init();

            for (List<ItemStack> list : victim.getInventory().compartments) {
                for (int i = 0; i < list.size(); i++) {
                    ItemStack stack = list.get(i);
                    if (GameUtils.shouldDropOnDeath(stack)) {
                        victim.drop(stack, true, false);
                        list.set(i, ItemStack.EMPTY);
                    }
                }
            }

            if (gameWorldComponent.isInnocent(victim)) {
                final var gameTimeComponent = SREGameTimeComponent.KEY.get(victim.level());
                this.addKillRewardTime(gameTimeComponent);
            }
            if (!TrainVoicePlugin.isVoiceChatMissing() && victim.isSpectator()) {
                TrainVoicePlugin.addPlayer(victim.getUUID());
            }
        }
    }

    /**
     * 击杀平民奖励时长。（this.killPlayer触发）
     * 
     * @param gameTimeComponent
     */
    public void addKillRewardTime(SREGameTimeComponent gameTimeComponent) {
        if (gameTimeComponent.getTime() < gameTimeComponent.getResetTime()) {
            gameTimeComponent.addTime(GameConstants.TIME_ON_CIVILIAN_KILL);
        }
    }

    public boolean canAllPeopleSeeTime() {
        return false;
    }
}
