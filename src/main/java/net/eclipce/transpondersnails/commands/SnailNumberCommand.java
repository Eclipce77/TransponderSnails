package net.eclipce.transpondersnails.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.eclipce.transpondersnails.data.SnailNumberRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Debug command system for the SnailNumberRegistry
 * Usage: /snailnumber <subcommand> [args]
 */
@Mod.EventBusSubscriber
public class SnailNumberCommand {

    // Track pending clear confirmations - maps player UUID to timestamp
    private static final Map<UUID, Long> pendingClearConfirmations = new ConcurrentHashMap<>();
    private static final long CONFIRMATION_TIMEOUT_MS = 30000; // 30 seconds

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("snailnumber")
                .requires(source -> source.hasPermission(2)) // Requires OP level 2

                // /snailnumber list - List all assigned numbers
                .then(Commands.literal("list")
                        .executes(SnailNumberCommand::listAssignedNumbers)
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(SnailNumberCommand::listAssignedNumbersPaged)))

                // /snailnumber stats - Show registry statistics
                .then(Commands.literal("stats")
                        .executes(SnailNumberCommand::showStats))

                // /snailnumber lookup <number> - Look up what snail owns a number
                .then(Commands.literal("lookup")
                        .then(Commands.argument("number", IntegerArgumentType.integer(1000, 9999))
                                .executes(SnailNumberCommand::lookupNumber)))

                // /snailnumber find <player> - Find all snails owned by a player
                .then(Commands.literal("find")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(SnailNumberCommand::findPlayerSnails)))

                // /snailnumber remove <number> - Remove a number assignment (ADMIN ONLY)
                .then(Commands.literal("remove")
                        .requires(source -> source.hasPermission(3)) // Requires OP level 3
                        .then(Commands.argument("number", IntegerArgumentType.integer(1000, 9999))
                                .executes(SnailNumberCommand::removeNumber)))

                // /snailnumber clear - Clear all assignments (ADMIN ONLY)
                .then(Commands.literal("clear")
                        .requires(source -> source.hasPermission(4)) // Requires OP level 4
                        .executes(SnailNumberCommand::requestClearConfirmation)
                        .then(Commands.literal("Y")
                                .executes(SnailNumberCommand::confirmClearAllNumbers))
                        .then(Commands.literal("N")
                                .executes(SnailNumberCommand::cancelClearAllNumbers)))

                // /snailnumber help - Show help information
                .then(Commands.literal("help")
                        .executes(SnailNumberCommand::showHelp))
        );
    }

    /**
     * Lists all assigned snail numbers with pagination
     */
    private static int listAssignedNumbers(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return listAssignedNumbersPaged(context, 1);
    }

    private static int listAssignedNumbersPaged(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int page = IntegerArgumentType.getInteger(context, "page");
        return listAssignedNumbersPaged(context, page);
    }

    private static int listAssignedNumbersPaged(CommandContext<CommandSourceStack> context, int page) throws CommandSyntaxException {
        SnailNumberRegistry registry = SnailNumberRegistry.getInstance();
        if (registry == null) {
            context.getSource().sendFailure(Component.literal("Registry not available!"));
            return 0;
        }

        // Get all assignments and sort by number
        List<Map.Entry<Integer, UUID>> assignments = new ArrayList<>();
        for (int i = 1000; i <= 9999; i++) {
            UUID snailUUID = registry.getSnailByNumber(i);
            if (snailUUID != null) {
                assignments.add(Map.entry(i, snailUUID));
            }
        }

        if (assignments.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No snail numbers are currently assigned."), false);
            return 1;
        }

        // Pagination
        int itemsPerPage = 10;
        int totalPages = (int) Math.ceil((double) assignments.size() / itemsPerPage);
        page = Math.max(1, Math.min(page, totalPages)); // Clamp page to valid range

        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, assignments.size());

        // Header
        Component header = Component.literal("=== Assigned Snail Numbers (Page " + page + "/" + totalPages + ") ===")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        context.getSource().sendSuccess(() -> header, false);

        // List assignments for this page
        for (int i = startIndex; i < endIndex; i++) {
            Map.Entry<Integer, UUID> assignment = assignments.get(i);
            int number = assignment.getKey();
            UUID uuid = assignment.getValue();

            Component numberComponent = Component.literal("#" + number)
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);

            Component uuidComponent = Component.literal(uuid.toString().substring(0, 8) + "...")
                    .withStyle(ChatFormatting.GRAY)
                    .withStyle(style -> style
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("Full UUID: " + uuid.toString())))
                            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, uuid.toString())));

            Component line = Component.literal("  ")
                    .append(numberComponent)
                    .append(Component.literal(" → ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(uuidComponent);

            context.getSource().sendSuccess(() -> line, false);
        }

        // Navigation footer
        if (totalPages > 1) {
            Component nav = Component.literal("Use ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("/snailnumber list <page>").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" to navigate pages").withStyle(ChatFormatting.GRAY));
            context.getSource().sendSuccess(() -> nav, false);
        }

        return 1;
    }

    /**
     * Shows registry statistics
     */
    private static int showStats(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        SnailNumberRegistry registry = SnailNumberRegistry.getInstance();
        if (registry == null) {
            context.getSource().sendFailure(Component.literal("Registry not available!"));
            return 0;
        }

        int assigned = registry.getAssignedCount();
        int available = registry.getAvailableCount();
        int total = assigned + available;
        double percentUsed = (double) assigned / total * 100;

        Component header = Component.literal("=== Snail Number Registry Statistics ===")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);

        Component assignedLine = Component.literal("Assigned: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(assigned))
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));

        Component availableLine = Component.literal("Available: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(available))
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));

        Component totalLine = Component.literal("Total Capacity: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(total))
                        .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));

        Component percentLine = Component.literal("Usage: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.format("%.1f%%", percentUsed))
                        .withStyle(percentUsed > 80 ? ChatFormatting.RED :
                                        percentUsed > 50 ? ChatFormatting.YELLOW : ChatFormatting.GREEN,
                                ChatFormatting.BOLD));

        context.getSource().sendSuccess(() -> header, false);
        context.getSource().sendSuccess(() -> assignedLine, false);
        context.getSource().sendSuccess(() -> availableLine, false);
        context.getSource().sendSuccess(() -> totalLine, false);
        context.getSource().sendSuccess(() -> percentLine, false);

        // Warn if getting full
        if (percentUsed > 90) {
            Component warning = Component.literal("⚠ Warning: Registry is nearly full!")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            context.getSource().sendSuccess(() -> warning, false);
        }

        return 1;
    }

    /**
     * Looks up which snail owns a specific number
     */
    private static int lookupNumber(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int number = IntegerArgumentType.getInteger(context, "number");

        SnailNumberRegistry registry = SnailNumberRegistry.getInstance();
        if (registry == null) {
            context.getSource().sendFailure(Component.literal("Registry not available!"));
            return 0;
        }

        UUID snailUUID = registry.getSnailByNumber(number);
        if (snailUUID == null) {
            Component message = Component.literal("Snail number #" + number + " is not assigned to any snail.")
                    .withStyle(ChatFormatting.YELLOW);
            context.getSource().sendSuccess(() -> message, false);
            return 1;
        }

        Component numberComponent = Component.literal("#" + number)
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);

        Component uuidComponent = Component.literal(snailUUID.toString())
                .withStyle(ChatFormatting.GRAY)
                .withStyle(style -> style
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Click to copy UUID")))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, snailUUID.toString())));

        Component message = Component.literal("Snail number ")
                .append(numberComponent)
                .append(Component.literal(" is assigned to: "))
                .append(uuidComponent);

        context.getSource().sendSuccess(() -> message, false);
        return 1;
    }

    /**
     * Finds all snails associated with a player (placeholder - would need player tracking)
     */
    private static int findPlayerSnails(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");

        // This is a placeholder implementation since we don't track player ownership
        // In a full implementation, you might track who first activated each snail
        Component message = Component.literal("Player snail tracking is not yet implemented. ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("Snail numbers are tied to individual items, not players.")
                        .withStyle(ChatFormatting.GRAY));

        context.getSource().sendSuccess(() -> message, false);
        return 1;
    }

    /**
     * Removes a number assignment (DANGEROUS)
     */
    private static int removeNumber(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int number = IntegerArgumentType.getInteger(context, "number");

        SnailNumberRegistry registry = SnailNumberRegistry.getInstance();
        if (registry == null) {
            context.getSource().sendFailure(Component.literal("Registry not available!"));
            return 0;
        }

        UUID snailUUID = registry.getSnailByNumber(number);
        if (snailUUID == null) {
            context.getSource().sendFailure(Component.literal("Number #" + number + " is not assigned!"));
            return 0;
        }

        boolean removed = registry.removeSnailAssignment(snailUUID);
        if (removed) {
            Component message = Component.literal("Successfully removed assignment for snail number #" + number)
                    .withStyle(ChatFormatting.GREEN);
            context.getSource().sendSuccess(() -> message, false);

            Component warning = Component.literal("⚠ Warning: This may break existing calls using that number!")
                    .withStyle(ChatFormatting.RED);
            context.getSource().sendSuccess(() -> warning, false);

            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Failed to remove assignment!"));
            return 0;
        }
    }

    /**
     * Requests confirmation for clearing all numbers
     */
    private static int requestClearConfirmation(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        UUID playerId = player.getUUID();

        SnailNumberRegistry registry = SnailNumberRegistry.getInstance();
        if (registry == null) {
            context.getSource().sendFailure(Component.literal("Registry not available!"));
            return 0;
        }

        int assignedCount = registry.getAssignedCount();
        if (assignedCount == 0) {
            context.getSource().sendSuccess(() ->
                    Component.literal("No snail numbers are assigned - nothing to clear.")
                            .withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        // Store confirmation request with timestamp
        pendingClearConfirmations.put(playerId, System.currentTimeMillis());

        // Warning messages
        Component header = Component.literal("⚠ DANGER ⚠")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

        Component warning1 = Component.literal("This will permanently delete ALL " + assignedCount + " ACTIVE SNAIL NUMBERS!")
                .withStyle(ChatFormatting.RED);

        Component warning2 = Component.literal("• All Transponder Snail numbers will be removed")
                .withStyle(ChatFormatting.YELLOW);

        Component warning3 = Component.literal("• All active calls will be terminated")
                .withStyle(ChatFormatting.YELLOW);

        Component prompt = Component.literal("Are you absolutely sure? This action cannot be undone!")
                .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);

        // Confirmation buttons
        Component yesButton = Component.literal("[YES - DELETE ALL]")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                .withStyle(style -> style
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Click to permanently delete all snail numbers")))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/snailnumber clear Y")));

        Component noButton = Component.literal("[NO]")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                .withStyle(style -> style
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Click to cancel and keep all snail numbers")))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/snailnumber clear N")));

        Component buttons = Component.literal("Click: ")
                .withStyle(ChatFormatting.WHITE)
                .append(yesButton)
                .append(Component.literal("  ").withStyle(ChatFormatting.WHITE))
                .append(noButton);

        Component timeout = Component.literal("This confirmation will expire in 30 seconds.")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);

        // Send all messages
        context.getSource().sendSuccess(() -> header, false);
        context.getSource().sendSuccess(() -> warning1, false);
        context.getSource().sendSuccess(() -> warning2, false);
        context.getSource().sendSuccess(() -> warning3, false);
        context.getSource().sendSuccess(() -> Component.literal(""), false); // Empty line
        context.getSource().sendSuccess(() -> prompt, false);
        context.getSource().sendSuccess(() -> buttons, false);
        context.getSource().sendSuccess(() -> timeout, false);

        return 1;
    }

    /**
     * Confirms and executes the clear all operation
     */
    private static int confirmClearAllNumbers(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        UUID playerId = player.getUUID();

        // Check if there's a pending confirmation
        Long confirmationTime = pendingClearConfirmations.get(playerId);
        if (confirmationTime == null) {
            context.getSource().sendFailure(Component.literal("No clear operation pending. Use /snailnumber clear first."));
            return 0;
        }

        // Check if confirmation has expired
        long currentTime = System.currentTimeMillis();
        if (currentTime - confirmationTime > CONFIRMATION_TIMEOUT_MS) {
            pendingClearConfirmations.remove(playerId);
            context.getSource().sendFailure(Component.literal("Confirmation expired. Use /snailnumber clear to try again."));
            return 0;
        }

        // Remove the pending confirmation
        pendingClearConfirmations.remove(playerId);

        // Execute the clear
        SnailNumberRegistry registry = SnailNumberRegistry.getInstance();
        if (registry == null) {
            context.getSource().sendFailure(Component.literal("Registry not available!"));
            return 0;
        }

        int clearedCount = registry.clearAllAssignments();

        // Success messages
        Component success = Component.literal("✓ Successfully cleared all snail number assignments!")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);

        Component details = Component.literal("Deleted " + clearedCount + " snail number assignments.")
                .withStyle(ChatFormatting.YELLOW);

        Component note = Component.literal("All Transponder Snails will need to be reactivated to get new numbers.")
                .withStyle(ChatFormatting.GRAY);

        context.getSource().sendSuccess(() -> success, false);
        context.getSource().sendSuccess(() -> details, false);
        context.getSource().sendSuccess(() -> note, false);

        // Log the admin action
        String adminName = player.getName().getString();
        System.out.println("ADMIN ACTION: " + adminName + " (" + playerId + ") cleared all snail number assignments (" + clearedCount + " numbers)");

        return 1;
    }

    /**
     * Cancels the clear all operation
     */
    private static int cancelClearAllNumbers(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        UUID playerId = player.getUUID();

        // Remove any pending confirmation
        boolean hadPending = pendingClearConfirmations.remove(playerId) != null;

        Component message = hadPending
                ? Component.literal("Clear operation cancelled. All snail numbers are safe.")
                .withStyle(ChatFormatting.GREEN)
                : Component.literal("No clear operation was pending.")
                .withStyle(ChatFormatting.YELLOW);

        context.getSource().sendSuccess(() -> message, false);
        return 1;
    }

    /**
     * Shows help information
     */
    private static int showHelp(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Component header = Component.literal("=== Snail Number Commands ===")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);

        Component[] helpLines = {
                Component.literal("/snailnumber list [page]").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(" - List all assigned numbers").withStyle(ChatFormatting.GRAY)),

                Component.literal("/snailnumber stats").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(" - Show registry statistics").withStyle(ChatFormatting.GRAY)),

                Component.literal("/snailnumber lookup <number>").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(" - Find which snail owns a number").withStyle(ChatFormatting.GRAY)),

                Component.literal("/snailnumber find <player>").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(" - Find all snails owned by a player").withStyle(ChatFormatting.GRAY)),

                Component.literal("/snailnumber remove <number>").withStyle(ChatFormatting.RED)
                        .append(Component.literal(" - Remove a number assignment (OP 3)").withStyle(ChatFormatting.GRAY)),

                Component.literal("/snailnumber clear").withStyle(ChatFormatting.DARK_RED)
                        .append(Component.literal(" - Clear all assignments (requires Y/N confirmation) (OP 4)").withStyle(ChatFormatting.GRAY)),
        };

        context.getSource().sendSuccess(() -> header, false);
        for (Component line : helpLines) {
            context.getSource().sendSuccess(() -> line, false);
        }

        return 1;
    }
}