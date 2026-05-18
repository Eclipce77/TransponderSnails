package net.eclipce.transpondersnails.entity.custom;

import net.eclipce.transpondersnails.entity.ModEntities;
import net.eclipce.transpondersnails.item.BabyDenDenMushiItem;
import net.eclipce.transpondersnails.item.DenDenMushiItem;
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
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

public class DenDenMushiEntity extends Animal {
    public DenDenMushiEntity(EntityType<? extends Animal> pAnimal, Level pLevel) {
        super(pAnimal, pLevel);
    }

    private static final EntityDataAccessor<Integer> BODY_COLOR = SynchedEntityData.defineId(DenDenMushiEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SHELL_COLOR = SynchedEntityData.defineId(DenDenMushiEntity.class, EntityDataSerializers.INT);

    public final AnimationState idleAnimationState = new AnimationState();
    private int idleAnimationTimeout = 0;

    @Override
    public void tick() {
        super.tick();

        if(this.level().isClientSide()) {
            setupAnimationStates();
        }
    }

    private void setupAnimationStates() {
        if(this.idleAnimationTimeout <= 0) {
            this.idleAnimationTimeout = this.random.nextInt(40) + 80;
            this.idleAnimationState.start(this.tickCount);
        } else {
            --this.idleAnimationTimeout;
        }
    }

    @Override
    protected void updateWalkAnimation(float pPartialTick) {
        float f;
        if(this.getPose() == Pose.STANDING) {
            f = Math.min(pPartialTick * 6F, 1f);
        } else {
            f = 0;
        }

        this.walkAnimation.update(f, 0.2f);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new BreedGoal(this, 1.15D));
        this.goalSelector.addGoal(1, new TemptGoal(this, 1.2D, Ingredient.of(Items.KELP), false));
        this.goalSelector.addGoal(2, new UnderwaterWalkingGoal(this, 2.2D));
        this.goalSelector.addGoal(3, new RandomStrollGoal(this, 1.1D));
        this.goalSelector.addGoal(4, new PanicGoal(this, 2.4D));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 5D)
                .add(Attributes.FOLLOW_RANGE, 24D)
                .add(Attributes.MOVEMENT_SPEED, 0.1D);
    }

    @Override
    public boolean checkSpawnRules(LevelAccessor level, MobSpawnType spawnType) {
        return true; // Handled by spawn conditions
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel pLevel, AgeableMob pOtherParent) {
        DenDenMushiEntity baby = ModEntities.DEN_DEN_MUSHI.get().create(pLevel);

        if (baby != null && pOtherParent instanceof DenDenMushiEntity otherDenDen) {
            // Inherit traits from parents - 50% chance for each trait from each parent

            // Inherit body color (50% from this parent, 50% from other parent)
            int babyBodyColor = this.random.nextBoolean() ?
                    this.getBodyColor() : otherDenDen.getBodyColor();

            // Inherit shell color (50% from this parent, 50% from other parent)
            int babyShellColor = this.random.nextBoolean() ?
                    this.getShellColor() : otherDenDen.getShellColor();

            // Set the baby's colors
            baby.setBodyColor(babyBodyColor);
            baby.setShellColor(babyShellColor);

        }

        return baby;
    }

    @Override
    public boolean isFood(ItemStack pStack) {
        return pStack.is(Items.KELP);
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource pDamageSource) {
        return SoundEvents.AXOLOTL_HURT;
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return SoundEvents.AXOLOTL_DEATH;
    }

    // Color Stuff - Fixed synchronization
    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();

        // Define with defaults - actual values will be set in finalizeSpawn
        this.entityData.define(BODY_COLOR, 0xF5E6A3); // Default pastel yellow
        this.entityData.define(SHELL_COLOR, 0); // Default white

    }

    // Generate colors only during spawn finalization to ensure proper sync
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData,
                                        @Nullable CompoundTag nbt) {

        // Only generate random colors on the server
        if (!level.isClientSide()) {
            int bodyColor = generateRandomPastelColor();
            int shellColor = generateRandomShellColor();

            this.setBodyColor(bodyColor);
            this.setShellColor(shellColor);

        }

        return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData, nbt);
    }

    // Monitor data synchronization
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);

        if (key.equals(SHELL_COLOR)) {
        }
    }

    // Generate random shell color
    private int generateRandomShellColor() {
        return this.random.nextInt(16); // DyeColor has 16 colors (IDs 0-15)
    }

    // Generate random pastel body color
    private int generateRandomPastelColor() {
        float hue = this.random.nextFloat();
        float saturation = 0.7f + (this.random.nextFloat() * 0.1f);
        float lightness = 0.7f + (this.random.nextFloat() * 0.2f);
        return hslToRgb(hue, saturation, lightness);
    }

    private int hslToRgb(float h, float s, float l) {
        float r, g, b;

        if (s == 0) {
            r = g = b = l; // Achromatic
        } else {
            float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
            float p = 2 * l - q;
            r = hueToRgb(p, q, h + 1f/3f);
            g = hueToRgb(p, q, h);
            b = hueToRgb(p, q, h - 1f/3f);
        }

        int red = Math.round(r * 255);
        int green = Math.round(g * 255);
        int blue = Math.round(b * 255);

        return (red << 16) | (green << 8) | blue;
    }

    private float hueToRgb(float p, float q, float t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1f/6f) return p + (q - p) * 6 * t;
        if (t < 1f/2f) return q;
        if (t < 2f/3f) return p + (q - p) * (2f/3f - t) * 6;
        return p;
    }

    // Getters and setters for colors
    public int getBodyColor() {
        return this.entityData.get(BODY_COLOR);
    }

    public void setBodyColor(int color) {
        this.entityData.set(BODY_COLOR, color);
    }

    public int getShellColor() {
        int color = this.entityData.get(SHELL_COLOR);
        return Math.max(0, Math.min(15, color)); // Clamp to valid range (0-15)
    }

    public void setShellColor(int color) {
        int clampedColor = Math.max(0, Math.min(15, color));
        this.entityData.set(SHELL_COLOR, clampedColor);
    }

    // Enhanced mobInteract with comprehensive debugging
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {

        ItemStack itemstack = player.getItemInHand(hand);

        // Check for pickup first (empty hand)
        if (itemstack.isEmpty()) {

            if (!this.level().isClientSide) {

                // Create item with this entity's data - use BabyDenDenMushiItem for babies
                ItemStack denDenMushiItem;
                if (this.isBaby()) {
                    denDenMushiItem = BabyDenDenMushiItem.createFromEntity(this);

                } else {
                    denDenMushiItem = DenDenMushiItem.createFromEntity(this);

                }

                // Give item to player
                if (!player.getInventory().add(denDenMushiItem)) {
                    player.drop(denDenMushiItem, false);
                }

                // Play pickup sound
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F,
                        ((this.random.nextFloat() - this.random.nextFloat()) * 0.7F + 1.0F) * 2.0F);

                // Remove entity
                this.discard();
            } else {
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        // Check for dyeing
        if (itemstack.getItem() instanceof DyeItem dyeItem) {
            if (!this.level().isClientSide) {
                DyeColor dyeColor = dyeItem.getDyeColor();
                if (dyeColor.getId() != this.getShellColor()) {
                    this.setShellColor(dyeColor.getId());
                    if (!player.isCreative()) {
                        itemstack.shrink(1);
                    }
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        return super.mobInteract(player, hand);
    }

    // Save and load colors from NBT
    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("BodyColor", this.getBodyColor());
        compound.putInt("ShellColor", this.getShellColor());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("BodyColor")) {
            this.setBodyColor(compound.getInt("BodyColor"));
        }
        if (compound.contains("ShellColor")) {
            this.setShellColor(compound.getInt("ShellColor"));
        }
    }

    // Support for /summon command with custom colors
    @Override
    public void load(CompoundTag compound) {
        super.load(compound);

        // Support hex string format for body color
        if (compound.contains("BodyColor")) {
            if (compound.getTagType("BodyColor") == 8) { // String type
                String colorStr = compound.getString("BodyColor");
                try {
                    // Remove # if present and parse hex
                    colorStr = colorStr.replace("#", "");
                    int color = Integer.parseInt(colorStr, 16);
                    this.setBodyColor(color);
                } catch (NumberFormatException e) {
                    // Invalid format, keep default
                }
            } else {
                this.setBodyColor(compound.getInt("BodyColor"));
            }
        }

        // Support both string and int for shell color
        if (compound.contains("ShellColor")) {
            if (compound.getTagType("ShellColor") == 8) { // String type
                String colorName = compound.getString("ShellColor").toLowerCase();
                for (DyeColor dye : DyeColor.values()) {
                    if (dye.getName().equals(colorName)) {
                        this.setShellColor(dye.getId());
                        break;
                    }
                }
            } else {
                this.setShellColor(compound.getInt("ShellColor"));
            }
        }
    }
}