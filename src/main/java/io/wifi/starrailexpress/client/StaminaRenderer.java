package io.wifi.starrailexpress.client;

import io.wifi.starrailexpress.api.ChargeableItemRegistry;
import io.wifi.starrailexpress.api.Role;
import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import io.wifi.starrailexpress.game.GameFunctions;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.PlayerStaminaGetter;
import io.wifi.starrailexpress.SREConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemCooldowns;

public class StaminaRenderer {
	public static StaminaBarRenderer view = new StaminaBarRenderer();
	public static float offsetDelta = 0f;

	// 添加刀蓄满力的视觉效果相关变量
	private static boolean knifeFullyCharged = false;
	private static long flashStartTime = 0L; // 闪光开始时间（毫秒）
	private static final long FLASH_DURATION_MS = 250L; // 闪光持续时间（毫秒）

	// 添加屏幕边缘红色效果相关变量
	private static long screenRedEffectStartTime = 0L; // 屏幕红色效果开始时间（毫秒）
	private static final long SCREEN_RED_EFFECT_DURATION_MS = 300L; // 屏幕红色效果持续时间（毫秒）
	private static final float MAX_RED_INTENSITY = 0.5f; // 最大红色强度（0-1）
	
	// 新增：通用屏幕边缘效果相关变量
	private static long generalScreenEffectStartTime = 0L; // 通用屏幕效果开始时间（毫秒）
	private static long GENERAL_SCREEN_EFFECT_DURATION_MS = 300L; // 通用屏幕效果持续时间（毫秒）
	private static int generalScreenEffectColor = 0xFF0000; // 通用屏幕效果颜色，默认为红色
	private static float generalScreenEffectIntensity = 0.5f; // 通用屏幕效果强度
	
	private static float lastCooldown = 0f;
	private static boolean playedCooldownSound = false;
	private static ItemStack lastMainHandStack = ItemStack.EMPTY; // 用于跟踪上一次的主手物品

	public interface StaminaProvider {
		float getCurrentStamina(Player clientPlayerEntity);
		float getMaxStamina(Player clientPlayerEntity);
		float getStaminaPercentage(Player clientPlayerEntity); // 0.0到1.0之间的值
	}

	// 默认的体力提供者（临时使用）
	private static StaminaProvider staminaProvider = new StaminaProvider() {

		@Override
		public float getCurrentStamina(Player clientPlayerEntity) {
			if (!clientPlayerEntity.level().isClientSide
					|| !(clientPlayerEntity instanceof PlayerStaminaGetter provider))
				return 0;
			return provider.starrailexpress$getStamina();
		}

		@Override
		public float getMaxStamina(Player clientPlayerEntity) {
			StarGameWorldComponent gameComponent = StarGameWorldComponent.KEY.get(clientPlayerEntity.level());
			if (GameFunctions.isPlayerAliveAndSurvival(clientPlayerEntity) && gameComponent != null ) {
				Role role = gameComponent.getRole(clientPlayerEntity);
				if (role == null) {
					return 0;
				}
				return role.getMaxSprintTime(clientPlayerEntity);
			}
			return 0;
		}

		@Override
		public float getStaminaPercentage(Player clientPlayerEntity) {
			return Mth.clamp(getCurrentStamina(clientPlayerEntity) / getMaxStamina(clientPlayerEntity), 0f, 1f);
		}
	};

	public static void setStaminaProvider(StaminaProvider provider) {
		staminaProvider = provider;
	}

