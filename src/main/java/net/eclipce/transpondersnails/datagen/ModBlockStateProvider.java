package net.eclipce.transpondersnails.datagen;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.block.ModBlocks;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockStateProvider extends BlockStateProvider {
    public ModBlockStateProvider(PackOutput output, ExistingFileHelper exFileHelper) {
        super(output, TransponderSnails.MOD_ID, exFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        blockWithItem(ModBlocks.DEN_DEN_MUSHI);
        blockWithItem(ModBlocks.TRANSPONDER_SNAIL);
        blockWithItem(ModBlocks.HORNED_TRANSPONDER_SNAIL);
        blockWithItem(ModBlocks.VISUAL_TRANSPONDER_SNAIL);
        blockWithItem(ModBlocks.SURVEILLANCE_TRANSPONDER_SNAIL);
        blockWithItem(ModBlocks.TRANSMISSION_TRANSPONDER_SNAIL);
    }

    private void blockWithItem(RegistryObject<Block> blockRegistryObject) {
        simpleBlockWithItem(blockRegistryObject.get(), cubeAll(blockRegistryObject.get()));
    }
}
