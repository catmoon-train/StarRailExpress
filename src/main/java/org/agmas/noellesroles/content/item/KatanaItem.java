package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.item.api.SREItemProperties;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.katana.KatanaCombat;
import org.agmas.noellesroles.katana.KatanaState;

import java.util.List;

/**
 * 武士刀 —— 三连招近战武器（横扫 / 突刺 / 劈砍）+ 右键格挡。
 *
 * <ul>
 * <li>左键：按顺序释放三连招，<b>实际命中玩家</b>后才推进到下一招式；
 * 每招固定扣除虚拟血量（6 / 7 / 7）并附带 1 点原版伤害作为击退载体。</li>
 * <li>右键：格挡（可格挡的死亡原因同防暴盾牌，含 Dream 铁斧 / 钻石剑 / 重锤），
 * 前摇 0.4 秒、有效 1.2 秒；衔接招式命中后的格挡前摇为 0，未衔接的格挡结束后
 * 进入 5 秒内置冷却；<b>格挡只在有效窗口内生效，窗口内不限次数，
 * 但每次格挡成功消耗 1 点耐久</b>（耐久不会低于 1，为 1 时无法格挡）。</li>
 * <li>攻击速度与原版剑一致；击杀玩家后进入 10 秒物品冷却。</li>
 * </ul>
 */
public class KatanaItem extends Item
        implements SREItemProperties.LeftClickHurtable, SREItemProperties.TrainWeapon {

    /** 武士刀耐久：5（格挡成功 -1，最低保留 1 点）。 */
    public static final int DURABILITY = 5;

    /** 虚拟血量归零 / 击杀判定时的死因。 */
    public static final ResourceLocation DEATH_REASON = GameConstants.DeathReasons.KATANA;

    public KatanaItem(Properties properties) {
        super(properties);
    }

    /** 注册处使用的物品属性：单持、耐久 5、原版剑攻击速度（1.6 → 修正 -2.4）。 */
    public static Properties createProperties() {
        return new Item.Properties()
                .stacksTo(1)
                .durability(DURABILITY)
                .attributes(SwordItem.createAttributes(Tiers.IRON, 3, -2.4F));
    }

    /** 剩余耐久是否为 1（此时无论如何无法格挡）。 */
    public static boolean isAtLastDurability(ItemStack stack) {
        return stack.getMaxDamage() > 0 && stack.getDamageValue() >= stack.getMaxDamage() - 1;
    }

    /**
     * 职业门禁：武士刀（攻击与右键格挡）只有开启了
     * {@code canUseSpVanillaWeapon} 的职业才能使用。双端统一判定。
     */
    public static boolean canPlayerUse(Player player) {
        var gameWorld = SREGameWorldComponent.KEY.get(player.level());
        var role = gameWorld == null ? null : gameWorld.getRole(player);
        return role != null && role.canUseSpVanillaWeapon();
    }

    // ── 右键：格挡 ────────────────────────────────────────────────

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 职业门禁：未授权职业无法开始格挡
        if (!canPlayerUse(player)) {
            return InteractionResultHolder.fail(stack);
        }
        // 耐久等于 1 时无法格挡
        if (isAtLastDurability(stack)) {
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide) {
            // 客户端：根据服务端同步的格挡内置冷却预判（实体事件 120/121）
            if (org.agmas.noellesroles.katana.client.KatanaClientState.isOnBlockCooldown(player)) {
                return InteractionResultHolder.fail(stack);
            }
        } else {
            KatanaState.PlayerState state = KatanaState.get(player);
            // 未衔接招式格挡结束后会进入 5 秒内置冷却，冷却期间无法开始格挡
            if (level.getGameTime() < state.blockCooldownUntil) {
                return InteractionResultHolder.fail(stack);
            }
            // 记录本次格挡是否衔接招式命中（决定前摇与结束后是否进入内置冷却）
            state.blockLinked = level.getGameTime() <= state.linkedUntil;
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        // 前摇 0.4s + 有效 1.2s，到时自动收刀
        return KatanaState.BLOCK_USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        // 格挡姿势由客户端 Mixin 自定义，不使用原版举盾动画
        return UseAnim.NONE;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        // 松开右键 / 强制结束时结算内置冷却（仅衔接失败的格挡）
        finishBlock(level, entity);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        // 持满 1.6 秒自然收刀时同样结算内置冷却
        finishBlock(level, entity);
        return stack;
    }

    /** 格挡结束：未衔接招式的格挡进入 5 秒内置冷却并同步给客户端。 */
    private void finishBlock(Level level, LivingEntity entity) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) {
            return;
        }
        KatanaState.PlayerState state = KatanaState.get(player);
        if (!state.blockLinked) {
            state.blockCooldownUntil = level.getGameTime() + KatanaState.BLOCK_INTERNAL_COOLDOWN_TICKS;
            KatanaState.broadcast(player, KatanaState.EVENT_BLOCK_COOLDOWN_START);
        }
    }

    // ── 左键：三连招 ─────────────────────────────────────────────

    @Override
    public boolean onServerAttack(ServerPlayer attacker, ServerPlayer target, ItemStack mainhandItem) {
        return KatanaCombat.attack(attacker, target, mainhandItem);
    }

    // ── 提示文本 ─────────────────────────────────────────────────

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.katana.tooltip.combo")
                .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("item.noellesroles.katana.tooltip.block")
                .withStyle(ChatFormatting.AQUA));
        if (isAtLastDurability(stack)) {
            tooltip.add(Component.translatable("item.noellesroles.katana.tooltip.no_block")
                    .withStyle(ChatFormatting.DARK_RED));
        }
        tooltip.add(Component.translatable("item.noellesroles.katana.tooltip.durability",
                        Math.max(1, stack.getMaxDamage() - stack.getDamageValue()), stack.getMaxDamage())
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
