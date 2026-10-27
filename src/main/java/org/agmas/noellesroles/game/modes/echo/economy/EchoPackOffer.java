package org.agmas.noellesroles.game.modes.echo.economy;

import org.agmas.noellesroles.game.modes.echo.cards.EchoDraftService;

import java.util.List;
import java.util.Locale;

/**
 * 卡包商品：把现有的三档物资包（普通 / 稀有 / 黑市）包装成可购买的商店条目，
 * 并分成「道具卡包」与「技能卡包」两类。
 *
 * <p>两类的区别只在抽取池与价格：
 * <ul>
 *   <li>道具卡包：偏向被动、机动、物流与信息类（改变你在地图上的能力）；</li>
 *   <li>技能卡包：偏向一次性主动技能（假人、伪造、破坏、交易）。</li>
 * </ul>
 * 抽卡本身仍然走 {@link EchoDraftService} 的三选一，不新增第二套抽取算法。
 */
public enum EchoPackOffer {
    /** 道具 · 普通：最便宜，稳定拿到一张可用的被动/机动卡。 */
    ITEM_COMMON(EchoCategory.ITEM, EchoDraftService.PackQuality.COMMON, 20),
    /** 道具 · 稀有：被动品质更高，价格翻倍。 */
    ITEM_RARE(EchoCategory.ITEM, EchoDraftService.PackQuality.RARE, 45),
    /** 道具 · 黑市：保底稀有、可能史诗。 */
    ITEM_BLACK(EchoCategory.ITEM, EchoDraftService.PackQuality.BLACK_MARKET, 80),
    /** 技能 · 普通：主动技能卡的基础档。 */
    SKILL_COMMON(EchoCategory.SKILL, EchoDraftService.PackQuality.COMMON, 25),
    /** 技能 · 稀有：更容易抽到高级主动技能。 */
    SKILL_RARE(EchoCategory.SKILL, EchoDraftService.PackQuality.RARE, 50),
    /** 技能 · 黑市：最高档，含史诗主动技能。 */
    SKILL_BLACK(EchoCategory.SKILL, EchoDraftService.PackQuality.BLACK_MARKET, 90);

    /** 卡包分类。 */
    public enum EchoCategory {
        /** 道具卡包：被动、机动、物流、信息。 */
        ITEM,
        /** 技能卡包：一次性主动技能。 */
        SKILL
    }

    private final EchoCategory category;
    private final EchoDraftService.PackQuality quality;
    private final int price;

    EchoPackOffer(EchoCategory category, EchoDraftService.PackQuality quality, int price) {
        this.category = category;
        this.quality = quality;
        this.price = price;
    }

    public EchoCategory category() { return category; }
    public EchoDraftService.PackQuality quality() { return quality; }
    public int price() { return price; }
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String nameKey() { return "echo.shop.pack." + id() + ".name"; }
    public String descriptionKey() { return "echo.shop.pack." + id() + ".desc"; }

    public static List<EchoPackOffer> of(EchoCategory category) {
        return java.util.Arrays.stream(values()).filter(offer -> offer.category == category).toList();
    }
}
