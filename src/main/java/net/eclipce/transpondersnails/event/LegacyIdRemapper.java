package net.eclipce.transpondersnails.event;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.block.ModBlocks;
import net.eclipce.transpondersnails.item.ModItems;
import net.eclipce.transpondersnails.visual.LegacyIds;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;

/**
 * A world (or a player's inventory) saved with the old id of the camera snail ("visual_transmitter_transponder_snail") is loaded with the
 * renamed one: the old camera snail is the Visual Transmission Transponder Snail WITH a Transmitter. See {@link LegacyIds}.
 */
@Mod.EventBusSubscriber(modid = TransponderSnails.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LegacyIdRemapper {

    private LegacyIdRemapper() {}

    @SubscribeEvent
    public static void onMissingMappings(MissingMappingsEvent event) {
        for (MissingMappingsEvent.Mapping<Block> mapping : event.getMappings(ForgeRegistries.Keys.BLOCKS, TransponderSnails.MOD_ID)) {
            String path = mapping.getKey().getPath();
            if (LegacyIds.NEW_HORNED_BLOCK.equals(LegacyIds.currentBlock(path))) {
                mapping.remap(ModBlocks.HORNED_TRANSPONDER_SNAIL.get());                                    // a placed jammer: now the Horned Transponder Snail (switched off)
            } else if (LegacyIds.currentBlock(path) != null) {
                mapping.remap(ModBlocks.VISUAL_TRANSMISSION_TRANSPONDER_SNAIL_TRANSMITTER.get());
            }
        }
        for (MissingMappingsEvent.Mapping<Item> mapping : event.getMappings(ForgeRegistries.Keys.ITEMS, TransponderSnails.MOD_ID)) {
            if (LegacyIds.current(mapping.getKey().getPath()) != null) mapping.remap(ModItems.VISUAL_TRANSMISSION_TRANSPONDER_SNAIL_TRANSMITTER.get());
        }
    }
}
