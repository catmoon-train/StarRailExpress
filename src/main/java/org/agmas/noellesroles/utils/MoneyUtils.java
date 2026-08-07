package org.agmas.noellesroles.utils;

import io.wifi.starrailexpress.cca.SREPlayerMinigameTaskComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.world.entity.player.Player;

public class MoneyUtils {
    public static void setBalance(Player player, int balance) {
        SREPlayerShopComponent.KEY.get(player).setBalance(balance);
    }

    public static void addToBalance(Player player, int balance) {
        SREPlayerShopComponent.KEY.get(player).addToBalance(balance);
    }

    public static int getBalance(Player player, int balance) {
        return SREPlayerShopComponent.KEY.get(player).balance;
    }

    public static void setMinigamesTokens(Player player, int balance) {
        SREPlayerMinigameTaskComponent.KEY.get(player).setTokens(balance);
    }

    public static void addToMinigamesTokens(Player player, int balance) {
        SREPlayerMinigameTaskComponent.KEY.get(player).addTokens(balance);
    }

    public static int getMinigamesTokens(Player player, int balance) {
        return SREPlayerMinigameTaskComponent.KEY.get(player).getTokens();
    }
}
