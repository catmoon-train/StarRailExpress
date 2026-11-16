package io.wifi.srepokemoncompat.sweep;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** 执行生产策略和回调字节码，替换环境类型以避免启动 Minecraft 注册表。 */
public class AotakeProtectionTest {
    public static class Entity { boolean named; Boolean pokemon; public boolean hasCustomName() { return named; } }
    public static class Living extends Entity { }
    public static class Player extends Living { }
    public static class Boat extends Entity { }
    public static class ChestBoat extends Boat { }
    public static class Minecart extends Entity { }
    public static class StorageMinecart extends Minecart { }
    public static class Dragon extends Living { }
    public static class DragonPart extends Entity { public Dragon parentMob = new Dragon(); }
    public static class Level { }
    public interface Ownable {
        UUID getOwnerUUID();
        default Living getOwner() { return null; }
    }
    public interface Traceable { Entity getOwner(); }
    public static class Pet extends Living implements Ownable {
        UUID ownerId;
        @Override public UUID getOwnerUUID() { return ownerId; }
    }
    public static class CustomPet extends Pet {
        Living owner;
        @Override public Living getOwner() { return owner; }
    }
    public static class TypedPet extends Pet {
        @Override public Player getOwner() { return null; }
    }
    public static class Follower extends Living implements Traceable {
        Entity owner;
        @Override public Entity getOwner() { return owner; }
    }
    public static class NativePlayerPet extends Living {
        Player owner;
        UUID ownerId;
        public Player getOwner() { return owner; }
        public java.util.Optional<UUID> getOwnerUuid() { return java.util.Optional.ofNullable(ownerId); }
    }
    public static class NativeGenericPet extends Living {
        Living owner;
        public Living getOwnerPlayer() { return owner; }
        public UUID getOwnerUUID() { return UUID.randomUUID(); }
    }
    public static class Query {
        public static Boolean protectedState(Object entity) { return ((Entity) entity).pokemon; }
    }
    private ClassLoader loader;
    private Method protect;
    private static final String ROOT = "io/wifi/srepokemoncompat/";

