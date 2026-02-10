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

    // Snail Sounds
    public static final RegistryObject<SoundEvent> SNAIL_CONNECTED = registerSoundEvents("snail_connected");
    public static final RegistryObject<SoundEvent> SNAIL_DISCONNECTED = registerSoundEvents("snail_disconnected");
    public static final RegistryObject<SoundEvent> SNAIL_RINGING = registerSoundEvents("snail_ringing");
    public static final RegistryObject<SoundEvent> SNAIL_BUSY = registerSoundEvents("snail_busy");

    public static final RegistryObject<SoundEvent> SNAIL_PICK_UP = registerSoundEvents("snail_pick_up");
    public static final RegistryObject<SoundEvent> SNAIL_HANG_UP = registerSoundEvents("snail_hang_up");

    // Dial GUI Sounds
    public static final RegistryObject<SoundEvent> DIAL_BUTTON = registerSoundEvents("dial_button");
    public static final RegistryObject<SoundEvent> CLEAR_BUTTON = registerSoundEvents("clear_button");

    // Static Sounds
    public static final RegistryObject<SoundEvent> LOOPING_STATIC = registerSoundEvents("looping_static");

    private static RegistryObject<SoundEvent> registerSoundEvents(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(TransponderSnails.MOD_ID, name)));
    }

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }

}
