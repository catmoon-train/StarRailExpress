package io.wifi.starrailexpress.content.item;

import java.util.List;

/**
 * 伪装变体表。
 * <p>
 * 每个变体对应一种伪装皮肤。变体在 {@link ModEffects#DISGUISE 伪装效果} 中通过
 * <b>等级（amplifier）</b> 区分：等级 0 = 第 0 个变体，等级 1 = 第 1 个变体，以此类推。
 * 因此「不同药水效果（等级）→ 不同皮肤」。
 * <p>
 * 新增一套伪装皮肤的步骤：
 * <ol>
 *   <li>在 {@link #VARIANTS} 末尾追加一个 {@link Variant}（皮肤资源路径 + 是否纤细模型）；</li>
 *   <li>在 {@code TMMItems} 注册一个对应的 {@link DisguiseItem}，构造参数传入该变体的下标；</li>
 *   <li>放置皮肤 png（64×64）与物品图标，并补充语言文件。</li>
 * </ol>
 * 皮肤路径相对 {@code starrailexpress} 命名空间，即实际文件位于
 * {@code assets/starrailexpress/<skinPath>}。
 */
public final class DisguiseVariants {

    /**
     * @param skinPath starrailexpress 命名空间下的皮肤纹理路径（64×64 标准皮肤）
     * @param slim     true = Alex 纤细模型，false = Steve 经典模型
     */
    public record Variant(String skinPath, boolean slim) {
    }

    /** 伪装变体列表，下标即伪装效果的等级（amplifier）。 */
    public static final List<Variant> VARIANTS = List.of(
            new Variant("textures/entity/disguise/disguise_skin_1.png", false),
            new Variant("textures/entity/disguise/disguise_skin_2.png", false),
            new Variant("textures/entity/disguise/disguise_skin_3.png", true));

    /**
     * 根据伪装效果等级取对应变体。
     *
     * @return 对应变体；等级越界时返回 {@code null}
     */
    public static Variant byAmplifier(int amplifier) {
        if (amplifier < 0 || amplifier >= VARIANTS.size()) {
            return null;
        }
        return VARIANTS.get(amplifier);
    }

    private DisguiseVariants() {
    }
}
