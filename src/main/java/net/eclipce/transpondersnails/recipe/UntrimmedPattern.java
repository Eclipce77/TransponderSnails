package net.eclipce.transpondersnails.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;

/**
 * Vanilla trims every blank row and column off a shaped recipe when it loads, so
 * {@code "   ", " DT", "   "} becomes a 2x1 recipe that matches (and is displayed) anywhere.
 *
 * <p>This rebuilds the recipe at the size it was <b>written</b> in the JSON, with empty cells where the
 * blanks were. A recipe built from the result is a true 3x3 (or whatever size the pattern was), so:
 * <ul>
 *   <li>it only matches when the ingredients are in those exact cells (mirroring is still allowed by
 *       vanilla), and only in a 3x3 crafting grid, not the 2x2 inventory grid;</li>
 *   <li>JEI, EMI, REI and the recipe book all show it as written, with no special integration.</li>
 * </ul>
 *
 * <p>Call {@link #restore} <b>after</b> vanilla's parser has accepted the JSON, so the pattern is known
 * to be well-formed (rectangular, at most 3x3, every key defined).
 */
public final class UntrimmedPattern {

    private UntrimmedPattern() {
    }

    /** Dimensions and ingredients of the recipe at its written size. */
    public record Result(int width, int height, NonNullList<Ingredient> ingredients) {
    }

    /**
     * @param json    the recipe JSON (needs its {@code "pattern"} array)
     * @param trimmed the recipe as vanilla parsed it, i.e. already trimmed
     */
    public static Result restore(JsonObject json, ShapedRecipe trimmed) {
        if (!json.has("pattern") || !json.get("pattern").isJsonArray()) {
            return unchanged(trimmed);
        }

        JsonArray rows = json.getAsJsonArray("pattern");
        int height = rows.size();
        if (height == 0) {
            return unchanged(trimmed);
        }
        int width = rows.get(0).getAsString().length();

        // How many blank columns/rows did vanilla cut from the left/top? (mirrors ShapedRecipe.shrink)
        int offsetX = Integer.MAX_VALUE;
        int offsetY = -1;
        for (int y = 0; y < height; y++) {
            String row = rows.get(y).getAsString();
            int firstNonSpace = 0;
            while (firstNonSpace < row.length() && row.charAt(firstNonSpace) == ' ') {
                firstNonSpace++;
            }
            if (firstNonSpace == row.length()) {
                continue; // blank row
            }
            if (offsetY < 0) {
                offsetY = y;
            }
            offsetX = Math.min(offsetX, firstNonSpace);
        }
        if (offsetY < 0) {
            return unchanged(trimmed);
        }

        // Put every trimmed ingredient back into its original cell; everything else stays empty.
        NonNullList<Ingredient> padded = NonNullList.withSize(width * height, Ingredient.EMPTY);
        NonNullList<Ingredient> source = trimmed.getIngredients();
        for (int y = 0; y < trimmed.getHeight(); y++) {
            for (int x = 0; x < trimmed.getWidth(); x++) {
                int cellX = x + offsetX;
                int cellY = y + offsetY;
                if (cellX >= width || cellY >= height) {
                    continue; // can't happen for a valid pattern; never index out of range
                }
                padded.set(cellX + cellY * width, source.get(x + y * trimmed.getWidth()));
            }
        }
        return new Result(width, height, padded);
    }

    private static Result unchanged(ShapedRecipe trimmed) {
        return new Result(trimmed.getWidth(), trimmed.getHeight(), trimmed.getIngredients());
    }
}
