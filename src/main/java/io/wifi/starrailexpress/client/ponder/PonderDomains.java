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

package io.wifi.starrailexpress.client.ponder;

import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * 本模组的六个「领域」及其思索场景适配。
 *
 * <p>领域等级与服务端 {@code ModEffects.DOMAIN_MARK} 的 amplifier 一一对应，
 * 规范见 {@code ModEffects} 的注释：一个领域只占一个等级，0~5 已全部分配。
 * 新增领域只能往后排，禁止复用已有等级，这里的枚举顺序与该等级保持一致。
 *
 * <p>职业教程演示领域技时，用 {@code starrailexpress:role_domain} 步骤传入
 * {@link #id()} 即可把教程场景切换成对应领域（见 {@link PonderSreExtension}）。
 * 客户端没有 {@code ServerLevel}，场景由 {@link PonderDomainScenes} 按服务端配色重建。
 */
public enum PonderDomains {

    /** 愚者开会：塔罗会大厅，暖白石灰岩大厅 + 青铜长桌 + 王座高台。 */
    FOOL_MEETING("fool_meeting", 0),

    /** 咒术师角斗场：灰髓之境，圆形凝灰岩祭场 + 灰玻璃雾墙 + 黑石祭坛。 */
    WARLOCK_ASH_MARROW("warlock_ash_marrow", 1),

    /** 冒险家游记：格罗赛尔游记，旅途营地。 */
    ADVENTURER_TRAVELOGUE("adventurer_travelogue", 2),

    /** 祈月者吞月：苍白月面 + 陨石坑 + 玻璃边墙。 */
    POOLER_MOON("pooler_moon", 3),

    /** 祈月者水中镜：玻璃水缸 + 珊瑚海草 + 中央气泡柱。 */
    POOLER_AQUARIUM("pooler_aquarium", 4),

    /** 轮盘恶魔轮盘决斗：秘密黑室。 */
    DEVIL_BLACK_ROOM("devil_black_room", 5);

    private final String id;
    private final int markLevel;

    PonderDomains(String id, int markLevel) {
        this.id = id;
        this.markLevel = markLevel;
    }

    /** 场景 JSON 里写这个 id。 */
    public String id() {
        return id;
    }

    /** 与服务端 {@code DOMAIN_MARK} 的 amplifier 对应，供教程文案/校验使用。 */
    public int markLevel() {
        return markLevel;
    }

    /** 领域名（1 级 = amplifier 0）。 */
    public String titleKey() {
        return "ponder.starrailexpress.domain." + id;
    }

    public Component displayName() {
        return Component.translatable(titleKey());
    }

    /**
     * 解析 JSON 里写的领域 id，同时接受：
     * {@code pooler_moon}、{@code starrailexpress:pooler_moon}、{@code POOLER_MOON}。
     */
    @Nullable
    public static PonderDomains byId(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim();
        ResourceLocation location = ResourceLocation.tryParse(key);
        if (location != null) {
            key = location.getPath();
        }
        key = key.toLowerCase(Locale.ROOT);
        for (PonderDomains domain : values()) {
            if (domain.id.equals(key) || domain.name().toLowerCase(Locale.ROOT).equals(key)) {
                return domain;
            }
        }
        return null;
    }

    /** 在当前思索场景里把该领域搭出来（会先清掉该区域内已有的方块）。 */
    public void build(SceneBuilder scene, SceneBuildingUtil util, BlockPos center) {
        PonderDomainScenes.build(this, scene, util, center);
    }
}
