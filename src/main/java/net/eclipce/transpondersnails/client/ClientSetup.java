// Fixed Client Setup - matching your exact package structure
package net.eclipce.transpondersnails.client;

// Import your exact classes from the screen package
import net.eclipce.transpondersnails.block.entity.ModBlockEntities;
import net.eclipce.transpondersnails.client.renderer.BlackTransponderSnailBlockEntityRenderer;
import net.eclipce.transpondersnails.client.renderer.HornedDenDenMushiBlockEntityRenderer;
import net.eclipce.transpondersnails.entity.ModEntities;
import net.eclipce.transpondersnails.entity.client.BabyBlackTransponderSnailRenderer;
import net.eclipce.transpondersnails.entity.client.BlackTransponderSnailRenderer;
import net.eclipce.transpondersnails.entity.client.DenDenMushiRenderer;
import net.eclipce.transpondersnails.entity.client.HornedDenDenMushiRenderer;
import net.eclipce.transpondersnails.entity.client.WhiteDenDenMushiRenderer;
import net.eclipce.transpondersnails.screen.DialingScreen;
import net.eclipce.transpondersnails.screen.ModMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = "transpondersnails", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // Register the screen for the menu type
            MenuScreens.register(ModMenuTypes.DIALING_MENU.get(), DialingScreen::new);
        });

        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parentScreen) -> new ClientConfigScreen(parentScreen)
                )
        );

        EntityRenderers.register(ModEntities.DEN_DEN_MUSHI.get(), DenDenMushiRenderer::new);
        EntityRenderers.register(ModEntities.WHITE_DEN_DEN_MUSHI.get(), WhiteDenDenMushiRenderer::new);
        EntityRenderers.register(ModEntities.HORNED_DEN_DEN_MUSHI.get(), HornedDenDenMushiRenderer::new);
        EntityRenderers.register(ModEntities.BABY_BLACK_TRANSPONDER_SNAIL.get(), BabyBlackTransponderSnailRenderer::new);
        EntityRenderers.register(ModEntities.BLACK_TRANSPONDER_SNAIL.get(), BlackTransponderSnailRenderer::new);

        // Block Entity Renderer — REQUIRED because BlackTransponderSnailBlock implements EntityBlock.
        // EntityBlock blocks render NO geometry without a registered BlockEntityRenderer.
        BlockEntityRenderers.register(ModBlockEntities.BLACK_TRANSPONDER_SNAIL_BE.get(), BlackTransponderSnailBlockEntityRenderer::new);

        // Block Entity Renderer for the Horned Den Den Mushi jammer block.
        // HornedDenDenMushiBlock uses RenderShape.ENTITYBLOCK_ANIMATED — without this
        // registration the block will be invisible in the world.
        BlockEntityRenderers.register(ModBlockEntities.HORNED_DEN_DEN_MUSHI_BLOCK_ENTITY.get(), HornedDenDenMushiBlockEntityRenderer::new);

        System.out.println("Registered entity renderers");

    }

    /**
     * Register keybinds on the MOD bus.
     * RegisterKeyMappingsEvent fires separately from FMLClientSetupEvent.
     */
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        KeyBindings.register(event);
    }
}