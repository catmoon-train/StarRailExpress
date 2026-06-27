package org.agmas.noellesroles.game.roles.neutral.reasoner;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.commands.GameUtilsCommand;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.ReasonerOpenScreenS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.UUID;

public class ReasonerPlayerComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<ReasonerPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            Noellesroles.id("reasoner"), ReasonerPlayerComponent.class);

    public static final int GIVE_COMPASS_TICKS = 2 * 60 * 20;
    public static final int KILLER_QUESTION_TICKS = 3 * 60 * 20;
    public static final int ANSWER_COOLDOWN_TICKS = 35 * 20;

    private final Player player;

    private boolean compassGiven;
    private int activeTicks;
    private int cooldownTicks;
    private UUID roleQuestionTarget;
    private UUID bodyQuestionTarget;
    private UUID taskQuestionTarget;
    private boolean solvedAliveCount;
    private boolean solvedRole;
    private boolean solvedDeathReason;
    private boolean solvedTask;
    private boolean solvedKillerCount;

    public ReasonerPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        compassGiven = false;
        activeTicks = 0;
        cooldownTicks = 0;
        roleQuestionTarget = null;
        bodyQuestionTarget = null;
        taskQuestionTarget = null;
        solvedAliveCount = false;
        solvedRole = false;
        solvedDeathReason = false;
        solvedTask = false;
        solvedKillerCount = false;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public void serverTick() {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (!game.isRole(player, ModRoles.REASONER)) {
            return;
        }
        if (!game.isRunning() || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }

        activeTicks++;
        boolean changed = false;
        if (cooldownTicks > 0) {
            cooldownTicks--;
            changed = cooldownTicks % 20 == 0 || cooldownTicks == 0;
        }

        if (!compassGiven && getElapsedTicks() >= GIVE_COMPASS_TICKS && player instanceof ServerPlayer serverPlayer) {
            giveCompass(serverPlayer);
            compassGiven = true;
            changed = true;
        }

        if (changed) {
            sync();
        }
    }

    public void openCompass(ServerPlayer serverPlayer) {
        if (cooldownTicks > 0) {
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.noellesroles.reasoner.cooldown", secondsLeft(cooldownTicks)).withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        refreshQuestionTargets(serverPlayer.serverLevel());
        ServerPlayNetworking.send(serverPlayer, new ReasonerOpenScreenS2CPacket(
                getRoleQuestionTargetName(serverPlayer.serverLevel()),
                getBodyQuestionTargetName(serverPlayer.serverLevel()),
                getTaskQuestionTargetName(serverPlayer.serverLevel()),
                deadPlayerCount(serverPlayer.serverLevel()) > 3 && bodyQuestionTarget != null,
                getElapsedTicks() >= KILLER_QUESTION_TICKS,
                allRoleIds(),
                allDeathReasonIds(),
                allTaskIds(),
                solvedAliveCount,
                solvedRole,
                solvedDeathReason,
                solvedTask,
                solvedKillerCount,
                cooldownTicks));
    }

    public void submitAnswer(ServerPlayer serverPlayer, int question, String answer) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(serverPlayer.level());
        if (!game.isRole(serverPlayer, ModRoles.REASONER) || !game.isRunning() || !GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
            return;
        }
        if (cooldownTicks > 0) {
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.noellesroles.reasoner.cooldown", secondsLeft(cooldownTicks)).withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (isSolved(question)) {
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.noellesroles.reasoner.already_solved").withStyle(ChatFormatting.YELLOW), true);
            return;
        }

        refreshQuestionTargets(serverPlayer.serverLevel());
        boolean correct = switch (question) {
            case 1 -> checkAliveCount(answer);
            case 2 -> checkRoleAnswer(serverPlayer.serverLevel(), answer);
            case 3 -> checkDeathReasonAnswer(serverPlayer.serverLevel(), answer);
            case 4 -> checkTaskAnswer(serverPlayer.serverLevel(), answer);
            case 5 -> checkKillerCountAnswer(serverPlayer.serverLevel(), answer);
            default -> false;
        };

        cooldownTicks = ANSWER_COOLDOWN_TICKS;
        if (correct) {
            markSolved(question);
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.noellesroles.reasoner.correct", solvedCount(), 5).withStyle(ChatFormatting.GREEN), false);
            checkWin(serverPlayer.serverLevel());
        } else {
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.noellesroles.reasoner.incorrect").withStyle(ChatFormatting.RED), false);
        }
        sync();
    }

    private void giveCompass(ServerPlayer serverPlayer) {
        if (hasCompass(serverPlayer)) {
            return;
        }
        if (RoleUtils.insertStackInFreeSlot(serverPlayer, ModItems.REASONER_COMPASS.getDefaultInstance())) {
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.noellesroles.reasoner.compass_given").withStyle(ChatFormatting.GOLD), false);
        }
    }

    private boolean hasCompass(ServerPlayer serverPlayer) {
        for (ItemStack stack : serverPlayer.getInventory().items) {
            if (stack.is(ModItems.REASONER_COMPASS)) {
                return true;
            }
        }
        return false;
    }

    private int getElapsedTicks() {
        if (player.level() instanceof ServerLevel) {
            SREGameTimeComponent time = SREGameTimeComponent.KEY.get(player.level());
            int reset = time.getResetTime();
            int current = time.getTime();
            if (reset > 0 && current >= 0 && reset >= current) {
                return reset - current;
            }
        }
        return activeTicks;
    }

    private void refreshQuestionTargets(ServerLevel level) {
        List<ServerPlayer> alive = alivePlayers(level);
        if (roleQuestionTarget == null || alive.stream().noneMatch(p -> p.getUUID().equals(roleQuestionTarget))) {
            roleQuestionTarget = pickRandomUuid(alive);
        }

        List<PlayerBodyEntity> bodies = getBodyTargets(level);
        if (bodyQuestionTarget == null || bodies.stream().noneMatch(b -> b.getPlayerUuid() != null && b.getPlayerUuid().equals(bodyQuestionTarget))) {
            bodyQuestionTarget = pickRandomBodyOwner(bodies);
        }

        List<ServerPlayer> taskTargets = alive.stream()
                .filter(this::isInnocentNonNeutral)
                .filter(p -> !SREPlayerTaskComponent.KEY.get(p).tasks.isEmpty())
                .toList();
        if (taskQuestionTarget == null || taskTargets.stream().noneMatch(p -> p.getUUID().equals(taskQuestionTarget))) {
            taskQuestionTarget = pickRandomUuid(taskTargets);
        }
    }

    private boolean checkAliveCount(String answer) {
        Integer guessed = parseNonNegativeInt(answer);
        return guessed != null && guessed == alivePlayers((ServerLevel) player.level()).size();
    }

    private boolean checkRoleAnswer(ServerLevel level, String answer) {
        if (solvedRole || roleQuestionTarget == null) {
            return false;
        }
        Player target = level.getServer().getPlayerList().getPlayer(roleQuestionTarget);
        if (target == null || !GameUtils.isPlayerAliveAndSurvival(target)) {
            return false;
        }
        SRERole role = SREGameWorldComponent.KEY.get(level).getRole(target);
        return role != null && role.identifier().toString().equals(answer);
    }

    private boolean checkDeathReasonAnswer(ServerLevel level, String answer) {
        if (solvedDeathReason || deadPlayerCount(level) <= 3 || bodyQuestionTarget == null) {
            return false;
        }
        PlayerBodyEntity body = findBody(level, bodyQuestionTarget);
        return body != null && answer.equals(body.getDeathReason());
    }

    private boolean checkTaskAnswer(ServerLevel level, String answer) {
        if (solvedTask || taskQuestionTarget == null) {
            return false;
        }
        ServerPlayer target = level.getServer().getPlayerList().getPlayer(taskQuestionTarget);
        if (target == null || !GameUtils.isPlayerAliveAndSurvival(target) || !isInnocentNonNeutral(target)) {
            return false;
        }
        try {
            SREPlayerTaskComponent.Task task = SREPlayerTaskComponent.Task.valueOf(answer);
            return SREPlayerTaskComponent.KEY.get(target).tasks.containsKey(task);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean checkKillerCountAnswer(ServerLevel level, String answer) {
        if (solvedKillerCount || getElapsedTicks() < KILLER_QUESTION_TICKS) {
            return false;
        }
        Integer guessed = parseNonNegativeInt(answer);
        return guessed != null && guessed == aliveKillerCount(level);
    }

    private int aliveKillerCount(ServerLevel level) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level);
        List<UUID> killerTeam = game.getAllKillerTeamPlayers();
        int count = 0;
        for (ServerPlayer p : alivePlayers(level)) {
            SRERole role = game.getRole(p);
            if (role != null && (role.canUseKiller() || killerTeam.contains(p.getUUID()))) {
                count++;
            }
        }
        return count;
    }

    private int deadPlayerCount(ServerLevel level) {
        return (int) level.players().stream().filter(player -> !GameUtils.isPlayerAliveAndSurvival(player)).count();
    }

    private boolean isInnocentNonNeutral(ServerPlayer target) {
        SRERole role = SREGameWorldComponent.KEY.get(target.level()).getRole(target);
        return role != null && role.isInnocent() && !role.canUseKiller() && !role.isNeutrals();
    }

    private List<ServerPlayer> alivePlayers(ServerLevel level) {
        return level.players().stream().filter(GameUtils::isPlayerAliveAndSurvival).toList();
    }

    private List<PlayerBodyEntity> getBodyTargets(ServerLevel level) {
        AABB allWorld = new AABB(-30000000, level.getMinBuildHeight(), -30000000, 30000000, level.getMaxBuildHeight(), 30000000);
        return level.getEntitiesOfClass(PlayerBodyEntity.class, allWorld, body -> body.getPlayerUuid() != null);
    }

    private PlayerBodyEntity findBody(ServerLevel level, UUID owner) {
        return getBodyTargets(level).stream()
                .filter(body -> owner.equals(body.getPlayerUuid()))
                .findFirst()
                .orElse(null);
    }

    private UUID pickRandomUuid(List<ServerPlayer> players) {
        if (players.isEmpty()) {
            return null;
        }
        return players.get(player.level().random.nextInt(players.size())).getUUID();
    }

    private UUID pickRandomBodyOwner(List<PlayerBodyEntity> bodies) {
        if (bodies.isEmpty()) {
            return null;
        }
        return bodies.get(player.level().random.nextInt(bodies.size())).getPlayerUuid();
    }

    private String getRoleQuestionTargetName(ServerLevel level) {
        ServerPlayer target = roleQuestionTarget == null ? null : level.getServer().getPlayerList().getPlayer(roleQuestionTarget);
        return target == null ? "?" : target.getGameProfile().getName();
    }

    private String getBodyQuestionTargetName(ServerLevel level) {
        PlayerBodyEntity body = bodyQuestionTarget == null ? null : findBody(level, bodyQuestionTarget);
        if (body == null) {
            return "?";
        }
        ServerPlayer target = level.getServer().getPlayerList().getPlayer(body.getPlayerUuid());
        if (target != null) {
            return target.getGameProfile().getName();
        }
        return body.getDisplayName().getString();
    }

    private String getTaskQuestionTargetName(ServerLevel level) {
        ServerPlayer target = taskQuestionTarget == null ? null : level.getServer().getPlayerList().getPlayer(taskQuestionTarget);
        return target == null ? "?" : target.getGameProfile().getName();
    }

    private List<String> allRoleIds() {
        return Noellesroles.getAllRolesSorted(true).stream()
                .map(role -> role.identifier().toString())
                .distinct()
                .toList();
    }

    private List<String> allDeathReasonIds() {
        List<String> reasons = GameUtilsCommand.DeathReasonSuggestions.getAllDeathReasons().stream()
                .map(ResourceLocation::toString)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        reasons.sort(Comparator.naturalOrder());
        return reasons;
    }

    private List<String> allTaskIds() {
        List<String> tasks = new ArrayList<>();
        for (SREPlayerTaskComponent.Task task : SREPlayerTaskComponent.Task.values()) {
            if (task != SREPlayerTaskComponent.Task.CUSTOM && task != SREPlayerTaskComponent.Task.MANIC) {
                tasks.add(task.name());
            }
        }
        tasks.sort(Comparator.comparing(s -> s.toLowerCase(Locale.ROOT)));
        return tasks;
    }

    private void markSolved(int question) {
        switch (question) {
            case 1 -> solvedAliveCount = true;
            case 2 -> solvedRole = true;
            case 3 -> solvedDeathReason = true;
            case 4 -> solvedTask = true;
            case 5 -> solvedKillerCount = true;
            default -> {
            }
        }
    }

    private boolean isSolved(int question) {
        return switch (question) {
            case 1 -> solvedAliveCount;
            case 2 -> solvedRole;
            case 3 -> solvedDeathReason;
            case 4 -> solvedTask;
            case 5 -> solvedKillerCount;
            default -> false;
        };
    }

    public int getSolvedCount() {
        return solvedCount();
    }

    private int solvedCount() {
        int count = 0;
        if (solvedAliveCount) count++;
        if (solvedRole) count++;
        if (solvedDeathReason) count++;
        if (solvedTask) count++;
        if (solvedKillerCount) count++;
        return count;
    }

    private void checkWin(ServerLevel level) {
        if (solvedAliveCount && solvedRole && solvedDeathReason && solvedTask && solvedKillerCount) {
            RoleUtils.customWinnerWin(level, GameUtils.WinStatus.CUSTOM,
                    ModRoles.REASONER_ID.getPath(), OptionalInt.of(ModRoles.REASONER.color()));
        }
    }

    private Integer parseNonNegativeInt(String answer) {
        try {
            int value = Integer.parseInt(answer.trim());
            return value >= 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int secondsLeft(int ticks) {
        return Math.max(1, (ticks + 19) / 20);
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("compassGiven", compassGiven);
        tag.putInt("activeTicks", activeTicks);
        tag.putInt("cooldownTicks", cooldownTicks);
        tag.putBoolean("solvedAliveCount", solvedAliveCount);
        tag.putBoolean("solvedRole", solvedRole);
        tag.putBoolean("solvedDeathReason", solvedDeathReason);
        tag.putBoolean("solvedTask", solvedTask);
        tag.putBoolean("solvedKillerCount", solvedKillerCount);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        compassGiven = tag.getBoolean("compassGiven");
        activeTicks = tag.getInt("activeTicks");
        cooldownTicks = tag.getInt("cooldownTicks");
        solvedAliveCount = tag.getBoolean("solvedAliveCount");
        solvedRole = tag.getBoolean("solvedRole");
        solvedDeathReason = tag.getBoolean("solvedDeathReason");
        solvedTask = tag.getBoolean("solvedTask");
        solvedKillerCount = tag.getBoolean("solvedKillerCount");
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        writeToSyncNbt(tag, registryLookup);
        if (roleQuestionTarget != null) tag.putUUID("roleQuestionTarget", roleQuestionTarget);
        if (bodyQuestionTarget != null) tag.putUUID("bodyQuestionTarget", bodyQuestionTarget);
        if (taskQuestionTarget != null) tag.putUUID("taskQuestionTarget", taskQuestionTarget);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        readFromSyncNbt(tag, registryLookup);
        roleQuestionTarget = tag.hasUUID("roleQuestionTarget") ? tag.getUUID("roleQuestionTarget") : null;
        bodyQuestionTarget = tag.hasUUID("bodyQuestionTarget") ? tag.getUUID("bodyQuestionTarget") : null;
        taskQuestionTarget = tag.hasUUID("taskQuestionTarget") ? tag.getUUID("taskQuestionTarget") : null;
    }
}
