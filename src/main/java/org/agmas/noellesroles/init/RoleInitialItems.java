package org.agmas.noellesroles.init;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.agmas.noellesroles.role.ModRoles;

import io.wifi.starrailexpress.api.Role;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;

public class RoleInitialItems {
    public static final Map<Role, List<Supplier<ItemStack>>> INITIAL_ITEMS_MAP = new HashMap<>();

    /**
     * 获取指定角色的初始物品列表
     * 
     * @param role 角色
     * @return 初始物品列表
     */
    public static List<ItemStack> getInitialItemsForRole(Role role, Player player) {
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
    public static void addInitialItemsForRole(Player player, Role role) {
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
     * 初始化初始物品映射
     */
    public static void initializeInitialItems() {
        INITIAL_ITEMS_MAP.clear();

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
        // 添加16个便签
        for (int i = 0; i < 4; i++) {
            awesomeBinglusItems.add(() -> TMMItems.NOTE.getDefaultInstance());
        }
        INITIAL_ITEMS_MAP.put(ModRoles.AWESOME_BINGLUS, awesomeBinglusItems);

        // 强盗初始物品
        List<Supplier<ItemStack>> banditItems = new ArrayList<>();
        banditItems.add(() -> org.agmas.noellesroles.repack.HSRItems.BANDIT_REVOLVER.getDefaultInstance());
        banditItems.add(() -> TMMItems.CROWBAR.getDefaultInstance());
        INITIAL_ITEMS_MAP.put(ModRoles.BANDIT, banditItems);

        // 特警初始物品
        List<Supplier<ItemStack>> swastItems = new ArrayList<>();
        swastItems.add(() -> TMMItems.SNIPER_RIFLE.getDefaultInstance());
        swastItems.add(() -> TMMItems.MAGNUM_BULLET.getDefaultInstance());
        INITIAL_ITEMS_MAP.put(ModRoles.SWAST, swastItems);

        // 武术教官初始物品
        List<Supplier<ItemStack>> martialArtsInstructorItems = new ArrayList<>();
        martialArtsInstructorItems.add(() -> TMMItems.NUNCHUCK.getDefaultInstance());
        INITIAL_ITEMS_MAP.put(ModRoles.MARTIAL_ARTS_INSTRUCTOR, martialArtsInstructorItems);
    }

}
