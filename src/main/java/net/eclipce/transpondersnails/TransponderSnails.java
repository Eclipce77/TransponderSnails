package net.eclipce.transpondersnails;

import com.mojang.logging.LogUtils;

import net.eclipce.transpondersnails.block.ModBlocks;
import net.eclipce.transpondersnails.block.entity.ModBlockEntities;
import net.eclipce.transpondersnails.block.entity.TransponderSnailBlockEntity;
import net.eclipce.transpondersnails.commands.CallCommand;
import net.eclipce.transpondersnails.commands.SnailNumberCommand;
import net.eclipce.transpondersnails.commands.SpawnTestCommand;
import net.eclipce.transpondersnails.commands.TransponderSnailItemCommand;
import net.eclipce.transpondersnails.data.SnailNumberRegistry;
import net.eclipce.transpondersnails.entity.ModEntities;
import net.eclipce.transpondersnails.item.ModCreativeModeTabs;
import net.eclipce.transpondersnails.item.ModItems;
import net.eclipce.transpondersnails.network.ModPackets;
import net.eclipce.transpondersnails.recipe.ModRecipeSerializers;
import net.eclipce.transpondersnails.recipe.ModRecipeTypes;
import net.eclipce.transpondersnails.screen.ModMenuTypes;
import net.eclipce.transpondersnails.sound.ModSounds;
import net.eclipce.transpondersnails.voice.server.TransponderCallManager;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

import static net.eclipce.transpondersnails.item.ModItemProperties.registerItemProperties;

/**
 * Main mod class for Transponder Snails
 * FIXED: Maximum crash resistance with immediate saves + JVM shutdown hook
 */
@Mod(TransponderSnails.MOD_ID)
@Mod.EventBusSubscriber
public class TransponderSnails {
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "transpondersnails";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // Call manager - will be set by the VoiceChat plugin
    private static TransponderCallManager callManager;

    // ⚡ Shutdown hook for emergency saves
    private static Thread emergencyShutdownHook;

    public TransponderSnails(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModCreativeModeTabs.register(modEventBus);

        ModSounds.register(modEventBus);

        ModEntities.register(modEventBus);
        modEventBus.register(ModEntities.class);
        ModBlockEntities.register(modEventBus);

        ModRecipeSerializers.register(modEventBus);
        ModRecipeTypes.register(modEventBus);

        ModMenuTypes.MENU_TYPES.register(modEventBus);

        ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT,
                net.eclipce.transpondersnails.config.ModConfig.CLIENT_SPEC,
                "transpondersnails-client.toml");

