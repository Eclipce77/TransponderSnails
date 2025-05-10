package net.eclipce.transpondersnails.sound;

import net.eclipce.transpondersnails.TransponderSnails;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, TransponderSnails.MOD_ID);

    public static final RegistryObject<SoundEvent> DEN_DEN_MUSHI_CA_CHA = registerSoundEvents("den_den_mushi_ca_cha");
    public static final RegistryObject<SoundEvent> DEN_DEN_MUSHI_RINGING = registerSoundEvents("den_den_mushi_ringing");

    private static RegistryObject<SoundEvent> registerSoundEvents(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(TransponderSnails.MOD_ID, name)));
    }

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
}

}
