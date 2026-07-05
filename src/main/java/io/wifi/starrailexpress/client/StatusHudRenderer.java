package io.wifi.starrailexpress.client;

import org.jetbrains.annotations.NotNull;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class StatusHudRenderer {

    public static void renderOxygen(GuiGraphics context, LocalPlayer player, float delta) {
        Minecraft client = Minecraft.getInstance();
        Player player = client.gui.getCameraPlayer();
        if (player != null) {
            int i = Mth.ceil(player.getHealth());
            boolean bl = this.healthBlinkTime > (long) this.tickCount
                    && (this.healthBlinkTime - (long) this.tickCount) / 3L % 2L == 1L;
            long l = Util.getMillis();
            if (i < this.lastHealth && player.invulnerableTime > 0) {
                this.lastHealthTime = l;
                this.healthBlinkTime = (long) (this.tickCount + 20);
            } else if (i > this.lastHealth && player.invulnerableTime > 0) {
                this.lastHealthTime = l;
                this.healthBlinkTime = (long) (this.tickCount + 10);
            }

            if (l - this.lastHealthTime > 1000L) {
                this.lastHealth = i;
                this.displayHealth = i;
                this.lastHealthTime = l;
            }

            this.lastHealth = i;
            int j = this.displayHealth;
            this.random.setSeed((long) (this.tickCount * 312871));
            int k = guiGraphics.guiWidth() / 2 - 91;
            int m = guiGraphics.guiWidth() / 2 + 91;
            int n = guiGraphics.guiHeight() - 39;
            float f = Math.max((float) player.getAttributeValue(Attributes.MAX_HEALTH), (float) Math.max(j, i));
            int o = Mth.ceil(player.getAbsorptionAmount());
            int p = Mth.ceil((f + (float) o) / 2.0F / 10.0F);
            int q = Math.max(10 - (p - 2), 3);
            int r = n - 10;
            int s = -1;
            if (player.hasEffect(MobEffects.REGENERATION)) {
                s = this.tickCount % Mth.ceil(f + 5.0F);
            }

            this.minecraft.getProfiler().push("armor");
            renderArmor(guiGraphics, player, n, p, q, k);
            this.minecraft.getProfiler().popPush("health");
            this.renderHearts(guiGraphics, player, k, n, q, s, f, i, j, o, bl);
            LivingEntity livingEntity = this.getPlayerVehicleWithHealth();
            int t = this.getVehicleMaxHearts(livingEntity);
            if (t == 0) {
                this.minecraft.getProfiler().popPush("food");
                this.renderFood(guiGraphics, player, n, m);
                r -= 10;
            }

            this.minecraft.getProfiler().popPush("air");
            int u = player.getMaxAirSupply();
            int v = Math.min(player.getAirSupply(), u);
            if (player.isEyeInFluid(FluidTags.WATER) || v < u) {
                int w = this.getVisibleVehicleHeartRows(t) - 1;
                r -= w * 10;
                int x = Mth.ceil((double) (v - 2) * (double) 10.0F / (double) u);
                int y = Mth.ceil((double) v * (double) 10.0F / (double) u) - x;
                RenderSystem.enableBlend();

                for (int z = 0; z < x + y; ++z) {
                    if (z < x) {
                        guiGraphics.blitSprite(AIR_SPRITE, m - z * 8 - 9, r, 9, 9);
                    } else {
                        guiGraphics.blitSprite(AIR_BURSTING_SPRITE, m - z * 8 - 9, r, 9, 9);
                    }
                }

                RenderSystem.disableBlend();
            }

            this.minecraft.getProfiler().pop();
        }
    }
}
