package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.content.item.KnifeItem;

public class SPKnifeItem extends KnifeItem {

    public SPKnifeItem(Properties settings) {
        super(settings);
    }
    
    @Override
    public String getItemSkinType() {
        return "sp_knife";
    }
}
