package net.eclipce.transpondersnails.data;

import net.eclipce.transpondersnails.TransponderSnails;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/**
 * World‐saved registry mapping player UUIDs to their immutable Snail Number.
 * Enforces:
 * 1) Uniqueness
 * 2) Immutable once set (unless via admin)
 * 3) Range restriction: no numbers 0–100
 */
public class SnailNumberRegistry extends SavedData {

    private static final String DATA_NAME = TransponderSnails.MOD_ID + "_snail_numbers";

    // player → number
    private final Map<UUID, Integer> playerToNumber = new HashMap<>();
    // number → player
    private final Map<Integer, UUID> numberToPlayer = new HashMap<>();

    public SnailNumberRegistry() {
        super();
    }

    private SnailNumberRegistry(CompoundTag tag) {
        super();
        fromTag(tag);
    }

    /** Fetches (or creates) the registry for this world */
    public static SnailNumberRegistry get(ServerLevel level) {
        return level.getDataStorage()
                .computeIfAbsent(
                        SnailNumberRegistry::load,      // ← supplier for a fresh instance
                        SnailNumberRegistry::new,     // ← function to rehydrate from NBT
                        DATA_NAME                      // ← storage key
                );
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        playerToNumber.forEach((uuid, num) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", uuid);
            entry.putInt("number", num);
            list.add(entry);
        });
        tag.put("entries", list);
        return tag;
    }

    private static SnailNumberRegistry load(CompoundTag tag) {
        return new SnailNumberRegistry(tag);
    }

    private void fromTag(CompoundTag tag) {
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            UUID uuid = entry.getUUID("player");
            int num = entry.getInt("number");
            playerToNumber.put(uuid, num);
            numberToPlayer.put(num, uuid);
        }
    }

    /**
     * Attempts to register `num` for `player`.
     * @return true on success, false if:
     *   - player already has a number
     *   - number < 101
     *   - number already in use
     */
    public boolean registerNumber(Player player, int num) {
        UUID uuid = player.getUUID();
        // 1) Immutable once set
        if (playerToNumber.containsKey(uuid)) {
            return false;
        }
        // 2) Disallow 0–100
        if (num < 101) {
            return false;
        }
        // 3) Unique
        if (numberToPlayer.containsKey(num)) {
            return false;
        }
        // 4) Register
        playerToNumber.put(uuid, num);
        numberToPlayer.put(num, uuid);
        setDirty();
        return true;
    }

    /**
     * Admin/OP override to force-set (or change) a player's number.
     * Will remove any previous mapping for that player, and evict
     * any other player who had `num`.
     */
    public boolean forceSetNumber(UUID playerUuid, int num) {
        // Disallow 0–100
        if (num < 101) {
            return false;
        }
        // If some other player already has this number, reject
        UUID existingHolder = numberToPlayer.get(num);
        if (existingHolder != null && !existingHolder.equals(playerUuid)) {
            return false;
        }
        // Remove old for this player
        Integer old = playerToNumber.remove(playerUuid);
        if (old != null) {
            numberToPlayer.remove(old);
        }
        // Evict any other holder of this num
        UUID prior = numberToPlayer.remove(num);
        if (prior != null) {
            playerToNumber.remove(prior);
        }
        // Assign new
        playerToNumber.put(playerUuid, num);
        numberToPlayer.put(num, playerUuid);
        setDirty();
        return true;
    }

    /**
     * Removes a player's Snail Number (admin only).
     * @return true if a number was removed, false if none existed
     */
    public boolean removeNumber(UUID playerUuid) {
        Integer old = playerToNumber.remove(playerUuid);
        if (old != null) {
            numberToPlayer.remove(old);
            setDirty();
            return true;
        }
        return false;
    }

    /** Lookup for a player's own number */
    public OptionalInt getNumber(Player player) {
        Integer num = playerToNumber.get(player.getUUID());
        return num == null ? OptionalInt.empty() : OptionalInt.of(num);
    }

    /** Lookup for which player holds a given number */
    public Optional<UUID> getPlayerByNumber(int num) {
        return Optional.ofNullable(numberToPlayer.get(num));
    }

    /** Does the player already have a set number? */
    public boolean hasNumber(Player player) {
        return playerToNumber.containsKey(player.getUUID());
    }

    public Collection<Integer> getAllNumbers() {
        return playerToNumber.values();
    }

    public OptionalInt getNumberByUuid(UUID groupUuid) {
        // Iterate all numbers, regenerate the UUID, and compare
        for (int num : playerToNumber.values()) {
            String str = String.format("%04d", num);
            UUID generated = UUID.nameUUIDFromBytes(str.getBytes());
            if (generated.equals(groupUuid)) {
                return OptionalInt.of(num);
            }
        }
        return OptionalInt.empty();
    }

}
