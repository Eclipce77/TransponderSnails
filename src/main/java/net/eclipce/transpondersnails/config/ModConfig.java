package net.eclipce.transpondersnails.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration class for Transponder Snails mod
 * Split into client-side (GUI preferences) and server-side (gameplay mechanics + spawning)
 */
@Mod.EventBusSubscriber(modid = "transpondersnails", bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModConfig {

    // Client configuration (player-specific settings)
    public static final ClientConfig CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;

    // Server configuration (gameplay mechanics + spawning)
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
                    .comment("These settings control the client");

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
        // Snail mechanics
        public final ForgeConfigSpec.DoubleValue locationalSnailRange;
        public final ForgeConfigSpec.DoubleValue handheldSnailRange;
        public final ForgeConfigSpec.LongValue ringTimeoutMs;
        public final ForgeConfigSpec.DoubleValue snailInteractionRange;
        public final ForgeConfigSpec.BooleanValue enablePhoneFilter;

        // Call Management
        public final ForgeConfigSpec.LongValue callInactivityTimeoutMs;
        public final ForgeConfigSpec.DoubleValue participantProximityRange;

        // Snail Number Management
        public final ForgeConfigSpec.DoubleValue numberPreservationDays;
        public final ForgeConfigSpec.LongValue numberCleanupIntervalSeconds;

        // Black Transponder Snail interception ranges
        public final ForgeConfigSpec.DoubleValue babyBlackSnailRange;
        public final ForgeConfigSpec.DoubleValue adultBlackSnailDefaultRange;
        public final ForgeConfigSpec.DoubleValue adultBlackSnailMinRange;
        public final ForgeConfigSpec.DoubleValue adultBlackSnailMaxRange;

        // Horned Den Den Mushi jamming settings
        public final ForgeConfigSpec.DoubleValue hornedDDMJammingRadius;

        // Spawn configuration
        public final SpawnConfig spawning;

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

            // Snail Settings
            builder.comment("Snail Settings")
                    .push("snails");

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

            // Call Management Settings
            builder.comment("Call Management Settings")
                    .comment("Configure call behavior and cleanup")
                    .push("call_management");

            callInactivityTimeoutMs = builder
                    .comment("How long (in milliseconds) a call can be inactive before auto-cleanup")
                    .comment("300000 ms = 5 minutes. Set to 0 to disable auto-cleanup.")
                    .defineInRange("call_inactivity_timeout_ms", 300000L, 0L, 3600000L);

            participantProximityRange = builder
                    .comment("Distance in blocks a call participant must stay within to remain active")
                    .comment("If a participant moves beyond this range, they can intercept their own call")
                    .comment("Set to 0 to disable proximity-based participation removal")
                    .defineInRange("participant_proximity_range", 15.0, 0.0, 100.0);

            builder.pop();

            // Snail Number Management Settings
            builder.comment("Snail Number Management Settings")
                    .comment("Configure how snail numbers are assigned and reclaimed")
                    .push("snail_numbers");

            numberPreservationDays = builder
                    .comment("How many Minecraft days a snail number is preserved after conversion to Den Den Mushi")
                    .comment("If reconverted to Transponder Snail within this time, keeps same number")
                    .comment("Set to 0 to disable number preservation (always get new number)")
                    .comment("Supports decimals (e.g., 0.5 = 12 hours, 2.5 = 2.5 days)")
                    .defineInRange("number_preservation_days", 2.0, 0.0, 30.0);

            numberCleanupIntervalSeconds = builder
                    .comment("How often (in seconds) to check for unused snail numbers and reclaim them")
                    .comment("Set to 0 to disable periodic cleanup")
                    .defineInRange("number_cleanup_interval_seconds", 300L, 0L, 3600L);

            builder.pop();

            // Audio processing settings
            builder.comment("Audio Processing Settings")
                    .push("audio");

            enablePhoneFilter = builder
                    .comment("Enable phone-call sounding audio for all Transponder Snail calls")
                    .define("enable_phone_filter", true);

            builder.pop();

            // Black Transponder Snail Interception Settings
            builder.comment("Black Transponder Snail Interception Settings")
                    .comment("Configure interception ranges for different Black Transponder Snail variants")
                    .push("interception");

            babyBlackSnailRange = builder
                    .comment("Interception range in blocks for Baby Black Transponder Snails (portable)")
                    .comment("This applies to both the portable item and baby snail entities")
                    .defineInRange("baby_black_snail_range", 50.0, 10.0, 200.0);

            adultBlackSnailDefaultRange = builder
                    .comment("Default interception range in blocks for Adult Black Transponder Snails")
                    .comment("This is the base range when placed or held without lightning rod enhancement")
                    .defineInRange("adult_black_snail_default_range", 75.0, 20.0, 300.0);

            adultBlackSnailMinRange = builder
                    .comment("Minimum enhanced range for Adult Black Transponder Snails with lightning rods")
                    .comment("This is the starting range with at least one lightning rod connected")
                    .defineInRange("adult_black_snail_min_enhanced_range", 100.0, 50.0, 500.0);

            adultBlackSnailMaxRange = builder
                    .comment("Maximum enhanced range for Adult Black Transponder Snails with lightning rods")
                    .comment("This is the maximum range achievable with a full lightning rod array")
                    .defineInRange("adult_black_snail_max_enhanced_range", 400.0, 100.0, 1000.0);

            builder.pop();

            // Horned Den Den Mushi Jamming Settings
            builder.comment("Horned Den Den Mushi Jamming Settings")
                    .comment("Configure the jamming sphere of the placed Horned Den Den Mushi block")
                    .push("horned_den_den_mushi");

            hornedDDMJammingRadius = builder
                    .comment("Radius in blocks of the jamming sphere")
                    .comment("Any transponder snail call with a participant (player or block) inside")
                    .comment("this sphere will be blocked or terminated.")
                    .comment("Default: 20 blocks — gives a 20-block spherical jamming area.")
                    .defineInRange("jamming_radius", 20.0, 1.0, 200.0);

            builder.pop();

            // Spawn configuration
            this.spawning = new SpawnConfig(builder);
        }
    }

    /**
     * Spawn configuration for all snail entities
     */
    public static class SpawnConfig {
        // Global spawn settings
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> allowedDimensions;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> spawnBiomes;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> disabledSnails;

        // Den Den Mushi (land snail)
        public final ForgeConfigSpec.DoubleValue denDenMushiSpawnRate;
        public final ForgeConfigSpec.IntValue denDenMushiMinGroup;
        public final ForgeConfigSpec.IntValue denDenMushiMaxGroup;

        // Black Transponder Snail (underwater snail)
        public final ForgeConfigSpec.DoubleValue blackTransponderSnailSpawnRate;
        public final ForgeConfigSpec.IntValue blackTransponderSnailMinGroup;
        public final ForgeConfigSpec.IntValue blackTransponderSnailMaxGroup;

        // Baby Black Transponder Snail (underwater snail)
        public final ForgeConfigSpec.DoubleValue babyBlackTransponderSnailSpawnRate;
        public final ForgeConfigSpec.IntValue babyBlackTransponderSnailMinGroup;
        public final ForgeConfigSpec.IntValue babyBlackTransponderSnailMaxGroup;

        // White Den Den Mushi (land snail - white body variant)
        public final ForgeConfigSpec.DoubleValue whiteDenDenMushiSpawnRate;
        public final ForgeConfigSpec.IntValue whiteDenDenMushiMinGroup;
        public final ForgeConfigSpec.IntValue whiteDenDenMushiMaxGroup;

        public SpawnConfig(ForgeConfigSpec.Builder builder) {
            builder.comment("=".repeat(60))
                    .comment("SPAWN CONFIGURATION")
                    .comment("Control where and how snails spawn in the world")
                    .comment("=".repeat(60))
                    .push("spawning");

            // Global Settings
            builder.comment("")
                    .comment("Global Spawn Settings")
                    .comment("-".repeat(60))
                    .push("global");

            allowedDimensions = builder
                    .comment("List of dimensions where snails can spawn")
                    .comment("Format: 'namespace:dimension_name'")
                    .comment("Default: Only Overworld")
                    .comment("Example: ['minecraft:overworld', 'minecraft:the_nether']")
                    .defineList("allowed_dimensions",
                            Arrays.asList("minecraft:overworld"),
                            obj -> obj instanceof String);

            spawnBiomes = builder
                    .comment("Biome tags where LAND snails (Den Den Mushi) can spawn")
                    .comment("Black Transponder Snails spawn underwater regardless of this setting")
                    .comment("Format: Biome tags (with #) or specific biomes")
                    .comment("Default: Forests, beaches, and water areas")
                    .defineList("spawn_biomes",
                            Arrays.asList(
                                    "#minecraft:is_forest",
                                    "#minecraft:is_jungle",
                                    "#minecraft:is_beach",
                                    "#minecraft:is_river"
                            ),
                            obj -> obj instanceof String);

            disabledSnails = builder
                    .comment("List of snails that should NOT spawn naturally")
                    .comment("Leave empty to allow all snails to spawn")
                    .comment("Valid values: 'den_den_mushi', 'white_den_den_mushi', 'black_transponder_snail', 'baby_black_transponder_snail'")
                    .comment("Example: ['baby_black_transponder_snail'] to disable only baby black snails")
                    .defineList("disabled_snails",
                            Arrays.asList(),
                            obj -> obj instanceof String);

            builder.pop(); // global

            // Den Den Mushi Configuration
            builder.comment("")
                    .comment("Den Den Mushi (Land Snail)")
                    .comment("-".repeat(60))
                    .push("den_den_mushi");

            denDenMushiSpawnRate = builder
                    .comment("Spawn rate percentage (0-100, decimals allowed)")
                    .comment("100 = normal spawn rate, 50 = half as common, 0 = disabled")
                    .comment("This multiplies the spawn weight in biome modifiers")
                    .defineInRange("spawn_rate", 100.0, 0.0, 100.0);

            denDenMushiMinGroup = builder
                    .comment("Minimum group size when spawning")
                    .defineInRange("min_group_size", 2, 1, 64);

            denDenMushiMaxGroup = builder
                    .comment("Maximum group size when spawning")
                    .defineInRange("max_group_size", 4, 1, 64);

            builder.pop(); // den_den_mushi

            // Black Transponder Snail Configuration
            builder.comment("")
                    .comment("Black Transponder Snail (Underwater Adult)")
                    .comment("-".repeat(60))
                    .comment("Spawns in medium to deep water (5-30 blocks deep)")
                    .push("black_transponder_snail");

            blackTransponderSnailSpawnRate = builder
                    .comment("Spawn rate percentage (0-100, decimals allowed)")
                    .comment("100 = normal spawn rate, 50 = half as common, 0 = disabled")
                    .defineInRange("spawn_rate", 100.0, 0.0, 100.0);

            blackTransponderSnailMinGroup = builder
                    .comment("Minimum group size when spawning")
                    .defineInRange("min_group_size", 1, 1, 64);

            blackTransponderSnailMaxGroup = builder
                    .comment("Maximum group size when spawning")
                    .defineInRange("max_group_size", 3, 1, 64);

            builder.pop(); // black_transponder_snail

            // Baby Black Transponder Snail Configuration
            builder.comment("")
                    .comment("Baby Black Transponder Snail (Underwater Baby)")
                    .comment("-".repeat(60))
                    .comment("Spawns in medium to deep water (5-30 blocks deep)")
                    .push("baby_black_transponder_snail");

            babyBlackTransponderSnailSpawnRate = builder
                    .comment("Spawn rate percentage (0-100, decimals allowed)")
                    .comment("100 = normal spawn rate, 50 = half as common, 0 = disabled")
                    .defineInRange("spawn_rate", 100.0, 0.0, 100.0);

            babyBlackTransponderSnailMinGroup = builder
                    .comment("Minimum group size when spawning")
                    .defineInRange("min_group_size", 1, 1, 64);

            babyBlackTransponderSnailMaxGroup = builder
                    .comment("Maximum group size when spawning")
                    .defineInRange("max_group_size", 2, 1, 64);

            builder.pop(); // baby_black_transponder_snail

            // White Den Den Mushi Configuration
            builder.comment("")
                    .comment("White Den Den Mushi (Land Snail - White Body Variant)")
                    .comment("-".repeat(60))
                    .comment("Spawns on land like regular Den Den Mushi")
                    .push("white_den_den_mushi");

            whiteDenDenMushiSpawnRate = builder
                    .comment("Spawn rate percentage (0-100, decimals allowed)")
                    .comment("100 = normal spawn rate, 50 = half as common, 0 = disabled")
                    .defineInRange("spawn_rate", 100.0, 0.0, 100.0);

            whiteDenDenMushiMinGroup = builder
                    .comment("Minimum group size when spawning")
                    .defineInRange("min_group_size", 1, 1, 64);

            whiteDenDenMushiMaxGroup = builder
                    .comment("Maximum group size when spawning")
                    .defineInRange("max_group_size", 2, 1, 64);

            builder.pop(); // white_den_den_mushi

            builder.pop(); // spawning
        }
    }

    // Default values (keeping existing + adding new)
    private static final boolean DEFAULT_ENABLE_NUMPAD = false;
    private static final double DEFAULT_LOCATIONAL_RANGE = 10.0;
    private static final double DEFAULT_HANDHELD_RANGE = 3.0;
    private static final long DEFAULT_RING_TIMEOUT = 30000L;
    private static final double DEFAULT_INTERACTION_RANGE = 10.0;
    private static final boolean DEFAULT_ENABLE_PHONE_FILTER = true;

    // Black Transponder Snail interception defaults
    private static final double DEFAULT_BABY_BLACK_SNAIL_RANGE = 50.0;
    private static final double DEFAULT_ADULT_BLACK_SNAIL_DEFAULT_RANGE = 75.0;
    private static final double DEFAULT_ADULT_BLACK_SNAIL_MIN_RANGE = 100.0;
    private static final double DEFAULT_ADULT_BLACK_SNAIL_MAX_RANGE = 400.0;

    private static final double DEFAULT_HORNED_DDM_JAMMING_RADIUS = 20.0;

    // Spawn defaults
    private static final double DEFAULT_DEN_DEN_MUSHI_SPAWN_RATE = 100.0;
    private static final double DEFAULT_BLACK_TRANSPONDER_SNAIL_SPAWN_RATE = 100.0;
    private static final double DEFAULT_BABY_BLACK_TRANSPONDER_SNAIL_SPAWN_RATE = 100.0;
    private static final double DEFAULT_WHITE_DEN_DEN_MUSHI_SPAWN_RATE = 100.0;

    // Cache for client values
    private static boolean cachedEnableNumpad = DEFAULT_ENABLE_NUMPAD;

    // Cache for server values
    private static double cachedLocationalRange = DEFAULT_LOCATIONAL_RANGE;
    private static double cachedHandheldRange = DEFAULT_HANDHELD_RANGE;
    private static long cachedRingTimeout = DEFAULT_RING_TIMEOUT;
    private static double cachedInteractionRange = DEFAULT_INTERACTION_RANGE;
    private static boolean cachedEnablePhoneFilter = DEFAULT_ENABLE_PHONE_FILTER;

    // Cache for interception ranges
    private static double cachedBabyBlackSnailRange = DEFAULT_BABY_BLACK_SNAIL_RANGE;
    private static double cachedAdultBlackSnailDefaultRange = DEFAULT_ADULT_BLACK_SNAIL_DEFAULT_RANGE;
    private static double cachedAdultBlackSnailMinRange = DEFAULT_ADULT_BLACK_SNAIL_MIN_RANGE;
    private static double cachedAdultBlackSnailMaxRange = DEFAULT_ADULT_BLACK_SNAIL_MAX_RANGE;

    private static double cachedHornedDDMJammingRadius = DEFAULT_HORNED_DDM_JAMMING_RADIUS;

    // PERFORMANCE: These were previously calling ForgeConfigSpec.get() on every use.
    // Cached here so hot-path callers (call session management, cleanup scheduler) read primitives.
    private static long   cachedCallInactivityTimeout    = 600000L;
    private static double cachedParticipantProximityRange = 10.0;
    private static double cachedNumberPreservationDays    = 30.0;
    private static long   cachedNumberCleanupInterval     = 3600L;

    // Cache for spawn values
    private static List<String> cachedAllowedDimensions = Arrays.asList("minecraft:overworld");
    private static List<String> cachedSpawnBiomes = Arrays.asList(
            "#minecraft:is_forest", "#minecraft:is_jungle",
            "#minecraft:is_beach", "#minecraft:is_river"
    );
    private static List<String> cachedDisabledSnails = Arrays.asList();

    private static double cachedDenDenMushiSpawnRate = DEFAULT_DEN_DEN_MUSHI_SPAWN_RATE;
    private static int cachedDenDenMushiMinGroup = 2;
    private static int cachedDenDenMushiMaxGroup = 4;

    private static double cachedBlackTransponderSnailSpawnRate = DEFAULT_BLACK_TRANSPONDER_SNAIL_SPAWN_RATE;
    private static int cachedBlackTransponderSnailMinGroup = 1;
    private static int cachedBlackTransponderSnailMaxGroup = 3;

    private static double cachedBabyBlackTransponderSnailSpawnRate = DEFAULT_BABY_BLACK_TRANSPONDER_SNAIL_SPAWN_RATE;
    private static int cachedBabyBlackTransponderSnailMinGroup = 1;
    private static int cachedBabyBlackTransponderSnailMaxGroup = 2;

    private static double cachedWhiteDenDenMushiSpawnRate = DEFAULT_WHITE_DEN_DEN_MUSHI_SPAWN_RATE;
    private static int cachedWhiteDenDenMushiMinGroup = 1;
    private static int cachedWhiteDenDenMushiMaxGroup = 2;

    /**
     * Called when config is loaded or changed
     */
    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() == CLIENT_SPEC) {
            // Client config changed
            cachedEnableNumpad = CLIENT.enableNumpadSupport.get();
        } else if (event.getConfig().getSpec() == SERVER_SPEC) {
            // Server config changed
            cachedLocationalRange = getValidatedValue(SERVER.locationalSnailRange.get(), DEFAULT_LOCATIONAL_RANGE, 1.0, 100.0);
            cachedHandheldRange = getValidatedValue(SERVER.handheldSnailRange.get(), DEFAULT_HANDHELD_RANGE, 1.0, 50.0);
            cachedRingTimeout = getValidatedValue(SERVER.ringTimeoutMs.get(), DEFAULT_RING_TIMEOUT, 5000L, 300000L);
            cachedInteractionRange = getValidatedValue(SERVER.snailInteractionRange.get(), DEFAULT_INTERACTION_RANGE, 1.0, 50.0);
            cachedEnablePhoneFilter = SERVER.enablePhoneFilter.get();

            // Load interception ranges
            cachedBabyBlackSnailRange = getValidatedValue(SERVER.babyBlackSnailRange.get(), DEFAULT_BABY_BLACK_SNAIL_RANGE, 10.0, 200.0);
            cachedAdultBlackSnailDefaultRange = getValidatedValue(SERVER.adultBlackSnailDefaultRange.get(), DEFAULT_ADULT_BLACK_SNAIL_DEFAULT_RANGE, 20.0, 300.0);
            cachedAdultBlackSnailMinRange = getValidatedValue(SERVER.adultBlackSnailMinRange.get(), DEFAULT_ADULT_BLACK_SNAIL_MIN_RANGE, 50.0, 500.0);
            cachedAdultBlackSnailMaxRange = getValidatedValue(SERVER.adultBlackSnailMaxRange.get(), DEFAULT_ADULT_BLACK_SNAIL_MAX_RANGE, 100.0, 1000.0);
            cachedHornedDDMJammingRadius = getValidatedValue(SERVER.hornedDDMJammingRadius.get(), DEFAULT_HORNED_DDM_JAMMING_RADIUS, 1.0, 200.0);
            cachedCallInactivityTimeout    = SERVER.callInactivityTimeoutMs.get();
            cachedParticipantProximityRange = SERVER.participantProximityRange.get();
            cachedNumberPreservationDays    = SERVER.numberPreservationDays.get();
            cachedNumberCleanupInterval     = SERVER.numberCleanupIntervalSeconds.get();
            System.out.println("TransponderSnails: Server config loaded");

            // Load spawn config
            cachedAllowedDimensions = SERVER.spawning.allowedDimensions.get().stream().map(Object::toString).toList();
            cachedSpawnBiomes = SERVER.spawning.spawnBiomes.get().stream().map(Object::toString).toList();
            cachedDisabledSnails = SERVER.spawning.disabledSnails.get().stream().map(Object::toString).toList();

            cachedDenDenMushiSpawnRate = getValidatedValue(SERVER.spawning.denDenMushiSpawnRate.get(), DEFAULT_DEN_DEN_MUSHI_SPAWN_RATE, 0.0, 100.0);
            cachedDenDenMushiMinGroup = SERVER.spawning.denDenMushiMinGroup.get();
            cachedDenDenMushiMaxGroup = SERVER.spawning.denDenMushiMaxGroup.get();

            cachedBlackTransponderSnailSpawnRate = getValidatedValue(SERVER.spawning.blackTransponderSnailSpawnRate.get(), DEFAULT_BLACK_TRANSPONDER_SNAIL_SPAWN_RATE, 0.0, 100.0);
            cachedBlackTransponderSnailMinGroup = SERVER.spawning.blackTransponderSnailMinGroup.get();
            cachedBlackTransponderSnailMaxGroup = SERVER.spawning.blackTransponderSnailMaxGroup.get();

            cachedBabyBlackTransponderSnailSpawnRate = getValidatedValue(SERVER.spawning.babyBlackTransponderSnailSpawnRate.get(), DEFAULT_BABY_BLACK_TRANSPONDER_SNAIL_SPAWN_RATE, 0.0, 100.0);
            cachedBabyBlackTransponderSnailMinGroup = SERVER.spawning.babyBlackTransponderSnailMinGroup.get();
            cachedBabyBlackTransponderSnailMaxGroup = SERVER.spawning.babyBlackTransponderSnailMaxGroup.get();

            cachedWhiteDenDenMushiSpawnRate = getValidatedValue(SERVER.spawning.whiteDenDenMushiSpawnRate.get(), DEFAULT_WHITE_DEN_DEN_MUSHI_SPAWN_RATE, 0.0, 100.0);
            cachedWhiteDenDenMushiMinGroup = SERVER.spawning.whiteDenDenMushiMinGroup.get();
            cachedWhiteDenDenMushiMaxGroup = SERVER.spawning.whiteDenDenMushiMaxGroup.get();

        }
    }

    /**
     * Validates a double value and returns default if invalid
     */
    private static double getValidatedValue(double value, double defaultValue, double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < min || value > max) {
            return defaultValue;
        }
        return value;
    }

    /**
     * Validates a long value and returns default if invalid
     */
    private static long getValidatedValue(long value, long defaultValue, long min, long max) {
        if (value < min || value > max) {
            return defaultValue;
        }
        return value;
    }

    // Client config getters
    public static boolean isNumpadEnabled() {
        return cachedEnableNumpad;
    }

    public static void setNumpadEnabled(boolean enabled) {
        CLIENT.enableNumpadSupport.set(enabled);
        cachedEnableNumpad = enabled;
        CLIENT_SPEC.save();
    }

    // Server config getters (existing)
    public static double getLocationalSnailRange() {
        if (cachedLocationalRange <= 0 || Double.isNaN(cachedLocationalRange)) {
            return DEFAULT_LOCATIONAL_RANGE;
        }
        return cachedLocationalRange;
    }

    public static double getHandheldSnailRange() {
        if (cachedHandheldRange <= 0 || Double.isNaN(cachedHandheldRange)) {
            return DEFAULT_HANDHELD_RANGE;
        }
        return cachedHandheldRange;
    }

    public static long getRingTimeoutMs() {
        if (cachedRingTimeout <= 0) {
            return DEFAULT_RING_TIMEOUT;
        }
        return cachedRingTimeout;
    }

    public static double getSnailInteractionRange() {
        if (cachedInteractionRange <= 0 || Double.isNaN(cachedInteractionRange)) {
            return DEFAULT_INTERACTION_RANGE;
        }
        return cachedInteractionRange;
    }

    public static long getCallInactivityTimeoutMs() {
        return cachedCallInactivityTimeout;
    }

    public static double getParticipantProximityRange() {
        return cachedParticipantProximityRange;
    }

    // Snail Number Getters
    public static double getNumberPreservationDays() {
        return cachedNumberPreservationDays;
    }

    public static long getNumberCleanupIntervalSeconds() {
        return cachedNumberCleanupInterval;
    }

    // Convert days to milliseconds for internal use
    public static long getNumberPreservationMs() {
        if (cachedNumberPreservationDays <= 0) return 0;
        return (long)(cachedNumberPreservationDays * 1200000);
    }

    public static boolean isPhoneFilterEnabled() {
        return cachedEnablePhoneFilter;
    }

    // Spawn config getters
    public static List<String> getAllowedDimensions() {
        return cachedAllowedDimensions;
    }

    public static List<String> getSpawnBiomes() {
        return cachedSpawnBiomes;
    }

    public static List<String> getDisabledSnails() {
        return cachedDisabledSnails;
    }

    public static boolean isSnailSpawnEnabled(String snailId) {
        return !cachedDisabledSnails.contains(snailId);
    }

    // Den Den Mushi spawn getters
    public static double getDenDenMushiSpawnRate() {
        return cachedDenDenMushiSpawnRate;
    }

    public static int getDenDenMushiMinGroup() {
        return cachedDenDenMushiMinGroup;
    }

    public static int getDenDenMushiMaxGroup() {
        return cachedDenDenMushiMaxGroup;
    }

    // Black Transponder Snail spawn getters
    public static double getBlackTransponderSnailSpawnRate() {
        return cachedBlackTransponderSnailSpawnRate;
    }

    public static int getBlackTransponderSnailMinGroup() {
        return cachedBlackTransponderSnailMinGroup;
    }

    public static int getBlackTransponderSnailMaxGroup() {
        return cachedBlackTransponderSnailMaxGroup;
    }

    // Baby Black Transponder Snail spawn getters
    public static double getBabyBlackTransponderSnailSpawnRate() {
        return cachedBabyBlackTransponderSnailSpawnRate;
    }

    public static int getBabyBlackTransponderSnailMinGroup() {
        return cachedBabyBlackTransponderSnailMinGroup;
    }

    public static int getBabyBlackTransponderSnailMaxGroup() {
        return cachedBabyBlackTransponderSnailMaxGroup;
    }

    // White Den Den Mushi spawn getters
    public static double getWhiteDenDenMushiSpawnRate() {
        return cachedWhiteDenDenMushiSpawnRate;
    }

    public static int getWhiteDenDenMushiMinGroup() {
        return cachedWhiteDenDenMushiMinGroup;
    }

    public static int getWhiteDenDenMushiMaxGroup() {
        return cachedWhiteDenDenMushiMaxGroup;
    }

    // Black Transponder Snail interception range getters
    public static double getBabyBlackSnailRange() {
        if (cachedBabyBlackSnailRange <= 0 || Double.isNaN(cachedBabyBlackSnailRange)) {
            return DEFAULT_BABY_BLACK_SNAIL_RANGE;
        }
        return cachedBabyBlackSnailRange;
    }

    public static double getAdultBlackSnailDefaultRange() {
        if (cachedAdultBlackSnailDefaultRange <= 0 || Double.isNaN(cachedAdultBlackSnailDefaultRange)) {
            return DEFAULT_ADULT_BLACK_SNAIL_DEFAULT_RANGE;
        }
        return cachedAdultBlackSnailDefaultRange;
    }

    public static double getAdultBlackSnailMinRange() {
        if (cachedAdultBlackSnailMinRange <= 0 || Double.isNaN(cachedAdultBlackSnailMinRange)) {
            return DEFAULT_ADULT_BLACK_SNAIL_MIN_RANGE;
        }
        return cachedAdultBlackSnailMinRange;
    }

    public static double getAdultBlackSnailMaxRange() {
        if (cachedAdultBlackSnailMaxRange <= 0 || Double.isNaN(cachedAdultBlackSnailMaxRange)) {
            return DEFAULT_ADULT_BLACK_SNAIL_MAX_RANGE;
        }
        return cachedAdultBlackSnailMaxRange;
    }

    public static double getHornedDDMJammingRadius() {
        if (cachedHornedDDMJammingRadius <= 0 || Double.isNaN(cachedHornedDDMJammingRadius)) {
            return DEFAULT_HORNED_DDM_JAMMING_RADIUS;
        }
        return cachedHornedDDMJammingRadius;
    }
}