	public static void renderHud(@NotNull LocalPlayer player, @NotNull GuiGraphics context, float delta) {
		if (staminaProvider == null) return;

		float maxStamina = staminaProvider.getMaxStamina(player);
		float staminaPercent = staminaProvider.getStaminaPercentage(player);

		final var mainHandStack = player.getMainHandItem();
		boolean isChargingWeapon = false;
		// 检查是否是蓄力物品
		if (ChargeableItemRegistry.isChargeableStack(mainHandStack)) {
			ChargeableItemRegistry.ChargeInfo chargeInfo = ChargeableItemRegistry.getChargeInfo(mainHandStack, player);
			if (chargeInfo != null) {
				maxStamina = chargeInfo.maxStamina;
				staminaPercent = chargeInfo.chargePercentage;
				isChargingWeapon = true;

				// 处理蓄力完成效果
				if (staminaPercent >= 1.0f && !knifeFullyCharged) { // 重用knifeFullyCharged变量作为通用蓄力完成标志
					knifeFullyCharged = true;
					flashStartTime = System.currentTimeMillis(); // 开始闪光效果
					screenRedEffectStartTime = System.currentTimeMillis(); // 触发屏幕红色效果
					// 调用蓄力完成回调
					ChargeableItemRegistry.onFullyCharged(mainHandStack, player);
				} else if (staminaPercent < 1.0f) {
					knifeFullyCharged = false;
				}
			}
		} else {
			// 保持原有逻辑用于兼容性
			if ( mainHandStack.getItem() == TMMItems.GRENADE){
				maxStamina = 20;
				final var itemUseTime = player.getTicksUsingItem();
				staminaPercent = Math.min( (float) itemUseTime / 20,1f);
				isChargingWeapon = true;
			}
			if (mainHandStack.getItem() == TMMItems.KNIFE ){
				maxStamina = 8;
				final var itemUseTime = player.getTicksUsingItem();
				staminaPercent = Math.min( (float) itemUseTime / 10,1f);
				isChargingWeapon = true;

				// 检测刀是否完全蓄力
				if (itemUseTime >= 10 && !knifeFullyCharged) {
					knifeFullyCharged = true;
					flashStartTime = System.currentTimeMillis(); // 开始闪光效果
					screenRedEffectStartTime = System.currentTimeMillis(); // 触发屏幕红色效果
				} else if (itemUseTime < 10) {
					knifeFullyCharged = false;
				}
			}
		}


		if (maxStamina <= 0) return; // 无体力系统

		// 使用与TimeRenderer类似的颜色逻辑
		if (Math.abs(view.getTarget() - staminaPercent) > 0.1f) {
			offsetDelta = staminaPercent > view.getTarget() ? .6f : -.6f;
		}
		offsetDelta = Mth.lerp(delta / 16, offsetDelta, 0f);

		view.setTarget(staminaPercent);

		// 计算颜色 - 绿色满体力，红色低体力
		float r = Mth.lerp(1f - staminaPercent, 0.2f, 1f);
		float g = Mth.lerp(staminaPercent, 0.2f, 1f);
		float b = 0.2f;
		int colour = Mth.color(r, g, b) | 0xFF000000;

		// 渲染主手物品冷却提示
		renderMainHandCooldown(context, player, delta);

		// 渲染体力条 - 移动到物品栏上方
		context.pose().pushPose();
		context.pose().translate(context.guiWidth() / 2f, context.guiHeight() - 35, 0); // 在物品栏上方显示

		// 检查是否应该禁用平滑动画（特别是对于武器蓄力）
		if ((SREConfig.disableStaminaBarSmoothing && isChargingWeapon) || isChargingWeapon) {
			// 如果是刀且完全蓄力，则添加特殊效果
			if (mainHandStack.getItem() == TMMItems.KNIFE && knifeFullyCharged && isFlashActive()) {
				// 创建闪烁效果
				int flashColour = getFlashColor(); // 红白交替闪烁
				view.renderWithoutSmoothing(context, flashColour, staminaPercent);
			} else {
				view.renderWithoutSmoothing(context, colour, staminaPercent);
			}
		} else {
			view.render(context, colour, delta);
		}

		context.pose().popPose();

		// 渲染屏幕边缘红色效果
		renderScreenRedEffect(context, delta);
	}

