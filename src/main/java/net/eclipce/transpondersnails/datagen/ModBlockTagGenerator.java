package net.eclipce.transpondersnails.datagen;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.block.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class ModBlockTagGenerator extends BlockTagsProvider {
    public ModBlockTagGenerator(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, TransponderSnails.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider pProvider) {
        this.tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(ModBlocks.KAIROSEKI_BLOCK.get(),
                        ModBlocks.DEEPSLATE_KAIROSEKI_ORE.get(),
                        ModBlocks.KAIROSEKI_ORE.get());

        this.tag(BlockTags.NEEDS_STONE_TOOL);

        this.tag(BlockTags.NEEDS_IRON_TOOL);

        this.tag(BlockTags.NEEDS_DIAMOND_TOOL)
                .add(ModBlocks.KAIROSEKI_BLOCK.get(),
                        ModBlocks.DEEPSLATE_KAIROSEKI_ORE.get(),
                        ModBlocks.KAIROSEKI_ORE.get());

        this.tag(BlockTags.CORAL_BLOCKS)
                .add(ModBlocks.DEN_DEN_MUSHI.get());

    }
}
