package net.exmo.sre.sixtyseconds.client;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.entity.SixtySecondsBossEntity;
import net.exmo.sre.sixtyseconds.network.PlayerHealthS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 末日60秒模式战斗 HUD：<b>右上中部</b>的目标血量条 + 伤害数字。
 * <p>
 * 准星对准实体时显示其名称与血量条；实体受到伤害时在条下方浮现放大的伤害数字
 * （末位右对齐血量显示右端、原地渐隐、无向上飘移，大伤害橙色）。射线检测范围 48 格（覆盖枪械射程）。
 * <ul>
 *   <li>非玩家实体（怪物/NPC/Boss）使用原版 {@code getHealth/getMaxHealth}。</li>
 *   <li>60s 玩家目标使用 {@link SixtySecondsStatsComponent} 同步过来的 health/healthMax（精简变体）。</li>
 *   <li>Boss 追加等级显示（Lv1~5）。</li>
 *   <li>失去目标 1.5s 后面板淡出消失。</li>
 * </ul>
 */
public final class SixtySecondsCombatHud {
    // ── 布局 ──
    private static final int PANEL_W = 150;
    private static final int PANEL_X_OFFSET = 8;
    private static final int PANEL_Y_NUMERATOR = 5; // y = screenH / 5（上中部）
    private static final int PAD = 5;
    private static final int NAME_H = 9;
    private static final int NAME_GAP = 2;
    private static final int BAR_H = 4;            // 纯色简约：原 6，-35% 厚度
    private static final int DMG_START_GAP = 6;
    private static final int DMG_LINE_H = 14;      // 伤害数字放大后的行距
    private static final float DAMAGE_SCALE = 1.4f;// 伤害数字放大倍数

    // ── 时序 ──
    private static final long TARGET_FADE_MS = 1500;
    private static final long DAMAGE_LIFETIME_MS = 1200;
    private static final int MAX_DAMAGE_ENTRIES = 5;
    private static final double TARGET_RANGE = 48.0;
    private static final int TARGET_UPDATE_INTERVAL = 3; // 每 3 帧更新一次射线检测

    // ── 配色 ──
    private static final int COL_NAME = 0xFFF0F0F0;
    private static final int COL_VALUE = 0xFFF0F0F0;
    private static final int COL_HP_HIGH = 0xFF4CAF50;
    private static final int COL_HP_MID = 0xFFFF9800;
    private static final int COL_HP_LOW = 0xFFF44336;
    private static final int COL_DAMAGE = 0xFFFFD700;
    private static final int COL_DAMAGE_BIG = 0xFFFF6B35;

    // ── 状态 ──
    private static Entity currentTarget = null;
    private static float lastHealth = -1;
    private static long lastSeenTime = 0;
    private static final List<DamageEntry> damageEntries = new ArrayList<>();
    private static int frameCounter = 0;
    private static Entity cachedTarget = null;

    private SixtySecondsCombatHud() {
    }

    public static void register() {
        CommonHudRenderCallback.EVENT.register((graphics, deltaTracker) -> render(graphics));
    }

