package org.agmas.noellesroles.game.modes.fourthroom.shop;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.game.modes.fourthroom.config.FourthRoomConfig;
import org.agmas.noellesroles.game.modes.fourthroom.game.FourthRoomGameManager;
import org.agmas.noellesroles.game.modes.fourthroom.game.FourthRoomPlayerState;
import org.agmas.noellesroles.game.modes.fourthroom.game.FourthRoomSavedData;
import org.agmas.noellesroles.game.modes.fourthroom.game.FourthRoomStickyNoteState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class FourthRoomShopService {
    private final FourthRoomGameManager manager;
    private final FourthRoomSavedData data;
    private final FourthRoomConfig config;

    public FourthRoomShopService(FourthRoomGameManager manager, FourthRoomSavedData data, FourthRoomConfig config) {
        this.manager = manager;
        this.data = data;
        this.config = config;
    }

    public boolean buy(UUID playerId, FourthRoomShopItem item) {
        FourthRoomPlayerState state = data.players.get(playerId);
        if (state == null || !state.alive) {
            return false;
        }
        int price = config.getPrice(item.id());
        if (state.coins < price) {
            return false;
        }
        state.coins -= price;
        switch (item) {
            case SCORPION -> state.scorpionCharges++;
            case HANDGUN -> state.handgunCharges++;
            case POISON_MUSHROOM -> state.poisonMushroomCharges++;
            case BULLETPROOF_VEST -> state.bulletproofVestCharges++;
            case TEST_STRIP -> state.testStripCharges++;
            case STICKY_NOTE -> state.stickyNoteCharges++;
        }
        manager.logPlayerRoomAction(playerId, "item", "购买了", manager.shopItemDisplayName(item), "", "花费 " + price + " 金币");
        data.setDirty(true);
        manager.syncMatchState();
        return true;
    }

    public boolean useAssassinationItem(UUID attackerId, UUID targetId, FourthRoomShopItem item) {
        FourthRoomPlayerState attacker = data.players.get(attackerId);
        FourthRoomPlayerState target = data.players.get(targetId);
        if (attacker == null || target == null || !attacker.alive || !target.alive) {
            return false;
        }
        if (!manager.taskScheduler().hasActiveTask()) {
            return false;
        }
        switch (item) {
            case SCORPION -> {
                if (attacker.scorpionCharges <= 0) {
                    return false;
                }
                attacker.scorpionCharges--;
                manager.logPlayerRoomAction(attackerId, "item", "使用了", manager.shopItemDisplayName(item), manager.playerName(targetId), "立即处决");
                manager.eliminatePlayer(targetId, "scorpion");
                return true;
            }
            case HANDGUN -> {
                if (attacker.handgunCharges <= 0) {
                    return false;
                }
                attacker.handgunCharges--;
                manager.logPlayerRoomAction(attackerId, "item", "使用了", manager.shopItemDisplayName(item), manager.playerName(targetId), "");
                if (target.bulletproofVestCharges > 0) {
                    target.bulletproofVestCharges--;
                    manager.logPlayerRoomAction(targetId, "defense", "触发了", manager.shopItemDisplayName(FourthRoomShopItem.BULLETPROOF_VEST), "", "挡下手枪");
                    manager.sendPrivate(targetId, Component.translatable("message.fourth_room.bulletproof_vest"));
                } else {
                    manager.eliminatePlayer(targetId, "handgun");
                }
                return true;
            }
            case POISON_MUSHROOM -> {
                if (attacker.poisonMushroomCharges <= 0) {
                    return false;
                }
                attacker.poisonMushroomCharges--;
                target.pendingPoisonDeathTick = manager.currentTick() + 3L * 60L * 20L;
                manager.logPlayerRoomAction(attackerId, "item", "使用了", manager.shopItemDisplayName(item), manager.playerName(targetId), "180 秒后生效");
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public boolean placeStickyNote(UUID playerId, BlockPos pos, Direction face, String text) {
        FourthRoomPlayerState playerState = data.players.get(playerId);
        if (playerState == null || playerState.stickyNoteCharges <= 0) {
            return false;
        }
        playerState.stickyNoteCharges--;
        FourthRoomStickyNoteState noteState = new FourthRoomStickyNoteState();
        noteState.ownerId = playerId;
        noteState.roomId = playerState.roomId;
        noteState.pos = pos;
        noteState.face = face;
        noteState.text = text;
        data.stickyNotes.add(noteState);
        data.setDirty(true);
        manager.syncMatchState();
        return true;
    }

    public List<String> searchNotes(UUID playerId, int roomId) {
        FourthRoomPlayerState seeker = data.players.get(playerId);
        if (seeker == null) {
            return List.of();
        }
        List<String> discovered = new ArrayList<>();
        for (FourthRoomStickyNoteState stickyNote : data.stickyNotes) {
            if (stickyNote.roomId != roomId || stickyNote.found || stickyNote.ownerId == null) {
                continue;
            }
            FourthRoomPlayerState owner = data.players.get(stickyNote.ownerId);
            if (owner != null && owner.team == seeker.team) {
                stickyNote.found = true;
                discovered.add(stickyNote.text);
            }
        }
        if (!discovered.isEmpty()) {
            data.setDirty(true);
            manager.syncMatchState();
        }
        return discovered;
    }
}