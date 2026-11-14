/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */
package org.agmas.noellesroles.content.block_entity;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.content.block.entity.SeatEntity;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.funny.SREDevilRouletteGameMode;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.minigame.DevilRouletteGame;
import org.agmas.noellesroles.game.minigame.roulette.*;
import org.agmas.noellesroles.init.ModBlocks;
import java.util.*;
import java.util.function.BiConsumer;

/** 服务端按阶段提交动作；客户端只根据公开快照画桌面，不持有暗弹顺序。 */
public class DevilRouletteTableEntity extends BlockEntity {
    // 保留旧实体类型的构造入口以兼容已有注册，不再为动画创建这些实体。
    public static class TableTextDisplay extends Display.TextDisplay {
        public TableTextDisplay(EntityType<?> type, Level level) { super(type, level); }
    }
    public static class TableItemDisplay extends Display.ItemDisplay {
        public TableItemDisplay(EntityType<?> type, Level level) { super(type, level); }
    }
    public static final int AFK_TICK = RouletteBalance.TURN_TICKS;
    public final Vec3 CENTER_POS;
    public final Vec3 TIME_POS;
    private UUID front, back;
    private UUID session = UUID.randomUUID();
    private long sequence, startedAt;
    private RoulettePhase phase = RoulettePhase.WAITING;
    private int duration, readyTicks, idleTicks, autoStreak;
    private boolean firstSupply;
    /** 会议期间冻结：>0 表示暂停开始的世界刻，恢复时把相位起点整体后移。 */
    private long meetingPausedAt;
    private DevilRouletteGame game;
    private DevilRouletteGame.DemonMatch demonMatch;
    private DevilRouletteGame.GameMode gameMode = DevilRouletteGame.GameMode.Lobby;
    private boolean matchInGlobalGame, settled, shotSaw;
    private final RouletteActionGate actionGate = new RouletteActionGate();
    private ResourceLocation matchMode;
    private String frontName = "", backName = "", message = "roulette.hint.sit";
    private RouletteItem actionItem, stolenItem, effect;
    private UUID actor = RouletteView.NONE, target = RouletteView.NONE;
    private int shell = -1, delta;
    private DevilRouletteRound.Outcome result;
    private RouletteView view = RouletteView.idle();
    private Selection selection;
    private final Map<UUID, Knowledge> knowledge = new HashMap<>();
    private record Knowledge(int offset, int shell) {}
    private record Selection(UUID token, UUID owner, InteractionHand hand, ItemStack held, Map<Integer, ItemStack> choices) {}

