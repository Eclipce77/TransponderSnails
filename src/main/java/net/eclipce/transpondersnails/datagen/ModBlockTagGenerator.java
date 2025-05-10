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
        this.tag(BlockTags.CORAL_BLOCKS)
                .add(ModBlocks.TRANSPONDER_SNAIL.get())
                .add(ModBlocks.HORNED_TRANSPONDER_SNAIL.get())
                .add(ModBlocks.VISUAL_TRANSPONDER_SNAIL.get())
                .add(ModBlocks.SURVEILLANCE_TRANSPONDER_SNAIL.get())
                .add(ModBlocks.TRANSMISSION_TRANSPONDER_SNAIL.get());

    }
}
