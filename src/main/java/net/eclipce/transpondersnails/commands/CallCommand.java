package net.eclipce.transpondersnails.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.block.entity.TransponderSnailBlockEntity;
import net.eclipce.transpondersnails.data.SnailNumberRegistry;
import net.eclipce.transpondersnails.voice.server.TransponderCallManager;
import net.eclipce.transpondersnails.block.custom.TransponderSnailBlock;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.*;

public class CallCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("call")
                // Call a specific snail number
                .then(Commands.literal("dial")
                        .then(Commands.argument("number", StringArgumentType.string())
                                .executes(CallCommand::dialNumber)))

                // Accept incoming call
                .then(Commands.literal("accept")
                        .executes(CallCommand::acceptCall))

                // End current call
                .then(Commands.literal("end")
                        .executes(CallCommand::endCall))

                // Debug commands
                .then(Commands.literal("debug")
                        .then(Commands.literal("info")
                                .executes(CallCommand::showDebugInfo))
                        .then(Commands.literal("calls")
                                .executes(CallCommand::showActiveCalls))
                        .then(Commands.literal("registry")
                                .executes(CallCommand::showSnailRegistry))
                        .then(Commands.literal("nearby")
                                .executes(CallCommand::showNearbySnails)))

                // Test/utility commands
                .then(Commands.literal("status")
                        .executes(CallCommand::showPlayerCallStatus))
                .then(Commands.literal("test")
                        .then(Commands.literal("ring")
                                .executes(CallCommand::testRing)))
        );
    }

    private static int dialNumber(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String targetNumberStr = StringArgumentType.getString(context, "number");

            TransponderCallManager callManager = getCallManager();
            if (callManager == null) {
                context.getSource().sendFailure(Component.literal("Voice chat system not available!"));
                return 0;
            }

            // Parse target number
            int targetNumber;
            try {
                targetNumber = Integer.parseInt(targetNumberStr);
            } catch (NumberFormatException e) {
                player.sendSystemMessage(Component.literal("Invalid number format: " + targetNumberStr));
                return 0;
            }

            // Find nearby Transponder Snail block
            TransponderSnailBlockEntity snailEntity = findNearbySnailBlockEntity(player);
            if (snailEntity == null) {
                player.sendSystemMessage(Component.literal("No Transponder Snail nearby! Stand within 3 blocks of one."));
                return 0;
            }

            // Ensure the snail has a number
            if (!snailEntity.hasAssignedNumber()) {
                snailEntity.ensureSnailNumberAssigned(player);
                if (!snailEntity.hasAssignedNumber()) {
                    player.sendSystemMessage(Component.literal("Failed to assign number to this snail!"));
                    return 0;
                }
            }

            // Initiate the call using the simplified API
            boolean success = callManager.initiateCallBySnailNumber(player, snailEntity.getSnailNumber(), targetNumber);

            if (success) {
                player.sendSystemMessage(Component.literal("Dialing " + targetNumber + " from snail #" + snailEntity.getSnailNumber() + "..."));
                return Command.SINGLE_SUCCESS;
            } else {
                player.sendSystemMessage(Component.literal("Failed to initiate call"));
                return 0;
            }

        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Failed to dial: " + e.getMessage()));
            return 0;
        }
    }

    private static int acceptCall(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            TransponderCallManager callManager = getCallManager();

            if (callManager == null) {
                context.getSource().sendFailure(Component.literal("Voice chat system not available!"));
                return 0;
            }

            // Find nearby ringing snail
            TransponderSnailBlockEntity snailEntity = findNearbySnailBlockEntity(player);
            if (snailEntity == null) {
                player.sendSystemMessage(Component.literal("No Transponder Snail nearby!"));
                return 0;
            }

            // Check if the snail is ringing
            if (!snailEntity.isRinging()) {
                player.sendSystemMessage(Component.literal("This snail is not ringing!"));
                return 0;
            }

            // Get the active call ID from the snail
            UUID callId = snailEntity.getActiveCallId();
            if (callId == null) {
                player.sendSystemMessage(Component.literal("No active call found!"));
                return 0;
            }

            if (callManager.acceptCall(player, callId)) {
                player.sendSystemMessage(Component.literal("Call accepted!"));
                return Command.SINGLE_SUCCESS;
            } else {
                player.sendSystemMessage(Component.literal("Failed to accept call"));
                return 0;
            }

        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Failed to accept call: " + e.getMessage()));
            return 0;
        }
    }

    private static int endCall(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            TransponderCallManager callManager = getCallManager();

            if (callManager == null) {
                context.getSource().sendFailure(Component.literal("Voice chat system not available!"));
                return 0;
            }

            if (callManager.isInCall(player.getUUID())) {
                callManager.endCall(player);
                player.sendSystemMessage(Component.literal("Call ended"));
                return Command.SINGLE_SUCCESS;
            } else {
                player.sendSystemMessage(Component.literal("You are not in a call"));
                return 0;
            }

        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Failed to end call: " + e.getMessage()));
            return 0;
        }
    }

    private static int showDebugInfo(CommandContext<CommandSourceStack> context) {
        try {
            TransponderCallManager callManager = getCallManager();

            if (callManager == null) {
                context.getSource().sendFailure(Component.literal("Voice chat system not available!"));
                return 0;
            }

            context.getSource().sendSuccess(() -> Component.literal("=== Call System Debug Info ==="), false);
            context.getSource().sendSuccess(() -> Component.literal("Call Manager: Available"), false);

            // Basic statistics
            int activeCalls = callManager.getActiveCalls().size();
            context.getSource().sendSuccess(() -> Component.literal("Active calls: " + activeCalls), false);

            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Failed to show debug info: " + e.getMessage()));
            return 0;
        }
    }

    private static int showActiveCalls(CommandContext<CommandSourceStack> context) {
        try {
            TransponderCallManager callManager = getCallManager();

            if (callManager == null) {
                context.getSource().sendFailure(Component.literal("Voice chat system not available!"));
                return 0;
            }

            context.getSource().sendSuccess(() -> Component.literal("=== Active Calls ==="), false);

            int activeCallCount = callManager.getActiveCalls().size();
            context.getSource().sendSuccess(() -> Component.literal("Number of active calls: " + activeCallCount), false);

            if (activeCallCount == 0) {
                context.getSource().sendSuccess(() -> Component.literal("No active calls"), false);
            }

            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Failed to show active calls: " + e.getMessage()));
            return 0;
        }
    }

    private static int showSnailRegistry(CommandContext<CommandSourceStack> context) {
        try {
            SnailNumberRegistry registry = SnailNumberRegistry.getInstance();

            if (registry == null) {
                context.getSource().sendFailure(Component.literal("Snail registry not available!"));
                return 0;
            }

            context.getSource().sendSuccess(() -> Component.literal("=== Snail Registry Info ==="), false);
            context.getSource().sendSuccess(() -> Component.literal("Assigned snails: " + registry.getAssignedCount()), false);
            context.getSource().sendSuccess(() -> Component.literal("Available numbers: " + registry.getAvailableCount()), false);

            // Print detailed registry info to console
            registry.debugPrintState();

            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Failed to show registry info: " + e.getMessage()));
            return 0;
        }
    }

    private static int showNearbySnails(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();

            context.getSource().sendSuccess(() -> Component.literal("=== Nearby Transponder Snails ==="), false);

            List<TransponderSnailBlockEntity> nearbySnails = findAllNearbySnails(player, 10);

            if (nearbySnails.isEmpty()) {
                context.getSource().sendSuccess(() -> Component.literal("No Transponder Snails found within 10 blocks"), false);
            } else {
                for (TransponderSnailBlockEntity snail : nearbySnails) {
                    BlockPos pos = snail.getBlockPos();
                    String status = "Unknown";

                    if (snail.hasAssignedNumber()) {
                        status = "Snail #" + snail.getSnailNumber();

                        // Add call status
                        if (snail.isRinging()) {
                            status += " (Ringing)";
                        } else if (snail.getActiveCallId() != null) {
                            status += " (In Call)";
                        } else {
                            status += " (Idle)";
                        }
                    } else {
                        status = "Unregistered";
                    }

                    final String finalStatus = status;
                    context.getSource().sendSuccess(() -> Component.literal("  " + pos + ": " + finalStatus), false);
                }
            }

            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Failed to show nearby snails: " + e.getMessage()));
            return 0;
        }
    }

    private static int showPlayerCallStatus(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            TransponderCallManager callManager = getCallManager();

            if (callManager == null) {
                context.getSource().sendFailure(Component.literal("Voice chat system not available!"));
                return 0;
            }

            boolean inCall = callManager.isInCall(player.getUUID());

            context.getSource().sendSuccess(() -> Component.literal("=== Your Call Status ==="), false);
            context.getSource().sendSuccess(() -> Component.literal("In call: " + (inCall ? "Yes" : "No")), false);

            if (inCall) {
                UUID callId = callManager.getPlayerCallId(player.getUUID());
                if (callId != null) {
                    context.getSource().sendSuccess(() -> Component.literal("Call ID: " + callId.toString().substring(0, 8)), false);
                }

                // Try to find which snail they're using
                TransponderSnailBlockEntity nearbySnail = findNearbySnailBlockEntity(player);
                if (nearbySnail != null && nearbySnail.hasAssignedNumber()) {
                    context.getSource().sendSuccess(() -> Component.literal("Using snail #" + nearbySnail.getSnailNumber()), false);

                    String snailStatus = "Idle";
                    if (nearbySnail.isRinging()) {
                        snailStatus = "Ringing";
                    } else if (nearbySnail.getActiveCallId() != null) {
                        snailStatus = "Connected";
                    }

                    final String finalSnailStatus = snailStatus;
                    context.getSource().sendSuccess(() -> Component.literal("Snail status: " + finalSnailStatus), false);
                }
            }

            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Failed to show player status: " + e.getMessage()));
            return 0;
        }
    }

    private static int testRing(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();

            // Find nearby snail
            TransponderSnailBlockEntity snailEntity = findNearbySnailBlockEntity(player);
            if (snailEntity == null) {
                player.sendSystemMessage(Component.literal("No Transponder Snail nearby!"));
                return 0;
            }

            // Test ringing state
            snailEntity.setRinging(true);
            snailEntity.setActiveCall(UUID.randomUUID()); // Set a test call ID

            String snailNumberText = snailEntity.hasAssignedNumber() ? "#" + snailEntity.getSnailNumber() : "unregistered";
            player.sendSystemMessage(Component.literal("Test: Set snail " + snailNumberText + " to ringing"));

            // Auto-stop ringing after 5 seconds
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    snailEntity.setRinging(false);
                    snailEntity.setActiveCall(null);
                    player.sendSystemMessage(Component.literal("Test: Stopped ringing for snail " + snailNumberText));
                }
            }, 5000);

            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Failed to test ring: " + e.getMessage()));
            return 0;
        }
    }

    // =================== HELPER METHODS ===================

    /**
     * Gets the call manager instance
     */
    private static TransponderCallManager getCallManager() {
        return TransponderSnails.getCallManager();
    }

    /**
     * Finds the nearest Transponder Snail block entity to a player
     */
    private static TransponderSnailBlockEntity findNearbySnailBlockEntity(ServerPlayer player) {
        BlockPos playerPos = player.blockPosition();

        // Search in a 3x3x3 area around the player
        for (int x = -3; x <= 3; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -3; z <= 3; z++) {
                    BlockPos checkPos = playerPos.offset(x, y, z);

                    if (player.level().getBlockState(checkPos).getBlock() instanceof TransponderSnailBlock) {
                        BlockEntity blockEntity = player.level().getBlockEntity(checkPos);
                        if (blockEntity instanceof TransponderSnailBlockEntity snailEntity) {
                            return snailEntity;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Finds all Transponder Snail block entities within a specified range
     */
    private static List<TransponderSnailBlockEntity> findAllNearbySnails(ServerPlayer player, int range) {
        List<TransponderSnailBlockEntity> snails = new ArrayList<>();
        BlockPos playerPos = player.blockPosition();

        for (int x = -range; x <= range; x++) {
            for (int y = -range/2; y <= range/2; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos checkPos = playerPos.offset(x, y, z);

                    if (player.level().getBlockState(checkPos).getBlock() instanceof TransponderSnailBlock) {
                        BlockEntity blockEntity = player.level().getBlockEntity(checkPos);
                        if (blockEntity instanceof TransponderSnailBlockEntity snailEntity) {
                            snails.add(snailEntity);
                        }
                    }
                }
            }
        }
        return snails;
    }

    /**
     * Finds the position of a nearby Transponder Snail block (for backward compatibility)
     */
    private static BlockPos findNearbyTransponderSnail(ServerPlayer player) {
        TransponderSnailBlockEntity snailEntity = findNearbySnailBlockEntity(player);
        return snailEntity != null ? snailEntity.getBlockPos() : null;
    }
}