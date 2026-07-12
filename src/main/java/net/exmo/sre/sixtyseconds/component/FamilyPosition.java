package net.exmo.sre.sixtyseconds.component;

/**
 * 家庭身份（轻量标签，不占用 SRERole 角色池）。
 * 括号内为准备阶段的携带上限：父亲 8，其余 2。
 */
public enum FamilyPosition {
    FATHER(8),
    MOTHER(2),
    SISTER(2),
    BROTHER(2);

    /** 准备阶段可携带的物资数量上限（P0 骨架仅存储，enforce 留 TODO）。 */
    public final int carryLimit;

    FamilyPosition(int carryLimit) {
        this.carryLimit = carryLimit;
    }

    /** 该家庭在编队中第 index 个成员（0=父,1=母,2=妹,3=哥）。 */
    public static FamilyPosition byIndex(int index) {
        FamilyPosition[] values = values();
        return values[Math.floorMod(index, values.length)];
    }
}