    private static void render(FakeGuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        if (SREClient.gameComponent == null || !SREClient.gameComponent.isRunning()) return;
        if (SixtySecondsMod.MODE == null || SREClient.gameComponent.getGameMode() != SixtySecondsMod.MODE) return;
        if (client.player.isSpectator()) return;

        LocalPlayer player = client.player;
        long now = System.currentTimeMillis();

        // 射线检测目标（每 3 帧更新一次，降低性能开销）
        frameCounter++;
        if (frameCounter % TARGET_UPDATE_INTERVAL == 0) {
            cachedTarget = findTarget(player);
        }
        Entity target = cachedTarget;

        // 更新目标与伤害追踪
        if (target != null && target.isAlive()) {
            float health = getEntityHealth(target);
            if (target == currentTarget && lastHealth >= 0 && health < lastHealth) {
                float dmg = lastHealth - health;
                damageEntries.add(new DamageEntry(dmg, now));
                while (damageEntries.size() > MAX_DAMAGE_ENTRIES) {
                    damageEntries.remove(0);
                }
            }
            currentTarget = target;
            lastHealth = health;
            lastSeenTime = now;
        } else {
            if (currentTarget != null && now - lastSeenTime > TARGET_FADE_MS) {
                currentTarget = null;
                lastHealth = -1;
            }
        }

        // 清理过期伤害数字
        Iterator<DamageEntry> it = damageEntries.iterator();
        while (it.hasNext()) {
            if (now - it.next().timestamp > DAMAGE_LIFETIME_MS) {
                it.remove();
            }
        }

        if (currentTarget == null && damageEntries.isEmpty()) return;

        // 淡出透明度
        float fadeAlpha = 1.0f;
        if (target == null || !target.isAlive()) {
            long since = now - lastSeenTime;
            fadeAlpha = Mth.clamp(1.0f - (float) since / TARGET_FADE_MS, 0, 1);
        }
        if (fadeAlpha <= 0.01f && damageEntries.isEmpty()) return;

        // ── 计算布局（不绘制面板背景）──
        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        int panelX = screenW - PANEL_W - PANEL_X_OFFSET;
        int panelY = screenH / PANEL_Y_NUMERATOR;

        boolean hasTarget = currentTarget != null && currentTarget.isAlive();

        // 内容高度（仅用于早退判断）
        int contentH = 0;
        if (hasTarget) {
            contentH += NAME_H + NAME_GAP + BAR_H; // 名称 + 血量条
        }
        if (!damageEntries.isEmpty()) {
            contentH += (contentH > 0 ? DMG_START_GAP : 0) + damageEntries.size() * DMG_LINE_H;
        }
        if (contentH <= 0) return;

        int x = panelX + PAD;
        int y = panelY + PAD;
        int right = panelX + PANEL_W - PAD;
        int usableW = PANEL_W - PAD * 2;

        // ── 实体名称 ──
        if (hasTarget) {
            Component name = getEntityName(currentTarget);
            graphics.drawString(client.font, name, x, y, withAlpha(COL_NAME, fadeAlpha));
            y += NAME_H + NAME_GAP;

            // ── 血量条（纯色简约：仅实心填充，无轨道/边框/刻度；厚度 -35%）──
            float health = getEntityHealth(currentTarget);
            float maxHealth = getEntityMaxHealth(currentTarget);
            float ratio = maxHealth > 0 ? Mth.clamp(health / maxHealth, 0, 1) : 0;
            int barColor = ratio > 0.6f ? COL_HP_HIGH : ratio > 0.3f ? COL_HP_MID : COL_HP_LOW;

            String hpText = Math.round(health) + "/" + Math.round(maxHealth);
            int hpTextW = client.font.width(hpText);
            int barW = usableW - hpTextW - 4;

            drawBar(graphics, x, y, barW, ratio, barColor, fadeAlpha);
            graphics.drawString(client.font, hpText, x + barW + 4, y, withAlpha(COL_VALUE, fadeAlpha));
            y += BAR_H + DMG_START_GAP;
        } else {
            y += DMG_START_GAP;
        }

        // ── 伤害数字：放大、末位右对齐到血量显示右端、仅渐隐（无向上飘移）──
        int lineH = (int) (client.font.lineHeight * DAMAGE_SCALE) + 2;
        for (int i = 0; i < damageEntries.size(); i++) {
            DamageEntry entry = damageEntries.get(i);
            float age = (now - entry.timestamp) / 1000.0f;
            float lifeRatio = age / (DAMAGE_LIFETIME_MS / 1000.0f);
            float alpha = Mth.clamp(1.0f - lifeRatio, 0, 1) * fadeAlpha;
            if (alpha <= 0.01f) continue;

            String dmgText = "-" + Math.round(entry.amount);
            int baseColor = entry.amount >= 20 ? COL_DAMAGE_BIG : COL_DAMAGE;
            int color = withAlpha(baseColor, alpha);

            int textW = client.font.width(dmgText);
            int scaledW = (int) (textW * DAMAGE_SCALE);
            int drawX = right - scaledW;            // 末位数字对齐血量显示右端
            int drawY = y + i * lineH;

            graphics.pose().pushPose();
            graphics.pose().translate(drawX, drawY, 0);
            graphics.pose().scale(DAMAGE_SCALE, DAMAGE_SCALE, 1f);
            graphics.drawString(client.font, dmgText, 0, 0, color);
            graphics.pose().popPose();
        }
    }

    /** 射线检测准星对准的 LivingEntity（排除自己）。 */
    private static Entity findTarget(LocalPlayer player) {
        HitResult hit = ProjectileUtil.getHitResultOnViewVector(player,
                entity -> entity instanceof LivingEntity && entity != player, TARGET_RANGE);
        if (hit instanceof EntityHitResult ehr) {
            Entity entity = ehr.getEntity();
            if (entity.isAlive() && !entity.isRemoved()) {
                return entity;
            }
        }
        return null;
    }

    /**
     * 获取实体当前血量。
     * 玩家走 {@link PlayerHealthS2CPacket#CLIENT_HEALTH} 网络包缓存（他人）或组件（自己）；
     * 其余走原版 {@code getHealth}。
     */
    private static float getEntityHealth(Entity entity) {
        if (entity instanceof Player p) {
            int[] data = PlayerHealthS2CPacket.CLIENT_HEALTH.get(p.getUUID());
            if (data != null) return data[0];
            // 本地玩家 fallback：自己的组件有完整同步
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(p);
            if (stats.teamId >= 0) return stats.health;
            return -1; // 未知
        }
        if (entity instanceof LivingEntity living) return living.getHealth();
        return 0;
    }

    /** 获取实体最大血量（同 getEntityHealth 的数据源）。 */
    private static float getEntityMaxHealth(Entity entity) {
        if (entity instanceof Player p) {
            int[] data = PlayerHealthS2CPacket.CLIENT_HEALTH.get(p.getUUID());
            if (data != null) return data[1];
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(p);
            if (stats.teamId >= 0) return stats.healthMax;
            return -1; // 未知
        }
        if (entity instanceof LivingEntity living) return living.getMaxHealth();
        return 1;
    }

    /** 获取实体显示名称（Boss 追加等级）。 */
    private static Component getEntityName(Entity entity) {
        Component name = entity.getDisplayName();
        if (entity instanceof SixtySecondsBossEntity boss) {
            int level = boss.bossLevel();
            if (level > 0) {
                return Component.empty().append(name).append(Component.literal(" Lv" + level));
            }
        }
        return name;
    }

    /** 血量条（纯色简约：仅实心填充，无轨道/边框/刻度/高光）。 */
    private static void drawBar(FakeGuiGraphics g, int x, int y, int w, float ratio, int color, float alpha) {
        int h = BAR_H;
        ratio = Mth.clamp(ratio, 0, 1);
        int fillW = (int) Math.round(w * ratio);
        if (fillW > 0) {
            g.fill(x, y, x + fillW, y + h, withAlpha(color, alpha));
        }
    }

    /** 给 ARGB 颜色应用透明度倍率。 */
    private static int withAlpha(int color, float alpha) {
        int a = (int) ((color >> 24 & 255) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    /** 伤害数字条目。 */
    private static final class DamageEntry {
        final float amount;
        final long timestamp;

        DamageEntry(float amount, long timestamp) {
            this.amount = amount;
            this.timestamp = timestamp;
        }
    }
}
