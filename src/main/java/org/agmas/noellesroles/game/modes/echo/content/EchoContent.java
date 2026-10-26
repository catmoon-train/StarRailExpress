package org.agmas.noellesroles.game.modes.echo.content;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/** 模式实体工具只注册一次；虚拟卡牌不是原版物品栈。 */
public final class EchoContent {
    public static final Item CUTTER=Registry.register(BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath("noellesroles","echo_cutter"),new EchoCutterItem(new Item.Properties().stacksTo(1)));
    public static final Item CARD_PROXY=Registry.register(BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath("noellesroles","echo_card_proxy"),new EchoCardProxyItem(new Item.Properties().stacksTo(1)));
    public static final Item SKILL_TOKEN=Registry.register(BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath("noellesroles","echo_skill_token"),new EchoSkillItem(new Item.Properties().stacksTo(1)));
    public static final Item RESOURCE=Registry.register(BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath("noellesroles","echo_resource"),new EchoResourceItem(new Item.Properties().stacksTo(2)));
    /** 工具和物资展示都不可进入原版移动、复制或丢弃通道。 */
    public static boolean readOnly(net.minecraft.world.item.ItemStack stack) { return tool(stack) || stack.is(RESOURCE); }
    public static boolean tool(net.minecraft.world.item.ItemStack stack) { return stack.is(CUTTER) || stack.is(CARD_PROXY) || stack.is(SKILL_TOKEN); }
    /** 地图编辑工具：只给管理员，手持时显示会话 HUD，右键打开编辑界面。 */
    public static final Item EDITOR=Registry.register(BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath("noellesroles","echo_editor"),new EchoEditorItem(new Item.Properties().stacksTo(1)));
    private EchoContent() { }
    public static void register() { EchoMachines.register(); EchoTerrainBlocks.register(); }
}