	/**
	 * 渲染主手物品冷却提示
	 */
	private static void renderMainHandCooldown(@NotNull GuiGraphics context, @NotNull LocalPlayer player, float delta) {
		ItemStack mainHandStack = player.getMainHandItem();
			ItemCooldowns cooldowns = player.getCooldowns();
			float cooldown = cooldowns.getCooldownPercent(mainHandStack.getItem(), delta);

			// 检查是否是同一个物品且冷却刚刚结束
			if (lastCooldown > 0 && cooldown == 0 && !playedCooldownSound && ItemStack.isSameItemSameComponents(lastMainHandStack, mainHandStack)) {
				// 播放冷却结束音效
				Minecraft.getInstance().getSoundManager().play(
						SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7f, 1.0f)
				);
				playedCooldownSound = true;
			} else if (cooldown > 0 || !ItemStack.isSameItemSameComponents(lastMainHandStack, mainHandStack)) {
				// 如果物品已切换，则重置冷却音效标志
				if (!ItemStack.isSameItemSameComponents(lastMainHandStack, mainHandStack)) {
					// 如果切换到刀，则播放切刀音效
//					if (mainHandStack.getItem() == TMMItems.KNIFE && lastMainHandStack.getItem() != TMMItems.KNIFE) {
//						Minecraft.getInstance().getSoundManager().play(
//								SimpleSoundInstance.forUI(SoundEvents.IRON_GOLEM_REPAIR, 0.4f, 2.1f)
//						);
//					}
					playedCooldownSound = false;
				}
				// 如果物品仍在冷却中，重置音效标志
				if (cooldown > 0) {
					playedCooldownSound = false;
				}
			}

			// 更新上一次冷却值和物品
			lastCooldown = cooldown;
			lastMainHandStack = mainHandStack.copy();

