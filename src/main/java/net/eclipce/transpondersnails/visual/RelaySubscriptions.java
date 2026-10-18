package net.eclipce.transpondersnails.visual;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Who wants the picture of which camera relayed to them: a viewer whose client cannot render the camera itself (its surroundings are not
 * loaded there) asks, and has to keep asking - a request that is not renewed runs out. Pure (no Minecraft classes), unit tested.
 *
 * @param <C> the camera (a position and a dimension)
 * @param <V> the viewer (a player id)
 */
public final class RelaySubscriptions<C, V> {

    private final Map<C, Map<V, Long>> subscriptions = new HashMap<>();

    /** The viewer wants the camera until (and not including) {@code expiresAt}; asking again renews it. */
    public void touch(C camera, V viewer, long expiresAt) {
        subscriptions.computeIfAbsent(camera, k -> new HashMap<>()).put(viewer, expiresAt);
    }

    public void remove(C camera, V viewer) {
        Map<V, Long> viewers = subscriptions.get(camera);
        if (viewers == null) return;
        viewers.remove(viewer);
        if (viewers.isEmpty()) subscriptions.remove(camera);
    }

    /** The viewer leaves (logs out, changes dimension): all its requests go. */
    public void removeViewer(V viewer) {
        Iterator<Map.Entry<C, Map<V, Long>>> it = subscriptions.entrySet().iterator();
        while (it.hasNext()) {
            Map<V, Long> viewers = it.next().getValue();
            viewers.remove(viewer);
            if (viewers.isEmpty()) it.remove();
        }
    }

    /** Drops every request that has run out; a camera nobody wants any more is forgotten. */
    public void expire(long now) {
        Iterator<Map.Entry<C, Map<V, Long>>> it = subscriptions.entrySet().iterator();
        while (it.hasNext()) {
            Map<V, Long> viewers = it.next().getValue();
            viewers.values().removeIf(expiresAt -> expiresAt <= now);
            if (viewers.isEmpty()) it.remove();
        }
    }

    /** The viewers of a camera (a copy). */
    public Set<V> viewersOf(C camera) {
        Map<V, Long> viewers = subscriptions.get(camera);
        return viewers == null ? Collections.emptySet() : new HashSet<>(viewers.keySet());
    }

    public boolean isWatching(C camera, V viewer) {
        Map<V, Long> viewers = subscriptions.get(camera);
        return viewers != null && viewers.containsKey(viewer);
    }

    /** The cameras somebody wants (a copy). */
    public Set<C> cameras() {
        return new HashSet<>(subscriptions.keySet());
    }

    public int viewerCount(C camera) {
        Map<V, Long> viewers = subscriptions.get(camera);
        return viewers == null ? 0 : viewers.size();
    }

    public boolean isEmpty() {
        return subscriptions.isEmpty();
    }
}
