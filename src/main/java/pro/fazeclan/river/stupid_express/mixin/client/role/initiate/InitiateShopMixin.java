package pro.fazeclan.river.stupid_express.mixin.client.role.initiate;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedHandledScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.fazeclan.river.stupid_express.constants.SERoles;

@Mixin(LimitedInventoryScreen.class)
public abstract class InitiateShopMixin extends LimitedHandledScreen<InventoryMenu> {

    @Shadow
    @Final
    public LocalPlayer player;

    public InitiateShopMixin(InventoryMenu handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void initiateShopEntries(CallbackInfo ci) {
        var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorldComponent.isRole(player, SERoles.INITIATE)) {
            var screen = (LimitedInventoryScreen) (Object) this;
            var entries = SERoles.INITIATE_SHOP;
            int apart = 36;
            int x = screen.width / 2 - entries.size() * apart / 2 + 9;
            int shouldBeY = (screen.height - 32) / 2;
            int y = shouldBeY - 46;

            for (int i = 0; i < entries.size(); i++) {
                addRenderableWidget(new LimitedInventoryScreen.StoreItemWidget(
                        screen,
                        x + apart * i,
                        y,
                        entries.get(i),
                        i
                ));
            }
        }
    }

}
