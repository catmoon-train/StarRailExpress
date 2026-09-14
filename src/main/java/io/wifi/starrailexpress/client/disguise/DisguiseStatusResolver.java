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

package io.wifi.starrailexpress.client.disguise;

import io.wifi.starrailexpress.client.util.ClientSkinCache;
import io.wifi.starrailexpress.disguise.EntityDisguise;
import io.wifi.starrailexpress.disguise.EntityDisguiseState;
import io.wifi.starrailexpress.morph.MorphAppearance;
import io.wifi.starrailexpress.client.morph.ClientMorphCache;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 「这名玩家现在看起来是什么」——把全部伪装来源汇总成可显示的名字，供 HUD 与瞄准提示复用。
 * <p>
 * 三个来源互不排斥，所以返回的是列表（通常是 0 或 1 条）：
 * <ol>
 * <li><b>实体伪装</b>（{@code EntityDisguise}）：模型被整体替换，最外层。</li>
 * <li><b>职业形态</b>（皮革噶的 / 兔子 / 番茄头 / 幻灵 / 熊猫）：判定走
 * {@link org.agmas.noellesroles.client.RoleDisguiseResolver}，每 tick 一次。</li>
 * <li><b>皮肤变形</b>（{@code MorphApi}）：只换皮肤 / 名牌归属。</li>
 * </ol>
 * 名字一律用组件（原版实体翻译键 / 目标玩家名），所以每个客户端看到的是自己语言的名字。
 */
@Environment(EnvType.CLIENT)
public final class DisguiseStatusResolver {

    private DisguiseStatusResolver() {
    }

    /** 当前生效的全部伪装名字；没有任何伪装时返回空列表。 */
    public static List<Component> describe(Player player) {
        if (player == null) {
            return List.of();
        }
        List<Component> names = new ArrayList<>(3);
        EntityDisguiseState state = ClientEntityDisguiseCache.get(player.getUUID());
        if (!state.isNone()) {
            names.add(EntityDisguise.displayName(state.type()));
        }
        if (player instanceof AbstractClientPlayer clientPlayer) {
            Component form = roleForm(clientPlayer);
            if (form != null) {
                names.add(form);
            }
        }
        Component morph = morph(player.getUUID());
        if (morph != null) {
            names.add(morph);
        }
        return names;
    }

    /** 职业形态。判定顺序与 {@code RoleDisguiseResolver} 的优先级一致；同一名玩家只可能有其中一种。 */
    private static Component roleForm(AbstractClientPlayer player) {
        var flags = org.agmas.noellesroles.client.RoleDisguiseResolver.resolve(player);
        if (flags.panda) {
            return Component.translatable(EntityType.PANDA.getDescriptionId());
        }
        if (flags.pig) {
            return Component.translatable(EntityType.PIG.getDescriptionId());
        }
        if (flags.rabbit) {
            return Component.translatable(EntityType.RABBIT.getDescriptionId());
        }
        if (flags.tomato) {
            return Component.translatable("hud.sre.entitydisguise.form.tomato");
        }
        if (flags.allay) {
            return Component.translatable(EntityType.ALLAY.getDescriptionId());
        }
        return null;
    }

    /** 皮肤变形：变形为玩家时显示「看起来是谁」，贴图变形没有对象可指，用固定文案。 */
    private static Component morph(UUID uuid) {
        MorphAppearance morph = ClientMorphCache.get(uuid);
        if (morph.isPlayer() && morph.targetPlayer() != null) {
            Component name = playerName(morph.targetPlayer());
            return name == null ? Component.translatable("hud.sre.entitydisguise.morph.texture") : name;
        }
        if (morph.isTexture()) {
            return Component.translatable("hud.sre.entitydisguise.morph.texture");
        }
        return null;
    }

    private static Component playerName(UUID uuid) {
        PlayerInfo info = ClientSkinCache.getCachedPlayerInfo(uuid);
        Minecraft client = Minecraft.getInstance();
        if (info == null && client.getConnection() != null) {
            info = client.getConnection().getPlayerInfo(uuid);
        }
        if (info != null && info.getProfile() != null) {
            return Component.literal(info.getProfile().getName());
        }
        return null;
    }
}
