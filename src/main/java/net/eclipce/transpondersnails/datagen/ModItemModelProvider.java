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
        simpleItem(ModItems.KAIROSEKIINGOT);
        simpleItem(ModItems.RAWKAIROSEKI);

        simpleItem(ModItems.GOMU_GOMU_NO_MI);
        simpleItem(ModItems.MERA_MERA_NO_MI);
        simpleItem(ModItems.OPE_OPE_NO_MI);
        simpleItem(ModItems.PIKA_PIKA_NO_MI);
        simpleItem(ModItems.SUNA_SUNA_NO_MI);
        simpleItem(ModItems.TORI_TORI_NO_MI_MODEL_PHOENIX);

        simpleItem(ModItems.SOGEKING_THEME_SONG);
        simpleItem(ModItems.LUFFY_BAKA_SONG);
        simpleItem(ModItems.BINKS_SAKE);
        simpleItem(ModItems.FRANKYS_THEME);
        simpleItem(ModItems.WE_ARE);

        simpleItem(ModItems.SMILE);
    }

    private ItemModelBuilder simpleItem(RegistryObject<Item> item) {
        return withExistingParent(item.getId().getPath(),
            new ResourceLocation("item/generated")).texture("layer0",
            new ResourceLocation(TransponderSnails.MOD_ID, "item/" + item.getId().getPath()));
    }
}
