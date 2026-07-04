package io.wifi.starrailexpress.client;

import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.api.ChargeableItemRegistry;
import io.wifi.starrailexpress.client.render.hud.stamina.StaminaDefaultRenderer;
import io.wifi.starrailexpress.client.render.hud.stamina.StaminaOldRenderer;
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
			case BAMBOO_STYLE ->
				StaminaDefaultRenderer.render(player, mainHandStack, context, delta, stamina, itemCharge,
						isChargingWeapon);
			case MINECRAFT_STYLE ->
				StaminaDefaultRenderer.render(player, mainHandStack, context, delta, stamina, itemCharge,
						isChargingWeapon);
			default -> {
				/* 不渲染; */
			}
		}

		// 渲染屏幕边缘红色效果
		StaminaDefaultRenderer.renderScreenRedEffect(context, delta);
	}

	public static void tick() {
		// 红色效果渲染
		StaminaDefaultRenderer.tick();
	}

	public static void triggerScreenEdgeEffect(int edgeColor, long min, float max) {
		StaminaDefaultRenderer.triggerScreenEdgeEffect(edgeColor, edgeColor, min);
	}

	public static void triggerScreenEdgeEffect(int rgb) {
		StaminaDefaultRenderer.triggerScreenEdgeEffect(rgb);
	}
}