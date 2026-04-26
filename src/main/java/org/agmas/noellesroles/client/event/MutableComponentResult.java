package org.agmas.noellesroles.client.event;

import net.minecraft.network.chat.MutableComponent;
import org.agmas.noellesroles.utils.MessageDetail;

import java.util.ArrayList;

public class MutableComponentResult {
    public MessageDetail singleContent = null;
    public ArrayList<MessageDetail> mutipleContent = new ArrayList<>();

    public MutableComponentResult() {

    }

    public MutableComponentResult(MessageDetail content) {
        this.singleContent = content;
    }

    public MutableComponentResult(MutableComponent content) {
        this.singleContent = new MessageDetail(content, false);
    }
}
