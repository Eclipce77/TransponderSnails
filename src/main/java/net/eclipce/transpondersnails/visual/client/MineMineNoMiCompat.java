package net.eclipce.transpondersnails.visual.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;

/**
 * Compatibility switch for Mine Mine no Mi. The mod is NOT a dependency: nothing in this project references its classes, and nothing here runs unless it is installed.
 * Only its mod id is looked up, once, when a feed is first captured (the mod list is complete by then).
 *
 * What the compatibility does (all of it is guarded by {@link #present()}):
 *  - VisualFeedManager: LevelRenderer#entityTarget is hidden during a capture, so the mod's forced "entity outline" step cannot reset the GL viewport inside the picture.
 *  - VisualClientEvents: its camera hooks (dizzy roll, camera lock) are overridden for the feed camera.
 */
@OnlyIn(Dist.CLIENT)
public final class MineMineNoMiCompat {

    public static final String MOD_ID = "mineminenomi";

    private static Boolean present;

    private MineMineNoMiCompat() {}

    /** True when Mine Mine no Mi is installed. Not cached until the mod list exists. */
    public static boolean present() {
        if (present == null) {
            ModList mods = ModList.get();
            if (mods == null) return false;
            present = mods.isLoaded(MOD_ID);
        }
        return present;
    }
}
