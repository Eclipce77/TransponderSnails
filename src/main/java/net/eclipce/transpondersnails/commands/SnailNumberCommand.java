package net.eclipce.transpondersnails.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.data.CallLogRegistry;
import net.eclipce.transpondersnails.data.SnailNumberRegistry;
import net.eclipce.transpondersnails.voice.server.TransponderCallManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.Charset;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Admin command for Snail Numbers.
 *
 * Usage: /snailnumber <find|help|history|list|logs|lookup|remove|stats> [args]
 *
 * Layout: players always get the symbol layout. The server console gets it too when its output charset can
 * draw the symbols, otherwise it gets a plain-text layout with the same information (see Sym). Nothing is
 * bold, and nothing depends on hover text or buttons; the remove-all confirmation is typed, not clicked.
 * The only click event is on the teleport command that find prints, and its visible text is the command itself.
 *
 * Force a layout for the console with the JVM flag -Dtranspondersnails.console=unicode or =ascii.
 *
 * Permissions: read-only commands need OP 2, "remove <number>" needs OP 3, "remove all" (and its confirm /
 * cancel) needs OP 4. The server console counts as OP 4.
 */
@Mod.EventBusSubscriber
public class SnailNumberCommand {

    // Pending "remove all" confirmations: command source -> time the prompt was shown.
    // The key is the player's UUID, or the source name for the console / RCON / command blocks.
    private static final Map<String, Long> pendingRemoveAll = new ConcurrentHashMap<>();
    private static final long CONFIRMATION_TIMEOUT_MS = 30000; // 30 seconds

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    // ---- Symbols. Basic Multilingual Plane only, so the default Minecraft font can draw them.
    // Written as unicode escapes so this file stays ASCII whatever encoding the compiler assumes.
    private static final String PHONE = "\u260E";   // telephone, marks titles
    private static final String OK = "\u2714";      // check mark
    private static final String NO = "\u2718";      // cross
    private static final String WARN = "\u26A0";    // warning sign
    private static final String DOT = "\u2022";     // bullet
    private static final String OUT = "\u2192";     // right arrow: outgoing call / "to"
    private static final String IN = "\u2190";      // left arrow: incoming call
    private static final String HOME = "\u2302";    // house: placed snail
    private static final String PLAYER = "\u263B";  // face: player
    private static final String PIN = "\u2316";     // position indicator: location
    private static final String GO = "\u27A4";      // arrowhead: teleport
    private static final String LIVE = "\u25CF";    // circle: call in progress
    private static final String REDO = "\u21BB";    // circular arrow: registers again
    private static final String RULE = "\u2500";    // horizontal line, for titles
    private static final String MID = "\u00B7";     // middle dot, separator
    private static final String BAR_ON = "\u2588";  // filled block, for the usage bar
    private static final String BAR_OFF = "\u2591"; // light block, for the usage bar

    private static final String ALL_SYMBOLS = PHONE + OK + NO + WARN + DOT + OUT + IN + HOME + PLAYER + PIN
            + GO + LIVE + REDO + RULE + MID + BAR_ON + BAR_OFF;

    // Colors: titles gold, labels and dim text gray, numbers aqua, good green, caution yellow, bad red
    private static final ChatFormatting C_TITLE = ChatFormatting.GOLD;
    private static final ChatFormatting C_DIM = ChatFormatting.GRAY;
    private static final ChatFormatting C_NUM = ChatFormatting.AQUA;
    private static final ChatFormatting C_GOOD = ChatFormatting.GREEN;
    private static final ChatFormatting C_WARN = ChatFormatting.YELLOW;
    private static final ChatFormatting C_BAD = ChatFormatting.RED;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("snailnumber")
                .requires(source -> source.hasPermission(2)) // Requires OP level 2 (the console always passes)

                // /snailnumber find <number> - Where a snail is right now
                .then(Commands.literal("find")
                        .then(Commands.argument("number", IntegerArgumentType.integer(1000, 9999))
                                .executes(SnailNumberCommand::findSnail)))

                // /snailnumber help - Show help information
                .then(Commands.literal("help")
                        .executes(SnailNumberCommand::showHelp))

