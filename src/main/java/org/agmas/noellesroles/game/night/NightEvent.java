package org.agmas.noellesroles.game.night;

import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SRETrainWorldComponent;
import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnGameStarted;
import io.wifi.starrailexpress.event.OnPlayerDeathWithBody;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.modes.funny.SRERoleRotationGameMode;
import io.wifi.starrailexpress.game.modes.funny.SRERoleRotationSingleSelectGameMode;
import io.wifi.starrailexpress.game.modes.funny.SREVolunteerOpenSelectGameMode;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.harpymodloader.SREDisableManager;
import org.agmas.harpymodloader.events.GameInitializeEvent;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.content.entity.UndeadEntity;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 黑夜事件只改变本局环境，不替换职业、阵营或胜负规则。 */
public final class NightEvent {
    private static final Map<MinecraftServer, NightRoundState> QUEUES = new IdentityHashMap<>();
    private static final Map<ServerLevel, Round> ROUNDS = new IdentityHashMap<>();
    private static boolean registered;
    private static boolean prepared;
    private static boolean selected;

    private static final class Round {
        final NightRoundState state = new NightRoundState();
        final Set<UUID> participants = new HashSet<>();
        final Set<UUID> announced = new HashSet<>();
        final Set<UUID> undead = new HashSet<>();
        long originalDayTime;
        SRETrainWorldComponent.TimeOfDay originalTrainTime;
        final Map<UUID, PlayerBodyEntity> bodies = new HashMap<>();
        boolean active;
    }

    private NightEvent() {}

