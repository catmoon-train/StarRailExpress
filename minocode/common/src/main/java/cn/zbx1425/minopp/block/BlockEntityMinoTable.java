package cn.zbx1425.minopp.block;

import cn.zbx1425.minopp.Mino;
import cn.zbx1425.minopp.effect.EffectEvent;
import cn.zbx1425.minopp.effect.EffectEvents;
import cn.zbx1425.minopp.effect.SeatActionTakenEffectEvent;
import cn.zbx1425.minopp.game.ActionMessage;
import cn.zbx1425.minopp.game.ActionReport;
import cn.zbx1425.minopp.game.CardGame;
import cn.zbx1425.minopp.game.CardPlayer;
import cn.zbx1425.minopp.item.ItemHandCards;
import cn.zbx1425.minopp.network.S2CActionEphemeralPacket;
import cn.zbx1425.minopp.network.S2CEffectListPacket;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class BlockEntityMinoTable extends BlockEntity {

    public Map<Direction, CardPlayer> players = new HashMap<>();
    public CardGame game = null;
    public ActionMessage state = ActionMessage.NO_GAME;

    public List<Pair<ActionMessage, Long>> clientMessageList = new ArrayList<>();

    public ItemStack award = ItemStack.EMPTY;
    public boolean demo = false;

    public static final List<Direction> PLAYER_ORDER = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
    public BlockEntityMinoTable(BlockPos blockPos, BlockState blockState) {
        super(Mino.BLOCK_ENTITY_TYPE_MINO_TABLE.get(), blockPos, blockState);
        for (Direction direction : PLAYER_ORDER) {
            players.put(direction, null);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag compoundTag, HolderLookup.Provider provider) {
        super.saveAdditional(compoundTag, provider);
        CompoundTag playersTag = new CompoundTag();
        for (Map.Entry<Direction, CardPlayer> entry : players.entrySet()) {
            if (entry.getValue() != null) {
                playersTag.put(entry.getKey().getSerializedName(), entry.getValue().toTag());
            }
        }
        compoundTag.put("players", playersTag);
        if (game != null) {
            compoundTag.put("game", game.toTag());
        }
        compoundTag.put("state", state.toTag());
        if (!award.isEmpty()) compoundTag.put("award", award.save(provider));
        compoundTag.putBoolean("demo", demo);
    }

    @Override
    protected void loadAdditional(CompoundTag compoundTag, HolderLookup.Provider provider) {
        super.loadAdditional(compoundTag, provider);
        CompoundTag playersTag = compoundTag.getCompound("players");
        for (Direction direction : PLAYER_ORDER) {
            if (playersTag.contains(direction.getSerializedName())) {
                players.put(direction, new CardPlayer(playersTag.getCompound(direction.getSerializedName())));
            } else {
                players.put(direction, null);
            }
        }
        CardGame previousGame = game;
        if (compoundTag.contains("game")) {
            game = new CardGame(compoundTag.getCompound("game"));
        } else {
            game = null;
        }
        ActionMessage newState = new ActionMessage(compoundTag.getCompound("state"));
        if (!newState.equals(state)) {
            if (previousGame == null && game != null) {
                clientMessageList.clear();
            } else {
                clientMessageList.add(new Pair<>(state, System.currentTimeMillis() + 16000));
            }
            state = newState;
            clientMessageList.removeIf(entry -> entry.getFirst().type() == ActionMessage.Type.FAIL);
        }
        if (compoundTag.contains("award")) {
            award = ItemStack.parse(provider, compoundTag.get("award")).orElse(ItemStack.EMPTY);
        } else {
            award = ItemStack.EMPTY;
        }
        if (compoundTag.contains("demo", Tag.TAG_BYTE)) {
            demo = compoundTag.getBoolean("demo");
        } else {
            demo = false;
        }
    }

    public List<CardPlayer> getPlayersList() {
        // Return a list of players in the order of NORTH, EAST, SOUTH, WEST, without null elements
        List<CardPlayer> playersList = new ArrayList<>();
        for (Direction direction : PLAYER_ORDER) {
            if (players.get(direction) != null) {
                playersList.add(players.get(direction));
            }
        }
        return playersList;
    }

    public List<Direction> getEmptyDirections() {
        List<Direction> emptyDirections = new ArrayList<>();
        for (Direction direction : PLAYER_ORDER) {
            if (players.get(direction) == null) {
                emptyDirections.add(direction);
            }
        }
        return emptyDirections;
    }

    private static final int PLAYER_RANGE = 20;

    public void joinPlayerToTable(CardPlayer cardPlayer, Vec3 playerPos) {
        if (game != null) return;
        BlockPos centerPos = getBlockPos().offset(1, 0, 1);
        Vec3 playerOffset = playerPos.subtract(centerPos.getX(), centerPos.getY(), centerPos.getZ());
        Direction playerDirection = Direction.fromYRot(Mth.atan2(playerOffset.z, playerOffset.x) * 180 / Math.PI - 90);
        for (Direction checkDir : players.keySet()) {
            if (cardPlayer.equals(players.get(checkDir))) {
                players.put(checkDir, null);
            }
        }
        players.put(playerDirection, cardPlayer);
        sync();
    }

    @SuppressWarnings("unchecked, rawtypes")
    public void startGame(CardPlayer initiator) {
        if (game != null) return;
        List<CardPlayer> playerList = getPlayersList();
        if (playerList.size() < 2) return;

        // Give hand card items to players
        AABB searchArea = AABB.ofSize(Vec3.atLowerCornerWithOffset(getBlockPos(), 1, 1, 1), PLAYER_RANGE, PLAYER_RANGE, PLAYER_RANGE);
        for (CardPlayer cardPlayer : playerList) {
            boolean playerFound = false;
            for (Entity entity : level.getEntities(null, searchArea)) {
                if (entity instanceof Player mcPlayer) {
                    if (cardPlayer.uuid.equals(mcPlayer.getGameProfile().getId())) {
                        // We've found the player, give them a card item
                        ItemStack handCard = new ItemStack(Mino.ITEM_HAND_CARDS.get());
                        ItemHandCards.CardGameBindingComponent newBinding =
                                new ItemHandCards.CardGameBindingComponent(getBlockPos(), cardPlayer.uuid);
                        handCard.set(Mino.DATA_COMPONENT_TYPE_CARD_GAME_BINDING.get(), newBinding);
                        if (Inventory.isHotbarSlot(mcPlayer.getInventory().selected)
                            && mcPlayer.getInventory().getSelected().isEmpty()) {
                            // If the player has an empty hand slot, put the card there
                            mcPlayer.getInventory().setItem(mcPlayer.getInventory().selected, handCard);
                            playerFound = true;
                        } else {
                            // Main hand is occupied, try to put the card in the inventory
                            boolean addSuccessful = mcPlayer.getInventory().add(handCard);
                            if (!addSuccessful) {
                                // Inventory is full, drop the card
                                ItemEntity itemEntity = mcPlayer.drop(handCard, false);
                                if (itemEntity != null) {
                                    itemEntity.setNoPickUpDelay();
                                    itemEntity.setTarget(mcPlayer.getUUID());
                                }
                            }
                            mcPlayer.displayClientMessage(Component.translatable("game.minopp.play.hand_card_in_inventory"), false);
                            playerFound = true;
                        }
                    }
                } else {
                    if (cardPlayer.uuid.equals(entity.getUUID())) {
                        // We've found an auto player, hopefully bound to this table
                        playerFound = true;
                    }
                }
                if (playerFound) break;
            }
            if (!playerFound) {
                // No player found or no hand card item given, destroy the game
                destroyGame(initiator);
                state = ActionReport.builder(initiator).panic(Component.translatable("game.minopp.play.player_unavailable", cardPlayer.name)).state;
                return;
            }
        }

        players.values().forEach(p -> { if (p != null) {
            p.hand.clear();
            p.hasShoutedMino = false;
        } });
        game = new CardGame(getPlayersList());
        state = game.initiate(initiator, 7).state;
        sendSeatActionTakenToAll();
        sync();
    }

    public void destroyGame(CardPlayer initiator) {
        if (game != null) sendSeatActionTakenToAll();
        game = null;

        // Remove hand card items from players
        for (Player mcPlayer : level.players()) {
            for (ItemStack invItem : mcPlayer.getInventory().items) {
                if (!invItem.is(Mino.ITEM_HAND_CARDS.get())) continue;
                ItemHandCards.CardGameBindingComponent gameBinding = invItem.get(Mino.DATA_COMPONENT_TYPE_CARD_GAME_BINDING.get());
                if (gameBinding != null && gameBinding.tablePos().equals(getBlockPos())) {
                    // This is the one bound to this table, remove
                    mcPlayer.getInventory().removeItem(invItem);
                }
            }
        }

        // Remove hand card from other entities eg. AutoPlayer, TLM
        for (CardPlayer cardPlayer : players.values()) {
            if (cardPlayer == null) continue;
            Entity entity = ((ServerLevel)level).getEntity(cardPlayer.uuid);
            if (entity instanceof LivingEntity livingEntity) {
                for (InteractionHand hand : InteractionHand.values()) {
                    ItemStack stack = livingEntity.getItemInHand(hand);
                    if (stack.is(Mino.ITEM_HAND_CARDS.get())) {
                        livingEntity.setItemInHand(hand, ItemStack.EMPTY);
                    }
                }
            }
        }

        players.values().forEach(p -> { if (p != null) {
            p.hand.clear();
            p.hasShoutedMino = false;
        } });
        state = ActionReport.builder(initiator).gameDestroyed().state;
        sync();
    }

    public void resetSeats(CardPlayer initiator) {
        sendSeatActionTakenToAll();
        players.replaceAll((d, v) -> null);
        state = ActionReport.builder(initiator).panic(Component.translatable("game.minopp.play.seats_reset", initiator.name)).state;
        sync();
    }

    public void handleActionResult(ActionReport result, CardPlayer cardPlayer, ServerPlayer player) {
        if (result != null) {
            if (result.shouldDestroyGame) {
                destroyGame(cardPlayer);
            }
            if (result.state != null) state = result.state;
            for (ActionMessage message : result.messages) {
                switch (message.type()) {
                    case FAIL -> {
                        if (player != null) S2CActionEphemeralPacket.sendS2C(player, getBlockPos(), message);
                    }
                    case MESSAGE_ALL -> sendMessageToAll(message);
                }
            }
            if (!result.effects.isEmpty()) {
                MinecraftServer server = ((ServerLevel)level).getServer();
                BlockPos tableCenterPos = getBlockPos().offset(1, 0, 1);
                for (EffectEvent effect : result.effects) {
                    effect.summonServer((ServerLevel) level, tableCenterPos, this);
                }
                for (ServerPlayer serverPlayer : server.getPlayerList().getPlayers()) {
                    if (serverPlayer.level().dimension() == level.dimension()) {
                        if (serverPlayer.position().distanceToSqr(Vec3.atCenterOf(tableCenterPos)) <= EffectEvents.EFFECT_RADIUS * EffectEvents.EFFECT_RADIUS) {
                            boolean playerPartOfGame = getPlayersList().stream().anyMatch(p -> p.uuid.equals(serverPlayer.getGameProfile().getId()));
                            S2CEffectListPacket.sendS2C(serverPlayer, result.effects, tableCenterPos, playerPartOfGame);
                        }
                    }
                }
            }
            sync();
        }
    }

    private void sendMessageToAll(ActionMessage message) {
        for (CardPlayer player : getPlayersList()) {
            Player mcPlayer = level.getPlayerByUUID(player.uuid);
            if (mcPlayer != null) {
                S2CActionEphemeralPacket.sendS2C((ServerPlayer) mcPlayer, getBlockPos(), message);
            }
        }
    }

    private void sendSeatActionTakenToAll() {
        for (CardPlayer player : getPlayersList()) {
            Player mcPlayer = level.getPlayerByUUID(player.uuid);
            BlockPos tableCenterPos = getBlockPos().offset(1, 0, 1);
            List<EffectEvent> events = List.of(new SeatActionTakenEffectEvent());
            if (mcPlayer != null) {
                S2CEffectListPacket.sendS2C((ServerPlayer) mcPlayer, events, tableCenterPos, true);
            }
        }
    }

    public void sync() {
        setChanged();
        BlockState blockState = level.getBlockState(getBlockPos());
        level.sendBlockUpdated(getBlockPos(), blockState, blockState, 2);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, provider);
        return tag;
    }

    @Nullable @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