    @BeforeEach void isolate() throws Exception {
        var mappings = new HashMap<String, String>();
        for (var pair : Map.ofEntries(
                Map.entry("net/minecraft/world/entity/Entity", Entity.class),
                Map.entry("net/minecraft/world/entity/LivingEntity", Living.class),
                Map.entry("net/minecraft/world/entity/player/Player", Player.class),
                Map.entry("net/minecraft/world/entity/OwnableEntity", Ownable.class),
                Map.entry("net/minecraft/world/entity/TraceableEntity", Traceable.class),
                Map.entry("net/minecraft/world/entity/vehicle/Boat", Boat.class),
                Map.entry("net/minecraft/world/entity/vehicle/AbstractMinecart", Minecart.class),
                Map.entry("net/minecraft/world/entity/boss/EnderDragonPart", DragonPart.class),
                Map.entry("net/minecraft/world/entity/boss/enderdragon/EnderDragon", Dragon.class),
                Map.entry("net/minecraft/server/level/ServerLevel", Level.class),
                Map.entry(ROOT + "sweep/PokemonSweepQuery", Query.class)).entrySet()) {
            mappings.put(pair.getKey(), Type.getInternalName(pair.getValue()));
        }
        String[] subjects = {"sweep/SweepProtection", "sweep/SweepProtection$1", "sweep/CustomPlayerOwnerQuery",
                "sweep/CustomPlayerOwnerQuery$1", "sweep/CustomPlayerOwnerQuery$Access", "mixin/sweep/AotakeCandidateMixin",
                "mixin/sweep/AotakeRecyclingMixin", "mixin/sweep/AotakeRemovalMixin"};
        for (String subject : subjects) mappings.put(ROOT + subject, ROOT + "sweep/probe/" + subject.substring(subject.lastIndexOf('/') + 1));
        var definitions = new HashMap<String, byte[]>();
        for (String subject : subjects) {
            try (var in = getClass().getResourceAsStream("/" + ROOT + subject + ".class")) {
                assertNotNull(in);
                var writer = new ClassWriter(0);
                new ClassReader(in).accept(new ClassRemapper(writer, new SimpleRemapper(mappings)), 0);
                definitions.put(mappings.get(ROOT + subject).replace('/', '.'), writer.toByteArray());
            }
        }
        loader = new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] bytes = definitions.get(name);
                if (bytes == null) throw new ClassNotFoundException(name);
                return defineClass(name, bytes, 0, bytes.length);
            }
        };
        protect = loader.loadClass("io.wifi.srepokemoncompat.sweep.probe.SweepProtection").getMethod("isProtected", Entity.class);
    }
    private boolean protectedEntity(Entity entity) throws Exception { return (boolean) protect.invoke(null, entity); }
    private CallbackInfoReturnable<Boolean> hook(String type, String method, Class<?>[] parameters, Object... args) throws Exception {
        Class<?> cls = loader.loadClass("io.wifi.srepokemoncompat.sweep.probe." + type);
        var handler = cls.getDeclaredMethod(method, parameters); handler.setAccessible(true);
        var callback = new CallbackInfoReturnable<Boolean>(method, true);
        Object[] all = java.util.Arrays.copyOf(args, args.length + 1); all[args.length] = callback;
        handler.invoke(java.lang.reflect.Modifier.isStatic(handler.getModifiers()) ? null : cls.getConstructor().newInstance(), all);
        return callback;
    }

    @Test void boatsAndAllMinecartSubclassesAreProtected() throws Exception {
        for (Entity entity : new Entity[]{new Boat(), new ChestBoat(), new Minecart(), new StorageMinecart()}) assertTrue(protectedEntity(entity));
    }
    @Test void onlyCustomNamedLivingEntitiesAreProtectedByName() throws Exception {
        var animal = new Living(); assertFalse(protectedEntity(animal));
        animal.named = true; assertTrue(protectedEntity(animal));
        var item = new Entity(); item.named = true; assertFalse(protectedEntity(item));
        assertFalse(protectedEntity(null));
    }
    @Test void originalPlayerUuidLookupProtectsOfflinePets() throws Exception {
        var pet = new Pet(); assertFalse(protectedEntity(pet));
        pet.ownerId = UUID.randomUUID(); assertTrue(protectedEntity(pet));
    }
    @Test void customOwnerMustActuallyBePlayerAndNpcUuidIsNotEnough() throws Exception {
        var pet = new CustomPet(); pet.ownerId = UUID.randomUUID();
        pet.owner = new Player(); assertTrue(protectedEntity(pet));
        pet.owner = new Living(); assertFalse(protectedEntity(pet));
        pet.owner = null; assertFalse(protectedEntity(pet));
    }
    @Test void traceableLivingFollowersWithPlayerOwnersAreProtected() throws Exception {
        var pet = new Follower(); pet.owner = new Player(); assertTrue(protectedEntity(pet));
        pet.owner = new Living(); assertFalse(protectedEntity(pet));
        pet.owner = null; assertFalse(protectedEntity(pet));
    }
    @Test void nativePublicPlayerOwnerApiAlsoProtectsOfflineUuid() throws Exception {
        var pet = new NativePlayerPet();
        assertFalse(protectedEntity(pet));
        pet.owner = new Player(); assertTrue(protectedEntity(pet));
        var ownable = new TypedPet(); ownable.ownerId = UUID.randomUUID();
        assertTrue(protectedEntity(ownable));
        pet.owner = null; pet.ownerId = UUID.randomUUID(); assertTrue(protectedEntity(pet));
    }
    @Test void nativeGenericOwnerDoesNotTreatAnyUuidAsPlayer() throws Exception {
        var pet = new NativeGenericPet();
        assertFalse(protectedEntity(pet));
        pet.owner = new Player(); assertTrue(protectedEntity(pet));
        pet.owner = new Living(); assertFalse(protectedEntity(pet));
    }
    @Test void pokemonOwnershipAnswerTakesPrecedenceOverGenericUuid() throws Exception {
        var pokemon = new Pet(); pokemon.ownerId = UUID.randomUUID();
        pokemon.pokemon = false; assertFalse(protectedEntity(pokemon));
        pokemon.pokemon = true; assertTrue(protectedEntity(pokemon));
        pokemon.pokemon = false; pokemon.named = true; assertTrue(protectedEntity(pokemon));
    }
    @Test void multipartCanonicalIdentityPreservesNamedDragon() throws Exception {
        var part = new DragonPart(); assertFalse(protectedEntity(part));
        part.parentMob.named = true; assertTrue(protectedEntity(part));
    }
    @Test void candidateAndRecycleHooksRefuseProtectionAndOtherwiseContinue() throws Exception {
        var entity = new Living();
        for (boolean named : new boolean[]{false, true, false}) {
            entity.named = named;
            var candidate = hook("AotakeCandidateMixin", "sre$protectCandidate",
                    new Class<?>[]{Entity.class, CallbackInfoReturnable.class}, entity);
            var recycle = hook("AotakeRecyclingMixin", "sre$protectRecycling",
                    new Class<?>[]{Object.class, CallbackInfoReturnable.class}, entity);
            assertEquals(named, candidate.isCancelled()); assertEquals(named, recycle.isCancelled());
            if (named) { assertFalse(candidate.getReturnValue()); assertFalse(recycle.getReturnValue()); }
        }
    }
    @Test void schedulingAndPendingRemovalAreRecheckedAgainstCurrentState() throws Exception {
        var entity = new Living();
        for (boolean battle : new boolean[]{false, true, false}) {
            entity.pokemon = battle;
            var scheduled = hook("AotakeRemovalMixin", "sre$protectSchedule",
                    new Class<?>[]{Entity.class, boolean.class, CallbackInfoReturnable.class}, entity, true);
            var pending = hook("AotakeRemovalMixin", "sre$protectPending",
                    new Class<?>[]{Level.class, Entity.class, Boolean.class, CallbackInfoReturnable.class}, new Level(), entity, false);
            assertEquals(battle, scheduled.isCancelled()); assertEquals(battle, pending.isCancelled());
            if (battle) { assertFalse(scheduled.getReturnValue()); assertTrue(pending.getReturnValue()); }
        }
    }
}
