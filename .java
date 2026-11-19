package com.wildlife.elkmod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
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

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

    public static final RegistryObject<EntityType<ElkEntity>> ELK = ENTITY_TYPES.register("elk",
            () -> EntityType.Builder.of(ElkEntity::new, MobCategory.CREATURE)
                    .sized(1.4f, 1.8f)
                    .build(new ResourceLocation(MOD_ID, "elk").toString()));

    public static final RegistryObject<Item> ELK_SPAWN_EGG = ITEMS.register("elk_spawn_egg",
            () -> new ForgeSpawnEggItem(ELK, 0x6E472B, 0xC8A985, new Item.Properties()));

    public ElkMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);

        modEventBus.addListener(this::registerAttributes);
        modEventBus.addListener(this::addCreative);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ELK.get(), ElkEntity.createAttributes().build());
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ELK_SPAWN_EGG);
        }
    }

    // --- CLIENT-SIDE RENDERER & MODEL REGISTRATION ---
    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(ElkModel.LAYER_LOCATION, ElkModel::createBodyLayer);
        }

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ELK.get(), ElkRenderer::new);
        }
    }

    // --- BASIC 3D MODEL FOR THE ELK ---
    public static class ElkModel extends EntityModel<ElkEntity> {
        public static final ModelLayerLocation LAYER_LOCATION =
                new ModelLayerLocation(new ResourceLocation(MOD_ID, "elk"), "main");

        private final ModelPart body;
        private final ModelPart head;
        private final ModelPart leftAntler;
        private final ModelPart rightAntler;
        private final ModelPart leg1;
        private final ModelPart leg2;
        private final ModelPart leg3;
        private final ModelPart leg4;

        public ElkModel(ModelPart root) {
            this.body = root.getChild("body");
            this.head = root.getChild("head");
            this.leftAntler = this.head.getChild("left_antler");
            this.rightAntler = this.head.getChild("right_antler");
            this.leg1 = root.getChild("leg1");
            this.leg2 = root.getChild("leg2");
            this.leg3 = root.getChild("leg3");
            this.leg4 = root.getChild("leg4");
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition meshdefinition = new MeshDefinition();
            PartDefinition partdefinition = meshdefinition.getRoot();

            // Torso
            partdefinition.addOrReplaceChild("body", CubeListBuilder.create()
                    .texOffs(0, 0).addBox(-6.0F, -10.0F, -10.0F, 12.0F, 10.0F, 20.0F),
                    PartPose.offset(0.0F, 12.0F, 0.0F));

            // Head & Neck
            PartDefinition head = partdefinition.addOrReplaceChild("head", CubeListBuilder.create()
                    .texOffs(0, 30).addBox(-4.0F, -12.0F, -6.0F, 8.0F, 12.0F, 8.0F)
                    .texOffs(32, 30).addBox(-3.0F, -10.0F, -11.0F, 6.0F, 5.0F, 5.0F),
                    PartPose.offset(0.0F, 6.0F, -9.0F));

            // Antlers (attached to head so they move with it)
            head.addOrReplaceChild("left_antler", CubeListBuilder.create()
                    .texOffs(0, 0).addBox(2.0F, -22.0F, -2.0F, 2.0F, 10.0F, 2.0F)
                    .texOffs(0, 0).addBox(2.0F, -20.0F, -6.0F, 2.0F, 2.0F, 6.0F),
                    PartPose.ZERO);

            head.addOrReplaceChild("right_antler", CubeListBuilder.create()
                    .texOffs(0, 0).addBox(-4.0F, -22.0F, -2.0F, 2.0F, 10.0F, 2.0F)
                    .texOffs(0, 0).addBox(-4.0F, -20.0F, -6.0F, 2.0F, 2.0F, 6.0F),
                    PartPose.ZERO);

            // 4 Legs
            CubeListBuilder legBox = CubeListBuilder.create().texOffs(44, 0).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F);
            partdefinition.addOrReplaceChild("leg1", legBox, PartPose.offset(-3.5F, 12.0F, 7.0F));
            partdefinition.addOrReplaceChild("leg2", legBox, PartPose.offset(3.5F, 12.0F, 7.0F));
            partdefinition.addOrReplaceChild("leg3", legBox, PartPose.offset(-3.5F, 12.0F, -7.0F));
            partdefinition.addOrReplaceChild("leg4", legBox, PartPose.offset(3.5F, 12.0F, -7.0F));

            return LayerDefinition.create(meshdefinition, 64, 64);
        }

        @Override
        public void setupAnim(ElkEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            // Head rotation
            this.head.xRot = headPitch * ((float) Math.PI / 180F);
            this.head.yRot = netHeadYaw * ((float) Math.PI / 180F);

            // Walking leg swing
            this.leg1.xRot = Mth.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
            this.leg2.xRot = Mth.cos(limbSwing * 0.6662F + (float) Math.PI) * 1.4F * limbSwingAmount;
            this.leg3.xRot = Mth.cos(limbSwing * 0.6662F + (float) Math.PI) * 1.4F * limbSwingAmount;
            this.leg4.xRot = Mth.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;

            // Scale antlers dynamically based on your ANTLER_SIZE genetics!
            float antlerScale = entity.getAntlerSize();
            this.leftAntler.xScale = antlerScale;
            this.leftAntler.yScale = antlerScale;
            this.leftAntler.zScale = antlerScale;
            this.rightAntler.xScale = antlerScale;
            this.rightAntler.yScale = antlerScale;
            this.rightAntler.zScale = antlerScale;
        }

        @Override
        public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
            body.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
            head.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
            leg1.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
            leg2.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
            leg3.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
            leg4.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        }
    }

    // --- ENTITY RENDERER ---
    public static class ElkRenderer extends MobRenderer<ElkEntity, ElkModel> {
        private static final ResourceLocation TEXTURE = new ResourceLocation(MOD_ID, "textures/entity/elk.png");

        public ElkRenderer(EntityRendererProvider.Context context) {
            super(context, new ElkModel(context.bakeLayer(ElkModel.LAYER_LOCATION)), 0.85F);
        }

        @Override
        public ResourceLocation getTextureLocation(ElkEntity entity) {
            return TEXTURE;
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
