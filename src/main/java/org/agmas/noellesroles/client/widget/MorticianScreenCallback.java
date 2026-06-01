package org.agmas.noellesroles.client.widget;

import java.util.UUID;

/**
 * 葬仪屏幕回调接口
 * 用于BodymakerScreenMixin中的多阶段选择流程
 */
public interface MorticianScreenCallback {
    /**
     * 设置选中的玩家
     */
    void setSelectedPlayer(UUID uuid);
    
    /**
     * 设置选中的死亡原因
     */
    void setSelectedDeathReason(String deathReason);
}
