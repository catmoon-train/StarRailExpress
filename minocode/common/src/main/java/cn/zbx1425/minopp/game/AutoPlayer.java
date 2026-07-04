package cn.zbx1425.minopp.game;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

import java.util.Random;

public class AutoPlayer {

    // Better Abstraction?

    public boolean aiNoWin = false;
    public boolean aiNoPlayerDraw = false;
    public float aiForgetChance = 0.2f;
    public byte aiNoDelay = 0;
    public boolean aiStartGame = false;

    public ActionReport playAtGame(CardGame game, CardPlayer realPlayer, MinecraftServer server) {
        Card topCard = game.topCard;
        boolean forgetsMino = new Random().nextFloat() < aiForgetChance;
        boolean shoutsMino = !forgetsMino && realPlayer.hand.size() <= 2;

        // If the next player is a human player, we should not play Draw cards
        CardPlayer nextPlayer = game.players.get((game.currentPlayerIndex + (game.isAntiClockwise ? -1 : 1) + game.players.size()) % game.players.size());
        boolean canPlayDrawCard = !aiNoPlayerDraw || server.getPlayerList().getPlayer(nextPlayer.uuid) == null;

        if (aiNoWin) {
            if (realPlayer.hand.size() <= 1) {
                return game.playNoCard(realPlayer);
            }
        }

        // Prioritize switching suit to make things more interesting
        // If we have a card of same number but different suit
        for (Card card : realPlayer.hand) {
            if (card.number == topCard.number && card.suit != topCard.getEquivSuit() && card.suit != Card.Suit.WILD) {
                if (!canPlayDrawCard && card.family == Card.Family.DRAW) continue;
                ActionReport result = game.playCard(realPlayer, card, null, shoutsMino);
                if (!result.isFail) return result;
            }
        }
        // If we have a Wild card
        for (Card card : realPlayer.hand) {
            if (card.suit == Card.Suit.WILD && card.family != Card.Family.DRAW) {
                ActionReport result = game.playCard(realPlayer, card, getMostCommonSuit(realPlayer), shoutsMino);
                if (!result.isFail) return result;
            }
        }

        // If we have a card of same suit
        for (Card card : realPlayer.hand) {
            if (card.suit == topCard.getEquivSuit() && card.suit != Card.Suit.WILD) {
                if (!canPlayDrawCard && card.family == Card.Family.DRAW) continue;
                ActionReport result = game.playCard(realPlayer, card, null, shoutsMino);
                if (!result.isFail) return result;
            }
        }
        // If we have any other card
        for (Card card : realPlayer.hand) {
            if (!canPlayDrawCard && card.family == Card.Family.DRAW) continue;
            if (card.canPlayOn(topCard)) {
                if (card.suit == Card.Suit.WILD) {
                    ActionReport result = game.playCard(realPlayer, card, getMostCommonSuit(realPlayer), shoutsMino);
                    if (!result.isFail) return result;
                } else {
                    ActionReport result = game.playCard(realPlayer, card, null, shoutsMino);
                    if (!result.isFail) return result;
                }
            }
        }

        // We're out of option
        return game.playNoCard(realPlayer);
    }

    private Card.Suit getMostCommonSuit(CardPlayer realPlayer) {
        int[] suitCount = new int[4];
        for (Card handCard : realPlayer.hand) {
            if (handCard.suit != Card.Suit.WILD) {
                suitCount[handCard.suit.ordinal()]++;
            }
        }
        Card.Suit mostCommonSuit = Card.Suit.values()[new Random().nextInt(0, 4)];
        for (int i = 1; i < 4; i++) {
            if (suitCount[i] > suitCount[mostCommonSuit.ordinal()]) {
                mostCommonSuit = Card.Suit.values()[i];
            }
        }
        return mostCommonSuit;
    }

    public void useConfigNbt(CompoundTag aiConfig) {
        aiNoWin = aiConfig.getBoolean("NoWin");
        aiNoPlayerDraw = aiConfig.getBoolean("NoPlayerDraw");
        aiForgetChance = aiConfig.contains("ForgetChance", CompoundTag.TAG_FLOAT) ? aiConfig.getFloat("ForgetChance") : 0.2f;
        aiNoDelay = aiConfig.getByte("NoDelay");
        aiStartGame = aiConfig.getBoolean("StartGame");
    }

    public CompoundTag toConfigNbt() {
        CompoundTag aiConfig = new CompoundTag();
        aiConfig.putBoolean("NoWin", aiNoWin);
        aiConfig.putBoolean("NoPlayerDraw", aiNoPlayerDraw);
        aiConfig.putFloat("ForgetChance", aiForgetChance);
        aiConfig.putByte("NoDelay", aiNoDelay);
        aiConfig.putBoolean("StartGame", aiStartGame);
        return aiConfig;
    }
}
