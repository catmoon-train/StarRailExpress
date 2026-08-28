package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.content.item.KnifeItem;

/**
 * 野人魔了形态专属的刀。
 *
 * <p>它不是制式杀手刀，因此不会被杀手刀的耐久标记或冷却表影响；目标阵营限制由
 * {@code ModRoles.BARBARIAN.onUseKnifeHit} 在服务端统一校验。</p>
 */
public final class BarbarianKnifeItem extends KnifeItem {
    public BarbarianKnifeItem(Properties properties) {
        super(properties);
    }
}