                // /snailnumber history <number> [count] - Calls placed, received and not connected
                .then(Commands.literal("history")
                        .then(Commands.argument("number", IntegerArgumentType.integer(1000, 9999))
                                .executes(SnailNumberCommand::showHistory)
                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                        .executes(SnailNumberCommand::showHistoryLimited))))

                // /snailnumber list - Every assigned number with its full UUID
                .then(Commands.literal("list")
                        .executes(SnailNumberCommand::listAssignedNumbers))

                // /snailnumber logs [count] - Every call ever placed
                .then(Commands.literal("logs")
                        .executes(SnailNumberCommand::showLogs)
                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                .executes(SnailNumberCommand::showLogsLimited)))

                // /snailnumber lookup <number> - Who owns a number, who used it last, usage history
                .then(Commands.literal("lookup")
                        .then(Commands.argument("number", IntegerArgumentType.integer(1000, 9999))
                                .executes(SnailNumberCommand::lookupNumber)))

                // /snailnumber remove <number>   (ADMIN ONLY)
                // The same word slot also accepts all, *, confirm and cancel (OP 4). They are parsed in
                // removeCommand instead of being literal nodes so tab completion only ever offers <number>.
                .then(Commands.literal("remove")
                        .requires(source -> source.hasPermission(3)) // Requires OP level 3
                        .then(Commands.argument("number", StringArgumentType.greedyString())
                                .executes(SnailNumberCommand::removeCommand)))

                // /snailnumber stats - Show registry statistics
                .then(Commands.literal("stats")
                        .executes(SnailNumberCommand::showStats))
        );
    }

    // =================== SYMBOL LAYOUT ===================

    /**
     * Picks between the symbol layout and the plain-text layout. Both carry the same information:
     * t(symbolText, plainText) returns whichever one applies.
     */
    private static final class Sym {
        private final boolean fancy;

        Sym(boolean fancy) {
            this.fancy = fancy;
        }

        String t(String fancyText, String plainText) {
            return fancy ? fancyText : plainText;
        }

        String sep() {
            return fancy ? " " + MID + " " : " | ";
        }

        String arrow() {
            return fancy ? " " + OUT + " " : " -> ";
        }
    }

    private static final Sym FANCY = new Sym(true);
    private static final Sym PLAIN = new Sym(false);

    private static Boolean detectedConsoleSymbols = null;

    /** True if the console's output charset can draw every symbol above (or the JVM flag forces it). */
    private static boolean consoleSupportsSymbols() {
        String forced = System.getProperty("transpondersnails.console");
        if ("unicode".equalsIgnoreCase(forced)) return true;
        if ("ascii".equalsIgnoreCase(forced)) return false;

        if (detectedConsoleSymbols == null) {
            boolean supported;
            try {
                String name = System.getProperty("stdout.encoding");            // Java 19+
                if (name == null) name = System.getProperty("sun.stdout.encoding"); // Java 17
                Charset charset = name != null ? Charset.forName(name) : Charset.defaultCharset();
                supported = charset.newEncoder().canEncode(ALL_SYMBOLS);
            } catch (RuntimeException e) {
                supported = false;
            }
            detectedConsoleSymbols = supported;
        }
        return detectedConsoleSymbols;
    }

    private static Sym sym(CommandContext<CommandSourceStack> context) {
        boolean player = context.getSource().getEntity() instanceof ServerPlayer;
        return player || consoleSupportsSymbols() ? FANCY : PLAIN;
    }

    // =================== OUTPUT HELPERS ===================

    /** One chat / console line made of colored pieces. */
    private static final class Line {
        private final MutableComponent root = Component.literal("");

        Line add(String text) {
            root.append(Component.literal(text));
            return this;
        }

        Line add(String text, ChatFormatting color) {
            root.append(Component.literal(text).withStyle(color));
            return this;
        }

        Line add(Line other) {
            root.append(other.root);
            return this;
        }
    }

    private static void emit(CommandContext<CommandSourceStack> context, Line line) {
        Component message = line.root;
        context.getSource().sendSuccess(() -> message, false);
    }

    private static void send(CommandContext<CommandSourceStack> context, String text) {
        emit(context, new Line().add(text));
    }

    private static void send(CommandContext<CommandSourceStack> context, String text, ChatFormatting color) {
        emit(context, new Line().add(text, color));
    }

    /** "--- (phone) Title --- extra" in the symbol layout, "=== Title ===  extra" in plain text. */
    private static void title(CommandContext<CommandSourceStack> context, Sym s, String text, String extra) {
        Line line = new Line().add(s.t(RULE + RULE + RULE + " " + PHONE + " " + text + " " + RULE + RULE + RULE,
                "=== " + text + " ==="), C_TITLE);
        if (extra != null) {
            line.add("  " + extra, C_DIM);
        }
        emit(context, line);
    }

    private static int fail(CommandContext<CommandSourceStack> context, String text) {
        context.getSource().sendFailure(Component.literal(sym(context).t(NO + " ", "") + text));
        return 0;
    }

    private static int registryUnavailable(CommandContext<CommandSourceStack> context) {
        return fail(context, "Registry not available!");
    }

    private static String formatTime(long epochMs) {
        return TIME_FORMAT.format(Instant.ofEpochMilli(epochMs));
    }

    private static String formatDuration(long ms) {
        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%dh %02dm %02ds", hours, minutes, seconds);
        }
        if (minutes > 0) {
            return String.format(Locale.ROOT, "%dm %02ds", minutes, seconds);
        }
        return seconds + "s";
    }

    /** Who ran the command, for the server log. */
    private static String sourceName(CommandSourceStack source) {
        Entity entity = source.getEntity();
        if (entity instanceof ServerPlayer player) {
            return player.getName().getString() + " (" + player.getUUID() + ")";
        }
        return source.getTextName();
    }

    /** Key for pending confirmations: each player, and the console / RCON / command blocks, confirm separately. */
    private static String sourceKey(CommandSourceStack source) {
        Entity entity = source.getEntity();
        if (entity instanceof ServerPlayer player) {
            return "player:" + player.getUUID();
        }
        return "source:" + source.getTextName();
    }

    /** "ID  <uuid>" */
    private static void sendId(CommandContext<CommandSourceStack> context, Sym s, UUID uuid) {
        emit(context, new Line().add(s.t("ID  ", "ID: "), C_DIM).add(uuid.toString()));
    }

    /** "(house) placed  (pin) dim x y z" or "(face) held by Steve  (pin) dim x y z" */
    private static Line locationLine(Sym s, TransponderCallManager.SnailLocation location) {
        String where = location.dimension() + " " + location.x() + " " + location.y() + " " + location.z();
        Line line = new Line();
        if (location.placed()) {
            line.add(s.t(HOME + " placed", "Placed at"), C_GOOD);
        } else {
            line.add(s.t(PLAYER + " held by ", "Held by ") + location.holderName(), C_GOOD);
            if (!s.fancy) line.add(" at", C_GOOD);
        }
        return line.add(s.t("  " + PIN + " ", " ") + where);
    }

    /**
     * Prints a ready-to-use teleport command. The text is the command itself, so it reads the same in the
     * console; in chat it also runs when clicked.
     */
    private static void sendTeleport(CommandContext<CommandSourceStack> context, Sym s, String dimension, int x, int y, int z) {
        String command = "/execute in " + dimension + " run tp @s " + x + " " + y + " " + z;
        Line line = new Line().add(s.t(GO + " ", "Teleport: "), C_DIM);
        line.root.append(Component.literal(command)
                .withStyle(ChatFormatting.YELLOW)
                .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))));
        emit(context, line);
    }

    /** Points the admin at the call history when the log knows a number that has no snail any more. */
    private static void sendHistoryHint(CommandContext<CommandSourceStack> context, Sym s, int number) {
        CallLogRegistry log = CallLogRegistry.get();
        if (log != null && !log.getForNumber(number).isEmpty()) {
            emit(context, new Line().add(s.t(PHONE + " old history: ", "Call history from an earlier snail: "), C_DIM)
                    .add("/snailnumber history " + number, C_WARN));
        }
    }

    // =================== FIND ===================

    /**
     * Shows where a snail is: the placed block's location, or the location of the player holding it.
     * Snails that are not loaded fall back to the last location recorded in the call log.
     */
    private static int findSnail(CommandContext<CommandSourceStack> context) {
        int number = IntegerArgumentType.getInteger(context, "number");
        Sym s = sym(context);

        SnailNumberRegistry registry = SnailNumberRegistry.getInstance();
        if (registry == null) {
            return registryUnavailable(context);
        }

        UUID snailUUID = registry.getSnailByNumber(number);
        if (snailUUID == null) {
            send(context, s.t(NO + " ", "") + "#" + number + " is not assigned to any snail.", C_WARN);
            sendHistoryHint(context, s, number);
            return 1;
        }

        title(context, s, "Snail #" + number, null);
        sendId(context, s, snailUUID);

        TransponderCallManager callManager = TransponderSnails.getCallManager();
        TransponderCallManager.SnailLocation location = callManager != null ? callManager.locateSnail(number) : null;

        if (location != null) {
            emit(context, locationLine(s, location));
            sendTeleport(context, s, location.dimension(), location.x(), location.y(), location.z());
            return 1;
        }

        send(context, s.t(NO + " not loaded or not online",
                "Not found (not in a loaded chunk, and not in an online player's inventory)"), C_WARN);

        CallLogRegistry log = CallLogRegistry.get();
        CallLogRegistry.Sighting sighting = log != null ? log.getLastKnownLocation(number) : null;
        if (sighting == null) {
            send(context, s.t(PIN + " last seen: never logged", "Last known location: none recorded"), C_DIM);
            return 1;
        }

        CallLogRegistry.Party party = sighting.party();
        String what = "BLOCK".equals(party.type())
                ? s.t(HOME + " placed", "placed")
                : s.t(PLAYER + " held by ", "held by ") + (party.playerName() != null ? party.playerName() : "an unknown player");
        emit(context, new Line().add(s.t(PIN + " last seen: ", "Last known location: "), C_DIM)
                .add(what + s.t("  ", " at ") + party.dimension() + " " + party.x() + " " + party.y() + " " + party.z()));
        send(context, "  " + formatTime(sighting.timeMs()) + s.sep() + "call with #" + sighting.otherNumber(), C_DIM);
        sendTeleport(context, s, party.dimension(), party.x(), party.y(), party.z());
        return 1;
    }

    // =================== HELP ===================

    private static int showHelp(CommandContext<CommandSourceStack> context) {
        Sym s = sym(context);
        title(context, s, "/snailnumber", null);

        String[][] lines = {
                {"find <number>", "where a snail is + teleport command"},
                {"history <number> [count]", "a number's calls: placed, received, missed"},
                {"list", "every number with its full UUID"},
                {"logs [count]", "every call ever placed"},
                {"lookup <number>", "UUID, last user, usage history"},
                {"remove <number>", "reset one number (OP 3)"},
                {"remove all", "reset every number, asks first (OP 4)"},
                {"remove confirm | cancel", "answer that prompt within 30s (OP 4)"},
                {"stats", "registry usage"},
        };

        for (String[] entry : lines) {
            emit(context, new Line().add("/snailnumber " + entry[0], C_WARN).add(s.arrow() + entry[1], C_DIM));
        }

        if (s.fancy) {
            emit(context, new Line()
                    .add(OUT + " out  " + IN + " in  ", C_DIM)
                    .add(OK + " connected  ", C_GOOD)
                    .add(NO + " not connected  ", C_WARN)
                    .add(LIVE + " in progress", ChatFormatting.AQUA));
        }
        send(context, "Numbers are 1000-9999. Everything works from the console.", C_DIM);
        return 1;
    }

    // =================== HISTORY / LOGS ===================

    private static int showHistory(CommandContext<CommandSourceStack> context) {
        return showHistory(context, IntegerArgumentType.getInteger(context, "number"), 0);
    }

    private static int showHistoryLimited(CommandContext<CommandSourceStack> context) {
        return showHistory(context, IntegerArgumentType.getInteger(context, "number"),
                IntegerArgumentType.getInteger(context, "count"));
    }

    /**
     * Call history for one number: calls it placed, calls it received, and calls that never connected.
     * A number can be handed to a different snail after a removal, so if the log shows more than one snail
     * behind this number the lines are tagged with the first 8 characters of each snail's UUID.
     *
     * @param limit Show only the newest calls; 0 shows everything
     */
    private static int showHistory(CommandContext<CommandSourceStack> context, int number, int limit) {
        Sym s = sym(context);
        CallLogRegistry log = CallLogRegistry.get();
        if (log == null) {
            return registryUnavailable(context);
        }

        List<CallLogRegistry.Entry> entries = log.getForNumber(number);
        if (entries.isEmpty()) {
            send(context, s.t(NO + " ", "") + "No calls logged for #" + number + ".", C_WARN);
            return 1;
        }

        int placed = 0;
        int received = 0;
        int inProgress = 0;
        Map<CallLogRegistry.Outcome, Integer> outcomes = new EnumMap<>(CallLogRegistry.Outcome.class);
        Set<UUID> snails = new LinkedHashSet<>();

        for (CallLogRegistry.Entry entry : entries) {
            boolean outgoing = entry.getCallerNumber() == number;
            if (outgoing) placed++; else received++;

            CallLogRegistry.Party side = outgoing ? entry.getCaller() : entry.getCallee();
            if (side.snailUuid() != null) {
                snails.add(side.snailUuid());
            }

            if (entry.isInProgress()) {
                inProgress++;
            } else {
                outcomes.merge(entry.getOutcome(), 1, Integer::sum);
            }
        }

        int connected = outcomes.getOrDefault(CallLogRegistry.Outcome.ANSWERED, 0);
        int notConnected = entries.size() - inProgress - connected;

        title(context, s, "History #" + number, entries.size() + (entries.size() == 1 ? " call" : " calls"));

        Line summary = new Line()
                .add(s.t(OUT + " ", "Placed ") + placed + s.t(" out", ""), C_NUM)
                .add(s.t("  " + IN + " ", "   Received ") + received + s.t(" in", ""), C_NUM)
                .add(s.t("  " + OK + " ", "   Connected ") + connected, C_GOOD)
                .add(s.t("  " + NO + " ", "   Not connected ") + notConnected, notConnected > 0 ? C_WARN : C_DIM);
        if (notConnected > 0) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<CallLogRegistry.Outcome, Integer> outcome : outcomes.entrySet()) {
                if (outcome.getKey() != CallLogRegistry.Outcome.ANSWERED) {
                    parts.add(outcomeText(outcome.getKey()) + " " + outcome.getValue());
                }
            }
            summary.add(" (" + String.join(", ", parts) + ")", C_DIM);
        }
        if (inProgress > 0) {
            summary.add(s.t("  " + LIVE + " ", "   In progress ") + inProgress, ChatFormatting.AQUA);
        }
        emit(context, summary);

        boolean multipleSnails = snails.size() > 1;
        if (multipleSnails) {
            send(context, s.t(WARN + " #" + number + " has had " + snails.size() + " snails, [id] shows which",
                    "Note: #" + number + " has belonged to " + snails.size() + " different snails; [id] is the first 8 characters of the snail UUID"), C_WARN);
        }

        List<CallLogRegistry.Entry> shown = entries;
        if (limit > 0 && limit < entries.size()) {
            shown = entries.subList(entries.size() - limit, entries.size());
            send(context, "showing the newest " + limit + " of " + entries.size(), C_DIM);
        }

        for (CallLogRegistry.Entry entry : shown) {
            Line line = historyLine(s, entry, number);
            if (multipleSnails) {
                CallLogRegistry.Party side = entry.getCallerNumber() == number ? entry.getCaller() : entry.getCallee();
                line.add("  [" + (side.snailUuid() != null ? side.snailUuid().toString().substring(0, 8) : "unknown") + "]", C_DIM);
            }
            emit(context, line);
        }

        return 1;
    }

    private static int showLogs(CommandContext<CommandSourceStack> context) {
        return showLogs(context, 0);
    }

    private static int showLogsLimited(CommandContext<CommandSourceStack> context) {
        return showLogs(context, IntegerArgumentType.getInteger(context, "count"));
    }

    /**
     * Every call ever placed, oldest first, so the newest calls end up at the bottom of the chat / console.
     *
     * @param limit Show only the newest calls; 0 shows everything
     */
    private static int showLogs(CommandContext<CommandSourceStack> context, int limit) {
        Sym s = sym(context);
        CallLogRegistry log = CallLogRegistry.get();
        if (log == null) {
            return registryUnavailable(context);
        }

        List<CallLogRegistry.Entry> entries = log.getAll();
        if (entries.isEmpty()) {
            send(context, s.t(NO + " ", "") + "The call log is empty.", C_WARN);
            return 1;
        }

        title(context, s, "Call log", entries.size() + (entries.size() == 1 ? " call" : " calls"));

        List<CallLogRegistry.Entry> shown = entries;
        if (limit > 0 && limit < entries.size()) {
            shown = entries.subList(entries.size() - limit, entries.size());
            send(context, "showing the newest " + limit + " of " + entries.size(), C_DIM);
        }

        for (CallLogRegistry.Entry entry : shown) {
            emit(context, logLine(s, entry));
        }

        return 1;
    }

    /** One call, seen from one number: "time  (arrow) #1234  (check) 3m 12s  Steve -> Alex" */
    private static Line historyLine(Sym s, CallLogRegistry.Entry entry, int number) {
        boolean outgoing = entry.getCallerNumber() == number;
        Line line = new Line().add(formatTime(entry.getPlacedAt()), C_DIM).add("  ");
        if (outgoing) {
            line.add(s.t(OUT + " ", "OUT to ") + "#" + entry.getCalleeNumber(), C_NUM);
        } else {
            line.add(s.t(IN + " ", "IN from ") + "#" + entry.getCallerNumber(), C_NUM);
        }
        addResult(line.add("  "), s, entry);
        return line.add("  " + playersText(s, entry), C_DIM);
    }

    /** One call, number to number: "time  #4821 -> #1234  (check) 3m 12s  Steve -> Alex" */
    private static Line logLine(Sym s, CallLogRegistry.Entry entry) {
        Line line = new Line().add(formatTime(entry.getPlacedAt()), C_DIM).add("  ")
                .add("#" + entry.getCallerNumber(), C_NUM)
                .add(s.arrow())
                .add("#" + entry.getCalleeNumber(), C_NUM);
        addResult(line.add("  "), s, entry);
        return line.add("  " + playersText(s, entry), C_DIM);
    }

    /** "(check) 3m 12s" for a connected call, "(cross) missed" and so on for the rest, "(dot) ringing" while live. */
    private static void addResult(Line line, Sym s, CallLogRegistry.Entry entry) {
        if (entry.isInProgress()) {
            line.add(s.t(LIVE + " ", "") + (entry.getConnectedAt() > 0 ? "in call " + formatDuration(entry.getTalkTimeMs()) : "ringing"),
                    ChatFormatting.AQUA);
            return;
        }

        CallLogRegistry.Outcome outcome = entry.getOutcome();
        CallLogRegistry.EndReason reason = entry.getEndReason();

        if (outcome == CallLogRegistry.Outcome.ANSWERED) {
            String text = s.t(OK + " ", "answered ") + formatDuration(entry.getTalkTimeMs());
            if (reason != null && reason != CallLogRegistry.EndReason.HANG_UP && reason != CallLogRegistry.EndReason.UNKNOWN) {
                text += " (" + reasonText(reason) + ")";
            }
            line.add(text, C_GOOD);
            return;
        }

        String text = s.t(NO + " ", "") + outcomeText(outcome);
        if (outcome == CallLogRegistry.Outcome.FAILED && reason != null) {
            text += " (" + reasonText(reason) + ")";
        }
        boolean mild = outcome == CallLogRegistry.Outcome.MISSED || outcome == CallLogRegistry.Outcome.BUSY
                || outcome == CallLogRegistry.Outcome.CANCELLED;
        line.add(text, mild ? C_WARN : C_BAD);
    }

    private static String outcomeText(CallLogRegistry.Outcome outcome) {
        return switch (outcome) {
            case ANSWERED -> "answered";
            case MISSED -> "missed";
            case REJECTED -> "rejected";
            case CANCELLED -> "cancelled";
            case BUSY -> "busy";
            case UNREACHABLE -> "unreachable";
            case NO_SUCH_NUMBER -> "no such number";
            case FAILED -> "failed";
        };
    }

    private static String reasonText(CallLogRegistry.EndReason reason) {
        return switch (reason) {
            case HANG_UP -> "hang up";
            case REJECTED -> "rejected";
            case RING_TIMEOUT -> "no answer";
            case INACTIVITY -> "inactivity";
            case DISCONNECT -> "player left";
            case SNAIL_GONE -> "snail removed";
            case NUMBER_REMOVED -> "number removed";
            case SERVER_STOP -> "server stop";
            case UNKNOWN -> "unknown";
        };
    }

    private static String playersText(Sym s, CallLogRegistry.Entry entry) {
        return partyLabel(s, entry.getCaller()) + s.arrow() + partyLabel(s, entry.getCallee());
    }

    private static String partyLabel(Sym s, CallLogRegistry.Party party) {
        if (party.playerName() != null) {
            return party.playerName();
        }
        return "BLOCK".equals(party.type()) ? s.t(HOME + " placed", "(placed snail)") : "?";
    }

    // =================== LIST ===================

    /**
     * Lists every assigned snail number with its full UUID. Not paged.
     * (In chat only the last ~100 lines stay visible; the server console has no such limit.)
     */
    private static int listAssignedNumbers(CommandContext<CommandSourceStack> context) {
        Sym s = sym(context);
        SnailNumberRegistry registry = SnailNumberRegistry.getInstance();
        if (registry == null) {
            return registryUnavailable(context);
        }

        Map<Integer, UUID> assignments = registry.getAllAssignments();
        if (assignments.isEmpty()) {
            send(context, s.t(NO + " ", "") + "No snail numbers are currently assigned.", C_WARN);
            return 1;
        }

        title(context, s, "Snail numbers", String.valueOf(assignments.size()));
        for (Map.Entry<Integer, UUID> assignment : assignments.entrySet()) {
            emit(context, new Line().add("#" + assignment.getKey(), C_NUM).add("  " + assignment.getValue(), C_DIM));
        }

        return 1;
    }

    // =================== LOOKUP ===================

    /** Per-player usage of one snail, for lookup. */
    private static final class UserStats {
        final String name;
        final UUID playerId;
        int uses;
        long firstUse = Long.MAX_VALUE;
        long lastUse = 0;

        UserStats(String name, UUID playerId) {
            this.name = name;
            this.playerId = playerId;
        }
    }

    /**
     * Everything known about one number: its UUID, where it is, who used it last, and its usage history.
     * A "use" is placing a call from the snail, or picking up a call it received.
     */
    private static int lookupNumber(CommandContext<CommandSourceStack> context) {
        int number = IntegerArgumentType.getInteger(context, "number");
        Sym s = sym(context);

        SnailNumberRegistry registry = SnailNumberRegistry.getInstance();
        if (registry == null) {
            return registryUnavailable(context);
        }

        UUID snailUUID = registry.getSnailByNumber(number);
        if (snailUUID == null) {
            send(context, s.t(NO + " ", "") + "#" + number + " is not assigned to any snail.", C_WARN);
            sendHistoryHint(context, s, number);
            return 1;
        }

        title(context, s, "Snail #" + number, null);
        sendId(context, s, snailUUID);

        TransponderCallManager callManager = TransponderSnails.getCallManager();
        TransponderCallManager.SnailLocation location = callManager != null ? callManager.locateSnail(number) : null;
        if (location == null) {
            emit(context, new Line().add(s.t(NO + " not loaded", "Not loaded"), C_WARN)
                    .add(s.t("  ", " (") + "/snailnumber find " + number + s.t("", ")"), C_DIM));
        } else {
            emit(context, locationLine(s, location));
        }

        CallLogRegistry log = CallLogRegistry.get();
        List<CallLogRegistry.Entry> entries = log != null ? log.getForNumber(number) : new ArrayList<>();

        int placed = 0;
        int received = 0;
        int earlierSnailCalls = 0;
        List<CallLogRegistry.Entry> ownCalls = new ArrayList<>();
        Map<String, UserStats> users = new LinkedHashMap<>();
        CallLogRegistry.Entry lastUse = null;
        CallLogRegistry.Party lastUseParty = null;

        for (CallLogRegistry.Entry entry : entries) {
            boolean outgoing = entry.getCallerNumber() == number;
            CallLogRegistry.Party side = outgoing ? entry.getCaller() : entry.getCallee();

            // Calls that belonged to a previous snail that held this number are not this snail's usage
            if (side.snailUuid() != null && !side.snailUuid().equals(snailUUID)) {
                earlierSnailCalls++;
                continue;
            }

            ownCalls.add(entry);
            if (outgoing) placed++; else received++;

            boolean used = outgoing || entry.getOutcome() == CallLogRegistry.Outcome.ANSWERED;
            if (!used) continue;

            lastUse = entry;
            lastUseParty = side;

            if (side.playerId() != null) {
                UserStats stats = users.computeIfAbsent(side.playerId().toString(),
                        id -> new UserStats(side.playerName() != null ? side.playerName() : "unknown", side.playerId()));
                stats.uses++;
                stats.firstUse = Math.min(stats.firstUse, entry.getPlacedAt());
                stats.lastUse = Math.max(stats.lastUse, entry.getPlacedAt());
            }
        }

        if (lastUse == null) {
            send(context, s.t(PLAYER + " ", "Last used by: ") + "nobody yet", C_DIM);
        } else {
            String who = lastUseParty.playerId() != null
                    ? (lastUseParty.playerName() != null ? lastUseParty.playerName() : "unknown") + " (" + lastUseParty.playerId() + ")"
                    : "an unknown player";
            String what = lastUse.getCallerNumber() == number
                    ? s.t(OUT + " #", "outgoing call to #") + lastUse.getCalleeNumber()
                    : s.t(IN + " #", "answered call from #") + lastUse.getCallerNumber();
            emit(context, new Line().add(s.t(PLAYER + " last used: ", "Last used by: "), C_DIM).add(who)
                    .add(s.t(s.sep(), " on ") + formatTime(lastUse.getPlacedAt()) + s.t(s.sep(), " (") + what + s.t("", ")"), C_DIM));
        }

        emit(context, new Line()
                .add(s.t(PHONE + " ", "Calls: ") + ownCalls.size(), C_NUM)
                .add(s.t("  " + OUT + " ", " (") + placed + s.t(" out", " placed"), C_DIM)
                .add(s.t("  " + IN + " ", ", ") + received + s.t(" in", " received") + s.t("", ")"), C_DIM));

        if (!users.isEmpty()) {
            send(context, s.t("Players", "Players who used this snail (most recent first):"), C_DIM);
            List<UserStats> sorted = new ArrayList<>(users.values());
            sorted.sort((a, b) -> Long.compare(b.lastUse, a.lastUse));
            for (UserStats stats : sorted) {
                emit(context, new Line().add("  " + s.t(DOT, "-") + " " + stats.name)
                        .add(" (" + stats.playerId + ")  " + stats.uses + (stats.uses == 1 ? " use" : " uses")
                                + s.sep() + "first " + formatTime(stats.firstUse)
                                + s.sep() + "last " + formatTime(stats.lastUse), C_DIM));
            }
        }

        if (!ownCalls.isEmpty()) {
            int show = Math.min(5, ownCalls.size());
            send(context, "Recent", C_DIM);
            for (CallLogRegistry.Entry entry : ownCalls.subList(ownCalls.size() - show, ownCalls.size())) {
                emit(context, new Line().add("  ").add(historyLine(s, entry, number)));
            }
            emit(context, new Line().add(s.t(GO + " ", "Full call history: "), C_DIM).add("/snailnumber history " + number, C_WARN));
        }

        if (earlierSnailCalls > 0) {
            send(context, s.t(WARN + " " + earlierSnailCalls + " older calls belong to a previous snail on this number",
                    earlierSnailCalls + " logged calls belong to an earlier snail that held this number and are not counted above."), C_DIM);
        }

        return 1;
    }

    // =================== REMOVE ===================

    /**
     * /snailnumber remove <number | all | * | confirm | cancel>
     *
     * The keywords are parsed here rather than registered as literals, so tab completion only offers
     * <number>. They still need OP 4; a plain number needs OP 3 (checked by the command tree).
     */
    private static int removeCommand(CommandContext<CommandSourceStack> context) {
        String argument = StringArgumentType.getString(context, "number").trim();

        if (argument.matches("\\d{1,9}")) {
            int number = Integer.parseInt(argument);
            if (number < 1000 || number > 9999) {
                return fail(context, "Snail numbers are 1000-9999.");
            }
            return removeNumber(context, number);
        }

        String word = argument.toLowerCase(Locale.ROOT);
        if (word.equals("all") || word.equals("*") || word.equals("confirm") || word.equals("cancel")) {
            if (!context.getSource().hasPermission(4)) {
                return fail(context, "\"remove " + word + "\" needs OP level 4.");
            }
            return switch (word) {
                case "confirm" -> confirmRemoveAll(context);
                case "cancel" -> cancelRemoveAll(context);
                default -> requestRemoveAll(context);
            };
        }

        return fail(context, "Enter a snail number: /snailnumber remove <number>");
    }

    /**
     * Removes one number. The snail behind it loses its identity everywhere it can be reached right now
     * (placed block, inventory item, active call) and registers a brand new number on its next use.
     * Snails that are not loaded are reset when they load or are used, because the old UUID is revoked.
     */
    private static int removeNumber(CommandContext<CommandSourceStack> context, int number) {
        Sym s = sym(context);

        SnailNumberRegistry registry = SnailNumberRegistry.getInstance();
        if (registry == null) {
            return registryUnavailable(context);
        }

        UUID snailUUID = registry.getSnailByNumber(number);
        if (snailUUID == null) {
            return fail(context, "Number #" + number + " is not assigned!");
        }

        // The registry must be updated first: applyRevocation relies on the UUID already being revoked
        if (registry.revokeSnail(snailUUID) == -1) {
            return fail(context, "Failed to remove number #" + number + "!");
        }

        TransponderCallManager callManager = TransponderSnails.getCallManager();
        TransponderCallManager.RevocationReport report = callManager != null
                ? callManager.applyRevocation(List.of(number)) : null;

        send(context, s.t(OK + " Removed #" + number, "Removed snail number #" + number + "."), C_GOOD);
        sendId(context, s, snailUUID);
        if (report != null) {
            if (report.callsEnded() > 0) {
                send(context, s.t(DOT, "-") + " ended the active call");
            }
            for (String placed : report.placedReset()) {
                send(context, s.t(DOT, "-") + " reset placed snail " + placed);
            }
            for (String held : report.heldReset()) {
                send(context, s.t(DOT, "-") + " reset held snail " + held);
            }
        }
        send(context, s.t(REDO + " registers a new number on its next use", "It registers a new number the next time it is used."), C_DIM);

        System.out.println("ADMIN ACTION: " + sourceName(context.getSource()) + " removed snail number #" + number + " (snail " + snailUUID + ")");
        return 1;
    }

    /** First step of "remove all": explains what will happen and waits for "remove confirm". */
    private static int requestRemoveAll(CommandContext<CommandSourceStack> context) {
        Sym s = sym(context);

        SnailNumberRegistry registry = SnailNumberRegistry.getInstance();
        if (registry == null) {
            return registryUnavailable(context);
        }

        int assignedCount = registry.getAssignedCount();
        if (assignedCount == 0) {
            send(context, s.t(NO + " ", "") + "No snail numbers are assigned - nothing to remove.", C_WARN);
            return 1;
        }

        // Forget expired prompts from everyone, then remember this one
        long now = System.currentTimeMillis();
        pendingRemoveAll.values().removeIf(time -> now - time > CONFIRMATION_TIMEOUT_MS);
        pendingRemoveAll.put(sourceKey(context.getSource()), now);

        send(context, s.t(WARN + " Remove ALL " + assignedCount + " snail numbers?",
                "WARNING: this will remove ALL " + assignedCount + " snail numbers."), C_BAD);
        send(context, s.t(DOT + " every snail, placed or held, registers again on its next use",
                "- Every snail (placed or held) must register a new number the next time it is used."), C_WARN);
        send(context, s.t(DOT + " active calls end " + MID + " this cannot be undone",
                "- All active calls will be ended. This cannot be undone."), C_WARN);
        emit(context, new Line().add(s.t(OK + " ", "To confirm within 30 seconds, type: "), C_GOOD)
                .add("/snailnumber remove confirm")
                .add(s.t("  (30s)", ""), C_DIM));
        emit(context, new Line().add(s.t(NO + " ", "To cancel, type: "), C_BAD)
                .add("/snailnumber remove cancel"));
        return 1;
    }

    /** Second step of "remove all". Runs only if this same source asked within the last 30 seconds. */
    private static int confirmRemoveAll(CommandContext<CommandSourceStack> context) {
        Sym s = sym(context);
        String key = sourceKey(context.getSource());

        Long requestedAt = pendingRemoveAll.remove(key);
        if (requestedAt == null) {
            return fail(context, "Nothing to confirm. Use /snailnumber remove all first.");
        }
        if (System.currentTimeMillis() - requestedAt > CONFIRMATION_TIMEOUT_MS) {
            return fail(context, "Confirmation expired. Use /snailnumber remove all to try again.");
        }

        SnailNumberRegistry registry = SnailNumberRegistry.getInstance();
        if (registry == null) {
            return registryUnavailable(context);
        }

        // The registry must be updated first: applyRevocation relies on the UUIDs already being revoked
        Map<Integer, UUID> removed = registry.revokeAllSnails();

        TransponderCallManager callManager = TransponderSnails.getCallManager();
        TransponderCallManager.RevocationReport report = callManager != null
                ? callManager.applyRevocation(removed.keySet()) : null;

        send(context, s.t(OK + " Removed all " + removed.size() + " snail numbers", "Removed all snail numbers (" + removed.size() + ")."), C_GOOD);
        if (report != null) {
            send(context, s.t(DOT + " " + report.callsEnded() + " calls ended" + s.sep() + report.placedReset().size()
                            + " placed snails reset" + s.sep() + report.itemsReset() + " items reset",
                    "Ended " + report.callsEnded() + " active calls. Reset " + report.placedReset().size()
                            + " placed snails and " + report.itemsReset() + " snail items in online players' inventories."));
        }
        send(context, s.t(REDO + " every snail registers a new number on its next use",
                "Every snail registers a new number the next time it is used. Snails in unloaded chunks or containers reset when they are next loaded or used."), C_DIM);

        System.out.println("ADMIN ACTION: " + sourceName(context.getSource()) + " removed all snail numbers (" + removed.size() + " numbers)");
        return 1;
    }

    private static int cancelRemoveAll(CommandContext<CommandSourceStack> context) {
        Sym s = sym(context);
        boolean hadPending = pendingRemoveAll.remove(sourceKey(context.getSource())) != null;

        if (hadPending) {
            send(context, s.t(OK + " Cancelled " + MID + " numbers unchanged", "Remove all cancelled. All snail numbers are unchanged."), C_GOOD);
        } else {
            send(context, s.t(NO + " ", "") + "No remove all was pending.", C_WARN);
        }
        return 1;
    }

    // =================== STATS ===================

    /**
     * Shows registry statistics
     */
    private static int showStats(CommandContext<CommandSourceStack> context) {
        Sym s = sym(context);
        SnailNumberRegistry registry = SnailNumberRegistry.getInstance();
        if (registry == null) {
            return registryUnavailable(context);
        }

        int assigned = registry.getAssignedCount();
        int available = registry.getAvailableCount();
        int total = assigned + available;
        double percentUsed = (double) assigned / total * 100;
        ChatFormatting usageColor = percentUsed > 80 ? C_BAD : percentUsed > 50 ? C_WARN : C_GOOD;

        int barLength = 20;
        int filled = (int) Math.round(percentUsed / 100 * barLength);
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < barLength; i++) {
            bar.append(i < filled ? s.t(BAR_ON, "#") : s.t(BAR_OFF, "-"));
        }
        bar.append("]");

        title(context, s, "Snail registry", null);
        emit(context, new Line().add(bar.toString(), usageColor)
                .add(" " + String.format(Locale.ROOT, "%.1f%%", percentUsed), usageColor));
        emit(context, new Line().add(String.valueOf(assigned), C_NUM).add(" assigned" + s.sep(), C_DIM)
                .add(String.valueOf(available), C_NUM).add(" free" + s.sep(), C_DIM)
                .add(String.valueOf(total), C_NUM).add(" total", C_DIM));

        // Warn if getting full
        if (percentUsed > 90) {
            send(context, s.t(WARN + " ", "WARNING: ") + "registry is nearly full!", C_BAD);
        }

        return 1;
    }
}