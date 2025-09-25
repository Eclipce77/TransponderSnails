package net.eclipce.transpondersnails.entity.custom;

import net.eclipce.transpondersnails.entity.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

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

        this.goalSelector.addGoal(2, new RandomStrollGoal(this, 1.1D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 3f));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

        this.goalSelector.addGoal(5, new PanicGoal(this, 1.1D));

    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 5D)
                .add(Attributes.FOLLOW_RANGE, 24D)
                .add(Attributes.MOVEMENT_SPEED, 0.1D);
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel pLevel, AgeableMob pOtherParent) {
        return ModEntities.DEN_DEN_MUSHI.get().create(pLevel);
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

    // Color Stuff //

    // Add this method to initialize entity data:
    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        // Generate random pastel body color on spawn
        this.entityData.define(BODY_COLOR, generateRandomPastelColor());
        // Default shell color (white)
        this.entityData.define(SHELL_COLOR, 0);
    }

    // Body color generation (same as in BlockEntity)
    private int generateRandomPastelColor() {
        // Use the entity's random field directly (it's a RandomSource, not java.util.Random)

        // Generate random hue (0-360 degrees)
        float hue = this.random.nextFloat();

        // Pastel colors have moderate saturation (30-60%)
        float saturation = 0.7f + (this.random.nextFloat() * 0.1f);

        // Pastel colors have high lightness (70-90%)
        float lightness = 0.7f + (this.random.nextFloat() * 0.2f);

        // Convert HSL to RGB
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
        return this.entityData.get(SHELL_COLOR);
    }

    public void setShellColor(int color) {
        this.entityData.set(SHELL_COLOR, Math.max(0, Math.min(15, color)));
    }

    // Allow dyeing the shell by right-clicking with dye
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

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
