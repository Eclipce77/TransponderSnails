package net.eclipce.transpondersnails.sound;

import net.eclipce.one_piece_mod.OnePieceMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, OnePieceMod.MOD_ID);

    //public static final RegistryObject<SoundEvent> KAIROSEKI_ORE_BREAK = registerSoundEvents("kairoseki_ore_break");
    //public static final RegistryObject<SoundEvent> KAIROSEKI_ORE_STEP = registerSoundEvents("kairoseki_ore_step");
    //public static final RegistryObject<SoundEvent> KAIROSEKI_ORE_PLACE = registerSoundEvents("kairoseki_ore_place");
    //public static final RegistryObject<SoundEvent> KAIROSEKI_ORE_HIT = registerSoundEvents("kairoseki_ore_hit");

    //public static final ForgeSoundType KAIROSEKI_ORE_SOUNDS = new ForgeSoundType( 1f, 1f,
    //        ModSounds.KAIROSEKI_ORE_BREAK, ModSounds.KAIROSEKI_ORE_STEP, ModSounds.KAIROSEKI_ORE_PLACE, ModSounds.KAIROSEKI_ORE_HIT);

    public static final RegistryObject<SoundEvent> DEVIL_FRUIT_GAIN = registerSoundEvents("devil_fruit_gain");

    public static final RegistryObject<SoundEvent> SOGEKING_THEME_SONG = registerSoundEvents("sogeking_theme_song");
    public static final RegistryObject<SoundEvent> LUFFY_BAKA_SONG = registerSoundEvents("luffy_baka_song");
    public static final RegistryObject<SoundEvent> BINKS_SAKE = registerSoundEvents("binks_sake");
    public static final RegistryObject<SoundEvent> FRANKYS_THEME = registerSoundEvents("frankys_theme");
    public static final RegistryObject<SoundEvent> WE_ARE = registerSoundEvents("we_are");

    private static RegistryObject<SoundEvent> registerSoundEvents(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(OnePieceMod.MOD_ID, name)));
    }

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
}

}
