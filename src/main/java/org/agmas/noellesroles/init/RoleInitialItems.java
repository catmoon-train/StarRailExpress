package org.agmas.noellesroles.init;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.RedHouseRoles;
import org.agmas.noellesroles.role.TraitorAndModifiers;

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
                    result.add(itemStack.copy());
                }
            }
        }
        return result;
    }

    /**
     * 为玩家添加指定角色的初始物品
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
                    player.addItem(itemStack.copy());
                }
            }
        }
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
            INITIAL_ITEMS_MAP.put(ModRoles.BASEBALL_PLAYER, items);
        }
        {
            // 最好的小脑
            List<Supplier<ItemStack>> items = new ArrayList<>();
            items.add(() -> TMMItems.GRENADE.getDefaultInstance());
            INITIAL_ITEMS_MAP.put(ModRoles.BEST_VIGILANTE, items);
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

//        //黑白
//        List<Supplier<ItemStack>> monokuma_items = new ArrayList<>();
//        elfItems.add(TMMItems.REVOLVER::getDefaultInstance);
//        INITIAL_ITEMS_MAP.put(ModRoles.MONOKUMA, monokuma_items);

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
    }

}
