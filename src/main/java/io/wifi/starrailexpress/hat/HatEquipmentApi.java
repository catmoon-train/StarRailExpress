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

import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.client.hat.ClientHatEquipmentCache;
import io.wifi.starrailexpress.event.OnResolveDisplayedSkinOwner;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role_data.killer.MorphlingRoleData;

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
            var morphComponent = RoleData.getNullable(MorphlingRoleData.class, player);
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
     * 判断玩家当前是否处于"固定皮肤替换"的隐藏状态（客户端）。
     * <p>
     * 这些状态下皮肤管线把玩家渲染为固定的伪装/角色皮肤
     * （不属于任何真实玩家），与皮肤表现对齐，帽子一并隐藏：
     * <ul>
     * <li>疯魔模式（psycho）：全员显示疯魔皮肤（{@code PLAYER_PSYCHO_CACHE}）</li>
     * <li>难民惩罚：旁观者视角全员显示默认皮肤（{@code getLooseEndPenalty}）</li>
     * <li>亡命徒 / 超级亡命徒：统一显示 th_sariel 皮肤</li>
     * <li>角色固定皮肤（{@code SRERole#getNormalSkin} 非空，如 jester、remilia 等）</li>
     * </ul>
     */
    @Environment(EnvType.CLIENT)
    private static boolean isConcealedByFixedSkin(AbstractClientPlayer player) {
        // 大堂中皮肤不替换，帽子正常显示
        if (io.wifi.starrailexpress.SRE.isLobby || io.wifi.starrailexpress.client.SREClient.isInLobby) {
            return false;
        }
        // 疯魔模式
        if (io.wifi.starrailexpress.client.SREClient.PLAYER_PSYCHO_CACHE
                .getOrDefault(player.getUUID(), false)) {
            return true;
        }
        // 难民惩罚（旁观者全员默认皮肤）
        if (io.wifi.starrailexpress.client.SREClient.getLooseEndPenalty()) {
            return true;
        }
        // 亡命徒 / 超级亡命徒（统一皮肤）
        if (org.agmas.noellesroles.utils.RoleUtils.isPlayerTheJob(player,
                io.wifi.starrailexpress.api.TMMRoles.LOOSE_END)
                || org.agmas.noellesroles.utils.RoleUtils.isPlayerTheJob(player,
                        io.wifi.starrailexpress.game.roles.SpecialGameModeRoles.SUPER_LOOSE_END)) {
            return true;
        }
        // 角色固定皮肤（与 PlayerEntityRendererMixin 的皮肤判定一致）
        if (io.wifi.starrailexpress.client.SREClient.gameComponent != null) {
            io.wifi.starrailexpress.api.SRERole role = io.wifi.starrailexpress.client.SREClient.gameComponent
                    .getRole(player.getUUID());
            if (role != null) {
                boolean slim = player.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
                if (role.getNormalSkin(player, slim) != null) {
                    return true;
                }
            }
        }
        return false;
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
        // 客户端配置：不显示所有人的帽子
        SREClientConfig config = SREClientConfig.instance();
        if (config.hideAllHats) {
            return "default";
        }
        // 客户端配置：只显示自己的帽子
        if (config.showOwnHatOnly && !player.getUUID().equals(Minecraft.getInstance().player.getUUID())) {
            return "default";
        }
        // DISGUISE 伪装效果（含渡鸦的伪装）下隐藏帽子
        if (player.hasEffect(org.agmas.noellesroles.init.ModEffects.DISGUISE)) {
            return "default";
        }
        // 固定皮肤替换状态（疯魔模式、亡命徒统一皮肤、难民旁观者默认皮肤、角色固定皮肤）：
        // 显示的既不是本人皮肤、也不属于任何真实玩家，隐藏帽子避免暴露身份
        if (isConcealedByFixedSkin(player)) {
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
