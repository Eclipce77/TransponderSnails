package net.eclipce.transpondersnails.event;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.block.ModBlocks;
import net.eclipce.transpondersnails.entity.ModEntities;
import net.eclipce.transpondersnails.item.ModItems;
import net.eclipce.transpondersnails.visual.LegacyIds;
import net.minecraft.world.entity.EntityType;
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
            String path = mapping.getKey().getPath();
            if (LegacyIds.current(path) != null) mapping.remap(ModItems.VISUAL_TRANSMISSION_TRANSPONDER_SNAIL_TRANSMITTER.get());
            else if (LegacyIds.currentSpawnEgg(path) != null) {
                mapping.remap(LegacyIds.currentSpawnEgg(path).startsWith("baby_")
                        ? ModItems.BABY_BLACK_DEN_DEN_MUSHI_SPAWN_EGG.get() : ModItems.BLACK_DEN_DEN_MUSHI_SPAWN_EGG.get());
            }
        }
        // creatures saved with the old entity ids (placed in a world) become the Black Den Den Mushi
        for (MissingMappingsEvent.Mapping<EntityType<?>> mapping : event.getMappings(ForgeRegistries.Keys.ENTITY_TYPES, TransponderSnails.MOD_ID)) {
            String current = LegacyIds.currentEntity(mapping.getKey().getPath());
            if (current == null) continue;
            mapping.remap(current.startsWith("baby_") ? ModEntities.BABY_BLACK_DEN_DEN_MUSHI.get() : ModEntities.BLACK_DEN_DEN_MUSHI.get());
        }
    }
}
