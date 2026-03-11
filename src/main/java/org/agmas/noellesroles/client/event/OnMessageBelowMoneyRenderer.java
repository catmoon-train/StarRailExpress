package org.agmas.noellesroles.client.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface OnMessageBelowMoneyRenderer {

    Event<OnMessageBelowMoneyRenderer> EVENT = createArrayBacked(OnMessageBelowMoneyRenderer.class,
            listeners -> (client, guiGraphics, deltaTracker) -> {
                MutableComponentResult a = new MutableComponentResult();
                for (OnMessageBelowMoneyRenderer listener : listeners) {

                    var res = listener.onRenderer(client, guiGraphics, deltaTracker);
                    if (res != null && res.singleContent != null)
                        a.mutipleContent.add(res.singleContent);
                }
                return a;
            });

    MutableComponentResult onRenderer(Minecraft client, GuiGraphics guiGraphics, DeltaTracker deltaTracker);
}