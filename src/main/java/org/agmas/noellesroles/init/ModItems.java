package org.agmas.noellesroles.init;

import dev.doctor4t.ratatouille.util.registrar.ItemRegistrar;
import io.wifi.starrailexpress.api.ChargeableItemRegistry;
import io.wifi.starrailexpress.api.impl.KnifeChargeableItem;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.Foods;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.item.*;
import org.agmas.noellesroles.item.charge_item.AntidoteChargeItem;
import org.agmas.noellesroles.item.charge_item.AntidoteReagentChargeItem;
import org.agmas.noellesroles.item.charge_item.BowenBadgeChargeItem;
import org.agmas.noellesroles.item.charge_item.ToxinChargeItem;
import org.agmas.noellesroles.repack.HSRItems;
import org.agmas.noellesroles.utils.LocalDateData;

import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.item.KnifeItem;

public class ModItems {
    public static ResourceKey<CreativeModeTab> MISC_CREATIVE_GROUP = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            Noellesroles.id("misc"));
    public static final ItemRegistrar registrar = new ItemRegistrar(Noellesroles.MOD_ID);

    public static final Item COOKED_FOOD = register(
            new ChefFoodItem(new Item.Properties().stacksTo(1)), "cooked_food");
    public static final Item A_BOTTLE_OF_WATER = register(
            new ChefWaterItem((new Item.Properties()).stacksTo(1).food(Foods.HONEY_BOTTLE)),
            "a_bottle_of_water");
    public static final Item LINGSHI = register(
            new ChefFoodItem((new Item.Properties()).stacksTo(1)), "lingshi");

    public static final Item FOOD_STUFF = register(
            new FoodStuffItem((new Item.Properties()).stacksTo(16)), "foodstuff");
    public static final Item PAN = register(
            new PanItem((new Item.Properties()).stacksTo(1)), "pan");
    public static final Item BUCKET_OF_H2SO4 = register(
            new H2SO4AcidItem((new Item.Properties()).stacksTo(1)), "bucket_of_h2so4");
    public static final Item LETTER_ITEM = register(
            new LetterItem((new Item.Properties()).stacksTo(1)), "letter");

    public static final Item ONCE_REVOLVER = register(
            new OnceRevolverItem((new Item.Properties()).stacksTo(1).durability(1)), "once_revolver");
    public static final Item HANDCUFFS = register(
            new HandCuffsItem((new Item.Properties()).stacksTo(1)), "handcuffs");
    public static final Item PATROLLER_REVOLVER = register(
            new PatrollerRevolverItem((new Item.Properties()).stacksTo(1)), "patroller_revolver");
    public static final Item SINGER_MUSIC_DISC = register(
            new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)),
            "singer_music_disc");
    public static final Item NIGHT_VISION_GLASSES = register(
            new NightGlassesItem(ArmorMaterials.TURTLE, net.minecraft.world.item.ArmorItem.Type.HELMET,
                    (new Item.Properties()).durability(60)),
            "night_vision_glasses");

    public static final Item FAKE_KNIFE = register(
            new FakeKnifeItem(new Item.Properties().stacksTo(1)),
            "fake_knife");
    public static final Item SP_KNIFE = register(
            new KnifeItem(new Item.Properties().stacksTo(1)),
            "sp_knife");
    public static final Item FAKE_REVOLVER = register(
            new FakeRevolverItem(new Item.Properties().stacksTo(1).durability(4)),
            "fake_revolver");

    public static final Item FAKE_BAT = register(
            new FakeBatItem(new Item.Properties().stacksTo(1)),
            "fake_bat");

    public static final Item FAKE_PSYCHO_MODE = register(
            new Item(new Item.Properties().stacksTo(1)),
            "fake_psycho_mode");

    public static final Item FAKE_GRENADE = register(
            new FakeGrenadeItem(new Item.Properties().stacksTo(1)),
            "fake_grenade");

    public static final Item FAKE_LOCKPICK = register(
            new FakeLockpickItem(new Item.Properties().stacksTo(1)),
            "fake_lockpick");

    public static final Item FAKE_CROWBAR = register(
            new FakeCrowbarItem(new Item.Properties().stacksTo(1)),
            "fake_crowbar");

    public static final Item FAKE_BODY_BAG = register(
            new FakeBodyBagItem(new Item.Properties().stacksTo(1)),
            "fake_body_bag");

    public static final Item MASTER_KEY = register(
            new Item(new Item.Properties().stacksTo(1)),
            "master_key");
    public static final Item MASTER_KEY_P = register(
            new MasterKeyItem(new Item.Properties().stacksTo(1).durability(5)),
            "master_key_p");
    public static final Item DELUSION_VIAL = register(
            new Item(new Item.Properties().stacksTo(1)),
            "delusion_vial");

    public static final Item ROLE_MINE = register(
            new Item(new Item.Properties().stacksTo(1)),
            "role_mine");

    public static final Item DEFIBRILLATOR = register(
            new DefibrillatorItem(new Item.Properties().stacksTo(1)),
            "defibrillator");

    public static final Item BOXING_GLOVE = register(
            new BoxingGloveItem(new Item.Properties().stacksTo(1)),
            "boxing_glove");

    public static final Item ANTIDOTE_REAGENT = register(
            new AntidoteReagentItem(new Item.Properties().stacksTo(16).durability(5)),
            "antidote_reagent");

    /**
     * 阴谋之书页
     * - 阴谋家专属物品
     * - 在商店以250金币购买
     * - 右键使用打开玩家/角色选择GUI
     */
    public static final Item CONSPIRACY_PAGE = register(
            new ConspiracyPageItem(new Item.Properties().stacksTo(1)),
            "conspiracy_page");

    /**
     * 空包弹
     * - 滑头鬼专属物品
     * - 在商店以100金币购买
     * - 右键对目标玩家使用，使其手中枪械进入30秒冷却
     */
    public static final Item BLANK_CARTRIDGE = register(
            new BlankCartridgeItem(new Item.Properties().stacksTo(16)),
            "blank_cartridge");

    /**
     * 烟雾弹
     * - 滑头鬼专属物品
     * - 在商店以300金币购买
     * - 右键投掷，形成烟雾区域
     * - 进入烟雾的玩家获得失明效果
     * - 直接命中玩家时清空目标san值
     */
    public static final Item SMOKE_GRENADE = register(
            new SmokeGrenadeItem(new Item.Properties().stacksTo(8)),
            "smoke_grenade");

    /**
     * 氯气弹
     * - 可投掷物品
     * - 右键投掷，落地时使半径3格内玩家中毒
     * - 落地时播放火熄灭声
     */
    public static final Item CHLORINE_BOMB = register(
            new ChlorineBombItem(new Item.Properties().stacksTo(8)),
            "chlorine_bomb");

    /**
     * 净化弹
     * - 可投掷物品
     * - 右键投掷，落地时取消半径3格内玩家的中毒状态
     * - 落地时播放守卫者激光射击声
     * - 粒子效果为气泡
     */
    public static final Item PURIFY_BOMB = register(
            new PurifyBombItem(new Item.Properties().stacksTo(8)),
            "purify_bomb");

    /**
     * 闪光弹
     * - 可投掷物品
     * - 右键投掷，落地时使半径6格内有闪光弹的玩家获得试炼之兆效果（WEAVING）3秒
     * - 落地时播放火熄灭声
     */
    public static final Item FLASH_GRENADE = register(
            new FlashGrenadeItem(new Item.Properties().stacksTo(8)),
            "flash_grenade");

    /**
     * 诱饵弹
     * - 可投掷物品
     * - 右键投掷，落地时不会产生粒子效果
     * - 在落地处发生5声左轮手枪射击的声音（时间间隔不一）
     */
    public static final Item DECOY_GRENADE = register(
            new DecoyGrenadeItem(new Item.Properties().stacksTo(8)),
            "decoy_grenade");

    /**
     * 加固门道具
     * - 工程师专属物品
     * - 在商店以75金币购买
     * - 右键门：使门能够防御一次撬棍攻击
     * - 蹲下右键被卡住的门：解除卡住状态
     */
    public static final Item REINFORCEMENT = register(
            new ReinforcementItem(new Item.Properties().stacksTo(16)),
            "reinforcement");

    public static final Item SCREWDRIVER = register(
            new ScrewdriverItem(new Item.Properties().stacksTo(16)),
            "screwdriver");

    /**
     * 警报陷阱
     * - 工程师专属物品
     * - 在商店以120金币购买
     * - 右键门：在门上放置警报陷阱
     * - 当撬棍使用时触发，发出响亮的警报声
     */
    public static final Item ALARM_TRAP = register(
            new AlarmTrapItem(new Item.Properties().stacksTo(16)),
            "alarm_trap");

    /**
     * 传递盒
     * - 邮差专属物品
     * - 在商店以150金币购买
     * - 指针对准玩家并右键使用，打开传递界面
     * - 双方可以放入一样物品并交换
     */
    public static final Item DELIVERY_BOX = register(
            new DeliveryBoxItem(new Item.Properties().stacksTo(8)),
            "delivery_box");

    /**
     * 迷幻瓶
     * - 迷幻师专属物品
     * - 在商店购买
     * - 右键使用，制造大量烟雾
     * - 20格范围内玩家视野会随机偏离视角
     * - 迷雾范围：20格
     * - 持续时间：3秒
     * - 触发间隔：1秒
     * - 耐久：2点
     */
    public static final Item HALLUCINATION_BOTTLE = register(
            new HallucinationBottleItem(new Item.Properties().stacksTo(1).durability(2)),
            "hallucination_bottle");

    /**
     * 薄荷糖
     * - 心理学家专属物品
     * - 游戏开始时给予一个
     * - 在商店可以花费100金币购买
     * - 吃掉时恢复0.35的san值（35%）
     */
    public static final Item MINT_CANDIES = register(
            new MintCandiesItem(new Item.Properties().stacksTo(16)),
            "mint_candies");
    /**
     * 记录笔记
     * - 记录员专属物品
     * - 开局给予
     * - 右键使用打开记录界面
     */
    public static final Item WRITTEN_NOTE = register(
            new WrittenNoteItem(new Item.Properties().stacksTo(1)),
            "written_note");
    /**
     * 炸弹
     * - 炸弹客专属物品
     * - 倒计时10秒，前5秒隐形
     * - 右键传递
     */
    public static final Item BOMB = register(
            new BombItem(new Item.Properties().stacksTo(1)),
            "bomb");
    /**
     * 炸弹
     * - 炸弹客专属物品
     * - 倒计时10秒，前5秒隐形
     * - 右键传递
     */
    public static final Item WHEELCHAIR = register(
            new WheelchairItem(),
            "wheelchair");
    /**
     * 锁
     * - 工程师专属物品
     * - 工程师商店购买
     * - 右键门：将门锁上，使用撬锁器时需要解锁，失败后损坏撬锁器
     * - 默认长度为6，如有需要以后可以利用json进行配置
     */
    public static final Item LOCK_ITEM = register(
            new LockItem(6, 0.1f, new Item.Properties().stacksTo(1)),
            "lock");

    /**
     * 怀表
     * - 右键使用查看当前局内游戏时间
     * - 使用后进入60秒冷却
     * - 钟表匠商店可用100金币购买
     */
    public static final Item POCKET_WATCH = register(
            new PocketWatchItem(new Item.Properties().stacksTo(1)),
            "pocket_watch");

    /**
     * 肾上腺素
     * - 一次性道具
     * - 对目标使用后增加体力上限
     */
    public static final Item ADRENALINE = register(
            new AdrenalineItem(new Item.Properties().stacksTo(1)),
            "adrenaline");

    /**
     * 抗生素
     * - 一次性道具
     * - 对目标使用后使目标解除中毒
     */
    public static final Item ANTIBIOTIC = register(
            new AntibioticItem(new Item.Properties().stacksTo(1)),
            "antibiotic");

    /**
     * 鹤顶红
     * - 一次性道具
     * - 对目标使用后使目标中毒
     */
    public static final Item HEDINGHONG = register(
            new HedinghongItem(new Item.Properties().stacksTo(1)),
            "hedinghong");

    /**
     * 狗皮膏药
     * - 一次性道具
     * - 对目标使用后使目标30秒内san值不会下降
     */
    public static final Item DOGSKIN_PLASTER = register(
            new DogskinPlasterItem(new Item.Properties().stacksTo(1)),
            "dogskin_plaster");

    /**
     * 消防斧
     * - 3点耐久
     * - Shift+右键：直接撬开门，消耗1点耐久，30秒冷却
     * - 直接右键：像刀一样举起，蓄力2秒，可击杀一名玩家，消耗3点耐久（需满耐久）
     * - 击杀玩家会触发误杀惩罚
     */
    public static final Item FIRE_AXE = register(
            new FireAxeItem(new Item.Properties().stacksTo(1).durability(3)),
            "fire_axe");
    public static final Item THROWING_KNIFE = register(
            new ThrowingKnife((new Item.Properties()).stacksTo(1)), "throwing_knife");
    /**
     * 绳索
     * - 2点耐久
     * - 右键：将前方直线距离12格内你瞄准的玩家拉到自己身前
     * - 使用后进入5秒冷却并消耗1点耐久
     */
    public static final Item ROPE = register(
            new RopeItem(new Item.Properties().stacksTo(1).durability(2)),
            "rope");

    /**
     * 灭火器
     * - 5点耐久
     * - 右键对人喷射：每使用一次消耗1点耐久
     * 长按右键持续喷射：最多持续5秒，持续消耗耐久
     * - 对人喷射效果：缓慢 + 失明（持续1.5秒）
     * - 持续喷射同一人会刷新效果时间
     * - 如果被喷射的人被纵火犯浇湿，则清除浇湿状态
     */
    public static final Item EXTINGUISHER = register(
            new ExtinguisherItem(new Item.Properties().stacksTo(1).durability(5)),
            "extinguisher");

    /**
     * 存折
     * - 用于查看和记录金币数量
     * - 右键使用显示当前金币
     */
    public static final Item PASSBOOK = register(
            new PassbookItem(new Item.Properties().stacksTo(1)),
            "passbook");

    /**
     * 药剂素材
     * - 用于药剂相关合成
     */
    public static final Item ALCHEMY_MATERIAL = register(
            new AlchemyMaterialItem(new Item.Properties().stacksTo(64)),
            "alchemy_material");

    /**
     * 签名纸
     */
    public static final Item SIGNATURE_PAPER = register(
            new SignaturePaperItem(new Item.Properties().stacksTo(1)),
            "signature_paper");

    /**
     * 生死状
     */
    public static final Item LIFE_AND_DEATH_SHAPE = register(
            new SignedPaperItem(new Item.Properties().stacksTo(1)),
            "life_and_death_shape");

    /**
     * 明星签名
     */
    public static final Item SIGNED_PAPER = register(
            new SignedPaperItem(new Item.Properties().stacksTo(1)),
            "signed_paper");

    public static final Item ZHANWEIFU1 = register(
            new Item(new Item.Properties().stacksTo(64)),
            "zhanweifu1");
    public static final Item ZHANWEIFU2 = register(
            new Item(new Item.Properties().stacksTo(64)),
            "zhanweifu2");

    public static final ItemStack ExamplerPsychoItemStack = TMMItems.PSYCHO_MODE.getDefaultInstance();

    static {
        var examplerPsychoLore = new ItemLore(
                List.of(Component.translatable("itemstack.exampler.psychoitem.item_lore.1"),
                        Component.translatable("itemstack.exampler.psychoitem.item_lore.2")));
        ExamplerPsychoItemStack.set(DataComponents.LORE, examplerPsychoLore);
        ExamplerPsychoItemStack.set(DataComponents.ITEM_NAME,
                Component.translatable("itemstack.exampler.psychoitem.item_name"));
        ChargeableItemRegistry.register(ANTIDOTE_REAGENT, new AntidoteReagentChargeItem());
        ChargeableItemRegistry.register(FunnyItems.BOWEN_BADGE, new BowenBadgeChargeItem());
        ChargeableItemRegistry.register(HSRItems.TOXIN, new ToxinChargeItem());
        ChargeableItemRegistry.register(ModItems.THROWING_KNIFE, new KnifeChargeableItem());
        ChargeableItemRegistry.register(HSRItems.ANTIDOTE, new AntidoteChargeItem());
    }
    // public static final Item SHERIFF_GUN_MAINTENANCE = register(
    // new SheriffGunMaintenanceItem(new Item.Settings().maxCount(1)),
    // "sheriff_gun_maintenance"
    // );
    // public static final Item SHERIFF_GUN_MAINTENANCE = register(
    // new SheriffGunMaintenanceItem(new Item.Settings().maxCount(1)),
    // "sheriff_gun_maintenance"
    // );

    @SuppressWarnings("unchecked")
    public static Item register(Item item, String id) {
        // Create the identifier for the item.
        // Register the item.
        var registeredItem = registrar.create(id, item, new ResourceKey[] { MISC_CREATIVE_GROUP });
        // Item registeredItem = Registry.register(BuiltInRegistries.ITEM, itemID,
        // item);

        // Return the registered item!
        return registeredItem;
    }

    public static void init() {
        registrar.registerEntries();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, MISC_CREATIVE_GROUP, FabricItemGroup.builder()
                .title(Component.translatable("item_group.noellesroles.misc")).icon(() -> {
                    return new ItemStack(ModItems.WHEELCHAIR);
                }).build());
        TMMItems.INVISIBLE_ITEMS.add(ModItems.PAN);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.SMOKE_GRENADE);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.BLANK_CARTRIDGE);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.ALARM_TRAP);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.HALLUCINATION_BOTTLE);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.REINFORCEMENT);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.SCREWDRIVER);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.CONSPIRACY_PAGE);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.LETTER_ITEM);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.DEFIBRILLATOR);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.BOMB);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.WRITTEN_NOTE);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.FLASH_GRENADE);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.DECOY_GRENADE);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.PURIFY_BOMB);
        // TMMItems.INVISIBLE_ITEMS.add(TMMItems.KNIFE);

        TMMItems.INIT_ITEMS.LETTER = LETTER_ITEM;
        TMMItems.INIT_ITEMS.LETTER_UpdateItemFunc = (letter, serverPlayerEntity) -> {
            Component displayName = serverPlayerEntity.getDisplayName();
            letter.set(DataComponents.ITEM_NAME,
                    Component.translatable("tip.n.letter.item_name", displayName)
                            .withStyle(ChatFormatting.AQUA));

            int letterColor = 0xC5AE8B;
            String tipString = "tip.n.letter.";
            letter.update(DataComponents.LORE, ItemLore.EMPTY, component -> {
                List<Component> text = new ArrayList<>();
                UnaryOperator<Style> stylizer = style -> style.withItalic(false).withColor(letterColor);

                String string = displayName != null ? displayName.getString()
                        : serverPlayerEntity.getName().getString();
                if (string.charAt(string.length() - 1) == '\uE780') { // remove ratty supporter icon
                    string = string.substring(0, string.length() - 1);
                }
                text.add(Component
                        .translatable(tipString + "name", string,
                                Component.translatable(tipString + "map_name"))
                        .withStyle(stylizer));
                text.add(Component.translatable(tipString + "room").withStyle(stylizer));
                var date = new LocalDateData();
                text.add(Component.translatable(tipString + "tooltip1",
                        Component.translatable(tipString + "date", date.getYear(),
                                date.getMonth(), date.getDay()))
                        .withStyle(stylizer));
                text.add(Component.translatable(tipString + "tooltip2").withStyle(stylizer));
                return new ItemLore(text);
            });
        };
    }

}