package org.agmas.noellesroles.game.command_block;

import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Steve：命令方块局里必然出现的警长。铁镐挖任务点换材料，材料换商店。
 */
public class SteveRole extends NormalRole {
    public SteveRole(ResourceLocation id, int color, boolean innocent, boolean killer, SRERole.MoodType mood, int sprint,
            boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
        setRoleData(SteveRoleData::new);
    }

    public static void registerSkill() {
        // cooldownTicks 必须与 SteveBuilder.COOLDOWN 一致：
        // 统一技能系统靠它在 markSkillUsed 时把冷却写进 SkillState 并同步客户端，
        // 默认技能 HUD（UnifiedSkillHud）只读 SkillState.cooldown，
        // 缺了它 HUD 永远显示“就绪”，看不到 45 秒冷却。
        RoleSkill.register(ModRoles.STEVE, RoleSkill.skill(
                SRE.id("steve_build"),
                "skill.noellesroles.steve.build",
                context -> SteveBuilder.use(context.player()))
                .cooldownTicks(SteveBuilder.COOLDOWN)
                .announceToSelf().showOnHud(true).build());
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        return List.of(ModItems.STEVE_PICKAXE.getDefaultInstance());
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> shop = new ArrayList<>();
        shop.add(SteveMiner.offer(ModItems.STEVE_BOAT.getDefaultInstance(), Items.OAK_PLANKS, 5));
        shop.add(SteveMiner.offer(ModItems.STEVE_DIAMOND_PICKAXE.getDefaultInstance(), Items.DIAMOND, 3));
        shop.add(SteveMiner.offer(ModItems.DREAM_DIAMOND_SWORD.getDefaultInstance(), Items.DIAMOND, 2));
        shop.add(SteveMiner.offer(ModItems.STEVE_MINECART.getDefaultInstance(), Items.IRON_INGOT, 5));
        shop.add(SteveMiner.offer(ModItems.STEVE_GOLDEN_APPLE.getDefaultInstance(), Items.GOLD_INGOT, 8));
        shop.add(SteveMiner.offer(ModItems.STEVE_PISTON.getDefaultInstance(), Items.IRON_INGOT, 4));
        return shop;
    }
}
