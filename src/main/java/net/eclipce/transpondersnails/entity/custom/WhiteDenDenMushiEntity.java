package net.eclipce.transpondersnails.entity.custom;

import net.eclipce.transpondersnails.entity.ModEntities;
import net.eclipce.transpondersnails.item.WhiteDenDenMushiItem;
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
 * White Den Den Mushi Entity
 * Fixed white body (0xFFFFFF) with dyeable shell
 */
public class WhiteDenDenMushiEntity extends Animal {

    private static final EntityDataAccessor<Integer> SHELL_COLOR =
            SynchedEntityData.defineId(WhiteDenDenMushiEntity.class, EntityDataSerializers.INT);

    public final AnimationState idleAnimationState = new AnimationState();
    private int idleAnimationTimeout = 0;

    public WhiteDenDenMushiEntity(EntityType<? extends Animal> pAnimal, Level pLevel) {
        super(pAnimal, pLevel);
    }

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
                .add(Attributes.MOVEMENT_SPEED, 0.05D);
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
        WhiteDenDenMushiEntity baby = ModEntities.WHITE_DEN_DEN_MUSHI.get().create(pLevel);

        if (baby != null && pOtherParent instanceof WhiteDenDenMushiEntity otherWhite) {
            int babyShellColor = this.random.nextBoolean() ?
                    this.getShellColor() : otherWhite.getShellColor();
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

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(SHELL_COLOR, 0);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData,
                                        @Nullable CompoundTag nbt) {

        if (!level.isClientSide()) {
            boolean hasDefaultShellColor = this.getShellColor() == 0;

            if (hasDefaultShellColor) {
                int shellColor = this.random.nextInt(16);
                this.setShellColor(shellColor);
            }
        }

        return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData, nbt);
    }

    public int getShellColor() {
        int color = this.entityData.get(SHELL_COLOR);
        return Math.max(0, Math.min(15, color));
    }

    public void setShellColor(int color) {
        int clampedColor = Math.max(0, Math.min(15, color));
        this.entityData.set(SHELL_COLOR, clampedColor);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (itemstack.isEmpty()) {
            if (!this.level().isClientSide) {
                ItemStack whiteSnailItem = WhiteDenDenMushiItem.createFromEntity(this);

                if (!player.getInventory().add(whiteSnailItem)) {
                    player.drop(whiteSnailItem, false);
                }

                this.discard();
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

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

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("ShellColor", this.getShellColor());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("ShellColor")) {
            this.setShellColor(compound.getInt("ShellColor"));
        }
    }
}