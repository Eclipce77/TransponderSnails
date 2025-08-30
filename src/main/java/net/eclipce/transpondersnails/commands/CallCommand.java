package net.eclipce.transpondersnails.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.eclipce.transpondersnails.screen.DialingMenu;
import net.eclipce.transpondersnails.screen.DialingMenuProvider;
import net.eclipce.transpondersnails.TransponderSnails;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.eclipce.transpondersnails.voice.server.TransponderCallManager;
import net.eclipce.transpondersnails.voice.server.TransponderCall;
import net.eclipce.transpondersnails.voice.server.CallType;
import net.eclipce.transpondersnails.block.custom.TransponderSnailBlock;
import net.minecraftforge.network.NetworkHooks;

import java.util.*;

public class CallCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("call")
                // Call functionality
                .then(Commands.argument("players", EntityArgument.players())
                        .executes(CallCommand::executeCall))

                // Accept call
                .then(Commands.literal("accept")
                        .executes(CallCommand::acceptCall))

                // GUI test commands
                .then(Commands.literal("gui")
                        .then(Commands.literal("dialing")
                                .executes(CallCommand::openDialingGUI))
                        .then(Commands.literal("incoming")
                                .executes(CallCommand::openIncomingCallGUI))
                        .then(Commands.literal("active")
                                .executes(CallCommand::openActiveCallGUI)))

                // Call management commands
                .then(Commands.literal("end")
                        .executes(CallCommand::endCall))
                .then(Commands.literal("mute")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(CallCommand::mutePlayer)))
                .then(Commands.literal("unmute")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(CallCommand::unmutePlayer)))

                // Debug commands
                .then(Commands.literal("debug")
                        .then(Commands.literal("info")
                                .executes(CallCommand::showDebugInfo))
                        .then(Commands.literal("calls")
                                .executes(CallCommand::showActiveCalls))
                        .then(Commands.literal("audio")
                                .executes(CallCommand::showAudioInfo)))
        );
    }

    private static int executeCall(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer caller = context.getSource().getPlayerOrException();
            Collection<ServerPlayer> callees = EntityArgument.getPlayers(context, "players");

            TransponderCallManager callManager = getCallManager();
            if (callManager == null) {
                context.getSource().sendFailure(Component.literal("Voice chat system not available!"));
                return 0;
            }

            if (callees.size() == 1) {
                // Simple 2-person call
                ServerPlayer callee = callees.iterator().next();
                BlockPos nearbySnail = findNearbyTransponderSnail(caller);

                boolean success;
                if (nearbySnail != null) {
                    success = callManager.initiateCall(caller, callee, nearbySnail);
                } else {
                    success = callManager.initiateCall(caller, callee);
                }

                if (success) {
                    caller.sendSystemMessage(Component.literal("Calling " + callee.getName().getString() + "..."));
                    return Command.SINGLE_SUCCESS;
                } else {
                    context.getSource().sendFailure(Component.literal("Failed to initiate call"));
                    return 0;
                }
            } else {
                // Multi-party call
                CallType callType = findNearbyTransponderSnail(caller) != null ? CallType.LOCATIONAL : CallType.PERSONAL;
                UUID callId = callManager.createCall(caller, new ArrayList<>(callees), callType);

                if (callId != null) {
                    if (callType == CallType.LOCATIONAL) {
                        BlockPos snailPos = findNearbyTransponderSnail(caller);
                        TransponderCall call = callManager.getCall(callId);
                        if (call != null && snailPos != null) {
                            call.setLocation(caller.serverLevel(), snailPos);
                        }
                    }
                    caller.sendSystemMessage(Component.literal("Started " + callType.toString().toLowerCase() +
                            " call with " + callees.size() + " participants"));
                    return Command.SINGLE_SUCCESS;
                }
            }

            return 0;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Failed to execute call command: " + e.getMessage()));
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

            if (callManager.acceptCall(player)) {
                player.sendSystemMessage(Component.literal("Call accepted!"));
                return Command.SINGLE_SUCCESS;
            } else {
                player.sendSystemMessage(Component.literal("No incoming call to accept"));
                return 0;
            }

        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Failed to accept call: " + e.getMessage()));
            return 0;
        }
    }

    private static int openDialingGUI(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();

            // Open the dialing GUI
            NetworkHooks.openScreen(player, new DialingMenuProvider());

            player.sendSystemMessage(Component.literal("Opening dialing GUI..."));
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Failed to open dialing GUI: " + e.getMessage()));
            return 0;
        }
    }

    private static int openIncomingCallGUI(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();

            // Create a test incoming call GUI
            // You'll need to implement IncomingCallMenuProvider
            // NetworkHooks.openScreen(player, new IncomingCallMenuProvider("Test Caller"));

            player.sendSystemMessage(Component.literal("Incoming call GUI not implemented yet"));
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Failed to open incoming call GUI: " + e.getMessage()));
            return 0;
        }
    }

    private static int openActiveCallGUI(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();

            // Create a test active call GUI
            // You'll need to implement ActiveCallMenuProvider
            // NetworkHooks.openScreen(player, new ActiveCallMenuProvider());

            player.sendSystemMessage(Component.literal("Active call GUI not implemented yet"));
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Failed to open active call GUI: " + e.getMessage()));
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

            callManager.leaveCall(player);
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Failed to end call: " + e.getMessage()));
            return 0;
        }
    }

    private static int mutePlayer(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");
            TransponderCallManager callManager = getCallManager();

            if (callManager == null) {
                context.getSource().sendFailure(Component.literal("Voice chat system not available!"));
                return 0;
            }

            if (callManager.setParticipantMuted(player, targetPlayer.getUUID(), true)) {
                player.sendSystemMessage(Component.literal("Muted " + targetPlayer.getName().getString()));
                return Command.SINGLE_SUCCESS;
            } else {
                player.sendSystemMessage(Component.literal("Failed to mute player or not in same call"));
                return 0;
            }

        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Failed to mute player: " + e.getMessage()));
            return 0;
        }
    }

    private static int unmutePlayer(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");
            TransponderCallManager callManager = getCallManager();

            if (callManager == null) {
                context.getSource().sendFailure(Component.literal("Voice chat system not available!"));
                return 0;
            }

            if (callManager.setParticipantMuted(player, targetPlayer.getUUID(), false)) {
                player.sendSystemMessage(Component.literal("Unmuted " + targetPlayer.getName().getString()));
                return Command.SINGLE_SUCCESS;
            } else {
                player.sendSystemMessage(Component.literal("Failed to unmute player or not in same call"));
                return 0;
            }

        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Failed to unmute player: " + e.getMessage()));
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

            Map<String, Object> debugInfo = callManager.getDebugInfo();

            context.getSource().sendSuccess(() -> Component.literal("=== Call System Debug Info ==="), false);
            context.getSource().sendSuccess(() -> Component.literal("Active Calls: " + debugInfo.get("activeCalls")), false);
            context.getSource().sendSuccess(() -> Component.literal("Players in Calls: " + debugInfo.get("playersInCalls")), false);
            context.getSource().sendSuccess(() -> Component.literal("Pending Invites: " + debugInfo.get("pendingInvites")), false);

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

            Collection<TransponderCall> activeCalls = callManager.getActiveCalls();

            context.getSource().sendSuccess(() -> Component.literal("=== Active Calls ==="), false);

            if (activeCalls.isEmpty()) {
                context.getSource().sendSuccess(() -> Component.literal("No active calls"), false);
            } else {
                final int[] callNumber = {1}; // Use array to make it effectively final
                for (TransponderCall call : activeCalls) {
                    Map<String, Object> debugInfo = call.getDebugInfo();
                    final int currentCallNumber = callNumber[0]; // Capture current value
                    context.getSource().sendSuccess(() -> Component.literal(
                            "Call " + currentCallNumber + ": " + debugInfo.get("callType") +
                                    " with " + debugInfo.get("participantCount") + " participants"
                    ), false);
                    context.getSource().sendSuccess(() -> Component.literal(
                            "  Duration: " + debugInfo.get("duration") +
                                    ", Location: " + debugInfo.get("location")
                    ), false);
                    context.getSource().sendSuccess(() -> Component.literal(
                            "  Participants: " + debugInfo.get("participants")
                    ), false);
                    callNumber[0]++;
                }
            }

            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Failed to show active calls: " + e.getMessage()));
            return 0;
        }
    }

    private static int showAudioInfo(CommandContext<CommandSourceStack> context) {
        try {
            TransponderCallManager callManager = getCallManager();

            if (callManager == null) {
                context.getSource().sendFailure(Component.literal("Voice chat system not available!"));
                return 0;
            }

            Map<String, Object> debugInfo = callManager.getDebugInfo();

            context.getSource().sendSuccess(() -> Component.literal("=== Audio System Info ==="), false);

            @SuppressWarnings("unchecked")
            Map<UUID, String> audioChannels = (Map<UUID, String>) debugInfo.get("audioChannels");
            if (audioChannels != null) {
                context.getSource().sendSuccess(() -> Component.literal("Audio Channels: " + audioChannels.size()), false);
            }

            @SuppressWarnings("unchecked")
            Map<UUID, String> audioSessions = (Map<UUID, String>) debugInfo.get("audioSessions");
            if (audioSessions != null) {
                context.getSource().sendSuccess(() -> Component.literal("Audio Sessions: " + audioSessions.size()), false);
            }

            @SuppressWarnings("unchecked")
            Map<UUID, String> activeSounds = (Map<UUID, String>) debugInfo.get("activeSounds");
            if (activeSounds != null) {
                context.getSource().sendSuccess(() -> Component.literal("Active Sounds: " + activeSounds.size()), false);
            }

            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Failed to show audio info: " + e.getMessage()));
            return 0;
        }
    }

    // Helper methods
    private static TransponderCallManager getCallManager() {
        return TransponderSnails.getCallManager();
    }

    private static BlockPos findNearbyTransponderSnail(ServerPlayer player) {
        BlockPos playerPos = player.blockPosition();
        for (int x = -3; x <= 3; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -3; z <= 3; z++) {
                    BlockPos checkPos = playerPos.offset(x, y, z);
                    if (player.level().getBlockState(checkPos).getBlock() instanceof TransponderSnailBlock) {
                        return checkPos;
                    }
                }
            }
        }
        return null;
    }
}