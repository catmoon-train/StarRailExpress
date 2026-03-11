package pro.fazeclan.river.stupid_express.client.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import pro.fazeclan.river.stupid_express.network.SplitPersonalityPackets;
import pro.fazeclan.river.stupid_express.network.SplitPersonalitySwitchPacket;

public class SplitPersonalityClientPackets {
    
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
}
