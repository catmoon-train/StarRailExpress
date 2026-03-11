package pro.fazeclan.river.stupid_express.client.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import pro.fazeclan.river.stupid_express.network.SplitPersonalityPackets;
import pro.fazeclan.river.stupid_express.network.SplitPersonalitySwitchPacket;

public class SplitPersonalityClientNetwork {
    
    public static void sendChoicePacket(int choice) {
        if (Minecraft.getInstance().getConnection() != null) {
            ClientPlayNetworking.send(new SplitPersonalityPackets.SplitPersonalityChoicePayload(choice));
        }
    }
    
    public static void sendSwitchPacket() {
        if (Minecraft.getInstance().getConnection() != null) {
            ClientPlayNetworking.send(new SplitPersonalitySwitchPacket());
        }
    }
    
    public static void sendSacrificeChoice() {
        sendChoicePacket(0); // 0 = SACRIFICE
    }
    
    public static void sendBetrayChoice() {
        sendChoicePacket(1); // 1 = BETRAY
    }
}
