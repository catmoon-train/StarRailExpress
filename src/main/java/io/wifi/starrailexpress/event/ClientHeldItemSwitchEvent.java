package io.wifi.starrailexpress.event;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

@Environment(EnvType.CLIENT)
public interface ClientHeldItemSwitchEvent {
    Event<ClientHeldItemSwitchEvent> EVENT = createArrayBacked(ClientHeldItemSwitchEvent.class,
            listeners -> (player, mainHand, offHand) -> {
                for (ClientHeldItemSwitchEvent listener : listeners) {
                    listener.onSwitch(player, mainHand, offHand);
                }
            });

    void onSwitch(LocalPlayer player, ItemStack mainHand, ItemStack offHand);
}
