package net.eclipce.transpondersnails.visual;

/**
 * Which entities a snail's picture may show (pure, unit tested). The picture has terrain only inside the feed's view radius around the camera and
 * only where the player's own client has built it (the player's render distance); entities are tracked further out than that, and drawn anyway
 * they float over the empty sky. A camera shows what it can see: an entity is drawn only where there is terrain to show.
 */
public final class FeedEntityRules {

    private FeedEntityRules() {}

    /** Blocks from the camera that entities are still drawn: the feed's radius in chunks, and half a chunk for the section at its edge. */
    public static double radiusBlocks(int radiusChunks) {
        return radiusChunks * 16.0 + 8.0;
    }

    /**
     * @param dx,dy,dz        entity minus camera (blocks)
     * @param radiusBlocksSq  squared {@link #radiusBlocks}
     * @param entityChunkX    chunk of the entity
     * @param playerChunkX    chunk of the player of this client
     * @param viewDistance    chunks around the player that this client has built
     */
    public static boolean mayShow(double dx, double dy, double dz, double radiusBlocksSq,
                                  int entityChunkX, int entityChunkZ, int playerChunkX, int playerChunkZ, int viewDistance) {
        if (dx * dx + dy * dy + dz * dz > radiusBlocksSq) return false;
        return Math.abs(entityChunkX - playerChunkX) <= viewDistance && Math.abs(entityChunkZ - playerChunkZ) <= viewDistance;
    }
}
