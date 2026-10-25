package net.eclipce.transpondersnails.block.entity;

import net.eclipce.transpondersnails.voice.server.HornedDDMJammerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Block entity for HornedDenDenMushiBlock.
 *
 * Responsibilities:
 *  1. Persist shellColor (0-15) and bodyColor (RGB int) across world saves.
 *  2. Sync those values to the client via update-tag packets (needed by the BER).
 *  3. Register this jammer with HornedDDMJammerManager on load, unregister on removal.
 *
 * No ticker is needed — this block entity is entirely passive.
 */
public class HornedDenDenMushiBlockEntity extends BlockEntity {

    // NBT keys — must match what HornedDenDenMushiItem writes
    private static final String KEY_SHELL_COLOR = "ShellColor";
    private static final String KEY_BODY_COLOR  = "BodyColor";

    // Colour data
    private int shellColor = 0;   // DyeColor id, default white
    private int bodyColor  = 0;   // RGB int, default 0

    // The lightning rod tower wired to it (server): counted exactly as a Black Transponder Snail counts its own.
    private int lightningRodCount = 0;

    // Client only, not saved: how far its eyes have faded in (0..1) and when that was last updated.
    private float eyeGlow = 0.0F;
    private long lastGlowNanos = 0L;

    // =================== CONSTRUCTOR ===================

    public HornedDenDenMushiBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HORNED_DEN_DEN_MUSHI_BLOCK_ENTITY.get(), pos, state);
    }

    // =================== JAMMER LIFECYCLE ===================

    /**
     * Called by Minecraft when the block entity is loaded into the world
     * (chunk load, placement, world join). This is the correct hook for
     * registering with the jammer manager.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel sl) {
            HornedDDMJammerManager.getInstance().registerJammer(worldPosition, sl);
            updateAntennaConnection();
        }
    }

    /**
     * Called when the block entity is removed from the world
     * (block broken, chunk unloaded, /setblock).
     * Unregisters from the jammer manager so the area is no longer jammed.
     */
    @Override
    public void setRemoved() {
        // Unregister BEFORE calling super so the position is still valid
        HornedDDMJammerManager.getInstance().unregisterJammer(worldPosition);
        super.setRemoved();
    }

    // =================== NBT PERSISTENCE ===================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(KEY_SHELL_COLOR, shellColor);
        tag.putInt(KEY_BODY_COLOR,  bodyColor);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(KEY_SHELL_COLOR)) shellColor = tag.getInt(KEY_SHELL_COLOR);
        if (tag.contains(KEY_BODY_COLOR))  bodyColor  = tag.getInt(KEY_BODY_COLOR);
    }

    // =================== CLIENT SYNC ===================

    /**
     * Provides the packet sent when the block entity is first seen by a client
     * or when syncToClient() is called. The BER needs shell/body colour to render.
     */
    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * The tag sent inside the update packet. We reuse saveAdditional so the
     * client always has a complete copy of our data.
     */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (pkt.getTag() != null) {
            load(pkt.getTag());
        }
    }

    /**
     * Call this after changing shellColor or bodyColor to push the new
     * values to all clients watching this chunk.
     */
    public void syncToClient() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            setChanged();
        }
    }

    // =================== GETTERS / SETTERS ===================

    /**
     * Counts the lightning rods of the tower wired to this snail: through a wire on any side, or a rod right next to it - the first valid
     * tower found, with the rods of its stack counted (BlackTransponderSnailBlock.countLightningRodStack), exactly as a Black Transponder Snail does.
     * Each rod widens its jamming range (see JamMath.rangeForRods).
     */
    public void updateAntennaConnection() {
        if (level == null || level.isClientSide) return;
        int rods = 0;
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = worldPosition.relative(dir);
            net.minecraft.world.level.block.state.BlockState neighbor = level.getBlockState(neighborPos);
            if (neighbor.getBlock() instanceof net.eclipce.transpondersnails.block.custom.WireBlock) {
                BlockPos rodPos = net.eclipce.transpondersnails.block.custom.WireBlock.traceToLightningRod(level, neighborPos);
                if (rodPos != null) {
                    int count = net.eclipce.transpondersnails.block.custom.BlackTransponderSnailBlock.countLightningRodStack(level, rodPos);
                    if (count > 0) {
                        rods = count;
                        break;
                    }
                }
            }
            if (neighbor.getBlock() instanceof net.minecraft.world.level.block.LightningRodBlock) {
                int count = net.eclipce.transpondersnails.block.custom.BlackTransponderSnailBlock.countLightningRodStack(level, neighborPos);
                if (count > 0) {
                    rods = count;
                    break;
                }
            }
        }
        lightningRodCount = rods;
    }

    /** How many lightning rods its tower has (0 if it has none). */
    public int getLightningRodCount() {
        return lightningRodCount;
    }

    /**
     * Client: eases the eyes' red glow in (towards 1) over a little over half a second while the snail is switched on, and drops it to 0 at once when it is
     * switched off (the eyes close then). Called by the renderer every frame.
     */
    public float stepEyeGlow(boolean on) {
        long now = System.nanoTime();
        float dt = lastGlowNanos == 0L ? 0.0F : Math.min(0.25F, (now - lastGlowNanos) / 1.0e9F);
        lastGlowNanos = now;
        if (!on) {                                    // switched off: the eyes are closed, so there is nothing to fade - the next time it opens, the red fades in from nothing
            eyeGlow = 0.0F;
            return 0.0F;
        }
        float target = 1.0F;
        float step = dt / 0.6F;
        eyeGlow = target > eyeGlow ? Math.min(target, eyeGlow + step) : Math.max(target, eyeGlow - step);
        return eyeGlow;
    }

    public int getShellColor() {
        return shellColor;
    }

    public void setShellColor(int shellColor) {
        this.shellColor = shellColor;
        syncToClient();
    }

    public int getBodyColor() {
        return bodyColor;
    }

    public void setBodyColor(int bodyColor) {
        this.bodyColor = bodyColor;
        syncToClient();
    }
}