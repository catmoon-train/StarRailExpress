package org.agmas.noellesroles.game.roles.neutral.monokuma;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;

import java.awt.*;

public  class MonokumaRole extends NormalRole {
    public MonokumaRole() {
      super(ModRoles.MONOKUMA_ID, new Color(128, 128, 128).getRGB(), false, false, MoodType.FAKE, Integer.MAX_VALUE, true);
    }

    @Override
    public ResourceLocation getPsychoSkin(Player player, boolean isSlim) {
        long timeSlot = System.currentTimeMillis() / 3000;
        if (timeSlot % 2 == 0) {
            return Noellesroles.id("textures/entity/custom_psycho/black_psycho.png");
        } else {
            return Noellesroles.id("textures/entity/custom_psycho/white_psycho.png");
        }
    }



    @Override
    public boolean onPsychoGiveItem(Player player, SREPlayerPsychoComponent comp) {
      // 黑白已经在 onHitTriggered 中给了阴阳剑，不需要再给球棒
      return true;
    }

    @Override
    public Item getPsychoItem() {
      return org.agmas.noellesroles.init.ModItems.YINYANG_SWORD;
    }

    @Override
    public java.util.function.Predicate<Item> cantPickupItem(Player player) {
      // 黑白熊形态无法捡起任何物品
      var comp = org.agmas.noellesroles.game.roles.neutral.monokuma.MonokumaPlayerComponent.KEY.maybeGet(player).orElse(null);
      if (comp != null && comp.phase == 3) {
        return item -> true; // 禁止捡起所有物品
      }
      return super.cantPickupItem(player);
    }

//    @Override
//    public java.util.List<net.minecraft.world.item.ItemStack> getDefaultItems() {
//      return java.util.List.of(
//          new net.minecraft.world.item.ItemStack(TMMItems.REVOLVER)
//      );
//    }


//    @Override
//    public ResourceLocation getDisplayRole(Player player) {
//      // 对所有人（包括自己）显示为义警
//      var comp = org.agmas.noellesroles.game.roles.neutral.monokuma.MonokumaPlayerComponent.KEY.maybeGet(player).orElse(null);
//      if (comp != null && comp.phase <= 2) {
//        return TMMRoles.VIGILANTE.identifier();
//      }
//      return super.getDisplayRole(player);
//    }
  }