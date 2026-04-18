package org.agmas.noellesroles.game.modes.fourthroom.game;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.agmas.noellesroles.game.modes.fourthroom.card.CardInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class FourthRoomPlayerState {
    public UUID playerId;
    public FourthRoomTeam team = FourthRoomTeam.RED;
    public final List<String> identityBlocks = new ArrayList<>();
    public final List<Boolean> revealed = new ArrayList<>();
    public int coins;
    public boolean alive = true;
    public int roomId = -1;
    public final List<CardInstance> drawPile = new ArrayList<>();
    public final List<CardInstance> hand = new ArrayList<>();
    public final List<CardInstance> discardPile = new ArrayList<>();
    public String skillCardId = "";
    public int lifeShield;
    public int skipTurns;
    public int extraTurns;
    public int markedForKill;
    public boolean decoyArmed;
    public int bulletproofVestCharges;
    public int scorpionCharges;
    public int handgunCharges;
    public int poisonMushroomCharges;
    public int testStripCharges;
    public int stickyNoteCharges;
    public long pendingPoisonDeathTick = -1L;
    public boolean taskCompleted;
    public int drawNoticeSequence;
    public String lastDrawSummary = "";
    public final List<String> peekCache = new ArrayList<>();

    public FourthRoomPlayerState() {
    }

    public FourthRoomPlayerState(UUID playerId) {
        this.playerId = playerId;
    }

    public int hiddenIdentityCount() {
        int hidden = 0;
        for (int index = 0; index < revealed.size(); index++) {
            if (!revealed.get(index)) {
                hidden++;
            }
        }
        return hidden;
    }

    public boolean isIdentityFullyRevealed() {
        return hiddenIdentityCount() == 0;
    }

    public int firstHiddenIdentityIndex() {
        for (int index = 0; index < revealed.size(); index++) {
            if (!revealed.get(index)) {
                return index;
            }
        }
        return -1;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("PlayerId", playerId);
        tag.putString("Team", team.name());
        ListTag identityTag = new ListTag();
        for (String blockId : identityBlocks) {
            identityTag.add(StringTag.valueOf(blockId));
        }
        tag.put("IdentityBlocks", identityTag);
        ListTag revealTag = new ListTag();
        for (Boolean isRevealed : revealed) {
            revealTag.add(StringTag.valueOf(Boolean.toString(isRevealed)));
        }
        tag.put("Revealed", revealTag);
        tag.putInt("Coins", coins);
        tag.putBoolean("Alive", alive);
        tag.putInt("RoomId", roomId);
        tag.putString("SkillCardId", skillCardId == null ? "" : skillCardId);
        tag.putInt("LifeShield", lifeShield);
        tag.putInt("SkipTurns", skipTurns);
        tag.putInt("ExtraTurns", extraTurns);
        tag.putInt("MarkedForKill", markedForKill);
        tag.putBoolean("DecoyArmed", decoyArmed);
        tag.putInt("BulletproofVestCharges", bulletproofVestCharges);
        tag.putInt("ScorpionCharges", scorpionCharges);
        tag.putInt("HandgunCharges", handgunCharges);
        tag.putInt("PoisonMushroomCharges", poisonMushroomCharges);
        tag.putInt("TestStripCharges", testStripCharges);
        tag.putInt("StickyNoteCharges", stickyNoteCharges);
        tag.putLong("PendingPoisonDeathTick", pendingPoisonDeathTick);
        tag.putBoolean("TaskCompleted", taskCompleted);
        tag.putInt("DrawNoticeSequence", drawNoticeSequence);
        tag.putString("LastDrawSummary", lastDrawSummary == null ? "" : lastDrawSummary);
        tag.put("DrawPile", saveCards(drawPile));
        tag.put("Hand", saveCards(hand));
        tag.put("DiscardPile", saveCards(discardPile));
        ListTag peekTag = new ListTag();
        for (String peek : peekCache) {
            peekTag.add(StringTag.valueOf(peek));
        }
        tag.put("PeekCache", peekTag);
        return tag;
    }

    public static FourthRoomPlayerState load(CompoundTag tag) {
        FourthRoomPlayerState state = new FourthRoomPlayerState(tag.getUUID("PlayerId"));
        state.team = FourthRoomTeam.valueOf(tag.getString("Team"));
        for (Tag identityEntry : tag.getList("IdentityBlocks", Tag.TAG_STRING)) {
            state.identityBlocks.add(identityEntry.getAsString());
        }
        for (Tag revealedEntry : tag.getList("Revealed", Tag.TAG_STRING)) {
            state.revealed.add(Boolean.parseBoolean(revealedEntry.getAsString()));
        }
        state.coins = tag.getInt("Coins");
        state.alive = !tag.contains("Alive") || tag.getBoolean("Alive");
        state.roomId = tag.getInt("RoomId");
        state.skillCardId = tag.getString("SkillCardId");
        state.lifeShield = tag.getInt("LifeShield");
        state.skipTurns = tag.getInt("SkipTurns");
        state.extraTurns = tag.getInt("ExtraTurns");
        state.markedForKill = tag.getInt("MarkedForKill");
        state.decoyArmed = tag.getBoolean("DecoyArmed");
        state.bulletproofVestCharges = tag.getInt("BulletproofVestCharges");
        state.scorpionCharges = tag.getInt("ScorpionCharges");
        state.handgunCharges = tag.getInt("HandgunCharges");
        state.poisonMushroomCharges = tag.getInt("PoisonMushroomCharges");
        state.testStripCharges = tag.getInt("TestStripCharges");
        state.stickyNoteCharges = tag.getInt("StickyNoteCharges");
        state.pendingPoisonDeathTick = tag.contains("PendingPoisonDeathTick") ? tag.getLong("PendingPoisonDeathTick") : -1L;
        state.taskCompleted = tag.getBoolean("TaskCompleted");
        state.drawNoticeSequence = tag.getInt("DrawNoticeSequence");
        state.lastDrawSummary = tag.getString("LastDrawSummary");
        loadCards(tag.getList("DrawPile", Tag.TAG_COMPOUND), state.drawPile);
        loadCards(tag.getList("Hand", Tag.TAG_COMPOUND), state.hand);
        loadCards(tag.getList("DiscardPile", Tag.TAG_COMPOUND), state.discardPile);
        for (Tag peekEntry : tag.getList("PeekCache", Tag.TAG_STRING)) {
            state.peekCache.add(peekEntry.getAsString());
        }
        while (state.revealed.size() < state.identityBlocks.size()) {
            state.revealed.add(false);
        }
        return state;
    }

    private static ListTag saveCards(List<CardInstance> cards) {
        ListTag list = new ListTag();
        for (CardInstance card : cards) {
            list.add(card.save());
        }
        return list;
    }

    private static void loadCards(ListTag list, List<CardInstance> out) {
        for (Tag entry : list) {
            if (entry instanceof CompoundTag compoundTag) {
                out.add(CardInstance.load(compoundTag));
            }
        }
    }
}