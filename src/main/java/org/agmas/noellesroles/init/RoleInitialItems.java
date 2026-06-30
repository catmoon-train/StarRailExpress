package org.agmas.noellesroles.init;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import org.agmas.noellesroles.role.BounsRoles;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import org.agmas.noellesroles.role.touhou.RedHouseRoles;
import org.agmas.noellesroles.utils.MCItemsUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class RoleInitialItems {
    public static final Map<SRERole, List<Supplier<ItemStack>>> INITIAL_ITEMS_MAP = new HashMap<>();

    /**
     * 获取指定角色的初始物品列表
     * 
     * @param role 角色
     * @return 初始物品列表
     */
    public static List<ItemStack> getInitialItemsForRole(SRERole role, Player player) {
        List<ItemStack> result = new ArrayList<>();
        List<Supplier<ItemStack>> itemSuppliers = RoleInitialItems.INITIAL_ITEMS_MAP.get(role);
        if (itemSuppliers != null) {
            for (Supplier<ItemStack> itemSupplier : itemSuppliers) {
                ItemStack itemStack = itemSupplier.get();
                if (itemStack != null && !itemStack.isEmpty()) {
                    result.add(normalizeInitialItemForRole(role, itemStack));
                }
            }
        }
        return result;
    }

    /**
     * 为玩家添加指定角色的初始物品
     * 优先从 INITIAL_ITEMS_MAP 获取，若没有则回退到 role.getDefaultItems()
     * （自定义职业的初始物品通过 getDefaultItems() 返回）
     * 
     * @param player 玩家
     * @param role   角色
     */
    public static void addInitialItemsForRole(Player player, SRERole role) {
        List<Supplier<ItemStack>> itemSuppliers = RoleInitialItems.INITIAL_ITEMS_MAP.get(role);
        if (itemSuppliers != null) {
            for (Supplier<ItemStack> itemSupplier : itemSuppliers) {
                ItemStack itemStack = itemSupplier.get();
                if (itemStack != null && !itemStack.isEmpty()) {
                    MCItemsUtils.insertStackInFreeSlot(player, normalizeInitialItemForRole(role, itemStack));
                }
            }
        } else {
            // 静态 Map 中没有此角色 → 回退到 getDefaultItems()（自定义职业走这条路）
            List<ItemStack> defaultItems = role.getDefaultItems();
            if (defaultItems != null) {
                for (ItemStack stack : defaultItems) {
                    if (stack != null && !stack.isEmpty()) {
                        MCItemsUtils.insertStackInFreeSlot(player, normalizeInitialItemForRole(role, stack));
                    }
                }
            }
        }
    }

    public static ArrayList<ItemStack> getInitialItemsForRole(SRERole role) {
        ArrayList<ItemStack> result = new ArrayList<>();
        List<Supplier<ItemStack>> itemSuppliers = RoleInitialItems.INITIAL_ITEMS_MAP.get(role);
        if (itemSuppliers != null) {
            for (Supplier<ItemStack> itemSupplier : itemSuppliers) {
                ItemStack itemStack = itemSupplier.get();
                if (itemStack != null && !itemStack.isEmpty()) {
                    result.add(itemStack.copy());
                }
            }
        } else {
            // 静态 Map 中没有此角色 → 回退到 getDefaultItems()（自定义职业走这条路）
            List<ItemStack> defaultItems = role.getDefaultItems();
            for (var i : defaultItems) {
                if (i != null && !i.isEmpty()) {
                    result.add(i.copy());
                }
            }
        }
        return result;
    }

    private static ItemStack normalizeInitialItemForRole(SRERole role, ItemStack stack) {
        return stack.copy();
    }

    /**
     * 初始化初始物品映射，职业的初始物品加在这里。
     */
    public static void initializeInitialItems() {
        INITIAL_ITEMS_MAP.clear();

        {
            // baseball
            List<Supplier<ItemStack>> items = new ArrayList<>();
            items.add(() -> TMMItems.BAT.getDefaultInstance());
            INITIAL_ITEMS_MAP.put(BounsRoles.BASEBALL_PLAYER, items);
        }
        {
            // 最好的小脑
            List<Supplier<ItemStack>> items = new ArrayList<>();
            items.add(() -> TMMItems.GRENADE.getDefaultInstance());
            INITIAL_ITEMS_MAP.put(BounsRoles.BEST_VIGILANTE, items);
        }
        {
            // FURANDORU
            List<Supplier<ItemStack>> items = new ArrayList<>();
            items.add(() -> TMMItems.CROWBAR.getDefaultInstance());
            INITIAL_ITEMS_MAP.put(RedHouseRoles.FURANDORU, items);
        }

        {
            // JOJO
            List<Supplier<ItemStack>> items = new ArrayList<>();
            items.add(() -> FunnyItems.BOWEN_BADGE.getDefaultInstance());
            INITIAL_ITEMS_MAP.put(ModRoles.JOJO, items);
        }

        {
            // 里昂 - 左轮手枪（死亡时掉落；草药为自定义物品，不随手枪一起掉落）
            List<Supplier<ItemStack>> items = new ArrayList<>();
            items.add(() -> TMMItems.REVOLVER.getDefaultInstance());
            INITIAL_ITEMS_MAP.put(ModRoles.LEON, items);
        }
        // 故障机器人初始物品（无开局物品）
        INITIAL_ITEMS_MAP.put(ModRoles.GLITCH_ROBOT, new ArrayList<>());

        // 医生初始物品（不再有针管和解药）
        List<Supplier<ItemStack>> doctorItems = new ArrayList<>();
        doctorItems.add(() -> ModItems.DEFIBRILLATOR.getDefaultInstance());
        INITIAL_ITEMS_MAP.put(ModRoles.DOCTOR, doctorItems);

        // 游侠初始物品
        List<Supplier<ItemStack>> elfItems = new ArrayList<>();
        elfItems.add(() -> {
            var item = Items.BOW.getDefaultInstance();
            item.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
            return item;
        });
        INITIAL_ITEMS_MAP.put(ModRoles.ELF, elfItems);

        List<Supplier<ItemStack>> cupidItems = new ArrayList<>();
        cupidItems.add(() -> {
            var item = Items.BOW.getDefaultInstance();
            item.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
            return item;
        });
        INITIAL_ITEMS_MAP.put(ModRoles.CUPID, cupidItems);

        List<Supplier<ItemStack>> cakeMakerItems = new ArrayList<>();
        cakeMakerItems.add(ModItems.CAKE_INGREDIENTS::getDefaultInstance);
        cakeMakerItems.add(Items.BUNDLE::getDefaultInstance);
        INITIAL_ITEMS_MAP.put(ModRoles.CAKE_MAKER, cakeMakerItems);

        // 冒险家初始物品 — 格罗赛尔游记
        {
            List<Supplier<ItemStack>> adventurerItems = new ArrayList<>();
            adventurerItems.add(ModItems.GROSELL_TRAVELOG::getDefaultInstance);
            INITIAL_ITEMS_MAP.put(ModRoles.ADVENTURER, adventurerItems);
        }

        // //黑白
        // List<Supplier<ItemStack>> monokuma_items = new ArrayList<>();
        // elfItems.add(TMMItems.REVOLVER::getDefaultInstance);
        // INITIAL_ITEMS_MAP.put(ModRoles.MONOKUMA, monokuma_items);

        List<Supplier<ItemStack>> ninjaItems = new ArrayList<>();
        ninjaItems.add(() -> {
            ItemStack lockpick = TMMItems.LOCKPICK.getDefaultInstance();
            return lockpick;
        });
        INITIAL_ITEMS_MAP.put(ModRoles.NINJA, ninjaItems);

        // 亡命徒初始物品
        List<Supplier<ItemStack>> looseItems = new ArrayList<>();
        looseItems.add(TMMItems.CROWBAR::getDefaultInstance);
        looseItems.add(TMMItems.DERRINGER::getDefaultInstance);
        looseItems.add(TMMItems.KNIFE::getDefaultInstance);
        INITIAL_ITEMS_MAP.put(TMMRoles.LOOSE_END, looseItems);

        // 红尘客
        List<Supplier<ItemStack>> wayfarerItems = new ArrayList<>();
        wayfarerItems.add(() -> ModItems.FAKE_KNIFE.getDefaultInstance());
        wayfarerItems.add(() -> ModItems.FAKE_REVOLVER.getDefaultInstance());
        INITIAL_ITEMS_MAP.put(ModRoles.WAYFARER, wayfarerItems);

        // 乘务员初始物品
        List<Supplier<ItemStack>> attendantItems = new ArrayList<>();
        // 乘务员钥匙
        attendantItems.add(() -> ModItems.MASTER_KEY_P.getDefaultInstance());
        INITIAL_ITEMS_MAP.put(ModRoles.ATTENDANT, attendantItems);

        // 清道夫初始物品
        List<Supplier<ItemStack>> cleanerItems = new ArrayList<>();
        cleanerItems.add(() -> ModItems.BUCKET_OF_H2SO4.getDefaultInstance());
        INITIAL_ITEMS_MAP.put(ModRoles.CLEANER, cleanerItems);

        // 心理学家初始物品（不再有薄荷糖）
        List<Supplier<ItemStack>> psychologistItems = new ArrayList<>();
        INITIAL_ITEMS_MAP.put(ModRoles.PSYCHOLOGIST, psychologistItems);

        // 记录员初始物品
        List<Supplier<ItemStack>> recorderItems = new ArrayList<>();
        recorderItems.add(() -> ModItems.WRITTEN_NOTE.getDefaultInstance());
        INITIAL_ITEMS_MAP.put(ModRoles.RECORDER, recorderItems);

        // 小丑 & 指挥官初始物品
        List<Supplier<ItemStack>> jesterItems = new ArrayList<>();
        jesterItems.add(() -> ModItems.FAKE_KNIFE.getDefaultInstance());
        jesterItems.add(() -> ModItems.FAKE_REVOLVER.getDefaultInstance());
        INITIAL_ITEMS_MAP.put(ModRoles.COMMANDER, jesterItems);
        INITIAL_ITEMS_MAP.put(ModRoles.JESTER, jesterItems);

        // 列车长初始物品
        List<Supplier<ItemStack>> conductorItems = new ArrayList<>();
        conductorItems.add(() -> ModItems.MASTER_KEY.getDefaultInstance());
        conductorItems.add(() -> Items.SPYGLASS.getDefaultInstance());
        INITIAL_ITEMS_MAP.put(ModRoles.CONDUCTOR, conductorItems);

        // Awesome Binglus 初始物品
        List<Supplier<ItemStack>> awesomeBinglusItems = new ArrayList<>();
        // 添加4个便签
        {
            var t = TMMItems.NOTE.getDefaultInstance();
            t.setCount(4);
            awesomeBinglusItems.add(() -> t);
        }
        INITIAL_ITEMS_MAP.put(ModRoles.AWESOME_BINGLUS, awesomeBinglusItems);

        // 强盗初始物品
        List<Supplier<ItemStack>> banditItems = new ArrayList<>();
        banditItems.add(() -> ModItems.BANDIT_REVOLVER.getDefaultInstance());
        banditItems.add(() -> TMMItems.CROWBAR.getDefaultInstance());
        INITIAL_ITEMS_MAP.put(ModRoles.BANDIT, banditItems);

        // 雇佣兵初始物品
        List<Supplier<ItemStack>> mercenaryItems = new ArrayList<>();
        mercenaryItems.add(() -> TMMItems.REVOLVER.getDefaultInstance());
        INITIAL_ITEMS_MAP.put(ModRoles.MERCENARY, mercenaryItems);

        // 迷失杀手初始物品 - 左轮手枪
        List<Supplier<ItemStack>> lostKillerItems = new ArrayList<>();
        lostKillerItems.add(() -> TMMItems.REVOLVER.getDefaultInstance());
        INITIAL_ITEMS_MAP.put(ModRoles.LOST_KILLER, lostKillerItems);

        // 特警初始物品
        List<Supplier<ItemStack>> swastItems = new ArrayList<>();
        swastItems.add(() -> TMMItems.SNIPER_RIFLE.getDefaultInstance());
        swastItems.add(() -> TMMItems.MAGNUM_BULLET.getDefaultInstance());
        INITIAL_ITEMS_MAP.put(ModRoles.SWAST, swastItems);

        {
            // 诡异客人
            List<Supplier<ItemStack>> items = new ArrayList<>();
            items.add(() -> TMMItems.REVOLVER.getDefaultInstance());
            INITIAL_ITEMS_MAP.put(ModRoles.GUEST_GHOST, items);
        }

        // 武术教官初始物品
        List<Supplier<ItemStack>> martialArtsInstructorItems = new ArrayList<>();
        martialArtsInstructorItems.add(() -> TMMItems.NUNCHUCK.getDefaultInstance());
        INITIAL_ITEMS_MAP.put(ModRoles.MARTIAL_ARTS_INSTRUCTOR, martialArtsInstructorItems);

        // 海王初始物品 - 三叉戟
        // 附魔在在三叉戟mixin 因为需要level
        List<Supplier<ItemStack>> seaKingItems = new ArrayList<>();
        Supplier<ItemStack> getDefaultInstance = Items.TRIDENT::getDefaultInstance;
        // getDefaultInstance.get().enchant(BuiltInRegistries.Enchant, 3);
        seaKingItems.add(getDefaultInstance);
        INITIAL_ITEMS_MAP.put(ModRoles.SEA_KING, seaKingItems);

        // 水鬼初始物品 - 三叉戟
        // 激流附魔在RiptideTridentMixin中动态添加
        List<Supplier<ItemStack>> waterGhostItems = new ArrayList<>();
        waterGhostItems.add(() -> {
            ItemStack trident = Items.TRIDENT.getDefaultInstance();
            trident.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
            return trident;
        });
        INITIAL_ITEMS_MAP.put(ModRoles.WATER_GHOST, waterGhostItems);

        // 保安初始物品 - 防暴盾 与 警棍
        List<Supplier<ItemStack>> guardItems = new ArrayList<>();
        guardItems.add(() -> ModItems.RIOT_SHIELD.getDefaultInstance());
        guardItems.add(() -> ModItems.BATON.getDefaultInstance());
        INITIAL_ITEMS_MAP.put(ModRoles.GUARD, guardItems);

        // 警卫初始物品（无开局物品）
        INITIAL_ITEMS_MAP.put(ModRoles.SHERIFF, new ArrayList<>());

        // 鬼眼·杨间 初始物品（无开局物品；完成两个任务后才获得左轮手枪）
        INITIAL_ITEMS_MAP.put(ModRoles.GHOST_EYE, new ArrayList<>());

        // 广播员初始物品 - 对讲机
        List<Supplier<ItemStack>> broadcasterItems = new ArrayList<>();
        broadcasterItems.add(() -> ModItems.RADIO.getDefaultInstance());
        INITIAL_ITEMS_MAP.put(ModRoles.BROADCASTER, broadcasterItems);

        // 影隼初始物品 - 喷气背包
        List<Supplier<ItemStack>> shadowFalconItems = new ArrayList<>();
        shadowFalconItems.add(() -> ModItems.JETPACK.getDefaultInstance());
        INITIAL_ITEMS_MAP.put(ModRoles.SHADOW_FALCON, shadowFalconItems);

        // 大嗓门初始物品 - 对讲机
        List<Supplier<ItemStack>> noiseMakerItems = new ArrayList<>();
        noiseMakerItems.add(() -> ModItems.RADIO.getDefaultInstance());
        INITIAL_ITEMS_MAP.put(ModRoles.NOISEMAKER, noiseMakerItems);

        // 画家初始物品 - 画板
        List<Supplier<ItemStack>> painterItems = new ArrayList<>();
        painterItems.add(() -> TMMItems.DRAWING_BOARD.getDefaultInstance());
        INITIAL_ITEMS_MAP.put(ModRoles.PAINTER, painterItems);

        // 叛徒初始物品 - 短霰弹枪和手雷
        List<Supplier<ItemStack>> traitorItems = new ArrayList<>();
        traitorItems.add(() -> ModItems.SHORT_SHOTGUN.getDefaultInstance());
        traitorItems.add(() -> TMMItems.GRENADE.getDefaultInstance());
        INITIAL_ITEMS_MAP.put(TraitorAndModifiers.TRAITOR, traitorItems);

        // 悍匪初始物品 - C4炸药 + C4引爆器
        List<Supplier<ItemStack>> gangstersItems = new ArrayList<>();
        gangstersItems.add(() -> ModItems.C4.getDefaultInstance());
        gangstersItems.add(() -> ModItems.C4_DETONATOR.getDefaultInstance());
        INITIAL_ITEMS_MAP.put(ModRoles.GANGSTERS, gangstersItems);

        // 教父初始物品 - 德林加手枪(初始无子弹)
        List<Supplier<ItemStack>> godfatherItems = new ArrayList<>();
        godfatherItems.add(() -> {
            ItemStack derringer = TMMItems.DERRINGER.getDefaultInstance();
            derringer.set(io.wifi.starrailexpress.index.SREDataComponentTypes.USED, true);
            return derringer;
        });
        INITIAL_ITEMS_MAP.put(ModRoles.GODFATHER, godfatherItems);

        // 钳工初始物品 - 拆弹钳（无限次使用，不会损坏）
        List<Supplier<ItemStack>> fitterItems = new ArrayList<>();
        fitterItems.add(() -> {
            ItemStack pliers = ModItems.PLIERS.getDefaultInstance();
            pliers.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
            return pliers;
        });
        INITIAL_ITEMS_MAP.put(ModRoles.FITTER, fitterItems);

        // 信使初始物品 - 信封
        List<Supplier<ItemStack>> courierItems = new ArrayList<>();
        courierItems.add(() -> ModItems.COURIER_MAIL.getDefaultInstance());
        INITIAL_ITEMS_MAP.put(ModRoles.COURIER, courierItems);
    }

}
