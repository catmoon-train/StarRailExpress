package org.agmas.noellesroles.role.touhou.roles;

import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.init.FunnyItems;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 彩蛋职业。没有获胜条件。
 * THRinnosukeRole
 */
public class THRinnosukeRole extends TouhouRole {

  public THRinnosukeRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
      MoodType moodType, int maxSprintTime, boolean canSeeTime) {
    super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
  }

  static ArrayList<ShopEntry> SHOP = new ArrayList<>();
  static {
    // 对讲机
    SHOP.add(new ShopEntry(
        ModItems.RADIO.getDefaultInstance(),
        200,
        ShopEntry.Type.TOOL));
    SHOP.add(new ShopEntry(
        ModItems.MONITORING_TERMINAL.getDefaultInstance(),
        200,
        ShopEntry.Type.TOOL));
    // 医生商店
    SHOP.add(new ShopEntry(
        ModItems.ANTIDOTE_REAGENT.getDefaultInstance(),
        100,
        ShopEntry.Type.TOOL));
    // 针管 - 75金币
    SHOP.add(new ShopEntry(
        ModItems.ANTIDOTE.getDefaultInstance(),
        150,
        ShopEntry.Type.TOOL));
    // 药丸 - 75金币
    SHOP.add(new ShopEntry(
        ModItems.createPillStack(false),
        150,
        ShopEntry.Type.TOOL));
    // 净化弹 - 400金币
    SHOP.add(new ShopEntry(
        ModItems.PURIFY_BOMB.getDefaultInstance(),
        400,
        ShopEntry.Type.TOOL));
    ItemStack attendantLantern = Items.LANTERN.getDefaultInstance();
    SHOP.add(new ShopEntry(attendantLantern, 75, ShopEntry.Type.TOOL) {
      @Override
      public boolean onBuy(@NotNull Player player) {
        return RoleUtils.insertStackInFreeSlot(player, attendantLantern.copy());
      }
    });
    SHOP.add(new ShopEntry(
        ModItems.MINT_CANDIES.getDefaultInstance(),
        150,
        ShopEntry.Type.TOOL));
    // 维生素 - 125金币
    SHOP.add(new ShopEntry(
        ModItems.ALCHEMIST_BUFF_POTION.getDefaultInstance(),
        250,
        ShopEntry.Type.TOOL));
    // 收纳袋 - 150金币
    SHOP.add(new ShopEntry(
        Items.BUNDLE.getDefaultInstance(),
        300,
        ShopEntry.Type.TOOL));
    // 报纸 - 200金币
    SHOP.add(new ShopEntry(
        ModItems.NEWSPAPER.getDefaultInstance(),
        200,
        ShopEntry.Type.TOOL));

    // 警报陷阱 - 100金币
    SHOP.add(new ShopEntry(
        ModItems.ALARM_TRAP.getDefaultInstance(),
        100,
        ShopEntry.Type.TOOL));

    SHOP.add(new ShopEntry(
        ModItems.MASTER_KEY_P.getDefaultInstance(),
        300,
        ShopEntry.Type.TOOL));

    SHOP.add(new ShopEntry(
        ModItems.LOCK_ITEM.getDefaultInstance(),
        300,
        ShopEntry.Type.TOOL));

    SHOP.add(new ShopEntry(
        ModItems.REINFORCEMENT.getDefaultInstance(),
        300,
        ShopEntry.Type.TOOL));
    // 诱饵弹 - 25金币（捣蛋鬼专用）
    SHOP.add(new ShopEntry(ModItems.DECOY_GRENADE.getDefaultInstance(), 200, ShopEntry.Type.TOOL));

    // 照明弹 - 100金币
    SHOP.add(new ShopEntry(ModItems.FLARE.getDefaultInstance(), 100, ShopEntry.Type.TOOL));

    // 闪光弹 - 400
    SHOP.add(new ShopEntry(ModItems.FLASH_GRENADE.getDefaultInstance(), 400, ShopEntry.Type.TOOL));

    SHOP.add(new ShopEntry(
        ModItems.BLANK_CARTRIDGE.getDefaultInstance(),
        300,
        ShopEntry.Type.TOOL));
    // 烟雾弹 - 400金币
    SHOP.add(new ShopEntry(
        ModItems.SMOKE_GRENADE.getDefaultInstance(),
        400,
        ShopEntry.Type.TOOL));
    SHOP.add(new ShopEntry(
        TMMItems.CROWBAR.getDefaultInstance(),
        35,
        ShopEntry.Type.TOOL));
    SHOP.add(new ShopEntry(
        TMMItems.LOCKPICK.getDefaultInstance(),
        200,
        ShopEntry.Type.TOOL));

    SHOP.add(new ShopEntry(
        TMMItems.BODY_BAG.getDefaultInstance(),
        150,
        ShopEntry.Type.TOOL));

    SHOP.add(new ShopEntry(
        ModItems.ROPE.getDefaultInstance(),
        300,
        ShopEntry.Type.TOOL));
    SHOP.add(new ShopEntry(
        TMMItems.DRAWING_BOARD.getDefaultInstance(),
        500,
        ShopEntry.Type.TOOL));
    SHOP.add(new ShopEntry(
        ModItems.EXTINGUISHER.getDefaultInstance(),
        400,
        ShopEntry.Type.TOOL));
    SHOP.add(new ShopEntry(
        ModItems.PASSBOOK.getDefaultInstance(),
        200,
        ShopEntry.Type.TOOL));

    SHOP.add(new ShopEntry(
        TMMItems.IRON_DOOR_KEY.getDefaultInstance(),
        150,
        ShopEntry.Type.TOOL));
    SHOP.add(new ShopEntry(
        new ItemStack(Items.FIREWORK_ROCKET, 6),
        300,
        ShopEntry.Type.TOOL));

    // 血瓶 - 75金币
    SHOP.add(new ShopEntry(
        ModItems.BLOOD_BOTTLE.getDefaultInstance(),
        200,
        ShopEntry.Type.TOOL));
    SHOP.add(new ShopEntry(TMMItems.FIRECRACKER.getDefaultInstance(), 50, ShopEntry.Type.TOOL));
    SHOP.add(new ShopEntry(FunnyItems.PROBLEM_SET.getDefaultInstance(), 100, ShopEntry.Type.TOOL));
    // 饮料：
    {
      SHOP.add(new ShopEntry(TMMItems.OLD_FASHIONED.getDefaultInstance(), 50, ShopEntry.Type.TOOL));
      SHOP.add(new ShopEntry(TMMItems.MOJITO.getDefaultInstance(), 50, ShopEntry.Type.TOOL));
      SHOP.add(new ShopEntry(TMMItems.MARTINI.getDefaultInstance(), 50, ShopEntry.Type.TOOL));
      SHOP.add(new ShopEntry(TMMItems.COSMOPOLITAN.getDefaultInstance(), 50, ShopEntry.Type.TOOL));
      SHOP.add(new ShopEntry(TMMItems.CHAMPAGNE.getDefaultInstance(), 50, ShopEntry.Type.TOOL));
      SHOP.add(new ShopEntry(FunnyItems.SHISIYE.getDefaultInstance(), 500, ShopEntry.Type.TOOL));
    }
    // 食物
    {
      SHOP.add(new ShopEntry(Items.GLOW_BERRIES.getDefaultInstance(), 75, ShopEntry.Type.TOOL));
      SHOP.add(new ShopEntry(Items.BEEF.getDefaultInstance(), 50, ShopEntry.Type.TOOL));
      SHOP.add(new ShopEntry(Items.MUSHROOM_STEW.getDefaultInstance(), 50, ShopEntry.Type.TOOL));
      SHOP.add(new ShopEntry(Items.BREAD.getDefaultInstance(), 50, ShopEntry.Type.TOOL));
      SHOP.add(new ShopEntry(Items.POTATO.getDefaultInstance(), 50, ShopEntry.Type.TOOL));
      SHOP.add(new ShopEntry(Items.POISONOUS_POTATO.getDefaultInstance(), 50, ShopEntry.Type.TOOL));
    }
  }

  @Override
  public List<ShopEntry> getShopEntries() {
    return SHOP;
  }

  /**
   * 当玩家尝试丢弃物品时触发。该回调在物品真正被移除前执行，可用于拦截或自定义丢弃行为。
   * <p>
   * 根据返回值决定后续处理：
   * <ul>
   * <li><b>{@link InteractionResult#PASS}</b> — 使用默认逻辑，正常执行物品丢弃。</li>
   * <li><b>{@link InteractionResult#CONSUME}</b> — 取消本次丢弃行为</li>
   * <li><b>{@link InteractionResult#SUCCESS}</b> — 正常物品丢弃行为</li>
   * <li><b>{@link InteractionResult#FAIL}</b> — 取消本次丢弃行为</li>
   * </ul>
   *
   * @param player 尝试丢弃物品的玩家，不可为 {@code null}
   * @param item   准备丢弃的物品堆
   * @return 控制丢弃行为的交互结果，默认可返回 {@link InteractionResult#PASS}
   */
  @Override
  public InteractionResult onDropItem(Player player, ItemStack item) {
    if (item.is(TMMItems.LETTER) || item.is(TMMItems.KEY) || item.is(ModItems.BOMB)
        || item.is(FunnyItems.HOT_POTATO))
      return InteractionResult.FAIL;
    return InteractionResult.SUCCESS;
  }
}
