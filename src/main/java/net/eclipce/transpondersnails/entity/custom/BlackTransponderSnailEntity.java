package net.eclipce.transpondersnails.entity.custom;

import net.eclipce.transpondersnails.entity.ModEntities;
import net.eclipce.transpondersnails.item.BlackTransponderSnailItem;
import net.eclipce.transpondersnails.item.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

/**
 * Black Transponder Snail Entity - Larger version of Baby Black Transponder Snail
 * A larger interactive entity that can be picked up and placed, with dyeable shell
 */
public class BlackTransponderSnailEntity extends Animal {

    // Entity data accessors
    private static final EntityDataAccessor<Boolean> IS_ACTIVE =
            SynchedEntityData.defineId(BlackTransponderSnailEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> SHELL_COLOR =
            SynchedEntityData.defineId(BlackTransponderSnailEntity.class, EntityDataSerializers.INT);

    // Scale factor compared to baby snail (Baby = 1.0, this = larger)
    public static final float ENTITY_SCALE = 1.15f;
    
    // Base dimensions (before scaling) - same as baby
    private static final float BASE_WIDTH = 0.16f;
    private static final float BASE_HEIGHT = 0.12f;

    public final AnimationState idleAnimationState = new AnimationState();
    private int idleAnimationTimeout = 0;

    public BlackTransponderSnailEntity(EntityType<? extends Animal> type, Level level) {
        super(type, level);
    }

    @Override
    public float getEyeHeight(Pose pose) {
        return 0.3F * ENTITY_SCALE; // Scaled eye height
    }

    @Override
    public float getPickRadius() {
        return 0.4F * ENTITY_SCALE; // Scaled interaction radius
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(IS_ACTIVE, false);
        this.entityData.define(SHELL_COLOR, 0); // Default white
    }

    /**
     * Generate random shell color when spawned naturally
     */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData,
                                        @Nullable CompoundTag nbt) {
        // Only generate random color on server
        if (!level.isClientSide()) {
            int shellColor = generateRandomShellColor();
            this.setShellColor(shellColor);
            System.out.println("SERVER: Black Transponder Snail spawned with shell color: " +
                    DyeColor.byId(shellColor).getName());
        }
        return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData, nbt);
    }

    /**
     * Generate random shell color (DyeColor ID 0-15)
     */
    private int generateRandomShellColor() {
        return this.random.nextInt(16); // DyeColor has 16 colors (IDs 0-15)
    }

    /**
     * Get the scale factor for this entity
     */
    public float getScale() {
        return ENTITY_SCALE;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide()) {
            setupAnimationStates();
        }
    }

    private void setupAnimationStates() {
        if (this.idleAnimationTimeout <= 0) {
            this.idleAnimationTimeout = this.random.nextInt(40) + 80;
            this.idleAnimationState.start(this.tickCount);
        } else {
            --this.idleAnimationTimeout;
        }
    }

    @Override
    protected void updateWalkAnimation(float partialTick) {
        float f;
        if (this.getPose() == Pose.STANDING) {
            f = Math.min(partialTick * 6F, 1f);
        } else {
            f = 0;
        }
        this.walkAnimation.update(f, 0.2f);
    }

    @Override
    protected void registerGoals() {
        // No AI goals - stationary snail
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 3.0D) // 2 hearts (more than baby)
                .add(Attributes.FOLLOW_RANGE, 16D)
                .add(Attributes.MOVEMENT_SPEED, 0.05D); // Even slower than baby
    }

    // =================== WATER BREATHING ===================

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    // =================== BREEDING ===================

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return null; // Black Transponder Snails don't breed
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    // =================== SOUNDS ===================

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.AXOLOTL_HURT;
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return SoundEvents.AXOLOTL_DEATH;
    }

    // =================== DEATH HANDLING ===================

    @Override
    public void die(DamageSource damageSource) {
        // Spawn death particles
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            for (int i = 0; i < 30; i++) { // More particles than baby
                double offsetX = (this.random.nextDouble() - 0.5D) * 0.8D;
                double offsetY = this.random.nextDouble() * 0.8D;
                double offsetZ = (this.random.nextDouble() - 0.5D) * 0.8D;

                serverLevel.sendParticles(
                        net.minecraft.core.particles.ParticleTypes.POOF,
                        this.getX() + offsetX,
                        this.getY() + offsetY,
                        this.getZ() + offsetZ,
                        1, 0.0D, 0.0D, 0.0D, 0.0D
                );
            }
        }

        super.die(damageSource);
    }

    @Override
    protected void dropAllDeathLoot(DamageSource damageSource) {
        // Don't drop anything on death - pickup gives the item instead
    }

    // =================== INTERACTION (Pickup & Dyeing) ===================

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);

        // Check for pickup (empty hand)
        if (itemStack.isEmpty()) {
            if (!this.level().isClientSide) {
                System.out.println("=== BLACK TRANSPONDER SNAIL PICKUP DEBUG ===");
                System.out.println("Entity Shell Color: " + this.getShellColor() +
                        " (" + DyeColor.byId(this.getShellColor()).getName() + ")");

                // Create item with this entity's shell color
                ItemStack snailItem = BlackTransponderSnailItem.createFromEntity(this);

                // Give item to player or drop it
                if (!player.getInventory().add(snailItem)) {
                    player.drop(snailItem, false);
                }

                // Play pickup sound
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3F, 0.8F);

                System.out.println("Entity removed from world");
                System.out.println("=============================================");

                // Remove entity
                this.discard();
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        // Check for dyeing
        if (itemStack.getItem() instanceof DyeItem dyeItem) {
            if (!this.level().isClientSide) {
                DyeColor dyeColor = dyeItem.getDyeColor();
                if (dyeColor.getId() != this.getShellColor()) {
                    System.out.println("Dyeing Black Transponder Snail from " +
                            DyeColor.byId(this.getShellColor()).getName() +
                            " to " + dyeColor.getName());
                    this.setShellColor(dyeColor.getId());
                    if (!player.isCreative()) {
                        itemStack.shrink(1);
                    }
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        return super.mobInteract(player, hand);
    }

    // =================== ENTITY PROPERTIES ===================

    public boolean isActive() {
        return this.entityData.get(IS_ACTIVE);
    }

    public void setActive(boolean active) {
        this.entityData.set(IS_ACTIVE, active);
    }

    /**
     * Get the shell color (DyeColor ID 0-15)
     */
    public int getShellColor() {
        int color = this.entityData.get(SHELL_COLOR);
        return Math.max(0, Math.min(15, color)); // Clamp to valid range
    }

    /**
     * Set the shell color (DyeColor ID 0-15)
     */
    public void setShellColor(int color) {
        int clampedColor = Math.max(0, Math.min(15, color));
        this.entityData.set(SHELL_COLOR, clampedColor);
    }

    @Override
    public boolean fireImmune() {
        return false;
    }

    // =================== NBT SAVE/LOAD ===================

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putBoolean("IsActive", this.isActive());
        compound.putInt("ShellColor", this.getShellColor());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("IsActive")) {
            this.setActive(compound.getBoolean("IsActive"));
        }
        if (compound.contains("ShellColor")) {
            this.setShellColor(compound.getInt("ShellColor"));
        }
    }
}
