package io.wifi.starrailexpress.client;

import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.api.ChargeableItemRegistry;
import io.wifi.starrailexpress.client.render.hud.stamina.StaminaDefaultRenderer;
import io.wifi.starrailexpress.client.render.hud.stamina.StaminaMCStyleRenderer;
import io.wifi.starrailexpress.client.render.hud.stamina.StaminaOldRenderer;
import io.wifi.starrailexpress.client.render.hud.stamina.StaminaSplitStyleRenderer;
import io.wifi.starrailexpress.client.render.hud.stamina.utils.HotbarCooldownRenderer;
import io.wifi.starrailexpress.client.render.hud.stamina.utils.RedScreenRenderer;
import io.wifi.starrailexpress.client.render.hud.stamina.utils.StaminaProvider;
import io.wifi.starrailexpress.util.ProgressProvider;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.NotNull;

public class StaminaRenderer {
	public static SREClientConfig CLIENT_CONFIG = SREClientConfig.instance();
	// 默认的体力条提供者
	public static final StaminaProvider staminaProvider = new StaminaProvider();

	public static void renderHud(@NotNull LocalPlayer player, @NotNull GuiGraphics context, float delta) {
		if (staminaProvider == null)
			return;

		// 渲染屏幕边缘红色效果
		RedScreenRenderer.renderScreenRedEffect(context, delta);

		// 快捷栏上方冷却显示（默认开启）
		if (CLIENT_CONFIG.showHotbarCooldown) {
			HotbarCooldownRenderer.render(context, delta);
		}
		// 主手物品冷却显示默认开启
		if (CLIENT_CONFIG.showMainhandCooldown) {
			HotbarCooldownRenderer.renderMainHandCooldown(context, player, delta);
		}
		// 氧气值渲染（水下）
		if (CLIENT_CONFIG.showMainhandCooldown) {
			StatusHudRenderer.renderOxygen(context, player, delta);
		}

		if (player.isSpectator()){
			// 避免和职业信息渲染冲突
			return;
		}
		// 60s 模式下不显示体力条：60s 有自己的状态 HUD（健康/饥饿/口渴/理智/污染），
		// 体力条与 60s 状态系统无关，显示出来会与物品栏上方的 60s 状态面板重复且混乱。
		if (net.exmo.sre.sixtyseconds.SixtySecondsMod.MODE != null
				&& SREClient.gameComponent != null
				&& SREClient.gameComponent.isRunning()
				&& SREClient.gameComponent.getGameMode() == net.exmo.sre.sixtyseconds.SixtySecondsMod.MODE) {
			return;
		}
		ProgressProvider stamina = null;
		ProgressProvider itemCharge = null;
		final var mainHandStack = player.getMainHandItem();
		boolean isChargingWeapon = false;
		// 检查是否是蓄力物品
		if (ChargeableItemRegistry.isChargeableStack(mainHandStack)) {
			ChargeableItemRegistry.ChargeInfo chargeInfo = ChargeableItemRegistry.getChargeInfo(mainHandStack, player);
			if (chargeInfo != null) {
				isChargingWeapon = true;
				itemCharge = ProgressProvider.of(chargeInfo.chargePercentage);
				if (chargeInfo.chargePercentage >= 1.0f) {
					ChargeableItemRegistry.onFullyCharged(mainHandStack, player);
				}
			}
		}
		float staminaPercent = 0;
		float maxStamina = staminaProvider.getMaxStamina(player);

		if (maxStamina > 0 && maxStamina < Integer.MAX_VALUE) {
			staminaPercent = staminaProvider.getStaminaPercentage(player);
			stamina = ProgressProvider.of(staminaPercent);
		}
		switch (CLIENT_CONFIG.staminaStyle) {
			case DEFAULT -> StaminaDefaultRenderer.render(player, mainHandStack, context, delta, stamina, itemCharge,
					isChargingWeapon);
			case OLD_STYLE -> StaminaOldRenderer.render(player, mainHandStack, context, delta, stamina, itemCharge,
					isChargingWeapon);
			case SPLIT_STYLE ->
				StaminaSplitStyleRenderer.render(player, mainHandStack, context, delta, stamina, itemCharge,
						isChargingWeapon);
			case MINECRAFT_STYLE ->
				StaminaMCStyleRenderer.render(player, mainHandStack, context, delta, stamina, itemCharge,
						isChargingWeapon);
			default -> {
				/* 不渲染; */
			}
		}

	}

	public static void tick() {
		// 红色效果渲染
		switch (CLIENT_CONFIG.staminaStyle) {
			case DEFAULT:
				// 残月新款：从左到右，不居中
				StaminaDefaultRenderer.tick();
				break;
			case OLD_STYLE:
				// 残月旧款：居中
				StaminaOldRenderer.tick();
				break;
			case MINECRAFT_STYLE:
				StaminaMCStyleRenderer.tick();
				break;
			case SPLIT_STYLE:
				StaminaSplitStyleRenderer.tick();
				break;
			default:
				break;

		}
	}

	public static void triggerScreenEdgeEffect(int color, long durationMs, float intensity) {
		RedScreenRenderer.triggerScreenEdgeEffect(color, durationMs, intensity);
	}

	public static void triggerScreenEdgeEffect(int rgb) {
		RedScreenRenderer.triggerScreenEdgeEffect(rgb);
	}
}