    public DevilRouletteTableEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.DEVIL_ROULETTE_TABLE_ENTITY, pos, state);
        CENTER_POS = Vec3.atCenterOf(pos).add(0, -.2, 0);
        TIME_POS = Vec3.atCenterOf(pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getClockWise()));
    }
    public RouletteView getView() { return view; }
    public void acceptView(RouletteView next) {
        if (next.supersedes(view)) view = next;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag(); tag.put("RouletteView", view.toTag()); return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // 活跃对局不写入磁盘；该标签仅供初次追踪区块的客户端恢复当前画面。
        if (tag.contains("RouletteView")) acceptView(RouletteView.fromTag(tag.getCompound("RouletteView")));
    }
    public Direction getFacing() { return getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING); }
    private static UUID id(UUID value) { return value == null ? RouletteView.NONE : value; }
    private ServerPlayer player(UUID id) {
        if (!(level instanceof ServerLevel server) || id == null || id.equals(RouletteView.NONE)) return null;
        return server.getPlayerByUUID(id) instanceof ServerPlayer p ? p : null;
    }
    private DevilRouletteRound round() { return game.round(); }
    private void publish() {
        if (!(level instanceof ServerLevel)) return;
        ServerPlayer p1 = player(front), p2 = player(back);
        if (p1 != null) frontName = p1.getName().getString();
        if (p2 != null) backName = p2.getName().getString();
        var r = game == null ? null : round();
        int seconds = Math.max(0, (budget() - readyTicks + 19) / 20);
        // 会议暂停时把画面起点推到当前时刻，让客户端动画与倒计时停在原处。
        long frozenAt = meetingPausedAt > 0 ? level.getGameTime() : startedAt;
        view = new RouletteView(session, ++sequence, phase, frozenAt, duration, id(front), id(back),
                r == null ? RouletteView.NONE : r.operator(), frontName, backName,
                r == null ? RouletteBalance.HEALTH : r.health(front), r == null ? RouletteBalance.HEALTH : r.health(back),
                r == null ? RouletteBalance.HEALTH : r.maxHealth(front), r == null ? RouletteBalance.HEALTH : r.maxHealth(back),
                r == null ? 0 : r.publicAmmo().live(), r == null ? 0 : r.publicAmmo().blanks(), r == null ? 0 : r.remaining(),
                r != null && (phase == RoulettePhase.SHOOT ? shotSaw : r.saw()), r == null ? RouletteView.NONE : id(r.cuffed()),
                actionItem, stolenItem, actor, target, shell, delta, RouletteItems.visible(p1), RouletteItems.visible(p2),
                message, seconds, r == null ? RouletteView.NONE : id(r.winner()));
        Set<ServerPlayer> recipients = new HashSet<>(PlayerLookup.tracking(this));
        if (p1 != null) recipients.add(p1);
        if (p2 != null) recipients.add(p2);
        var payload = new RouletteNetworking.Public(worldPosition, view);
        for (ServerPlayer recipient : recipients) ServerPlayNetworking.send(recipient, payload);
    }
    private void privatePacket(ServerPlayer recipient, int kind, CompoundTag data) {
        if (recipient != null) ServerPlayNetworking.send(recipient, new RouletteNetworking.Private(worldPosition, session, kind, data));
    }
    private void hint(Player player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GOLD), true);
    }
    private void clearPrivate() {
        privatePacket(player(front), RouletteNetworking.CLEAR, new CompoundTag());
        privatePacket(player(back), RouletteNetworking.CLEAR, new CompoundTag());
        selection = null; knowledge.clear();
    }
    private void showKnowledge(UUID who, Knowledge known) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Key", known.shell == 1 ? "roulette.private.live" : "roulette.private.blank");
        tag.putInt("Index", known.offset); tag.putInt("Shell", known.shell);
        privatePacket(player(who), RouletteNetworking.INFO, tag);
    }
    private void updateKnowledge(int consumed, boolean inverted) {
        for (UUID who : List.copyOf(knowledge.keySet())) {
            Knowledge old = knowledge.get(who);
            int offset = old.offset - consumed;
            if (offset < 1) {
                knowledge.remove(who);
                privatePacket(player(who), RouletteNetworking.CLEAR, new CompoundTag());
            } else {
                Knowledge next = new Knowledge(offset, inverted && offset == 1 ? 1 - old.shell : old.shell);
                knowledge.put(who, next); showKnowledge(who, next);
            }
        }
    }
    private void begin(RoulettePhase next) {
        phase = next; startedAt = level.getGameTime(); duration = next.duration(); actionGate.begin(next, startedAt);
        selection = null;
        publish();
    }
    private int budget() { return AFK_TICK; }
    private void ready() { readyTicks = 0; begin(RoulettePhase.READY); }
    private void reload() {
        if (round().ended()) { begin(RoulettePhase.ENDING); return; }
        clearPrivate(); round().reload(); shell = -1; delta = 0; actionItem = null; stolenItem = null;
        message = "roulette.hint.memorize"; begin(RoulettePhase.RELOAD);
    }
    private boolean validParticipant(ServerPlayer player, boolean isFront) {
        return player != null && player.isAlive() && !player.isSpectator()
                && (!matchInGlobalGame || !GameUtils.isPlayerEliminated(player)) && seated(player, isFront);
    }
    private boolean seated(Player player, boolean isFront) {
        return player.getVehicle() instanceof SeatEntity seat && seat.getSeatPos() != null
                && isSeatAvailable(seat.getSeatPos()) && isFrontSeat(seat.getSeatPos()) == isFront;
    }
    /** 会议暂停：只冻结进度，不重置对局，也不改相位与剩余时间。 */
    public void pauseForMeeting(long now) {
        if (game != null && meetingPausedAt == 0 && !isRemoved()) meetingPausedAt = Math.max(1, now);
    }
    public boolean pausedForMeeting() { return meetingPausedAt > 0; }
    /** 暂停期间重发冻结画面：客户端按收到时刻倒计时，重发即可让倒计时停在原处。 */
    public void keepPausedView() { if (meetingPausedAt > 0) publish(); }
    /** 会议结束：把相位起点后移暂停时长，让下次渲染/操作从同一进度继续。 */
    public void resumeFromMeeting(long now) {
        long frozen = Math.max(0, now - meetingPausedAt);
        meetingPausedAt = 0;
        if (game == null) return;
        startedAt += frozen;
        publish();
    }
    public void serverTick(Level level, BlockPos pos, BlockState state) {
        if (isRemoved()) return;
        // 暂停期间只续发冻结画面，让客户端倒计时停在原处。
        if (meetingPausedAt > 0) { if (level.getGameTime() % 10 == 0) publish(); return; }
        if (game == null) {
            if (front != null && !validParticipant(player(front), true)) { front = null; frontName = ""; publish(); }
            if (back != null && !validParticipant(player(back), false)) { back = null; backName = ""; publish(); }
            if ((front != null || back != null) && ++idleTicks >= AFK_TICK) reset();
            return;
        }
        if (matchInGlobalGame && (!GameUtils.isGameStarted
                || !SREGameWorldComponent.KEY.get(level).getGameMode().identifier.equals(matchMode))) {
            reset(); return;
        }
        if (!round().ended()) {
            boolean p1 = validParticipant(player(front), true), p2 = validParticipant(player(back), false);
            if (!p1 || !p2) {
                if (!p1 && !p2) { reset(); return; }
                clearPrivate(); round().forfeit(!p1 ? front : back);
                message = "roulette.result.forfeit"; begin(RoulettePhase.ENDING); return;
            }
        }
        long age = level.getGameTime() - startedAt;
        if (phase == RoulettePhase.READY) {
            if (++readyTicks >= budget()) {
                autoStreak++; message = "roulette.hint.timeout";
                shoot(round().operator(), true, true); return;
            }
            if (readyTicks % 20 == 0) {
                if (budget() - readyTicks <= 100) hint(player(round().operator()), "roulette.hint.timeout_warning");
                publish();
            }
            return;
        }
        if (actionGate.claimImpact(level.getGameTime())) commitAction();
        if (phase == RoulettePhase.SUPPLY && age >= 4 && age <= (demonMatch == null ? 28 : 34) && (age - 4) % 6 == 0) {
            if (demonMatch != null) {
                deliverDemonSupply(player(front)); deliverDemonSupply(player(back));
            } else {
                RouletteSupply supply = RouletteSupply.get((ServerLevel) level);
                for (UUID who : List.of(front, back)) { ServerPlayer p = player(who); if (p != null) supply.deliver(p); }
            }
            publish();
        }
        if (age < duration) return;
        switch (phase) {
            case INTRO -> reload();
            case RELOAD -> {
                int amount = level.random.nextInt(2, 6);
                if (demonMatch != null) {
                    demonMatch.reserve(amount, RouletteItems.count(player(front)), RouletteItems.count(player(back)),
                            new Random(level.random.nextLong()));
                } else {
                    RouletteSupply supply = RouletteSupply.get((ServerLevel) level);
                    supply.reserve(player(front), amount, firstSupply); supply.reserve(player(back), amount, firstSupply);
                }
                firstSupply = false;
                message = "roulette.hint.supply"; begin(RoulettePhase.SUPPLY);
            }
            case SUPPLY -> {
                for (UUID who : List.of(front, back)) {
                    ServerPlayer p = player(who);
                    int pending = demonMatch == null ? RouletteSupply.get((ServerLevel) level).pending(who) : demonMatch.pending(who);
                    if (p != null && pending > 0) hint(p, "roulette.hint.supply_pending");
                }
                message = "roulette.hint.controls"; ready();
            }
            case SHOOT, ITEM, ADRENALINE -> {
                if (round().ended()) begin(RoulettePhase.ENDING);
                else if (round().remaining() == 0) reload();
                else if (result != null && (result.switched() || result.skipped())) begin(RoulettePhase.HANDOFF);
                else ready();
            }
            case HANDOFF -> ready();
            case ENDING -> {
                if (!settled) {
                    boolean dedicated = demonMatch != null;
                    finishMatch();
                    // 专用回调可能已移除、重置甚至重开此桌，之后绝不再读写旧对局。
                    if (dedicated) return;
                    duration = 100; publish();
                }
                else reset();
            }
            default -> { }
        }
    }
    private void deliverDemonSupply(ServerPlayer player) {
        if (player == null || RouletteItems.count(player) >= RouletteItems.CAPACITY) return;
        RouletteItem kind = demonMatch.peek(player.getUUID());
        int slot = player.getInventory().getFreeSlot();
        if (kind == null || slot < 0) return;
        player.getInventory().setItem(slot, RouletteItems.stack(kind));
        demonMatch.delivered(player.getUUID());
        player.getInventory().setChanged();
        player.displayClientMessage(Component.translatable("roulette.result.received", Component.translatable(kind.titleKey())), true);
    }
    /** 只有取得关键帧提交权的 tick 能改变规则状态。 */
    private void commitAction() {
        if (game == null || round().ended()) return;
        int before = round().remaining();
        result = phase == RoulettePhase.SHOOT ? round().fire(target) : round().use(effect);
        if (!result.valid()) { message = result.message(); publish(); return; }
        actor = result.actor(); target = result.target(); shell = result.shell(); delta = result.delta(); message = result.message();
        if (phase == RoulettePhase.SHOOT) {
            if (result.skipped()) message = "roulette.result.cuffs_skipped";
            else if (actor.equals(target)) message = shell == 0 ? "roulette.result.self_blank" : "roulette.result.self_live";
            else message = shell == 0 ? "roulette.result.opponent_blank" : "roulette.result.opponent_live";
        }
        updateKnowledge(before - round().remaining(), effect == RouletteItem.INVERTER && phase != RoulettePhase.SHOOT);
        if (result.knownOffset() > 0) {
            Knowledge known = new Knowledge(result.knownOffset(), result.knownShell());
            knowledge.put(actor, known); showKnowledge(actor, known);
        }
        publish();
    }
    private void shoot(UUID who, boolean self, boolean timeout) {
        actor = who; target = self ? who : round().opponent(who); effect = null;
        shotSaw = round().saw();
        actionItem = null; stolenItem = null; shell = -1; delta = 0; result = null;
        if (!timeout) autoStreak = 0;
        clearSelection(); begin(RoulettePhase.SHOOT);
    }
    private void use(RouletteItem kind, RouletteItem stolen) {
        actor = round().operator(); target = actor; effect = stolen == null ? kind : stolen;
        actionItem = kind; stolenItem = stolen; shell = -1; delta = 0; result = null; autoStreak = 0;
        clearSelection(); begin(stolen == null ? RoulettePhase.ITEM : RoulettePhase.ADRENALINE);
    }
    private void clearSelection() { selection = null; }
    /** 会议暂停期间不接受任何操作：赌局停在同一相位，等散会后继续。 */
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) return ItemInteractionResult.SUCCESS;
        int operation = operation(player.getUUID(), pos);
        if (operation < 0) { hint(player, "roulette.error.operation_area"); return ItemInteractionResult.CONSUME; }
        if (!seated(player, Objects.equals(front, player.getUUID())) || !checkPlayerInRightSeat(player, Objects.equals(front, player.getUUID()))) {
            hint(player, "roulette.error.seat"); return ItemInteractionResult.CONSUME;
        }
        if (operation == 1 && stack.isEmpty() && hand == InteractionHand.MAIN_HAND
                && RouletteItems.kind(player.getOffhandItem()) != null) { stack = player.getOffhandItem(); hand = InteractionHand.OFF_HAND; }
        if (operation == 1 && stack.isEmpty()) {
            privatePacket(serverPlayer, RouletteNetworking.HELP, new CompoundTag()); return ItemInteractionResult.CONSUME;
        }
        if (phase != RoulettePhase.READY || game == null || round().ended() || pausedForMeeting()) {
            hint(player, "roulette.error.busy"); return ItemInteractionResult.CONSUME;
        }
        if (!game.canOperate(player.getUUID())) { hint(player, "roulette.error.turn"); return ItemInteractionResult.CONSUME; }
        if (operation != 1) {
            shoot(player.getUUID(), operation == 0, false); return ItemInteractionResult.CONSUME;
        }
        RouletteItem kind = RouletteItems.kind(stack);
        if (kind == null) { hint(player, "roulette.error.item"); return ItemInteractionResult.CONSUME; }
        if (kind == RouletteItem.ADRENALINE) { openSelection(serverPlayer, hand); return ItemInteractionResult.CONSUME; }
        String failure = round().failure(kind);
        if (failure != null) { hint(player, failure); return ItemInteractionResult.CONSUME; }
        stack.shrink(1); player.getInventory().setChanged(); use(kind, null);
        return ItemInteractionResult.CONSUME;
    }
    private void openSelection(ServerPlayer player, InteractionHand hand) {
        ServerPlayer opponent = player(round().opponent(player.getUUID()));
        if (opponent == null) { hint(player, "roulette.error.no_choices"); return; }
        Map<Integer, ItemStack> choices = new HashMap<>(); ListTag rows = new ListTag();
        for (int slot = 0; slot < opponent.getInventory().getContainerSize(); slot++) {
            ItemStack stack = opponent.getInventory().getItem(slot); RouletteItem kind = RouletteItems.kind(stack);
            if (kind == null || kind == RouletteItem.ADRENALINE || round().failure(kind) != null) continue;
            choices.put(slot, stack.copy());
            CompoundTag row = new CompoundTag(); row.putInt("Slot", slot); row.putInt("Kind", kind.ordinal()); row.putInt("Count", stack.getCount());
            rows.add(row);
        }
        if (choices.isEmpty()) { hint(player, "roulette.error.no_choices"); return; }
        UUID token = UUID.randomUUID();
        selection = new Selection(token, player.getUUID(), hand, player.getItemInHand(hand).copy(), choices);
        CompoundTag tag = new CompoundTag(); tag.putUUID("Token", token); tag.put("Choices", rows);
        privatePacket(player, RouletteNetworking.SELECT, tag);
    }
    public void handleSelection(ServerPlayer player, RouletteNetworking.Select packet) {
        Selection request = selection;
        if (request == null || game == null || !RouletteSelectionPolicy.authorized(
                session, packet.session(), request.token, packet.token(), request.owner, player.getUUID(),
                phase == RoulettePhase.READY && readyTicks < budget(), game.canOperate(player.getUUID()),
                seated(player, player.getUUID().equals(front)),
                player.level() == level && player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= 64,
                validParticipant(player, player.getUUID().equals(front)))) {
            hint(player, "roulette.error.stale_selection"); return;
        }
        ServerPlayer other = player(round().opponent(player.getUUID()));
        ItemStack snapshot = request.choices.get(packet.slot());
        if (other == null || !validParticipant(other, other.getUUID().equals(front)) || snapshot == null
                || packet.slot() < 0 || packet.slot() >= other.getInventory().getContainerSize()) {
            clearSelection(); hint(player, "roulette.error.stale_selection"); return;
        }
        RouletteItem kind = RouletteItems.kind(snapshot);
        String failure = round().failure(kind);
        if (!RouletteSelectionPolicy.stealable(kind,
                ItemStack.matches(snapshot, other.getInventory().getItem(packet.slot())),
                ItemStack.matches(request.held, player.getItemInHand(request.hand))
                        && RouletteItems.kind(player.getItemInHand(request.hand)) == RouletteItem.ADRENALINE,
                failure == null)) {
            clearSelection(); hint(player, failure == null ? "roulette.error.stale_selection" : failure); return;
        }
        // 所有检查结束后，在同一服务端任务中原子消费，令牌立即作废。
        clearSelection();
        player.getItemInHand(request.hand).shrink(1); other.getInventory().getItem(packet.slot()).shrink(1);
        player.getInventory().setChanged(); other.getInventory().setChanged();
        use(RouletteItem.ADRENALINE, kind);
    }
    /** 0 左侧自射，1 中间使用/帮助，2 右侧对射；对面座位的左右对称。 */
    public int operation(UUID player, BlockPos hit) {
        UUID first = front == null ? view.front() : front;
        UUID second = back == null ? view.back() : back;
        if (!Objects.equals(first, player) && !Objects.equals(second, player)) return -1;
        if (worldPosition.equals(hit)) return 1;
        int sign = Objects.equals(first, player) ? 1 : -1;
        BlockPos near = worldPosition.relative(getFacing(), sign);
        if (near.relative(getFacing().getClockWise(), sign).equals(hit)) return 0;
        if (near.equals(hit)) return 1;
        if (near.relative(getFacing().getCounterClockWise(), sign).equals(hit)) return 2;
        return -1;
    }
    public boolean isSeatAvailable(BlockPos seat) {
        return getSeatArea().contains(seat);
    }
    public boolean isFrontSeat(BlockPos seat) {
        Direction facing = getFacing();
        for (int y = -1; y <= 0; y++) for (int x = -1; x <= 1; x++)
            if (worldPosition.offset(0, y, 0).relative(facing, 2).relative(facing.getClockWise(), x).equals(seat)) return true;
        return false;
    }
    public List<BlockPos> getSeatArea() {
        List<BlockPos> seats = new ArrayList<>();
        for (int sign : new int[] {1, -1}) for (int y = -1; y <= 0; y++) for (int x = -1; x <= 1; x++)
            seats.add(worldPosition.offset(0, y, 0).relative(getFacing(), 2 * sign).relative(getFacing().getClockWise(), x));
        return seats;
    }
    public boolean checkPlayerInRightSeat(Player player, boolean isFront) {
        return Objects.equals(player.getUUID(), isFront ? front : back);
    }
    public boolean checkCanStartGame() {
        return front != null && back != null && validParticipant(player(front), true) && validParticipant(player(back), false);
    }
    public boolean canPlayerOperate(Player player) { return phase == RoulettePhase.READY && game != null && game.canOperate(player.getUUID()); }
    public boolean isGameActive() { return game != null; }
    public boolean addPlayer(Player player, boolean isFront) {
        if (game != null || !seated(player, isFront) || (isFront ? front : back) != null
                || Objects.equals(player.getUUID(), isFront ? back : front)) return false;
        if (isFront) { front = player.getUUID(); frontName = player.getName().getString(); }
        else { back = player.getUUID(); backName = player.getName().getString(); }
        idleTicks = 0; message = front != null && back != null ? "roulette.hint.start" : "roulette.hint.wait_opponent";
        publish(); hint(player, message); return true;
    }
    public boolean removePlayerIfSame(Player player, boolean isFront) {
        if (game != null || !checkPlayerInRightSeat(player, isFront)) return false;
        if (isFront) { front = null; frontName = ""; } else { back = null; backName = ""; }
        message = "roulette.hint.wait_opponent"; publish(); hint(player, "roulette.hint.unregistered"); return true;
    }
    public void startGame() { startMatch(RouletteBalance.HEALTH, RouletteBalance.HEALTH, null); }
    /** 调用方负责清空并托管背包、安排座位，以及在结算或取消后恢复玩家。 */
    public boolean startDemonMatch(ServerPlayer demon, ServerPlayer opponent, BiConsumer<UUID, UUID> settlement) {
        if (game != null || isRemoved() || !(level instanceof ServerLevel) || demon == null || opponent == null
                || settlement == null || demon.getUUID().equals(opponent.getUUID())
                || demon.level() != level || opponent.level() != level
                || front != null && !front.equals(demon.getUUID()) || back != null && !back.equals(opponent.getUUID())
                || !validParticipant(demon, true) || !validParticipant(opponent, false)) return false;
        front = demon.getUUID(); back = opponent.getUUID();
        return startMatch(RouletteBalance.DEVIL_HEALTH, RouletteBalance.HEALTH, new DevilRouletteGame.DemonMatch(front, back, settlement));
    }
    private boolean startMatch(int frontMaxHealth, int backMaxHealth, DevilRouletteGame.DemonMatch dedicated) {
        if (game != null || !checkCanStartGame() || !(level instanceof ServerLevel)) return false;
        demonMatch = dedicated;
        clearPrivate(); session = UUID.randomUUID(); sequence = 0;
        game = new DevilRouletteGame(new ArrayList<>(List.of(front, back)), level.getRandom(), level);
        game.setGameMode(gameMode); game.init();
        if (!game.start(frontMaxHealth, backMaxHealth)) { reset(); return false; }
        matchInGlobalGame = GameUtils.isGameStarted;
        matchMode = SREGameWorldComponent.KEY.get(level).getGameMode().identifier;
        settled = false; autoStreak = 0; readyTicks = 0; message = "roulette.hint.controls";
        firstSupply = true;
        begin(RoulettePhase.INTRO);
        return true;
    }
    public void assignMatch(Player first, Player second) {
        reset(); if (first == null || second == null) return;
        if (addPlayer(first, true) && addPlayer(second, false)) startGame();
    }
    private void finishMatch() {
        if (settled || game == null || !round().ended() || !actionGate.claimSettlement()) return;
        settled = true; clearPrivate();
        if (demonMatch != null) {
            UUID winner = round().winner(), loser = round().opponent(winner);
            demonMatch.settle(winner, loser);
            return;
        }
        ServerPlayer winner = player(round().winner());
        ServerPlayer loser = player(round().opponent(round().winner()));
        // 先标记结算，再调用可能触发游戏结束回调的外部接口。
        if (winner != null && gameMode == DevilRouletteGame.GameMode.Roulette
                && SREGameWorldComponent.KEY.get(level).getGameMode() instanceof SREDevilRouletteGameMode mode) {
            SREPlayerShopComponent.KEY.get(winner).addToBalance(SREDevilRouletteGameMode.WINNER_COIN * round().health(winner.getUUID()));
            mode.addWinner(winner); if (loser != null) mode.addLooser(loser);
        }
        if (matchInGlobalGame && GameUtils.isGameStarted && loser != null && !GameUtils.isPlayerEliminated(loser)) {
            GameUtils.killPlayer(loser, false, gameMode == DevilRouletteGame.GameMode.Roulette ? winner : null, SRE.id("gun_shot"), false);
        }
        if (winner != null) {
            winner.displayClientMessage(Component.translatable("noellesroles.game.devil_roulette.winner", winner.getName()).withStyle(ChatFormatting.GOLD), false);
        }
    }
    public void setGameMode(DevilRouletteGame.GameMode gameMode) { this.gameMode = gameMode; }
    public DevilRouletteGame.GameMode getGameMode() { return gameMode; }
    public int getTrueBulletNumber() { return game == null ? 0 : round().loadedLive(); }
    public void resetTick() { readyTicks = 0; autoStreak = 0; }
    public void init() { reset(); }
    public void reset() {
        if (demonMatch != null) { demonMatch.reset(); demonMatch = null; }
        clearPrivate(); game = null; front = null; back = null; frontName = ""; backName = "";
        session = UUID.randomUUID(); sequence = 0; phase = RoulettePhase.WAITING; startedAt = level == null ? 0 : level.getGameTime();
        duration = 0; readyTicks = 0; idleTicks = 0; autoStreak = 0; settled = false; actionGate.reset();
        matchInGlobalGame = false; matchMode = null; shotSaw = false;
        firstSupply = false; meetingPausedAt = 0;
        actionItem = null; stolenItem = null; effect = null; result = null; shell = -1; delta = 0;
        actor = RouletteView.NONE; target = RouletteView.NONE; message = "roulette.hint.sit";
        publish();
    }
    @Override public void setRemoved() {
        // 不留下 Scheduler 回调或未执行动作；持久补给独立于赌桌，不在此删除。
        if (level != null && !level.isClientSide) reset();
        super.setRemoved();
    }
}
