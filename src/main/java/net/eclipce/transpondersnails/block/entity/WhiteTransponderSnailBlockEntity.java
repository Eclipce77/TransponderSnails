package net.eclipce.transpondersnails.block.entity;

import net.eclipce.transpondersnails.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Block Entity for White Transponder Snail.
 * 
 * Stores the shell color separately from the blockstate to avoid
 * an explosion of blockstate variants (16 colors × 3 states × 4 wire configs × 4 directions = 768 variants!)
 * 
 * Instead, we store the color in the BE and use a color handler for tinting.
 * 
 * FIX: Added proper client-side handling for instant color updates
 */
public class WhiteTransponderSnailBlockEntity extends BlockEntity {

    // NBT keys
    private static final String TAG_SHELL_COLOR = "ShellColor";
    
    // Shell color stored as DyeColor ID (0-15, default 0 = white)
    private int shellColorId = 0;
    
    public WhiteTransponderSnailBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WHITE_TRANSPONDER_SNAIL.get(), pos, state);
    }
    
    // =================== SHELL COLOR MANAGEMENT ===================
    
    /**
     * Get the shell color ID (0-15)
     */
    public int getShellColorId() {
        return shellColorId;
    }
    
    /**
     * Set the shell color by ID (0-15)
     * FIX: Now properly syncs to client and triggers re-render
     */
    public void setShellColorId(int colorId) {
        if (colorId >= 0 && colorId <= 15) {
            int oldColor = this.shellColorId;
            this.shellColorId = colorId;
            setChanged();
            
            // Only sync if color actually changed
            if (oldColor != colorId) {
                syncToClient();
            }
        }
    }
    
    /**
     * Set the shell color by DyeColor
     */
    public void setShellColor(DyeColor color) {
        if (color != null) {
            setShellColorId(color.getId());
        }
    }
    
    /**
     * Get the shell color as DyeColor
     */
    public DyeColor getShellColor() {
        return DyeColor.byId(shellColorId);
    }
    
    /**
     * Get the actual RGB color value for the shell
     * This is used by the color handler for tinting
     */
    public int getShellColorRGB() {
        DyeColor color = getShellColor();
        // Use the map color's color value which is more suitable for tinting
        return color.getMapColor().col;
    }
    
    /**
     * Get the shell texture color for rendering
     * Uses the firework/text color which is brighter and better for items
     */
    public int getShellTextureColor() {
        DyeColor color = getShellColor();
        return color.getTextColor();
    }
    
    // =================== NBT SERIALIZATION ===================
    
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(TAG_SHELL_COLOR, shellColorId);
    }
    
    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(TAG_SHELL_COLOR)) {
            shellColorId = tag.getInt(TAG_SHELL_COLOR);
            // Clamp to valid range
            if (shellColorId < 0 || shellColorId > 15) {
                shellColorId = 0;
            }
        }
    }
    
    // =================== CLIENT SYNC - FIXED FOR INSTANT UPDATES ===================
    
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putInt(TAG_SHELL_COLOR, shellColorId);
        return tag;
    }
    
    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
    
    /**
     * FIX: Handle the update tag on client side and trigger re-render
     * This is called when the client receives the update packet
     */
    @Override
    public void handleUpdateTag(CompoundTag tag) {
        int oldColor = this.shellColorId;
        
        // Load the data
        load(tag);
        
        // FIX: If color changed, trigger immediate re-render
        if (oldColor != this.shellColorId && level != null && level.isClientSide) {
            // Force the block to re-render by marking the chunk dirty
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
            
            // Also mark the position for lighting/render update
            level.blockEntityChanged(worldPosition);

        }
    }
    
    /**
     * FIX: Called when receiving a data packet from server
     * Ensure proper render refresh on client
     */
    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            handleUpdateTag(tag);
        }
    }
    
    /**
     * Force sync to all tracking clients
     * FIX: Now also triggers proper block update for re-rendering
     */
    private void syncToClient() {
        if (level != null && !level.isClientSide) {
            // Send block entity data update
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 
                    Block.UPDATE_ALL | Block.UPDATE_IMMEDIATE);
            
            // Also mark dirty to ensure save
            setChanged();

        }
    }
    
    // =================== UTILITY ===================
    
    /**
     * Check if the block entity has been initialized with a color
     * (non-zero means it's been dyed at some point, but 0 is a valid color too)
     */
    public boolean hasCustomColor() {
        // White (0) is the default, so technically all are "valid"
        return true;
    }
    
    @Override
    public String toString() {
        return String.format("WhiteTransponderSnailBlockEntity{pos=%s, shellColor=%s(%d)}", 
                worldPosition, getShellColor().getName(), shellColorId);
    }
}
