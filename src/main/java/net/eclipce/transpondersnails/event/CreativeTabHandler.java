package net.eclipce.transpondersnails.event;

import net.eclipce.transpondersnails.item.ModItems;
import net.eclipce.transpondersnails.block.ModBlocks;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "transpondersnails", bus = Mod.EventBusSubscriber.Bus.MOD)
public class CreativeTabHandler {

    @SubscribeEvent
    public static void buildContents(BuildCreativeModeTabContentsEvent event) {

        // Add snail-related items to Ingredients tab
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {

            event.getEntries().putAfter(
                    Items.IRON_NUGGET.getDefaultInstance(),
                    ModItems.COPPER_NUGGET.get().getDefaultInstance(),
                    CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
            );
        }
    }
}