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

package io.wifi.starrailexpress.hat;

import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.client.hat.ClientHatEquipmentCache;
import io.wifi.starrailexpress.event.OnResolveDisplayedSkinOwner;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * 帽子装备对外 API。
 * <p>
 * 核心规则：<b>帽子跟随"显示的皮肤"，而不是跟随玩家实体本身</b>。
 * 当玩家因为某些机制（窃皮、易容等）显示为他人的皮肤时，
 * 其装备的帽子也会随之变为该皮肤拥有者所装备的帽子。
 * <p>
 * 具体实现（帽子物品的解析与渲染）由附属模组（sre-skin）完成，
 * 本体只提供装备状态的存储、同步与拥有者解析。
 */
public final class HatEquipmentApi {

    private HatEquipmentApi() {
    }

    /**
     * 注册本体默认的显示皮肤拥有者解析器（客户端初始化时调用）。
     * 覆盖窃皮者（Skincrawler）与入殓师（Embalmer）两种皮肤替换机制。
     */
    /**
     * 注册本体默认的显示皮肤拥有者解析器（客户端初始化时调用）。
     * <p>
     * 覆盖全部已知的皮肤替换机制（与皮肤渲染管线的判定保持一致）：
     * <ul>
     * <li>洗牌观察（JEB / 精神低落者看变形者，{@code MorphlingRendererMixin} 同款逻辑）</li>
     * <li>双重人格（SplitPersonality）</li>
     * <li>变形者（Morphling）变身</li>
     * <li>嬉命人（Embalmer）易容</li>
     * <li>窃皮者（Skincrawler）窃皮</li>
     * <li>阿蒙（Amon）夺舍</li>
     * </ul>
     */
    @Environment(EnvType.CLIENT)
    public static void registerDefaultOwnerResolvers() {
        OnResolveDisplayedSkinOwner.EVENT.register(player -> {
            // 与皮肤替换逻辑保持一致：大堂中不替换皮肤，帽子也不跟随他人
            if (io.wifi.starrailexpress.SRE.isLobby || io.wifi.starrailexpress.client.SREClient.isInLobby) {
                return null;
            }
            // 1. 洗牌观察（观察者视角的感知替换）
            UUID shuffled = resolveShuffledTarget(player);
            if (shuffled != null) {
                return shuffled;
            }
            // 2. 双重人格：非活跃人格显示为主人格
            var splitComponent = pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SkinSplitPersonalityComponent.KEY
                    .getNullable(player);
            if (splitComponent != null && splitComponent.getSkinToAppearAs() != null) {
                return splitComponent.getSkinToAppearAs();
            }
            // 3. 变形者变身中
            var morphComponent = org.agmas.noellesroles.game.roles.killer.morphling.MorphlingPlayerComponent.KEY
                    .getNullable(player);
            if (morphComponent != null && morphComponent.getMorphTicks() > 0 && morphComponent.disguise != null) {
                return morphComponent.disguise;
            }
            // 4. 嬉命人易容
            UUID replacement = org.agmas.noellesroles.client.ClientEmbalmerState.replacement(player.getUUID());
            if (replacement != null) {
                return replacement;
            }
            // 5. 窃皮者窃皮
            UUID stolen = org.agmas.noellesroles.client.ClientSkincrawlerState.stolenSkinFor(player.getUUID());
            if (stolen != null) {
                return stolen;
            }
            // 6. 阿蒙夺舍
            return org.agmas.noellesroles.client.ClientAmonState.disguiseTargetFor(player.getUUID());
        });
    }

    /**
     * 洗牌观察目标解析（与 {@code MorphlingRendererMixin#getShuffledTarget} 逻辑一致）：
     * JEB 洗牌，或精神低落者在配置允许时看到的变形者洗牌。
     */
    @Environment(EnvType.CLIENT)
    private static UUID resolveShuffledTarget(AbstractClientPlayer player) {
        final var level = player.level();
        if (level == null) {
            return null;
        }
        var worldModifiers = org.agmas.harpymodloader.component.WorldModifierComponent.KEY.get(level);
        if (worldModifiers != null
                && worldModifiers.isModifier(player, pro.fazeclan.river.stupid_express.constants.SEModifiers.JEB_)) {
            return org.agmas.noellesroles.client.NoellesrolesClient.JEB_SHUFFLED_PLAYER_ENTRIES_CACHE
                    .get(player.getUUID());
        }
        if (io.wifi.starrailexpress.client.SREClient.moodComponent == null) {
            return null;
        }
        if (!org.agmas.noellesroles.client.NoellesrolesClient.SHUFFLED_PLAYER_ENTRIES_CACHE
                .containsKey(player.getUUID())) {
            return null;
        }
        if (org.agmas.noellesroles.ConfigWorldComponent.KEY.get(level).insaneSeesMorphs
                && io.wifi.starrailexpress.client.SREClient.moodComponent.isLowerThanDepressed()) {
            return org.agmas.noellesroles.client.NoellesrolesClient.SHUFFLED_PLAYER_ENTRIES_CACHE
                    .get(player.getUUID());
        }
        return null;
    }

    /**
     * 解析某玩家当前显示皮肤的拥有者 UUID（客户端）。
     * 没有皮肤替换时返回玩家本人 UUID。
     */
    @Environment(EnvType.CLIENT)
    public static UUID resolveDisplayedOwnerUuid(AbstractClientPlayer player) {
        UUID resolved = OnResolveDisplayedSkinOwner.EVENT.invoker().resolveDisplayedOwner(player);
        return resolved != null ? resolved : player.getUUID();
    }

    /**
     * 获取某玩家<b>当前应当显示的</b>帽子皮肤名（客户端）。
     * <p>
     * 先解析显示皮肤的拥有者，再查询该拥有者装备的帽子。
     * 未装备帽子时返回 {@code "default"}。
     * <p>
     * 当玩家处于 {@code DISGUISE} 伪装效果（含渡鸦的伪装，二者为同一效果）时，
     * 直接返回 {@code "default"} —— 伪装状态下隐藏帽子，避免暴露身份。
     */
    @Environment(EnvType.CLIENT)
    public static String getDisplayedHatSkinName(AbstractClientPlayer player) {
        // DISGUISE 伪装效果（含渡鸦的伪装）下隐藏帽子
        if (player.hasEffect(org.agmas.noellesroles.init.ModEffects.DISGUISE)) {
            return "default";
        }
        UUID ownerUuid = resolveDisplayedOwnerUuid(player);
        String skin = ClientHatEquipmentCache.getHatSkin(ownerUuid);
        if (!"default".equals(skin)) {
            return skin;
        }
        // 回退：当查询对象就是本机玩家时，CCA 皮肤组件中也有权威数据
        // （广播包可能尚未到达）。
        if (ownerUuid.equals(player.getUUID())) {
            SREPlayerSkinsComponent component = SREPlayerSkinsComponent.KEY.getNullable(player);
            if (component != null) {
                String own = component.getEquippedSkin(HatEquipmentManager.HAT_TYPE);
                if (own != null && !own.isBlank()) {
                    return own;
                }
            }
        }
        return "default";
    }

    /**
     * 获取服务端权威的某玩家帽子皮肤名（服务端）。
     */
    public static String getServerHatSkinName(Player player) {
        return HatEquipmentManager.getServerHatSkinName(player);
    }
}
