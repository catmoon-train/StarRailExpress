package org.agmas.noellesroles.item;

import io.wifi.starrailexpress.item.KnifeItem;

public class SPKnifeItem extends KnifeItem {

    public SPKnifeItem(Properties settings) {
        super(settings);
    }
    
    @Override
    public String getItemSkinType() {
        return "sp_knife";
    }
}
