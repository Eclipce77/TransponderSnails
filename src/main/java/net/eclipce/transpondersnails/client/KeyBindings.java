package net.eclipce.transpondersnails.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Keybind definitions for Transponder Snails.
 *
 * Keybinds are always registered regardless of whether Curios is installed.
 * The handler (CuriosKeybindHandler) silently does nothing if no snail is equipped anywhere.
 */
public class KeyBindings {

    public static final String CATEGORY = "key.category.transpondersnails";

    /**
     * Open / close the Portable Black Transponder Snail.
     *
     * Works when the snail is:
     *   - Held in the main hand
     *   - Held in the offhand
     *   - Equipped in a Curios slot (if Curios is installed)
     *
     * Default key: ` (backtick/grave)  (rebindable in Options → Controls)
     */
    public static final KeyMapping SNAIL_INTERACT = new KeyMapping(
            "key.transpondersnails.snail_interact",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_GRAVE_ACCENT,
            CATEGORY
    );

    /**
     * Called from ClientSetup via RegisterKeyMappingsEvent (MOD bus).
     */
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(SNAIL_INTERACT);
    }
}