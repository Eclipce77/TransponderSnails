package net.eclipce.transpondersnails.datagen;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.item.ModItems;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.model.generators.ItemModelBuilder;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.RegistryObject;

public class ModItemModelProvider extends ItemModelProvider {
    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, TransponderSnails.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        simpleItem(ModItems.DEN_DEN_MUSHI);
        simpleItem(ModItems.BABY_TRANSPONDER_SNAIL);
        simpleItem(ModItems.BLACK_TRANSPONDER_SNAIL);
    }

    private ItemModelBuilder simpleItem(RegistryObject<Item> item) {
        return withExistingParent(item.getId().getPath(),
            new ResourceLocation("item/generated")).texture("layer0",
            new ResourceLocation(TransponderSnails.MOD_ID, "item/" + item.getId().getPath()));
    }
}
