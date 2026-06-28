package io.wifi.starrailexpress.content.item;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.GrenadeEntity;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class GrenadeItem extends SkinableItem {
	public static final ResourceLocation ITEM_ID = SRE.id("grenade");
	public static final int MAX_CHARGE_TIME = 20; // 最大蓄力时间（ticks），对应1秒

	public GrenadeItem(Item.Properties settings) {
		super(settings);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user, InteractionHand hand) {
		ItemStack itemStack = user.getItemInHand(hand);
		user.startUsingItem(hand);
		return InteractionResultHolder.consume(itemStack);
	}

	@Override
	public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
		if (user.isSpectator())
			return;
		if (!world.isClientSide) {
			if (user instanceof Player player && isAnyGrenadeOnCooldown(player))
				return;
			if (user instanceof Player player) {
				// 创造模式和超级亡命徒手雷无cd
				if (!player.isCreative()
						&& !SREGameWorldComponent.KEY.get(player.level()).isRole(player,
								SpecialGameModeRoles.SUPER_LOOSE_END)) {
					addGrenadeCooldown(player);
				}
			}
			// 计算蓄力时间
			int chargeTime = this.getUseDuration(stack, user) - remainingUseTicks;

			// 确保蓄力时间在合理范围内
			chargeTime = Math.max(0, Math.min(chargeTime, MAX_CHARGE_TIME));

			// 播放投掷声音
			world.playSound(null, user.getX(), user.getY(), user.getZ(), TMMSounds.ITEM_GRENADE_THROW,
					SoundSource.NEUTRAL, 0.5F, 1F + (world.random.nextFloat() - .5f) / 10f);

			// 创建手榴弹实体
			GrenadeEntity grenade = new GrenadeEntity(TMMEntities.GRENADE, world);
			grenade.setOwner(user);
			grenade.setPosRaw(user.getX(), user.getEyeY() - 0.1, user.getZ());

			// 根据蓄力时间计算投掷速度（最小速度0.3，最大速度1.2）
			float velocity = 0.4F + (0.75F * (float) chargeTime / MAX_CHARGE_TIME);

			// 设置手榴弹的速度和方向
			grenade.shootFromRotation(user, user.getXRot(), user.getYRot(), 0.0F, velocity, 1.0F);
			world.addFreshEntity(grenade);
			if (SRE.REPLAY_MANAGER != null) {
				SRE.REPLAY_MANAGER.recordItemUse(user.getUUID(), BuiltInRegistries.ITEM.getKey(this));
			}
		}

		// user.incrementStat(Stats.USED.getOrCreateStat(this));
		stack.consume(1, user);
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.BOW;
	}

	@Override
	public String getItemSkinType() {
		return "grenade";
	}

	// ─── 共用冷却辅助方法（三个手雷共享CD）───

	/** 检查三个手雷是否有任何一个在冷却中 */
	public static boolean isAnyGrenadeOnCooldown(Player player) {
		return player.getCooldowns().isOnCooldown(TMMItems.GRENADE)
				|| player.getCooldowns().isOnCooldown(TMMItems.STICKY_GRENADE)
				|| player.getCooldowns().isOnCooldown(TMMItems.TIMED_GRENADE);
	}

	/** 给三个手雷同时添加冷却 */
	public static void addGrenadeCooldown(Player player) {
		int cd = SREConfig.instance().grenadeCooldown;
		player.getCooldowns().addCooldown(TMMItems.GRENADE, cd);
		player.getCooldowns().addCooldown(TMMItems.STICKY_GRENADE, cd);
		player.getCooldowns().addCooldown(TMMItems.TIMED_GRENADE, cd);
	}
}