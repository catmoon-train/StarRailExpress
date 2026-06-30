package org.agmas.noellesroles.game.roles.killer.nostalgist;

import io.wifi.starrailexpress.api.ExtraEffectRole;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 怀旧者（里世界·杀手阵营）。
 *
 * <p>当场上存在一名以上杀手时，怀旧者处于「里世界」：视角灰白，对所有阵营隐身、
 * 奔跑无声无粒子、无法被看见/听见/攻击；身处里世界时无法击杀任何人，且无法说话（文字/语音）、
 * 无法使用物品、手持物品不显示，只能潜行与侦察。主动按技能键退出里世界需经过约 1.5 秒前摇
 * （伴随音效与粒子），前摇结束才现身。当场上仅剩怀旧者一名杀手时，里世界自动崩塌，怀旧者现身为
 * 普通杀手（见 {@link NostalgistPlayerComponent}）。
 *
 * <p>商店为通用杀手商店（{@link ShopContent#defaultKnifeEntries}）。
 */
public class NostalgistRole extends ExtraEffectRole {

    public NostalgistRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        // 通用杀手商店（与默认刀手商店一致）
        return ShopContent.defaultKnifeEntries;
    }

    @Override
    public InteractionResult onPickUpItem(Player player, ItemStack item) {
        // 处于里世界时无法捡起任何物品
        var comp = NostalgistPlayerComponent.KEY.maybeGet(player).orElse(null);
        if (comp != null && comp.isActiveBackWorld()) {
            return InteractionResult.FAIL;
        }
        return super.onPickUpItem(player, item);
    }
}
