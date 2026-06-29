package org.agmas.noellesroles.util;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.game.GameConstants;
import org.agmas.noellesroles.Noellesroles;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 死亡原因帮助类
 * 提供葬仪可用的死亡原因列表（与语言文件中已有的 death_reason.noellesroles.* 键匹配）
 */
public class DeathReasonHelper {
    
    private static Boolean fakeRoleEnabled = null;
    
    /**
     * 检查是否启用FakeRole选择功能
     */
    public static boolean isFakeRoleEnabled() {
        if (fakeRoleEnabled == null) {
            try {
                if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("kinswathe")) {
                    fakeRoleEnabled = true;
                } else {
                    fakeRoleEnabled = false;
                }
            } catch (Exception e) {
                fakeRoleEnabled = false;
            }
        }
        return fakeRoleEnabled;
    }
    
    /**
     * 获取可用的死亡原因物品列表（与语言文件 death_reason.noellesroles.* 键匹配）
     */
    public static ItemStack[] getAvailableDeathReasons() {
        return new ItemStack[] {
            new ItemStack(Items.IRON_SWORD),        // knife_stab - 刀刺
            new ItemStack(Items.BOW),               // gun_shot - 枪击
            new ItemStack(Items.TNT),               // grenade - 手雷
            new ItemStack(Items.BLAZE_ROD),         // bat_hit - 棒击
            new ItemStack(Items.POTION),            // poison - 中毒
            new ItemStack(Items.OMINOUS_BOTTLE),    // voodoo - 巫毒魔法
        };
    }
    
    /**
     * 获取死亡原因的ID（使用 starrailexpress 命名空间，与已有翻译键匹配）
     */
    public static String getDeathReasonId(ItemStack stack) {
        if (stack.is(Items.IRON_SWORD)) return GameConstants.DeathReasons.KNIFE.toString();
        if (stack.is(Items.BOW)) return SRE.deathReason("gun_shot").toString();
        if (stack.is(Items.TNT)) return GameConstants.DeathReasons.GRENADE.toString();
        if (stack.is(Items.BLAZE_ROD)) return GameConstants.DeathReasons.BAT.toString();
        if (stack.is(Items.POTION)) return GameConstants.DeathReasons.POISON.toString();
        if (stack.is(Items.OMINOUS_BOTTLE)) return Noellesroles.forceDeathReason("voodoo").toString();
        return GameConstants.DeathReasons.GENERIC.toString();
    }
}
