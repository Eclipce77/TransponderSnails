package net.eclipce.transpondersnails.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Configuration class for Transponder Snails mod
 * Split into client-side (GUI preferences) and server-side (gameplay mechanics)
 */
@Mod.EventBusSubscriber(modid = "transpondersnails", bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModConfig {

    // Client configuration (player-specific settings)
    public static final ClientConfig CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;

    // Server configuration (gameplay mechanics)
    public static final ServerConfig SERVER;
    public static final ForgeConfigSpec SERVER_SPEC;

    static {
        // Build client config
        final Pair<ClientConfig, ForgeConfigSpec> clientPair = new ForgeConfigSpec.Builder()
                .configure(ClientConfig::new);
        CLIENT_SPEC = clientPair.getRight();
        CLIENT = clientPair.getLeft();

        // Build server config
        final Pair<ServerConfig, ForgeConfigSpec> serverPair = new ForgeConfigSpec.Builder()
                .configure(ServerConfig::new);
        SERVER_SPEC = serverPair.getRight();
        SERVER = serverPair.getLeft();
    }

    /**
     * Client-side configuration (GUI and input preferences)
     * These values are per-player and not synced
     */
    public static class ClientConfig {
        public final ForgeConfigSpec.BooleanValue enableNumpadSupport;

        public ClientConfig(ForgeConfigSpec.Builder builder) {
            builder.comment("Transponder Snails Client Configuration")
                    .comment("These settings are per-player and affect input/GUI preferences")
                    .push("client");

            enableNumpadSupport = builder
                    .comment("Enable numpad keys for dialing in addition to number row keys")
                    .comment("This is a client-side preference that doesn't affect gameplay")
                    .define("enable_numpad", false);

            builder.pop();
        }
    }

    /**
     * Server-side configuration (synced to clients)
     * These values affect gameplay mechanics and must be consistent for all players
     */
    public static class ServerConfig {
        public final ForgeConfigSpec.DoubleValue locationalSnailRange;
        public final ForgeConfigSpec.DoubleValue handheldSnailRange;
        public final ForgeConfigSpec.LongValue ringTimeoutMs;
        public final ForgeConfigSpec.DoubleValue snailInteractionRange;

        public ServerConfig(ForgeConfigSpec.Builder builder) {
            builder.comment("Transponder Snails Server Configuration")
                    .comment("   =%%% :%%%: %@@@@@@@# ")
                    .comment(" :@=  .@#   *%%#######%%= ")
                    .comment(" :@=   @#   :-#*::::-#*:*@: ")
                    .comment("   =@: @# =@#*:-#=:+*:-#%@: ")
                    .comment("   =@: @# +@%#:::=#=:.-#%@: ")
                    .comment("   =@:    =@%#:-#=:+*:-#%@: ")
                    .comment("   =@:      +##*::::-*#*%@: ")
                    .comment("   =@:      .:*#*****#*:*@: ")
                    .comment("   =@:                :@= ")
                    .comment("   =@=:               :@= ")
                    .comment("     *%-::::::::::::::=@= ")
                    .comment("      .%%%%%%%%%%%%%%%* ")
                    .comment("These settings control gameplay mechanics and are synced to all players")
                    .push("gameplay");

            locationalSnailRange = builder
                    .comment("Range in blocks for voice chat through placed Transponder Snail blocks")
                    .comment("Players within this range can hear each other through the snail")
                    .defineInRange("locational_snail_range", 10.0, 1.0, 100.0);

            handheldSnailRange = builder
                    .comment("Range in blocks for voice chat through handheld Transponder Snails")
                    .comment("Players within this range can hear each other through handheld snails")
                    .defineInRange("handheld_snail_range", 3.0, 1.0, 50.0);

            ringTimeoutMs = builder
                    .comment("How long (in milliseconds) a snail will ring before timing out")
                    .comment("30000 ms = 30 seconds")
                    .defineInRange("ring_timeout_ms", 30000L, 5000L, 300000L);

            snailInteractionRange = builder
                    .comment("Range in blocks for interacting with Transponder Snail blocks")
                    .comment("Players must be within this range to use snail blocks")
                    .defineInRange("snail_interaction_range", 10.0, 1.0, 50.0);

            builder.pop();
        }
    }

    // Default values
    private static final boolean DEFAULT_ENABLE_NUMPAD = false;
    private static final double DEFAULT_LOCATIONAL_RANGE = 10.0;
    private static final double DEFAULT_HANDHELD_RANGE = 3.0;
    private static final long DEFAULT_RING_TIMEOUT = 30000L;
    private static final double DEFAULT_INTERACTION_RANGE = 10.0;

    // Cache for client values
    private static boolean cachedEnableNumpad = DEFAULT_ENABLE_NUMPAD;

    // Cache for server values
    private static double cachedLocationalRange = DEFAULT_LOCATIONAL_RANGE;
    private static double cachedHandheldRange = DEFAULT_HANDHELD_RANGE;
    private static long cachedRingTimeout = DEFAULT_RING_TIMEOUT;
    private static double cachedInteractionRange = DEFAULT_INTERACTION_RANGE;

    /**
     * Called when config is loaded or changed
     */
    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() == CLIENT_SPEC) {
            // Client config changed
            cachedEnableNumpad = CLIENT.enableNumpadSupport.get();
            System.out.println("TransponderSnails client config loaded:");
            System.out.println("  Numpad Enabled: " + cachedEnableNumpad);
        } else if (event.getConfig().getSpec() == SERVER_SPEC) {
            // Server config changed
            cachedLocationalRange = getValidatedValue(SERVER.locationalSnailRange.get(), DEFAULT_LOCATIONAL_RANGE, 1.0, 100.0);
            cachedHandheldRange = getValidatedValue(SERVER.handheldSnailRange.get(), DEFAULT_HANDHELD_RANGE, 1.0, 50.0);
            cachedRingTimeout = getValidatedValue(SERVER.ringTimeoutMs.get(), DEFAULT_RING_TIMEOUT, 5000L, 300000L);
            cachedInteractionRange = getValidatedValue(SERVER.snailInteractionRange.get(), DEFAULT_INTERACTION_RANGE, 1.0, 50.0);

            System.out.println("TransponderSnails server config loaded:");
            System.out.println("  Locational Snail Range: " + cachedLocationalRange);
            System.out.println("  Handheld Snail Range: " + cachedHandheldRange);
            System.out.println("  Ring Timeout: " + cachedRingTimeout + "ms");
            System.out.println("  Interaction Range: " + cachedInteractionRange);
        }
    }

    /**
     * Validates a double value and returns default if invalid
     */
    private static double getValidatedValue(double value, double defaultValue, double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < min || value > max) {
            System.out.println("TransponderSnails: Invalid config value " + value + ", using default " + defaultValue);
            return defaultValue;
        }
        return value;
    }

    /**
     * Validates a long value and returns default if invalid
     */
    private static long getValidatedValue(long value, long defaultValue, long min, long max) {
        if (value < min || value > max) {
            System.out.println("TransponderSnails: Invalid config value " + value + ", using default " + defaultValue);
            return defaultValue;
        }
        return value;
    }

    // Client config getters
    public static boolean isNumpadEnabled() {
        return cachedEnableNumpad;
    }

    /**
     * Updates the numpad setting and saves immediately
     * Used by the client config GUI
     */
    public static void setNumpadEnabled(boolean enabled) {
        CLIENT.enableNumpadSupport.set(enabled);
        cachedEnableNumpad = enabled;
        CLIENT_SPEC.save(); // Save immediately
    }

    // Server config getters
    public static double getLocationalSnailRange() {
        if (cachedLocationalRange <= 0 || Double.isNaN(cachedLocationalRange)) {
            System.out.println("TransponderSnails: Cached locational range invalid, using default");
            return DEFAULT_LOCATIONAL_RANGE;
        }
        return cachedLocationalRange;
    }

    public static double getHandheldSnailRange() {
        if (cachedHandheldRange <= 0 || Double.isNaN(cachedHandheldRange)) {
            System.out.println("TransponderSnails: Cached handheld range invalid, using default");
            return DEFAULT_HANDHELD_RANGE;
        }
        return cachedHandheldRange;
    }

    public static long getRingTimeoutMs() {
        if (cachedRingTimeout <= 0) {
            System.out.println("TransponderSnails: Cached ring timeout invalid, using default");
            return DEFAULT_RING_TIMEOUT;
        }
        return cachedRingTimeout;
    }

    public static double getSnailInteractionRange() {
        if (cachedInteractionRange <= 0 || Double.isNaN(cachedInteractionRange)) {
            System.out.println("TransponderSnails: Cached interaction range invalid, using default");
            return DEFAULT_INTERACTION_RANGE;
        }
        return cachedInteractionRange;
    }
}