        // Server config goes in the world's serverconfig folder
        ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.SERVER,
                net.eclipce.transpondersnails.config.ModConfig.SERVER_SPEC,
                "transpondersnails-server.toml");

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        modEventBus.addListener(this::setup);

        // ⚡ Register JVM shutdown hook for emergency saves
        registerEmergencyShutdownHook();
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // If you also have server→client packets, register them here too
        });
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");

        event.enqueueWork(() -> {
            // Initialize network packets here
            ModPackets.init();
            LOGGER.info("ModPackets Initialized");

            // Any other setup that needs to happen after registration
        });

        event.enqueueWork(() -> {
            System.out.println("=== ITEM CLASS CHECK ===");
            System.out.println("BLACK SNAIL CLASS: " + ModItems.BLACK_TRANSPONDER_SNAIL.get().getClass().getName());
            System.out.println("BABY BLACK CLASS: " + ModItems.BABY_BLACK_TRANSPONDER_SNAIL.get().getClass().getName());
            System.out.println("PORTABLE CLASS: " + ModItems.PORTABLE_BLACK_TRANSPONDER_SNAIL.get().getClass().getName());
            System.out.println("========================");
        });
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        // Add creative tab content here if needed
    }

    /**
     * ⚡ Register a JVM shutdown hook for emergency saves
     * This catches SIGTERM and other kill signals (but not SIGKILL)
     * Provides last-ditch attempt to save data when server is killed
     */
    private void registerEmergencyShutdownHook() {
        emergencyShutdownHook = new Thread(() -> {
            System.out.println("═════════════════════════════════════════════════");
            System.out.println("⚠️  EMERGENCY SHUTDOWN DETECTED! ⚠️");
            System.out.println("TransponderSnails: Attempting emergency save...");
            System.out.println("═════════════════════════════════════════════════");

            try {
                SnailNumberRegistry registry = SnailNumberRegistry.getInstance();
                if (registry != null) {
                    System.out.println("TransponderSnails: Emergency saving registry...");
                    registry.forceSave();
                    System.out.println("✅ TransponderSnails: Emergency save successful!");
                } else {
                    System.out.println("TransponderSnails: No registry to save (server may not have started)");
                }
            } catch (Exception e) {
                System.err.println("❌ TransponderSnails: Emergency save failed: " + e.getMessage());
                e.printStackTrace();
            }

            System.out.println("═════════════════════════════════════════════════");
        }, "TransponderSnails-EmergencyShutdown");

        Runtime.getRuntime().addShutdownHook(emergencyShutdownHook);
        System.out.println("TransponderSnails: Emergency shutdown hook registered");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");

        // Reset server state when server starts
        TransponderSnailBlockEntity.setServerStartingUp();
        System.out.println("TransponderSnails: Server starting - reset block entity state");

        // The registry will be automatically loaded when first accessed via getInstance()
        System.out.println("TransponderSnails: SnailNumberRegistry will load on first access");
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║ TransponderSnails: GRACEFUL SHUTDOWN INITIATED             ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // Set shutdown flag to prevent infinite loops during world save
        TransponderSnailBlockEntity.setServerShuttingDown();
        System.out.println("TransponderSnails: Prevented block entity loops");

        // Force save the registry
        SnailNumberRegistry registry = SnailNumberRegistry.getInstance();
        if (registry != null) {
            System.out.println("TransponderSnails: Graceful shutdown - saving registry...");
            registry.forceSave();
            System.out.println("TransponderSnails: ✅ Registry saved successfully");
        } else {
            System.out.println("TransponderSnails: No registry instance to save");
        }

        // Reset the instance cache
        SnailNumberRegistry.resetInstance();
        System.out.println("TransponderSnails: Instance cache reset");

        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║ TransponderSnails: GRACEFUL SHUTDOWN COMPLETE              ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
    }

    // Register commands
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CallCommand.register(event.getDispatcher());
        SnailNumberCommand.register(event.getDispatcher());
        TransponderSnailItemCommand.register(event.getDispatcher());
        SpawnTestCommand.register(event.getDispatcher());
    }

    // Handle player disconnection
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // Handle player logout events here if needed
    }

    // Static setter for the call manager (called by VoiceChat plugin)
    public static void setCallManager(TransponderCallManager manager) {
        callManager = manager;
        LOGGER.info("Call manager initialized successfully!");
    }

    // Static getter for the call manager
    public static TransponderCallManager getCallManager() {
        if (callManager == null) {
            LOGGER.warn("Call manager not initialized! Make sure Simple Voice Chat is installed and the server has started.");
            return null; // Return null instead of throwing exception to prevent crashes
        }
        return callManager;
    }

    public static final Collection<AbstractMap.SimpleEntry<Runnable, Integer>> workQueue = new ConcurrentLinkedQueue<>();

    /**
     * Client-only event bus subscriber
     * This inner class is properly isolated and only loads on the client
     */
    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // Some client setup code
            LOGGER.info("HELLO FROM CLIENT SETUP");

            // Get player name safely (only runs on client)
            event.enqueueWork(() -> {
                registerItemProperties();

                // Register wire block as cutout (transparent)
                ItemBlockRenderTypes.setRenderLayer(
                        ModBlocks.WIRE.get(),
                        RenderType.cutout()
                );

                try {
                    // This import is safe because this entire class is client-only
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    LOGGER.info("MINECRAFT NAME >> {}", mc.getUser().getName());
                } catch (Exception e) {
                    LOGGER.warn("Could not get Minecraft username: " + e.getMessage());
                }
            });
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickDebug(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        Item item = stack.getItem();

        if (item == ModItems.BLACK_TRANSPONDER_SNAIL.get() ||
                item == ModItems.BABY_BLACK_TRANSPONDER_SNAIL.get()) {

            System.out.println("RIGHT CLICK DETECTED: " + item.getClass().getSimpleName());
            System.out.println("Event canceled: " + event.isCanceled());
        }
    }
}