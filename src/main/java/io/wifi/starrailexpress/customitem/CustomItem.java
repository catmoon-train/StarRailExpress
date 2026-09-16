/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.customitem;

import io.wifi.starrailexpress.api.ChargeableItem;
import io.wifi.starrailexpress.client.StaminaRenderer;
import io.wifi.starrailexpress.content.item.api.SREItemProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.awt.Color;
import java.util.List;

/**
 * 自定义列车物品（全模组唯一的那个注册物品）。
 *
 * <p>
 * 自身的模型引用到不存在的地方（默认无材质），每个自定义列车物品都是它加上
 * {@link io.wifi.starrailexpress.index.SREDataComponentTypes#CUSTOM_ITEM_ID} 组件，
 * 具体行为由 {@link CustomItemRuntime} 按配置分发。
 */
public class CustomItem extends Item implements SREItemProperties.LeftClickHurtable, ChargeableItem {

    public CustomItem(Properties properties) {
        super(properties);
    }

    // ==================== 右键 ====================

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        CustomItemData data = CustomItemLoader.getData(stack);
        if (data == null) {
            return InteractionResultHolder.pass(stack);
        }
        switch (data.kind()) {
            case BASIC -> {
                // 没有任何可用行为（没配指令 / 冷却 / 消耗），或正在冷却中：不给反馈（不摆臂）
                if (data.basicDoesNothing() || CustomItemRuntime.isOnCooldown(player, stack)) {
                    return InteractionResultHolder.pass(stack);
                }
                if (!world.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                    CustomItemRuntime.executeBasic(serverPlayer, stack, data);
                }
                return InteractionResultHolder.consume(stack);
            }
            case CHARGE -> {
                if (CustomItemRuntime.isOnCooldown(player, stack)) {
                    return InteractionResultHolder.fail(stack);
                }
                player.startUsingItem(hand);
                return InteractionResultHolder.consume(stack);
            }
            case GUN -> {
                // 发射按键 = 左键：右键完全不做事（与狙击枪一致：左键才开火，右键吞掉防止顺手开箱子）
                if (data.fireButton() == CustomItemData.FireButton.LEFT) {
                    return InteractionResultHolder.consume(stack);
                }
                if (world.isClientSide()) {
                    // 自动射击期间该枪械不可用：不给任何反馈（不摆臂、无后坐力、无音效）
                    if (CustomItemRuntime.isClientAutoFiring(player, data.id)) {
                        return InteractionResultHolder.pass(stack);
                    }
                    // 弹药不足：空枪，不给后坐力
                    if (data.ammoSystem && CustomItemRuntime.getAmmo(stack, data) <= 0) {
                        return InteractionResultHolder.fail(stack);
                    }
                    // 后坐力是客户端视角变化，与左轮手枪/德林加一致
                    applyRecoil(player, data);
                    if (data.autoFire) {
                        // 记录客户端自动射击窗口，窗口内右键不再给任何反馈
                        CustomItemRuntime.beginClientAutoFire(player, data);
                    }
                    return InteractionResultHolder.consume(stack);
                }
                if (!(player instanceof ServerPlayer serverPlayer)) {
                    return InteractionResultHolder.pass(stack);
                }
                // 自动射击期间右键不触发射击与其它效果（音效由每发开火时播放）
                if (CustomItemRuntime.isServerAutoFiring(player, data.id)) {
                    return InteractionResultHolder.pass(stack);
                }
                boolean fired = CustomItemRuntime.useGun(serverPlayer, stack, data);
                return fired ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
            }
            case VANILLA_WEAPON -> {
                // 没配右键指令、或正在冷却中：服务端什么都不会做，客户端也就不摆臂
                if (data.weaponDoesNothing() || CustomItemRuntime.isOnCooldown(player, stack)) {
                    return InteractionResultHolder.pass(stack);
                }
                if (!world.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                    CustomItemRuntime.useVanillaWeapon(serverPlayer, stack, data);
                }
                return InteractionResultHolder.consume(stack);
            }
            case CUFF -> {
                // 手铐类物品只能对玩家用：右键空气没有效果
                return InteractionResultHolder.pass(stack);
            }
            case THROWABLE -> {
                if (data.throwNeedPin) {
                    // 需要拉栓：按住右键蓄力，松手投出（同手榴弹）
                    if (CustomItemRuntime.isOnCooldown(player, stack)) {
                        return InteractionResultHolder.fail(stack);
                    }
                    player.startUsingItem(hand);
                    return InteractionResultHolder.consume(stack);
                }
                // 不需要拉栓：右键直接投出（同烟雾弹，投出时播放拉栓音效）
                if (!world.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                    CustomItemRuntime.throwCustom(serverPlayer, stack, data);
                }
                return InteractionResultHolder.consume(stack);
            }
            case FOOD -> {
                if (data.isDrink || player.canEat(false)) {
                    player.startUsingItem(hand);
                    return InteractionResultHolder.consume(stack);
                }
                return InteractionResultHolder.fail(stack);
            }
        }
        return InteractionResultHolder.pass(stack);
    }

    /** 后坐力（客户端视角变化，与左轮手枪/德林加一致）。右键与左键发射共用。 */
    public static void applyRecoil(Player player, CustomItemData data) {
        if (data.recoil <= 0) {
            return;
        }
        player.setXRot(player.getXRot() - (float) data.recoil);
    }

    // ==================== 右键玩家（手铐类物品） ====================

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player user, LivingEntity entity,
            InteractionHand hand) {
        CustomItemData data = CustomItemLoader.getData(stack);
        if (data == null || data.kind() != CustomItemData.Kind.CUFF) {
            return InteractionResult.PASS;
        }
        if (user.level().isClientSide()) {
            // 客户端只负责挥手反馈，实际铐人在服务端做；目标不是玩家就不给反馈（服务端也不会做事）
            return entity instanceof Player ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (!(user instanceof ServerPlayer serverPlayer) || !(entity instanceof Player target)) {
            return InteractionResult.PASS;
        }
        return CustomItemRuntime.cuffPlayer(serverPlayer, stack, data, target);
    }

    // ==================== 使用时长 / 动作 ====================

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        CustomItemData data = CustomItemLoader.getData(stack);
        if (data == null) {
            return 0;
        }
        return switch (data.kind()) {
            case CHARGE -> Math.max(1, data.chargeTicks);
            case THROWABLE -> data.throwNeedPin ? Math.max(1, data.throwPinTicks) : 0;
            case FOOD -> Math.max(1, data.eatTicks);
            default -> 0;
        };
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        CustomItemData data = CustomItemLoader.getData(stack);
        if (data == null) {
            return UseAnim.NONE;
        }
        return switch (data.kind()) {
            case CHARGE -> switch (data.chargeAnim()) {
                case BOW -> UseAnim.BOW;
                case SPEAR -> UseAnim.SPEAR;
                case CROSSBOW -> UseAnim.CROSSBOW;
                case DRINK -> UseAnim.DRINK;
                case EAT -> UseAnim.EAT;
                case BLOCK -> UseAnim.BLOCK;
                case BRUSH -> UseAnim.BRUSH;
                case NONE -> UseAnim.NONE;
            };
            case THROWABLE -> data.throwNeedPin ? UseAnim.BOW : UseAnim.NONE;
            case FOOD -> data.isDrink ? UseAnim.DRINK : UseAnim.EAT;
            default -> UseAnim.NONE;
        };
    }

    // ==================== 蓄力 ====================

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeCharged) {
        if (level.isClientSide() || !(user instanceof ServerPlayer player)) {
            return;
        }
        CustomItemData data = CustomItemLoader.getData(stack);
        if (data == null) {
            return;
        }
        if (data.kind() == CustomItemData.Kind.THROWABLE) {
            // 需要拉栓的投掷物：松手即投出（与手榴弹一致，不要求蓄满）
            if (data.throwNeedPin) {
                CustomItemRuntime.throwCustom(player, stack, data);
            }
            return;
        }
        if (data.kind() != CustomItemData.Kind.CHARGE) {
            return;
        }
        int charged = getUseDuration(stack, user) - timeCharged;
        if (CustomItemRuntime.isChargeComplete(data, charged)) {
            CustomItemRuntime.completeCharge(player, stack, data);
        }
    }

    // ==================== 食用 / 蓄力条走满 ====================

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, net.minecraft.world.entity.LivingEntity entity) {
        CustomItemData data = CustomItemLoader.getData(stack);
        if (data == null || !(entity instanceof ServerPlayer player)) {
            return super.finishUsingItem(stack, level, entity);
        }
        return switch (data.kind()) {
            case FOOD -> {
                if (data.consumeOnEat) {
                    // 原版路径：施加食物数值并消耗 1 个
                    ItemStack result = super.finishUsingItem(stack, level, entity);
                    CustomItemRuntime.onFoodConsumed(player, result, data);
                    yield result;
                }
                // 不消耗：手动结算食物数值
                CustomItemRuntime.applyFoodValues(player, stack);
                CustomItemRuntime.onFoodConsumed(player, stack, data);
                yield stack;
            }
            case CHARGE -> {
                // 蓄力条自然走满 → 直接触发（无需松手）
                CustomItemRuntime.completeCharge(player, stack, data);
                yield stack;
            }
            default -> super.finishUsingItem(stack, level, entity);
        };
    }

    // ==================== 左键攻击（特殊原版物品） ====================

    @Override
    public boolean onServerAttack(ServerPlayer attacker, ServerPlayer target, ItemStack mainhandItem) {
        CustomItemData data = CustomItemLoader.getData(mainhandItem);
        if (data == null) {
            return true;
        }
        if (data.kind() != CustomItemData.Kind.VANILLA_WEAPON) {
            // 普通自定义物品默认不允许左键攻击玩家（基础设置里的「允许左键攻击玩家」可开）
            return data.allowLeftClickAttack;
        }
        return CustomItemRuntime.onVanillaWeaponAttack(attacker, target, mainhandItem, data);
    }

    // ==================== 蓄力条（客户端 HUD） ====================

    @Override
    public int getMaxChargeTime(ItemStack stack, Player player) {
        CustomItemData data = CustomItemLoader.getData(stack);
        if (data != null && data.kind() == CustomItemData.Kind.CHARGE) {
            return Math.max(1, data.chargeTicks);
        }
        return 0;
    }

    @Override
    public float getChargePercentage(ItemStack stack, Player player, int ticksUsingItem) {
        int max = getMaxChargeTime(stack, player);
        return max <= 0 ? 0f : Math.min(1f, (float) ticksUsingItem / (float) max);
    }

    @Override
    public float getMaxStamina(ItemStack stack, Player player) {
        return getMaxChargeTime(stack, player);
    }

    @Override
    public boolean hasSpecialVisualEffects(ItemStack stack, Player player) {
        return getMaxChargeTime(stack, player) > 0;
    }

    @Override
    public void onFullyCharged(ItemStack stack, Player player) {
        StaminaRenderer.triggerScreenEdgeEffect(Color.pink.getRGB(), 300L, 0.5f);
    }

    // ==================== 耐久条 ====================

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return CustomItemRuntime.hasDurabilityBar(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return CustomItemRuntime.durabilityBarWidth(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return CustomItemRuntime.durabilityBarColor(stack);
    }

    // ==================== tooltip ====================

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CustomItemData data = CustomItemLoader.getData(stack);
        if (data == null) {
            // 没有配置数据的裸物品（例如直接拿到的注册物品）
            tooltip.add(Component.translatable("item.starrailexpress.custom_item.empty")
                    .withStyle(style -> style.withColor(0xFF5555)));
            return;
        }
        if (data.kind() == CustomItemData.Kind.GUN && data.ammoSystem) {
            tooltip.add(Component.translatable("item.starrailexpress.custom_item.ammo",
                    CustomItemRuntime.getAmmo(stack, data), data.maxAmmo)
                    .withStyle(style -> style.withColor(0xFFB300)));
        }
        int maxDurability = CustomItemRuntime.maxDurability(data);
        if (maxDurability > 0) {
            tooltip.add(Component.translatable("sre.custom_item.durability",
                    CustomItemRuntime.remainingDurability(stack, data), maxDurability)
                    .withStyle(style -> style.withColor(0xFFAFAFAF)));
        }
        if (data.invisibleInHand) {
            // 与项目内其它「手持不可见」物品一致的提示
            tooltip.add(Component.translatable("starrailexpress.tip.invisible")
                    .withStyle(style -> style.withColor(0xFFAFAFAF)));
        }
    }
}
