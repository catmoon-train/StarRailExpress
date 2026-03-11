package org.agmas.noellesroles.roles.sheriff;


public class SheriffPlayerComponent {
//        implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
//    public static final ComponentKey<SheriffPlayerComponent> KEY = ComponentRegistry.getOrCreate(
//            org.agmas.noellesroles.Noellesroles.id("sheriff"),
//            SheriffPlayerComponent.class
//    );
//
//    private final PlayerEntity player;
//    public int gunDurability = 3; // 初始耐久度为3
//    public int baseGunCooldown = 20; // 基础冷却时间（ticks）
//    public int additionalCooldownPerShot = 5; // 每次空射击额外增加的冷却时间
//
//    public SheriffPlayerComponent(Player player) {
//        this.player = player;
//    }
//
//    public void sync() {
//        KEY.sync(this.player);
//    }
//
//    public void reset() {
//        this.gunDurability = 3;
//        this.sync();
//    }
//
//    // 使用枪支，减少耐久度
//    public void useGun() {
//        if (this.gunDurability > 0) {
//            this.gunDurability--;
//            this.sync();
//        }
//    }
//
//    // 维护枪支，恢复耐久度和重置冷却时间
//    public void repairGun() {
//        this.gunDurability = 3;
//        this.sync();
//    }
//
//    // 获取当前总冷却时间
//    public int getCurrentCooldown() {
//        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(player);
//        return this.baseGunCooldown + (ability.cooldown * this.additionalCooldownPerShot);
//    }
//
//    @Override
//    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
//        if (tag.contains("GunDurability")) {
//            this.gunDurability = tag.getInt("GunDurability");
//        }
//    }
//
//    @Override
//    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
//        tag.putInt("GunDurability", this.gunDurability);
//    }
//
//    @Override
//    public void clientTick() {
//    }
//
//    @Override
//    public void serverTick() {
//    }
}