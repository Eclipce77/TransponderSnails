package net.eclipce.transpondersnails.entity.client;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.data.SnailNBTHandler;
import net.eclipce.transpondersnails.voice.server.CallSession;
import net.eclipce.transpondersnails.voice.server.TransponderCallManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Client-side property calculations for Transponder Snail item models
 * Handles dynamic state changes for visual feedback
 */
public class TransponderSnailItemProperties {

    /**
     * Calculate call state property for item model switching
     * Returns: 0.0 (idle), 0.25 (ringing), 0.5 (call), 0.75 (active)
     */
    public static float calculateCallState(ItemStack stack, @Nullable ClientLevel level,
                                           @Nullable LivingEntity entity, int seed) {

        // Priority 1: Check NBT for immediate state (server-synced via inventoryTick)
        CompoundTag nbt = stack.getTag();
        if (nbt != null && nbt.contains("call_state")) {
            String state = nbt.getString("call_state");

            switch (state) {
                case "ringing":
                    return 0.25f; // Ringing state - sound model
                case "connected":
                    // Check if has active audio flag
                    if (nbt.getBoolean("has_active_audio")) {
                        return 0.75f; // Active state - sound + call
                    }
                    return 0.5f; // Call state - no sound
                case "busy":
                    return 0.25f; // Show sound model for busy signal
                default:
                    break;
            }
        }

        // Priority 2: Fallback to call manager check (shouldn't be needed with NBT sync)
        // This is here for robustness but NBT should always be up-to-date
        int snailNumber = SnailNBTHandler.getSnailNumber(stack);
        if (snailNumber != -1) {
            TransponderCallManager callManager = TransponderSnails.getCallManager();
            if (callManager != null) {
                // ✅ FIX: Check ringing FIRST (just like server-side logic)
                if (callManager.isSnailRinging(snailNumber)) {
                    return 0.25f; // Ringing
                }

                // ✅ FIX: Only show "call" state if snail is in call AND session is CONNECTED
                if (callManager.isSnailInCall(snailNumber)) {
                    // Try to get the call session to verify it's actually CONNECTED
                    UUID callId = null;

                    // Try to find the call session
                    for (CallSession session : callManager.getActiveCalls()) {
                        if (session.isParticipant(snailNumber)) {
                            callId = session.getCallId();

                            // ✅ Only show call/active state if session is CONNECTED
                            if (session.getState() == CallSession.CallState.CONNECTED) {
                                // Check for audio activity
                                if (callManager.hasActiveAudio(snailNumber)) {
                                    return 0.75f; // Active
                                }
                                return 0.5f; // Call
                            } else {
                                // Session exists but not connected (INITIATING/RINGING/ENDING)
                                // Return idle state
                                return 0.0f;
                            }
                        }
                    }

                    // ✅ Fallback: If we can't find the session, assume idle
                    // (This shouldn't happen, but better safe than sorry)
                    return 0.0f;
                }
            }
        }

        return 0.0f; // Idle state
    }

    /**
     * Calculate shell color property for item model switching
     * Returns: 0.00 to 0.15 for the 16 dye colors
     */
    public static float calculateShellColor(ItemStack stack, @Nullable ClientLevel level,
                                            @Nullable LivingEntity entity, int seed) {
        CompoundTag nbt = stack.getTag();
        if (nbt != null) {
            // Check top-level NBT
            if (nbt.contains("shell_color")) {
                return nbt.getInt("shell_color") / 100.0f;
            }
            // Check BlockEntityTag
            if (nbt.contains("BlockEntityTag")) {
                CompoundTag beTag = nbt.getCompound("BlockEntityTag");
                if (beTag.contains("ShellColor")) {
                    return beTag.getInt("ShellColor") / 100.0f;
                }
            }
        }
        return 0.0f; // Default white
    }
}