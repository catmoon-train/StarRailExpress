package net.exmo.sre.sixtyseconds.content.item;

import net.minecraft.world.item.Item;

/**
 * 60s 箭矢：弓/弩的弹药载体（{@link SixtySecondsBowItem} 发射时消耗一支，生成
 * {@code SixtySecondsArrowEntity} 按箭矢类型结算怪物伤害/玩家健康伤害与附加效果）。
 * 本身只携带 {@link ArrowType}，无使用逻辑。
 */
public class SixtySecondsArrowItem extends Item {

    /**
     * 箭矢类型：对怪基础伤害 / 对玩家基础健康伤害 / 附加效果 / 物品 id 尾缀。
     * 实际伤害 = 基础 × 弓强度倍率 × 拉弓充能。新值只能追加在末尾（id 用于实体同步/存档）。
     */
    public enum ArrowType {
        /** 简易箭：木石制，伤害低。 */
        CRUDE(0, 8.0F, 6, Effect.NONE, "crude_arrow"),
        /** 铁头箭：标准箭矢。 */
        IRON(1, 14.0F, 10, Effect.NONE, "iron_arrow"),
        /** 钢头箭：高穿透高伤害。 */
        STEEL(2, 24.0F, 16, Effect.NONE, "steel_arrow"),
        /** 火箭矢：命中点燃目标。 */
        FIRE(3, 14.0F, 9, Effect.FIRE, "fire_arrow"),
        /** 毒箭：命中施加中毒（对玩家附加污染）。 */
        POISON(4, 12.0F, 8, Effect.POISON, "poison_arrow"),
        /** 爆炸箭：命中/落地产生小范围爆炸。 */
        EXPLOSIVE(5, 20.0F, 14, Effect.EXPLODE, "explosive_arrow"),
        /** 污浊箭：命中增加感染值。 */
        TAINTED(6, 14.0F, 10, Effect.TAINT, "tained_arrow"),
        /** 破轮箭：对载具额外伤害。 */
        WHEEL_BREAKER(7, 24.0F, 16, Effect.WHEEL_BREAK, "wheel_breaker_arrow"),
        /** 穿甲箭：对有护甲玩家额外伤害。 */
        ARMOR_PIERCING(8, 20.0F, 14, Effect.ARMOR_PIERCE, "armor_piercing_arrow"),
        /** 光灵箭：命中后目标发光。 */
        GLOWING(9, 14.0F, 10, Effect.GLOW, "glowing_arrow"),
        /** 失明箭：命中后目标失明。 */
        BLINDING(10, 24.0F, 16, Effect.BLIND, "blinding_arrow"),
        /** 合金箭：2倍钢头箭伤害。 */
        ALLOY(11, 48.0F, 32, Effect.NONE, "alloy_arrow"),
        /** 狩猎箭：对怪物额外伤害。 */
        HUNTING(12, 14.0F, 10, Effect.HUNT, "hunting_arrow");

        public enum Effect { NONE, FIRE, POISON, EXPLODE, TAINT, WHEEL_BREAK, ARMOR_PIERCE, GLOW, BLIND, HUNT }

        public final int id;
        public final float monsterDamage;
        public final int playerInjury;
        public final Effect effect;
        public final String itemName;

        ArrowType(int id, float monsterDamage, int playerInjury, Effect effect, String itemName) {
            this.id = id;
            this.monsterDamage = monsterDamage;
            this.playerInjury = playerInjury;
            this.effect = effect;
            this.itemName = itemName;
        }

        public static ArrowType byId(int id) {
            for (ArrowType type : values()) {
                if (type.id == id) {
                    return type;
                }
            }
            return CRUDE;
        }

        /** 对应的箭矢物品（用于拾取返还/图标）。 */
        public Item item() {
            return switch (this) {
                case CRUDE -> org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_CRUDE_ARROW;
                case IRON -> org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_IRON_ARROW;
                case STEEL -> org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_STEEL_ARROW;
                case FIRE -> org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_FIRE_ARROW;
                case POISON -> org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_POISON_ARROW;
                case EXPLOSIVE -> org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_EXPLOSIVE_ARROW;
                case TAINTED -> org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_TAINTED_ARROW;
                case WHEEL_BREAKER -> org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_WHEEL_BREAKER_ARROW;
                case ARMOR_PIERCING -> org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_ARMOR_PIERCING_ARROW;
                case GLOWING -> org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_GLOWING_ARROW;
                case BLINDING -> org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_BLINDING_ARROW;
                case ALLOY -> org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_ALLOY_ARROW;
                case HUNTING -> org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_HUNTING_ARROW;
            };
        }
    }

    private final ArrowType type;

    public SixtySecondsArrowItem(Properties properties, ArrowType type) {
        super(properties);
        this.type = type;
    }

    public ArrowType type() {
        return type;
    }
}
