package net.eclipce.transpondersnails.entity.custom;

import net.eclipce.transpondersnails.entity.ModEntities;
import net.eclipce.transpondersnails.item.BabyBlackTransponderSnailItem;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

/**
 * Baby Black Transponder Snail Entity - Animal Version
 * A small interactive entity that can be picked up and placed
 */
public class BabyBlackTransponderSnailEntity extends Animal {

    // Entity data for future expansion
    private static final EntityDataAccessor<Boolean> IS_ACTIVE =
            SynchedEntityData.defineId(BabyBlackTransponderSnailEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> SHELL_COLOR =
            SynchedEntityData.defineId(BabyBlackTransponderSnailEntity.class, EntityDataSerializers.INT);

    public final AnimationState idleAnimationState = new AnimationState();
    private int idleAnimationTimeout = 0;

    public BabyBlackTransponderSnailEntity(EntityType<? extends Animal> type, Level level) {
        super(type, level);
    }

    @Override
    public float getEyeHeight(Pose pose) {
        return 0.12F; // Eye height WITHIN the entity
    }

    @Override
    public float getPickRadius() {
        return 0.16F; // Interaction radius
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
            System.out.println("SERVER: Baby Black Transponder Snail spawned with shell color: " +
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

    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 1.0D) // 1/2 heart (kept from your version)
                .add(Attributes.FOLLOW_RANGE, 16D)
                .add(Attributes.MOVEMENT_SPEED, 0.08D); // Slow baby snail
    }

    // =================== WATER BREATHING ===================

    @Override
    public boolean canBreatheUnderwater() {
        return true; // Can breathe underwater
    }

    @Override
    public boolean isPushedByFluid() {
        return false; // Not pushed by water
    }

    // =================== BREEDING (Optional - returns null for now) ===================

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return null; // Baby snails don't breed (can change later if needed)
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false; // Baby snails don't eat (can change later if needed)
    }

    // =================== SOUNDS ===================

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.AXOLOTL_HURT; // Kept from your version
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return SoundEvents.AXOLOTL_DEATH; // Kept from your version
    }

    // =================== DEATH HANDLING ===================

    @Override
    public void die(DamageSource damageSource) {
        // Spawn death particles (kept from your version)
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            for (int i = 0; i < 20; i++) {
                double offsetX = (this.random.nextDouble() - 0.5D) * 0.5D;
                double offsetY = this.random.nextDouble() * 0.5D;
                double offsetZ = (this.random.nextDouble() - 0.5D) * 0.5D;

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
        // Kept from your version (no drops)
    }

    // =================== INTERACTION (Pickup) ===================

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);

        // Check for pickup (empty hand)
        if (itemStack.isEmpty()) {
            if (!this.level().isClientSide) {
                System.out.println("=== BABY BLACK SNAIL PICKUP DEBUG ===");
                System.out.println("Entity Shell Color: " + this.getShellColor() +
                        " (" + DyeColor.byId(this.getShellColor()).getName() + ")");

                // Create item with this entity's shell color
                ItemStack snailItem = BabyBlackTransponderSnailItem.createFromEntity(this);

                // Give item to player or drop it
                if (!player.getInventory().add(snailItem)) {
                    player.drop(snailItem, false);
                }

                // Play pickup sound
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F, 0.2F);

                System.out.println("Entity removed from world");
                System.out.println("====================================");

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
                    System.out.println("Dyeing Baby Black Snail from " +
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

    // Active state getter/setter (kept from your version)
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
        return false; // Can take fire damage (kept from your version)
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