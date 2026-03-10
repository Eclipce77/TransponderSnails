package net.eclipce.transpondersnails.entity.custom;

import net.eclipce.transpondersnails.entity.ModEntities;
import net.eclipce.transpondersnails.item.HornedDenDenMushiItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

/**
 * Horned Den Den Mushi — a visual variant of the Den Den Mushi.
 *
 * Extends DenDenMushiEntity to inherit all behaviour:
 *   - AI goals (wander, tempt, breed, panic, underwater walking)
 *   - Shell & body colour syncing / NBT save-load
 *   - Right-click dyeing
 *   - Right-click pickup (overridden below to produce the correct item type)
 *   - Breeding (overridden below to produce HornedDenDenMushiEntity offspring)
 *   - Water breathing & no fluid push
 *
 * Only the model, renderer, pickup item, and breed offspring differ
 * from the base class.
 */
public class HornedDenDenMushiEntity extends DenDenMushiEntity {

    public HornedDenDenMushiEntity(EntityType<? extends Animal> entityType, Level level) {
        super(entityType, level);
    }

    // -----------------------------------------------------------------------
    // Attributes — identical to the regular Den Den Mushi
    // -----------------------------------------------------------------------

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 5D)
                .add(Attributes.FOLLOW_RANGE, 24D)
                .add(Attributes.MOVEMENT_SPEED, 0.1D);
    }

    // -----------------------------------------------------------------------
    // Breeding — offspring should be HornedDenDenMushiEntity, not DenDenMushiEntity
    // -----------------------------------------------------------------------

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        HornedDenDenMushiEntity baby = ModEntities.HORNED_DEN_DEN_MUSHI.get().create(level);

        if (baby != null && otherParent instanceof DenDenMushiEntity otherDenDen) {
            // Inherit colours from parents (50/50 chance per trait — same logic as base class)
            int babyBodyColor  = this.random.nextBoolean() ? this.getBodyColor()  : otherDenDen.getBodyColor();
            int babyShellColor = this.random.nextBoolean() ? this.getShellColor() : otherDenDen.getShellColor();

            baby.setBodyColor(babyBodyColor);
            baby.setShellColor(babyShellColor);

            System.out.println("HORNED BREEDING: Created baby with inherited colours");
            System.out.println("  Parent 1 - Body: #" + Integer.toHexString(this.getBodyColor()).toUpperCase() +
                    ", Shell: " + DyeColor.byId(this.getShellColor()).getName());
            System.out.println("  Parent 2 - Body: #" + Integer.toHexString(otherDenDen.getBodyColor()).toUpperCase() +
                    ", Shell: " + DyeColor.byId(otherDenDen.getShellColor()).getName());
            System.out.println("  Baby - Body: #" + Integer.toHexString(babyBodyColor).toUpperCase() +
                    ", Shell: " + DyeColor.byId(babyShellColor).getName());
        }

        return baby;
    }

    // -----------------------------------------------------------------------
    // Right-click interaction — pickup produces HornedDenDenMushiItem;
    // dyeing is fully inherited from DenDenMushiEntity and needs no override.
    // -----------------------------------------------------------------------

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);

        // Empty-hand pickup — same logic as base class but uses HornedDenDenMushiItem
        if (itemStack.isEmpty()) {
            System.out.println("HornedDenDenMushi: empty-hand pickup | side: " +
                    (level().isClientSide() ? "CLIENT" : "SERVER") + " | hand: " + hand);

            if (!this.level().isClientSide) {
                System.out.println("=== HORNED ENTITY PICKUP ===");
                System.out.println("Shell: " + DyeColor.byId(this.getShellColor()).getName());
                System.out.println("Body:  #" + Integer.toHexString(this.getBodyColor()).toUpperCase());
                System.out.println("Baby:  " + this.isBaby());

                // Horned DDM does not have a separate baby item — use adult item for both
                // (mirrors how WhiteDenDenMushi handles pickup; adjust if a baby variant is added later)
                ItemStack hornedItem = HornedDenDenMushiItem.createFromEntity(this);

                System.out.println("Created item shell: " + HornedDenDenMushiItem.getShellColor(hornedItem));
                System.out.println("Created item body:  #" +
                        Integer.toHexString(HornedDenDenMushiItem.getBodyColor(hornedItem)).toUpperCase());

                if (!player.getInventory().add(hornedItem)) {
                    player.drop(hornedItem, false);
                }

                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F,
                        ((this.random.nextFloat() - this.random.nextFloat()) * 0.7F + 1.0F) * 2.0F);

                this.discard();
                System.out.println("============================");
            }

            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        // All other interactions (dye, breeding food, etc.) — delegate to base class
        return super.mobInteract(player, hand);
    }
}