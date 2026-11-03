package net.eclipce.transpondersnails.visual;

/**
 * Ids that were renamed. The camera snail was "visual_transmitter_transponder_snail"; it is now the Visual Transmission Transponder Snail, and
 * the one that was is the one WITH a Transmitter. A world saved before still names the old id: it is mapped to the new one, so nothing placed
 * or carried disappears. Pure (no Minecraft classes), unit tested; LegacyIdRemapper applies it.
 */
public final class LegacyIds {

    private LegacyIds() {}

    public static final String OLD_CAMERA = "visual_transmitter_transponder_snail";
    public static final String NEW_CAMERA_WITH_TRANSMITTER = "visual_transmission_transponder_snail_transmitter";

    /** The jammer block was "horned_den_den_mushi_block" (placed by the Horned Den Den Mushi item); it is now its own block, the Horned Transponder Snail. */
    public static final String OLD_HORNED_BLOCK = "horned_den_den_mushi_block";
    public static final String NEW_HORNED_BLOCK = "horned_transponder_snail";

    /** The black creatures were "black_transponder_snail" and "baby_black_transponder_snail" (entity ids, and the ids of their spawn eggs); they are the Black Den Den Mushi. */
    public static final String OLD_BLACK_ENTITY = "black_transponder_snail";
    public static final String NEW_BLACK_ENTITY = "black_den_den_mushi";
    public static final String OLD_BABY_BLACK_ENTITY = "baby_black_transponder_snail";
    public static final String NEW_BABY_BLACK_ENTITY = "baby_black_den_den_mushi";

    /** The current ENTITY id (path) for an old one, or null if it was not renamed. (Only valid for the entity registry: the ITEMS of the same names still exist.) */
    public static String currentEntity(String oldPath) {
        if (OLD_BLACK_ENTITY.equals(oldPath)) return NEW_BLACK_ENTITY;
        if (OLD_BABY_BLACK_ENTITY.equals(oldPath)) return NEW_BABY_BLACK_ENTITY;
        return null;
    }

    /** The old name (as the config's disabled list had it) of a renamed creature, or null. */
    public static String legacyEntityName(String currentPath) {
        if (NEW_BLACK_ENTITY.equals(currentPath)) return OLD_BLACK_ENTITY;
        if (NEW_BABY_BLACK_ENTITY.equals(currentPath)) return OLD_BABY_BLACK_ENTITY;
        return null;
    }

    /** The current id (path) of a renamed SPAWN EGG item, or null. (The items "black_transponder_snail" and "baby_black_transponder_snail" themselves still exist.) */
    public static String currentSpawnEgg(String oldPath) {
        if ((OLD_BLACK_ENTITY + "_spawn_egg").equals(oldPath)) return NEW_BLACK_ENTITY + "_spawn_egg";
        if ((OLD_BABY_BLACK_ENTITY + "_spawn_egg").equals(oldPath)) return NEW_BABY_BLACK_ENTITY + "_spawn_egg";
        return null;
    }

    /** The current ITEM id (path) for an old one, or null if it was not renamed. (The jammer block never had an item of its own.) */
    public static String current(String oldPath) {
        return OLD_CAMERA.equals(oldPath) ? NEW_CAMERA_WITH_TRANSMITTER : null;
    }

    /** The current BLOCK id (path) for an old one, or null if it was not renamed. */
    public static String currentBlock(String oldPath) {
        if (OLD_CAMERA.equals(oldPath)) return NEW_CAMERA_WITH_TRANSMITTER;
        if (OLD_HORNED_BLOCK.equals(oldPath)) return NEW_HORNED_BLOCK;
        return null;
    }
}
