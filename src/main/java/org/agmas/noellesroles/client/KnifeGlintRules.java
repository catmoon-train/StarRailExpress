package org.agmas.noellesroles.client;

/** 刀的额外光泽决策；null 保留物品本身原有附魔效果。 */
public final class KnifeGlintRules {
    private KnifeGlintRules() {}
    public static Boolean foil(boolean realKnife, boolean fakeKnife, boolean civilianOrNeutral) {
        if (fakeKnife) return Boolean.FALSE;
        return realKnife && civilianOrNeutral ? Boolean.TRUE : null;
    }
}
