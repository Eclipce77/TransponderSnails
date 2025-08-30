package net.eclipce.transpondersnails;

import com.mojang.logging.LogUtils;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import net.eclipce.transpondersnails.block.ModBlocks;
import net.eclipce.transpondersnails.block.entity.ModBlockEntities;
import net.eclipce.transpondersnails.commands.CallCommand;
import net.eclipce.transpondersnails.item.ModCreativeModeTabs;
import net.eclipce.transpondersnails.item.ModItems;
import net.eclipce.transpondersnails.network.ModPackets;
import net.eclipce.transpondersnails.screen.ModMenuTypes;
import net.eclipce.transpondersnails.sound.ModSounds;

import net.eclipce.transpondersnails.voice.server.TransponderCallManager;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(TransponderSnails.MOD_ID)
@Mod.EventBusSubscriber
public class TransponderSnails {
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "transpondersnails";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // Call manager - will be set by the VoiceChat plugin
    private static TransponderCallManager callManager;

    public TransponderSnails(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModCreativeModeTabs.register(modEventBus);

        ModSounds.register(modEventBus);

        ModBlockEntities.register(modEventBus);
        ModMenuTypes.MENU_TYPES.register(modEventBus);

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        modEventBus.addListener(this::setup);

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
            // TransponderCallManager.init();
        });

    }

    // Add the example block item to the building blocks tab
    // You can use SubscribeEvent and let the Event Bus discover methods to call

    private void addCreative(BuildCreativeModeTabContentsEvent event) {

    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }

    // Register commands
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CallCommand.register(event.getDispatcher());
    }

    // Handle player disconnection
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (callManager != null && event.getEntity() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            callManager.handlePlayerDisconnect(player);
        }
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

    // Check if call manager is available
    public static boolean isCallManagerAvailable() {
        return callManager != null;
    }

    public static final Collection<AbstractMap.SimpleEntry<Runnable, Integer>> workQueue = new ConcurrentLinkedQueue<>();

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            // Some client setup code
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }
    }
}