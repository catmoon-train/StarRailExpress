package org.agmas.noellesroles.client.event;

import java.util.ArrayList;

import net.minecraft.network.chat.MutableComponent;

public class MutableComponentResult {
    public MutableComponent singleContent = null;
    public ArrayList<MutableComponent> mutipleContent = new ArrayList<>();

    public MutableComponentResult() {

    }

    public MutableComponentResult(MutableComponent content) {
        this.singleContent = content;
    }
}
