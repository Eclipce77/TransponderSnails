package net.eclipce.transpondersnails.item;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.block.custom.TransponderSnailBlock;
import net.eclipce.transpondersnails.block.entity.TransponderSnailBlockEntity;
import net.eclipce.transpondersnails.data.SnailNBTHandler;
import net.eclipce.transpondersnails.screen.DialingMenu;
import net.eclipce.transpondersnails.voice.server.CallSession;
import net.eclipce.transpondersnails.voice.server.TransponderCallManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * FIXED: Now uses TransponderCallManager as source of truth for call states
 * instead of relying on NBT tags which can be unreliable
 */
public class TransponderSnailItem extends BlockItem {

    // NBT keys for call state tracking (used for persistence/display only)
    private static final String CALL_STATE_TAG = "call_state";
    private static final String ACTIVE_CALL_ID_TAG = "active_call_id";
    private static final String CALL_START_TIME_TAG = "call_start_time";
    private static final String OTHER_SNAIL_NUMBER_TAG = "other_snail_number";

    public TransponderSnailItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);

        if (!level.isClientSide && entity instanceof ServerPlayer player) {
            int snailNumber = SnailNBTHandler.getSnailNumber(stack);
            if (snailNumber != -1) {
                TransponderCallManager callManager = TransponderSnails.getCallManager();
                if (callManager != null) {
                    if (isSelected || player.getOffhandItem() == stack) {
                        int currentHandheld = callManager.getPlayerHandheldSnail(player.getUUID());
                        if (currentHandheld != snailNumber) {
                            callManager.registerHandheldSnail(snailNumber, player.getUUID());
                        }
                    }
                }

                // Update call state in NBT based on call manager state
                updateCallStateFromManager(stack, player);

                // NEW: Update audio activity flag for visual feedback
                updateAudioActivityFlag(stack, snailNumber, callManager);
            }
        }
    }

    /**
     * Update the item's NBT based on the call manager's state
     * This keeps NBT in sync for tooltips/display, but call manager is the source of truth
     */
    private void updateCallStateFromManager(ItemStack stack, ServerPlayer player) {
        TransponderCallManager callManager = TransponderSnails.getCallManager();
        if (callManager == null) return;

        CompoundTag nbt = stack.getOrCreateTag();
        int snailNumber = SnailNBTHandler.getSnailNumber(stack);

        // ✅ PRIORITY 1: Check if this snail is RECEIVING a call (ringing)
        if (callManager.isSnailRinging(snailNumber)) {
            // Snail is receiving an incoming call - show ringing state
            if (!nbt.getString(CALL_STATE_TAG).equals("ringing")) {
                int callerNumber = callManager.getCallerSnailNumber(snailNumber);
                nbt.putString(CALL_STATE_TAG, "ringing");
                nbt.putInt(OTHER_SNAIL_NUMBER_TAG, callerNumber);
                System.out.println("DEBUG: Updated snail #" + snailNumber + " to RINGING state");
            }
            return;  // Exit early
        }

        // ✅ PRIORITY 2: Check if snail is in an active call
        if (callManager.isSnailInCall(snailNumber)) {
            UUID callId = callManager.getPlayerCallId(player.getUUID());
            if (callId != null) {
                // Get the actual CallSession to check its state
                CallSession session = null;
                for (CallSession s : callManager.getActiveCalls()) {
                    if (s.getCallId().equals(callId)) {
                        session = s;
                        break;
                    }
                }

                if (session != null) {
                    CallSession.CallState state = session.getState();

                    // ✅ Only show "connected" if call is actually CONNECTED
                    if (state == CallSession.CallState.CONNECTED) {
                        if (!nbt.getString(CALL_STATE_TAG).equals("connected")) {
                            nbt.putString(CALL_STATE_TAG, "connected");
                            nbt.putUUID(ACTIVE_CALL_ID_TAG, callId);
                            nbt.putLong(CALL_START_TIME_TAG, System.currentTimeMillis());
                            System.out.println("DEBUG: Updated snail #" + snailNumber + " to CONNECTED state");
                        }
                    } else {
                        // ✅ Call exists but is NOT connected (INITIATING, RINGING, ENDING)
                        // Clear any existing call state - snail should appear IDLE
                        if (nbt.contains(CALL_STATE_TAG)) {
                            String currentState = nbt.getString(CALL_STATE_TAG);
                            if (!currentState.isEmpty() && !"idle".equals(currentState)) {
                                System.out.println("DEBUG: Clearing snail #" + snailNumber +
                                        " call state (session is " + state + ", not CONNECTED)");
                                clearCallState(nbt);
                            }
                        }
                    }
                } else {
                    // Session not found but snail is supposedly in call - clear state
                    System.out.println("DEBUG: Session not found for snail #" + snailNumber + ", clearing state");
                    clearCallState(nbt);
                }
            } else {
                // No call ID for player but snail is in call - clear state
                clearCallState(nbt);
            }
        } else {
            // ✅ PRIORITY 3: Not in any call - ensure state is cleared
            if (nbt.contains(CALL_STATE_TAG)) {
                String currentState = nbt.getString(CALL_STATE_TAG);
                if (!currentState.isEmpty() && !"idle".equals(currentState)) {
                    System.out.println("DEBUG: Snail #" + snailNumber + " not in call, clearing state");
                    clearCallState(nbt);
                }
            }
        }
    }

    /**
     * Update audio activity flag in NBT for item model switching
     * This allows the "active" model to show when audio is being transmitted
     */
    private void updateAudioActivityFlag(ItemStack stack, int snailNumber, TransponderCallManager callManager) {
        CompoundTag nbt = stack.getOrCreateTag();

        if (callManager.isSnailInCall(snailNumber)) {
            boolean hasAudio = callManager.hasActiveAudio(snailNumber);

            if (hasAudio) {
                nbt.putBoolean("has_active_audio", true);
            } else {
                // Remove flag immediately when no audio detected
                // This makes handheld match block behavior (call -> active only when audio)
                nbt.remove("has_active_audio");
            }
        } else {
            // Not in call, ensure flag is removed
            nbt.remove("has_active_audio");
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack heldStack = context.getItemInHand();
        if (heldStack.getItem() != this) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState clickedBlock = level.getBlockState(pos);
        Player player = context.getPlayer();

        if (clickedBlock.getBlock() instanceof TransponderSnailBlock) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            int snailNumber = SnailNBTHandler.getSnailNumber(heldStack);
            TransponderCallManager callManager = TransponderSnails.getCallManager();

            if (snailNumber != -1 && callManager != null && callManager.isSnailInCall(snailNumber)) {
                InteractionResult result = super.useOn(context);

                if (result == InteractionResult.SUCCESS) {
                    BlockPos placedPos = context.getClickedPos().relative(context.getClickedFace());
                    BlockEntity blockEntity = level.getBlockEntity(placedPos);

                    if (blockEntity instanceof TransponderSnailBlockEntity snailBE) {
                        callManager.transitionHandheldToBlock(snailNumber, placedPos, snailBE);
                        serverPlayer.displayClientMessage(
                                Component.literal("Call seamlessly transferred to placed snail")
                                        .withStyle(ChatFormatting.GREEN),
                                true
                        );
                    }
                }

                return result;
            }
        }

        return super.useOn(context);
    }

    /**
     * FIXED: Now checks call manager as source of truth instead of NBT
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        if (!shouldHandleInteraction(player, hand)) {
            return InteractionResultHolder.pass(stack);
        }

        ServerPlayer serverPlayer = (ServerPlayer) player;
        TransponderCallManager callManager = TransponderSnails.getCallManager();

        if (callManager == null) {
            serverPlayer.displayClientMessage(
                    Component.literal("Voice chat system not available!")
                            .withStyle(ChatFormatting.RED),
                    true
            );
            return InteractionResultHolder.fail(stack);
        }

        // Ensure snail has a number
        int snailNumber = SnailNBTHandler.getSnailNumber(stack);
        if (snailNumber == -1) {
            UUID snailUUID = SnailNBTHandler.getOrCreateSnailUUID(stack, serverPlayer.getUUID());
            if (snailUUID != null) {
                snailNumber = SnailNBTHandler.getSnailNumber(stack);
                if (snailNumber != -1) {
                    serverPlayer.displayClientMessage(
                            Component.literal("Your handheld snail has been assigned number #" + snailNumber)
                                    .withStyle(ChatFormatting.GREEN),
                            true
                    );
                }
            }
        }

        // Register as handheld if not already
        if (snailNumber != -1) {
            callManager.registerHandheldSnail(snailNumber, serverPlayer.getUUID());
        }

        boolean isCrouching = player.isShiftKeyDown();

        // === FIXED: Check call manager instead of NBT for call state ===

        System.out.println("DEBUG: TransponderSnailItem.use() - Snail #" + snailNumber + ", Crouching: " + isCrouching);

        // FIRST: Check if snail is ringing (SOURCE OF TRUTH: call manager)
        if (callManager.isSnailRinging(snailNumber)) {
            UUID callId = callManager.getRingingCallId(snailNumber);
            int callerNumber = callManager.getCallerSnailNumber(snailNumber);

            System.out.println("DEBUG: Snail is RINGING - CallID: " + (callId != null ? callId.toString().substring(0, 8) : "null") + ", Caller: #" + callerNumber);

            if (isCrouching) {
                // Sneak + Right Click: Open GUI even while ringing
                System.out.println("DEBUG: Opening GUI while ringing (sneaking)");
                openDialingMenu(serverPlayer, stack);
                return InteractionResultHolder.success(stack);
            } else {
                // Right Click: Answer the incoming call
                System.out.println("DEBUG: Attempting to answer call");

                if (callId != null && callManager.acceptCall(serverPlayer, callId)) {
                    // Update NBT for display purposes
                    CompoundTag nbt = stack.getOrCreateTag();
                    nbt.putString(CALL_STATE_TAG, "connected");
                    nbt.putLong(CALL_START_TIME_TAG, System.currentTimeMillis());

                    serverPlayer.displayClientMessage(
                            Component.literal("Call answered!")
                                    .withStyle(ChatFormatting.GREEN),
                            true
                    );
                    System.out.println("DEBUG: Call answered successfully");
                } else {
                    System.out.println("DEBUG: Failed to answer call");
                    serverPlayer.displayClientMessage(
                            Component.literal("Failed to answer call!")
                                    .withStyle(ChatFormatting.RED),
                            true
                    );
                }
                return InteractionResultHolder.success(stack);
            }
        }

        // SECOND: Check if already in a call (SOURCE OF TRUTH: call manager)
        if (callManager.isSnailInCall(snailNumber)) {
            System.out.println("DEBUG: Snail is in ACTIVE CALL");

            if (isCrouching) {
                // Crouching in call: Open GUI
                System.out.println("DEBUG: Opening GUI during call (sneaking)");
                openDialingMenu(serverPlayer, stack);
                return InteractionResultHolder.success(stack);
            } else {
                // Not crouching in call: End call
                System.out.println("DEBUG: Hanging up call");
                callManager.endCall(serverPlayer);
                clearCallState(stack.getOrCreateTag());
                serverPlayer.displayClientMessage(
                        Component.literal("Call ended.")
                                .withStyle(ChatFormatting.YELLOW),
                        true
                );
                return InteractionResultHolder.success(stack);
            }
        }

        // THIRD: Not ringing or in call - open GUI
        System.out.println("DEBUG: Snail is IDLE - opening GUI");
        openDialingMenu(serverPlayer, stack);
        return InteractionResultHolder.success(stack);
    }

    /**
     * Clear call state from NBT (for display purposes only)
     */
    private void clearCallState(CompoundTag nbt) {
        nbt.remove(CALL_STATE_TAG);
        nbt.remove(ACTIVE_CALL_ID_TAG);
        nbt.remove(OTHER_SNAIL_NUMBER_TAG);
        nbt.remove(CALL_START_TIME_TAG);
    }

    private boolean shouldHandleInteraction(Player player, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND) {
            return true;
        } else {
            ItemStack mainHandStack = player.getItemInHand(InteractionHand.MAIN_HAND);
            return mainHandStack.isEmpty();
        }
    }

    private void openDialingMenu(ServerPlayer player, ItemStack stack) {
        NetworkHooks.openScreen(player, new TransponderSnailMenuProvider(stack), buf -> {
            buf.writeBoolean(true); // isHandheld flag
            buf.writeItem(stack);
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        SnailNBTHandler.addSnailTooltip(stack, level, tooltip, flag);

        // NBT is used here only for display (visual indicator)
        CompoundTag nbt = stack.getTag();
        if (nbt != null) {
            String callState = nbt.getString(CALL_STATE_TAG);
            if (!callState.isEmpty() && !"idle".equals(callState)) {
                Component stateTooltip = null;

                switch (callState) {
                    case "ringing":
                        int callerNumber = nbt.getInt(OTHER_SNAIL_NUMBER_TAG);
                        stateTooltip = Component.literal("Incoming call from #" + callerNumber)
                                .withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC);
                        break;
                    case "connected":
                        long duration = (System.currentTimeMillis() - nbt.getLong(CALL_START_TIME_TAG)) / 1000;
                        stateTooltip = Component.literal("In call (" + formatDuration(duration) + ")")
                                .withStyle(ChatFormatting.GREEN);
                        break;
                    case "busy":
                        stateTooltip = Component.literal("Line busy")
                                .withStyle(ChatFormatting.RED);
                        break;
                }

                if (stateTooltip != null) {
                    tooltip.add(stateTooltip);
                }
            }
        }

        if (flag.isAdvanced()) {
            tooltip.add(Component.literal("Right-click: Open dialer").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.literal("Sneak + Right-click: GUI in call").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private String formatDuration(long seconds) {
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack item, Player player) {
        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            int snailNumber = SnailNBTHandler.getSnailNumber(item);
            TransponderCallManager callManager = TransponderSnails.getCallManager();

            if (snailNumber != -1 && callManager != null) {
                callManager.unregisterHandheldSnail(snailNumber);

                if (callManager.isSnailInCall(snailNumber)) {
                    serverPlayer.displayClientMessage(
                            Component.literal("Dropped snail - call continues")
                                    .withStyle(ChatFormatting.YELLOW),
                            true
                    );
                }
            }
        }

        return super.onDroppedByPlayer(item, player);
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        super.onCraftedBy(stack, level, player);

        CompoundTag nbt = stack.getOrCreateTag();
        if (!nbt.contains(CALL_STATE_TAG)) {
            nbt.putString(CALL_STATE_TAG, "idle");
        }
    }

    private static class TransponderSnailMenuProvider implements net.minecraft.world.MenuProvider {
        private final ItemStack snailStack;

        public TransponderSnailMenuProvider(ItemStack snailStack) {
            this.snailStack = snailStack.copy();
        }

        @Override
        public Component getDisplayName() {
            return Component.translatable("gui.transpondersnails.dialing");
        }

        @Override
        public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int containerId,
                                                                              net.minecraft.world.entity.player.Inventory playerInventory,
                                                                              Player player) {
            return new DialingMenu(containerId, playerInventory, snailStack);
        }
    }

    public static float getCallStateProperty(ItemStack stack) {
        CompoundTag nbt = stack.getTag();
        if (nbt == null) return 0.0f;

        String state = nbt.getString(CALL_STATE_TAG);
        switch (state) {
            case "ringing": return 1.0f;
            case "connected": return 2.0f;
            case "busy": return 3.0f;
            default: return 0.0f;
        }
    }
}