			// 如果物品在冷却中，显示冷却百分比
			if (cooldown > 0) {
				int screenWidth = context.guiWidth();
				int screenHeight = context.guiHeight();

				// 在屏幕中心稍上方显示冷却文字
				int x = screenWidth / 2;
				int y = screenHeight - 48; // 物品栏上方

				String cooldownText = String.format("%d%%", (int)(cooldown * 100));

				// 根据冷却百分比改变颜色：红色->橙色->绿色
				int textColor;
				if (cooldown > 0.7f) {
					textColor = 0xFFFF0000; // 红色
				} else if (cooldown > 0.3f) {
					textColor = 0xFFFFA500; // 橙色
				} else {
					textColor = 0xFF00FF00; // 绿色
				}

				// 绘制文字背景（半透明黑色）
//				int textWidth = Minecraft.getInstance().font.width(cooldownText);
//				int padding = 4;
//				context.fill(
//						x - textWidth / 2 - padding,
//						y - padding,
//						x + textWidth / 2 + padding,
//						y + 9 + padding,
//						0x80000000
//				);

				// 绘制冷却文字
				context.drawCenteredString(
						Minecraft.getInstance().font,
						cooldownText,
						x,
						y,
						textColor
				);

		}
	}

	/**
	 * 渲染屏幕边缘红色效果（刀蓄力完毕时）
	 */
	private static void renderScreenRedEffect(@NotNull GuiGraphics context, float delta) {
		if (isScreenRedEffectActive()) {
			renderScreenEdgeEffect(context, screenRedEffectStartTime, SCREEN_RED_EFFECT_DURATION_MS, 0xFF0000, MAX_RED_INTENSITY);
		}
		
		// 同时渲染通用屏幕边缘效果
		if (isGeneralScreenEffectActive()) {
			renderScreenEdgeEffect(context, generalScreenEffectStartTime, GENERAL_SCREEN_EFFECT_DURATION_MS, generalScreenEffectColor, generalScreenEffectIntensity);
		}
	}
	
	/**
	 * 通用的屏幕边缘效果渲染方法
	 */
	private static void renderScreenEdgeEffect(@NotNull GuiGraphics context, long effectStartTime, long effectDurationMs, int color, float maxIntensity) {
		int screenWidth = context.guiWidth();
		int screenHeight = context.guiHeight();

		// 计算效果的强度（随时间递减）
		long currentTime = System.currentTimeMillis();
		long elapsed = currentTime - effectStartTime;
		float progress = 1.0f - (float) elapsed / effectDurationMs; // 随时间递减
		progress = Math.max(0.0f, progress);
		float intensity = maxIntensity * progress;

		// 分离RGB颜色组件
		int red = (color >> 16) & 0xFF;
		int green = (color >> 8) & 0xFF;
		int blue = color & 0xFF;

		// 设置颜色（带透明度）
		// int effectColor = (int)(intensity * 255) << 24 | (red << 16) | (green << 8) | blue;

		// 保存当前的混合状态
		PoseStack poseStack = context.pose();
		poseStack.pushPose();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();

		// 渲染四个边缘的渐变效果
		int edgeWidth = (int)(screenWidth * 0.1f); // 边缘宽度为屏幕宽度的10%
		int edgeHeight = (int)(screenHeight * 0.1f); // 边缘高度为屏幕高度的10%

		// 顶部边缘（从上到下的渐变）
		for (int i = 0; i < edgeHeight; i++) {
			float alpha = (1f - (float)i / edgeHeight) * intensity;
			int currentColor = (int)(alpha * 255) << 24 | (red << 16) | (green << 8) | blue;
			context.fill(0, i, screenWidth, i + 1, currentColor);
		}

		// 底部边缘（从下到上的渐变）
		for (int i = 0; i < edgeHeight; i++) {
			float alpha = (1f - (float)i / edgeHeight) * intensity;
			int currentColor = (int)(alpha * 255) << 24 | (red << 16) | (green << 8) | blue;
			context.fill(0, screenHeight - i - 1, screenWidth, screenHeight - i, currentColor);
		}

		// 左侧边缘（从左到右的渐变）
		for (int i = 0; i < edgeWidth; i++) {
			float alpha = (1f - (float)i / edgeWidth) * intensity;
			int currentColor = (int)(alpha * 255) << 24 | (red << 16) | (green << 8) | blue;
			context.fill(i, edgeHeight, i + 1, screenHeight - edgeHeight, currentColor);
		}

		// 右侧边缘（从右到左的渐变）
		for (int i = 0; i < edgeWidth; i++) {
			float alpha = (1f - (float)i / edgeWidth) * intensity;
			int currentColor = (int)(alpha * 255) << 24 | (red << 16) | (green << 8) | blue;
			context.fill(screenWidth - i - 1, edgeHeight, screenWidth - i, screenHeight - edgeHeight, currentColor);
		}

		RenderSystem.disableBlend();
		poseStack.popPose();
	}

	/**
	 * 检查屏幕红色效果是否仍然活跃
	 */
	private static boolean isScreenRedEffectActive() {
		if (screenRedEffectStartTime == 0L) {
			return false;
		}
		long currentTime = System.currentTimeMillis();
		return (currentTime - screenRedEffectStartTime) < SCREEN_RED_EFFECT_DURATION_MS;
	}
	
	/**
	 * 检查通用屏幕效果是否仍然活跃
	 */
	private static boolean isGeneralScreenEffectActive() {
		if (generalScreenEffectStartTime == 0L) {
			return false;
		}
		long currentTime = System.currentTimeMillis();
		return (currentTime - generalScreenEffectStartTime) < GENERAL_SCREEN_EFFECT_DURATION_MS;
	}
	
	/**
	 * 启动通用屏幕边缘效果
	 * 
	 * @param color 颜色值，例如 0xFF0000 为红色
	 * @param durationMs 效果持续时间（毫秒）
	 * @param intensity 最大强度（0.0-1.0）
	 */
	public static void triggerScreenEdgeEffect(int color, long durationMs, float intensity) {
		generalScreenEffectStartTime = System.currentTimeMillis();
		generalScreenEffectColor = color;
		generalScreenEffectIntensity = intensity;
		GENERAL_SCREEN_EFFECT_DURATION_MS = (int) durationMs; // 注意：这里会修改常量，需要重构
	}
	
	/**
	 * 启动通用屏幕边缘效果（使用默认参数）
	 * 
	 * @param color 颜色值，例如 0xFF0000 为红色
	 */
	public static void triggerScreenEdgeEffect(int color) {
		triggerScreenEdgeEffect(color, 300L, 0.5f);
	}
	
	/**
	 * 启动通用屏幕边缘效果（使用默认红色和持续时间）
	 * 
	 * @param intensity 最大强度（0.0-1.0）
	 */
	public static void triggerScreenEdgeEffect(float intensity) {
		triggerScreenEdgeEffect(0xFF0000, 300L, intensity);
	}
	
	/**
	 * 启动通用屏幕边缘效果（使用指定颜色和持续时间）
	 * 
	 * @param color 颜色值，例如 0xFF0000 为红色
	 * @param durationMs 效果持续时间（毫秒）
	 */
	public static void triggerScreenEdgeEffect(int color, long durationMs) {
		triggerScreenEdgeEffect(color, durationMs, 0.5f);
	}

	public static void tick() {
		view.update();
		// 如果不在使用蓄力物品，重置蓄力状态
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null) {
			ItemStack mainHandStack = minecraft.player.getMainHandItem();
			// 检查是否不是蓄力物品或不是原生的TMM刀
			if (!ChargeableItemRegistry.isChargeableStack(mainHandStack) && mainHandStack.getItem() != TMMItems.KNIFE) {
				knifeFullyCharged = false;
				flashStartTime = 0L;
				screenRedEffectStartTime = 0L;
			}
		}
	}

	/**
	 * 检查闪动效果是否仍然活跃
	 */
	private static boolean isFlashActive() {
		if (flashStartTime == 0L) {
			return false;
		}
		long currentTime = System.currentTimeMillis();
		return (currentTime - flashStartTime) < FLASH_DURATION_MS;
	}

	/**
	 * 获取当前闪动颜色
	 */
	private static int getFlashColor() {
		if (flashStartTime == 0L) {
			return 0xFFFFFFFF; // 默认白色
		}
		long currentTime = System.currentTimeMillis();
		long elapsed = currentTime - flashStartTime;
		// 使用更长的周期让闪烁更明显
		long cycleDuration = 100L; // 100毫秒一个周期
		long cyclePosition = elapsed % cycleDuration;
		// 在周期的前半段显示红色，在后半段显示白色
		return (cyclePosition < cycleDuration / 2) ? 0xFFFF0000 : 0xFFFFFFFF;
	}

	public static class StaminaBarRenderer {
		private float target;
		private float currentValue;
		private float lastValue;

		public void setTarget(float target) {
			this.target = Mth.clamp(target, 0f, 1f);
		}

		public void update() {
			this.lastValue = this.currentValue;
			this.currentValue = Mth.lerp(0.15f, this.currentValue, this.target);
			if (Math.abs(this.currentValue - this.target) < 0.01f) {
				this.currentValue = this.target;
			}
		}

		public void render(@NotNull GuiGraphics context, int colour, float delta) {
			float value = Mth.lerp(delta, this.lastValue, this.currentValue);

			// 体力条参数 - 更现代、更扁平的设计
			int barWidth = 120; // 总宽度增加
			int barHeight = 2;  // 高度减小变得更扁平
			int halfWidth = barWidth / 2;

			// 绘制背景（更现代化的半透明黑色）
			int backgroundColor = 0x66000000; // 更透明的背景
			context.fill(-halfWidth, -barHeight/2, halfWidth, barHeight/2, backgroundColor);

			// 计算当前体力条宽度
			int currentWidth = Math.round(barWidth * value);
			int currentHalfWidth = currentWidth / 2;

			if (currentWidth > 0) {
				// 绘制体力条（从中间向两边延伸）
				context.fill(-currentHalfWidth, -barHeight/2, currentHalfWidth, barHeight/2, colour);
			}

			// 绘制中心分隔线（更窄）
			int centerLineColor = 0x80FFFFFF;
			context.fill(-1, -barHeight/2 + 1, 1, barHeight/2 - 1, centerLineColor); // 更窄的线条
		}

		public void renderWithoutSmoothing(@NotNull GuiGraphics context, int colour, float value) {
			// 体力条参数 - 更现代、更扁平的设计
			int barWidth = 120; // 总宽度增加
			int barHeight = 2;  // 高度减小变得更扁平
			int halfWidth = barWidth / 2;

			// 绘制背景（更现代化的半透明黑色）
			int backgroundColor = 0x66000000; // 更透明的背景
			context.fill(-halfWidth, -barHeight/2, halfWidth, barHeight/2, backgroundColor);

			// 计算当前体力条宽度
			int currentWidth = Math.round(barWidth * value);
			int currentHalfWidth = currentWidth / 2;

			if (currentWidth > 0) {
				// 绘制体力条（从中间向两边延伸）
				context.fill(-currentHalfWidth, -barHeight/2, currentHalfWidth, barHeight/2, colour);
			}

			// 绘制中心分隔线（更窄）
			int centerLineColor = 0x80FFFFFF;
			context.fill(-1, -barHeight/2 + 1, 1, barHeight/2 - 1, centerLineColor); // 更窄的线条
		}

		public float getTarget() {
			return this.target;
		}
	}
}