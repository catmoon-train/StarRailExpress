package org.agmas.noellesroles.mixin.roles.thief;

import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SREPlayerShopComponent.class)
public abstract class DelayedCoinDisplayMixin {
    
    @Shadow public int balance;
    private int displayedBalance; 
    private int actualBalance; 
    private boolean hasNegativeCoins; 
    
    public int getActualBalance() {
        return actualBalance;
    }
    
    public int getDisplayedBalance() {
        return displayedBalance;
    }
    
    @Inject(method = "setBalance", at = @At("HEAD"))
    void setBalance(int balance, CallbackInfo ci) {

        actualBalance = balance;
        
        if (hasNegativeCoins && actualBalance >= 0) {
            displayedBalance = actualBalance;
            hasNegativeCoins = false;
        } 
        else if (balance > displayedBalance) {
            displayedBalance = balance;
            hasNegativeCoins = false;
        } 
        else if (balance < displayedBalance) {

            hasNegativeCoins = true;
        }
    }
}