    public static void register() {
        if (registered) return;
        registered = true;
        GameInitializeEvent.EVENT.register(NightEvent::prepare);
        OnGameStarted.EVENT.register(level -> {
            if (!defersEquipment(SREGameWorldComponent.KEY.get(level).getGameMode())) start(level);
        });
        OnPlayerDeathWithBody.EVENT.register((victim, killer, reason, body) -> {
            if (victim instanceof ServerPlayer player && isActive(player.level())) {
                Round round = ROUNDS.get(player.serverLevel());
                if (round.participants.contains(player.getUUID())) round.bodies.put(player.getUUID(), body);
            }
        });
        OnPlayerDeathWithKiller.FINAL_EVENT.register(NightEvent::onDeath);
        OnGameEnd.EVENT.register((level, game) -> clear(level));
        io.wifi.starrailexpress.event.AllowPlayerDeath.EVENT.register((victim, reason) -> {
            observeRevived(victim);
            return true;
        });
        io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller.EVENT.register((victim, killer, reason) -> {
            observeRevived(victim);
            return true;
        });
        ServerTickEvents.END_WORLD_TICK.register(NightEvent::tick);
        ServerWorldEvents.UNLOAD.register((server, level) -> clear(level));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (ServerLevel level : new ArrayList<>(ROUNDS.keySet())) {
                if (level.getServer() == server) clear(level);
            }
            QUEUES.remove(server);
            prepared = false;
            selected = false;
        });
    }

    /** 和命令方块一样使用事件职业列表；初始化已掷过的骰不会在安全时间结束时再抽一次。 */
    public static int chance() {
        return prepared ? (selected ? 10000 : 0) : Math.clamp(NoellesRolesConfig.instance().nightEventEnableChance, 0, 10000);
    }

    public static boolean forceNextRound(ServerLevel level) {
        return QUEUES.computeIfAbsent(level.getServer(), ignored -> new NightRoundState()).requestNext();
    }

    /** 客户端只读取现有停电组件同步的本局标记，不加载任何客户端类。 */
    public static boolean isActive(@Nullable Level level) {
        return level != null
                && SREGameWorldComponent.KEY.get(level).getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE
                && SREWorldBlackoutComponent.KEY.get(level).isNightEventActive()
                && (level.isClientSide || ROUNDS.containsKey(level));
    }

    /** 区块重载也必须属于当前会话，旧局亡灵不能混进新局。 */
    public static boolean isUndeadActive(UndeadEntity undead) {
        Round round = ROUNDS.get(undead.level());
        return isActive(undead.level()) && round != null && round.undead.contains(undead.getUUID());
    }

    private static boolean supports(GameMode mode) {
        if (!(mode instanceof SREMurderGameMode)) return false;
        String name = mode.getClass().getSimpleName();
        return !name.contains("HideAndSeek") && !name.contains("EvilWar") && !name.contains("TNT")
                && !name.contains("Gambler") && !name.contains("Discovery") && !name.contains("Goose");
    }

    private static boolean defersEquipment(GameMode mode) {
        return mode instanceof SRERoleRotationGameMode || mode instanceof SRERoleRotationSingleSelectGameMode
                || mode instanceof SREVolunteerOpenSelectGameMode;
    }

    private static void prepare(ServerLevel level, SREGameWorldComponent game, List<ServerPlayer> players) {
        clear(level);
        prepared = true;
        boolean allowed = supports(game.getGameMode()) && !SREDisableManager.isRoleDisabled(ModRoles.NIGHT_EVENT)
                && !Noellesroles.isRoleDisabled(ModRoles.NIGHT_EVENT);
        NightRoundState queue = QUEUES.computeIfAbsent(level.getServer(), ignored -> new NightRoundState());
        selected = queue.prepare(allowed, NoellesRolesConfig.instance().nightEventEnableChance, level.random.nextInt(10000));
        if (!selected) return;
        Round round = new Round();
        for (ServerPlayer player : players) round.participants.add(player.getUUID());
        ROUNDS.put(level, round);
    }

    public static void onEventRollResult(ServerLevel level, boolean enabled) {
        // 通用 API 强开同样保留模式约束，模式不支持时不改变游戏。
        if (!enabled || !supports(SREGameWorldComponent.KEY.get(level).getGameMode())) {
            clear(level);
            return;
        }
        if (!ROUNDS.containsKey(level)) {
            Round round = new Round();
            for (ServerPlayer player : level.players()) {
                if (GameUtils.isPlayerAliveAndSurvival(player)) round.participants.add(player.getUUID());
            }
            ROUNDS.put(level, round);
        }
        start(level);
    }

    private static void start(ServerLevel level) {
        Round round = ROUNDS.get(level);
        if (round == null || round.active
                || SREGameWorldComponent.KEY.get(level).getGameStatus() != SREGameWorldComponent.GameStatus.ACTIVE) return;
        round.active = true;
        round.originalDayTime = level.getDayTime();
        round.originalTrainTime = SRETrainWorldComponent.KEY.get(level).getTimeOfDay();
        SRETrainWorldComponent.KEY.get(level).setTimeOfDay(SRETrainWorldComponent.TimeOfDay.MIDNIGHT);
        level.setDayTime(18000);
        SREWorldBlackoutComponent.KEY.get(level).startNightEvent();
        for (ServerPlayer player : level.players()) {
            announce(round, player);
            equip(round, player);
        }
    }

    private static void announce(Round round, ServerPlayer player) {
        if (!round.announced.add(player.getUUID())) return;
        player.sendSystemMessage(Component.translatable("message.noellesroles.night.rules").withStyle(ChatFormatting.DARK_PURPLE));
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 100, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.noellesroles.night.title")
                .withStyle(ChatFormatting.DARK_PURPLE)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.noellesroles.night.subtitle")));
    }

    private static void equip(Round round, ServerPlayer player) {
        UUID id = player.getUUID();
        if (!round.participants.contains(id) || !GameUtils.isPlayerAliveAndSurvival(player) || !round.state.equip(id)) return;
        // 复用既有手电筒及开关，初始点亮；满包时落在本人脚下，避免默默丢装备。
        ItemStack flashlight = ModItems.FLASHLIGHT.getDefaultInstance();
        flashlight.set(SREDataComponentTypes.STATUS, true);
        player.getInventory().add(flashlight);
        if (!flashlight.isEmpty()) {
            var drop = player.drop(flashlight, false);
            if (drop == null) round.state.retryEquip(id);
            else drop.setTarget(id);
        }
        player.inventoryMenu.broadcastChanges();
    }

    private static void tick(ServerLevel level) {
        Round round = ROUNDS.get(level);
        if (round == null) return;
        if (SREGameWorldComponent.KEY.get(level).getGameStatus() != SREGameWorldComponent.GameStatus.ACTIVE) {
            clear(level);
            return;
        }
        if (!round.active || level.getGameTime() % 20 != 0) return;
        if (SRETrainWorldComponent.KEY.get(level).getTimeOfDay() != SRETrainWorldComponent.TimeOfDay.MIDNIGHT) {
            SRETrainWorldComponent.KEY.get(level).setTimeOfDay(SRETrainWorldComponent.TimeOfDay.MIDNIGHT);
        }
        if (level.getDayTime() % 24000 != 18000) level.setDayTime(18000);
        for (ServerPlayer player : level.players()) {
            if (GameUtils.isPlayerAliveAndSurvival(player)) round.state.observeAlive(player.getUUID());
            announce(round, player);
            equip(round, player);
        }
    }

    private static void observeRevived(Player victim) {
        Round round = ROUNDS.get(victim.level());
        if (round != null && GameUtils.isPlayerAliveAndSurvival(victim)) round.state.observeAlive(victim.getUUID());
    }

    private static void onDeath(Player player, @Nullable Player killer, ResourceLocation reason) {
        if (!(player instanceof ServerPlayer victim) || !victim.isSpectator() || !isActive(victim.level())) return;
        ServerLevel level = victim.serverLevel();
        Round round = ROUNDS.get(level);
        UUID id = victim.getUUID();
        if (!round.participants.contains(id) || !round.state.markDead(id)) return;
        PlayerBodyEntity body = round.bodies.remove(id);
        UndeadEntity undead = ModEntities.UNDEAD.create(level);
        if (undead == null) return;
        // 出生点先落定，再初始化漫游锚点；本事件亡灵不受亡灵之主的名额、存活或感染账本约束。
        undead.moveTo(victim.getX(), victim.getY(), victim.getZ(), victim.getYRot(), 0);
        undead.setupNight(id, Integer.MAX_VALUE);
        if (!level.addFreshEntity(undead)) return;
        round.undead.add(undead.getUUID());
        if (body != null && !body.isRemoved() && !body.getComponent().isFakeBody && !body.isNonInteractable()) body.discard();
        victim.sendSystemMessage(Component.translatable("message.noellesroles.night.transformed"));
    }

    public static void clear(ServerLevel level) {
        Round round = ROUNDS.remove(level);
        SREWorldBlackoutComponent.KEY.get(level).endNightEvent();
        if (round == null) return;
        if (round.active) {
            SRETrainWorldComponent.KEY.get(level).setTimeOfDay(round.originalTrainTime);
            level.setDayTime(round.originalDayTime);
        }
        for (UUID id : round.undead) {
            var entity = level.getEntity(id);
            if (entity instanceof UndeadEntity undead && undead.isNight()) undead.discard();
        }
        round.state.clearRound();
        // 下一局请求保存在 QUEUES 中，绝不随当前局收尾丢失。
    }
}
