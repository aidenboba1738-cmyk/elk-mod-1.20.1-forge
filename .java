package com.wildlife.elkmod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

@Mod(ElkMod.MOD_ID)
public class ElkMod {
    public static final String MOD_ID = "elkmod";

    // 1. Create Registries for Entities and Items
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

    // 2. Register the Elk Entity
    public static final RegistryObject<EntityType<ElkEntity>> ELK = ENTITY_TYPES.register("elk",
            () -> EntityType.Builder.of(ElkEntity::new, MobCategory.CREATURE)
                    .sized(1.4f, 1.8f) // Hitbox width and height
                    .build(new ResourceLocation(MOD_ID, "elk").toString()));

    // 3. Register the Elk Spawn Egg (Brown base: 0x6E472B, Tan spots: 0xC8A985)
    public static final RegistryObject<Item> ELK_SPAWN_EGG = ITEMS.register("elk_spawn_egg",
            () -> new ForgeSpawnEggItem(ELK, 0x6E472B, 0xC8A985, new Item.Properties()));

    public ElkMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Hook registries into the Mod Event Bus
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);

        // Register attributes and creative tab events
        modEventBus.addListener(this::registerAttributes);
        modEventBus.addListener(this::addCreative);

        MinecraftForge.EVENT_BUS.register(this);
    }

    // Give the Elk health, speed, and attack damage so it doesn't crash on spawn
    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ELK.get(), ElkEntity.createAttributes().build());
    }

    // Put the spawn egg into the Spawn Eggs tab in Creative Mode
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ELK_SPAWN_EGG);
        }
    }

    // --- ELK ENTITY CLASS CONTAINING ALL CUSTOM AI & GENETICS ---
    public static class ElkEntity extends Animal {
        private static final EntityDataAccessor<Float> ANTLER_SIZE = SynchedEntityData.defineId(ElkEntity.class, EntityDataSerializers.FLOAT);
        private static final EntityDataAccessor<Integer> COAT_VARIANT = SynchedEntityData.defineId(ElkEntity.class, EntityDataSerializers.INT);

        public ElkEntity(EntityType<? extends Animal> type, Level level) {
            super(type, level);
        }

        public static AttributeSupplier.Builder createAttributes() {
            return Animal.createLivingAttributes()
                    .add(Attributes.MAX_HEALTH, 25.0D)
                    .add(Attributes.MOVEMENT_SPEED, 0.25D)
                    .add(Attributes.ATTACK_DAMAGE, 4.0D)
                    .add(Attributes.FOLLOW_RANGE, 24.0D);
        }

        @Nullable
        @Override
        public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
            return ELK.get().create(level);
        }

        @Override
        protected void defineSynchedData() {
            super.defineSynchedData();
            this.entityData.define(ANTLER_SIZE, 1.0f);
            this.entityData.define(COAT_VARIANT, 0);
        }

        public float getAntlerSize() { return this.entityData.get(ANTLER_SIZE); }
        public void setAntlerSize(float size) { this.entityData.set(ANTLER_SIZE, size); }
        public int getCoatVariant() { return this.entityData.get(COAT_VARIANT); }
        public void setCoatVariant(int variant) { this.entityData.set(COAT_VARIANT, variant); }

        @Override
        public void spawnChildFromBreeding(ServerLevel level, Animal mate) {
            ElkEntity baby = (ElkEntity) this.getBreedOffspring(level, mate);
            if (baby != null) {
                float inheritedAntlers = ((this.getAntlerSize() + ((ElkEntity) mate).getAntlerSize()) / 2.0f) + (random.nextFloat() * 0.4f - 0.2f);
                baby.setAntlerSize(Math.max(0.5f, Math.min(2.5f, inheritedAntlers)));
                int variant = random.nextFloat() < 0.15f ? random.nextInt(3) : (random.nextBoolean() ? this.getCoatVariant() : ((ElkEntity) mate).getCoatVariant());
                baby.setCoatVariant(variant);
                level.addFreshEntity(baby);
            }
        }

        @Override
        public void die(DamageSource damageSource) {
            super.die(damageSource);
            if (!this.level().isClientSide()) {
                AABB searchBox = this.getBoundingBox().inflate(16.0D, 8.0D, 16.0D);
                List<ElkEntity> nearbyElk = this.level().getEntitiesOfClass(ElkEntity.class, searchBox);
                for (ElkEntity elk : nearbyElk) {
                    if (elk.isAlive()) {
                        LivingEntity threat = damageSource.getEntity() instanceof LivingEntity ? (LivingEntity) damageSource.getEntity() : null;
                        if (threat != null) {
                            elk.getNavigation().moveTo(threat, 1.4D);
                        }
                    }
                }
            }
        }

        @Override
        protected void registerGoals() {
            super.registerGoals();
            this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.5D, true));
            this.goalSelector.addGoal(2, new PanicGoal(this, 1.6D));
            this.goalSelector.addGoal(3, new BullFightGoal(this));
            this.goalSelector.addGoal(4, new ElkLeadHerdGoal(this));
            this.goalSelector.addGoal(5, new ElkHerdGroupGoal(this));
            this.goalSelector.addGoal(6, new ElkSeekWaterGoal(this, 1.1D));
            this.goalSelector.addGoal(7, new ElkGrazeGoal(this));
            this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 1.0D));

            this.targetSelector.addGoal(1, new BullChargeGoal(this));
        }
    }

    // --- CUSTOM AI BEHAVIORS ---

    public static class ElkGrazeGoal extends Goal {
        private final ElkEntity elk;
        private int grazingTime = 0;

        public ElkGrazeGoal(ElkEntity elk) {
            this.elk = elk;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (elk.getRandom().nextInt(500) != 0) return false;
            return elk.level().getBlockState(elk.blockPosition().below()).is(BlockTags.DIRT);
        }

        @Override
        public void start() {
            this.grazingTime = 40;
            elk.getNavigation().stop();
        }

        @Override
        public void tick() {
            grazingTime--;
            if (grazingTime <= 0) {
                BlockPos pos = elk.blockPosition().below();
                if (elk.level().getBlockState(pos).is(Blocks.GRASS_BLOCK)) {
                    elk.level().setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
                    elk.heal(2.0f);
                }
            }
        }
    }

    public static class ElkHerdGroupGoal extends Goal {
        private final ElkEntity elk;
        private int cooldown = 0;

        public ElkHerdGroupGoal(ElkEntity elk) {
            this.elk = elk;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (cooldown > 0) { cooldown--; return false; }
            cooldown = 40 + elk.getRandom().nextInt(40);
            List<ElkEntity> peers = elk.level().getEntitiesOfClass(ElkEntity.class, elk.getBoundingBox().inflate(12.0D, 4.0D, 12.0D));
            return peers.size() >= 2;
        }

        @Override
        public void start() {
            List<ElkEntity> peers = elk.level().getEntitiesOfClass(ElkEntity.class, elk.getBoundingBox().inflate(16.0D, 4.0D, 16.0D));
            if (peers.isEmpty()) return;
            double avgX = 0, avgZ = 0;
            for (ElkEntity peer : peers) { avgX += peer.getX(); avgZ += peer.getZ(); }
            avgX /= peers.size(); avgZ /= peers.size();
            if (elk.distanceToSqr(avgX, elk.getY(), avgZ) > 25.0D) {
                elk.getNavigation().moveTo(avgX, elk.getY(), avgZ, 1.0D);
            }
        }
    }

    public static class ElkLeadHerdGoal extends Goal {
        private final ElkEntity elk;
        private int cooldown = 0;

        public ElkLeadHerdGoal(ElkEntity elk) {
            this.elk = elk;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (elk.getCoatVariant() != 1) return false;
            if (cooldown > 0) { cooldown--; return false; }
            cooldown = 100;
            List<ElkEntity> peers = elk.level().getEntitiesOfClass(ElkEntity.class, elk.getBoundingBox().inflate(16.0D, 4.0D, 16.0D));
            float myAntlers = elk.getAntlerSize();
            for (ElkEntity peer : peers) {
                if (peer.getAntlerSize() > myAntlers) return false;
            }
            return true;
        }

        @Override
        public void start() {
            double forwardX = elk.getX() + (elk.getRandom().nextDouble() - 0.5) * 10.0;
            double forwardZ = elk.getZ() + (elk.getRandom().nextDouble() - 0.5) * 10.0;
            elk.getNavigation().moveTo(forwardX, elk.getY(), forwardZ, 1.1D);
        }
    }

    public static class BullFightGoal extends Goal {
        private final ElkEntity bull;
        private ElkEntity rival;
        private int timer = 0;

        public BullFightGoal(ElkEntity bull) {
            this.bull = bull;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (bull.getCoatVariant() != 1 || bull.getRandom().nextInt(800) != 0) return false;
            List<ElkEntity> peers = bull.level().getEntitiesOfClass(ElkEntity.class, bull.getBoundingBox().inflate(8.0D, 3.0D, 8.0D));
            for (ElkEntity peer : peers) {
                if (peer != bull && peer.getCoatVariant() == 1) { this.rival = peer; return true; }
            }
            return false;
        }

        @Override
        public void start() { this.timer = 100; bull.getNavigation().moveTo(rival, 1.3D); }

        @Override
        public void tick() {
            if (rival == null || !rival.isAlive()) { this.stop(); return; }
            bull.getLookControl().setLookAt(rival, 30.0F, 30.0F);
            if (bull.distanceToSqr(rival) <= 4.0D) {
                bull.getNavigation().stop();
                if (bull.tickCount % 20 == 0) {
                    rival.knockback(0.4F, bull.getX() - rival.getX(), bull.getZ() - rival.getZ());
                    bull.playSound(SoundEvents.ANVIL_LAND, 0.4F, 1.5F);
                }
            } else {
                bull.getNavigation().moveTo(rival, 1.2D);
            }
            timer--;
        }

        @Override
        public boolean canContinueToUse() { return timer > 0 && rival != null && rival.isAlive(); }
        @Override
        public void stop() { this.rival = null; }
    }

    public static class BullChargeGoal extends TargetGoal {
        private final ElkEntity elk;

        public BullChargeGoal(ElkEntity elk) {
            super(elk, true);
            this.elk = elk;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (elk.getCoatVariant() != 1) return false;
            LivingEntity target = this.mob.level().getNearestPlayer(this.mob, 8.0D);
            if (target != null && !target.isCreative()) {
                this.targetMob = target;
                return true;
            }
            return false;
        }

        @Override
        public void start() {
            this.mob.getNavigation().moveTo(this.targetMob, 1.6D);
            this.mob.playSound(SoundEvents.GOAT_SCREAMING, 1.0F, 0.6F);
            super.start();
        }

        @Override
        public void tick() {
            if (this.targetMob != null) {
                this.mob.getLookControl().setLookAt(this.targetMob, 30.0F, 30.0F);
                if (this.mob.distanceToSqr(this.targetMob) <= 6.25D) {
                    this.mob.doHurtTarget(this.targetMob);
                    this.stop();
                }
            }
        }
    }

    public static class ElkSeekWaterGoal extends MoveToBlockGoal {
        private final ElkEntity elk;
        public ElkSeekWaterGoal(ElkEntity elk, double speed) { super(elk, speed, 32); this.elk = elk; }
        @Override
        public boolean canUse() { return elk.getRandom().nextInt(400) == 0 && super.canUse(); }
        @Override
        protected boolean isValidTarget(LevelReader level, BlockPos pos) {
            return level.getBlockState(pos).is(Blocks.WATER) && level.isEmptyBlock(pos.above());
        }
    }
}
