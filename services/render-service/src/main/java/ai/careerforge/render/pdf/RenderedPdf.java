package ai.careerforge.render.pdf;

import java.util.Arrays;

/**
 * PDF bytes plus the facts about them {@code render-service}'s later steps (persistence,
 * {@code DocumentMetadata}) will need — never PDF content this class itself persists or exposes
 * over any endpoint (ADR-036: no PDF is generated for its own sake here, it is just returned
 * through this internal abstraction).
 *
 * <p>A plain class, not a record: {@code byte[]} has reference-equality {@code equals}/
 * {@code hashCode} by default, which a record's generated implementations would inherit
 * unchanged (arrays are not compared structurally) — this class overrides both correctly and
 * defensively copies the array on the way in and on the way out, so no caller can mutate the
 * bytes this instance hands back out.
 */
public final class RenderedPdf {

    private final byte[] bytes;
    private final int pageCount;

    public RenderedPdf(byte[] bytes, int pageCount) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("bytes must not be null or empty");
        }
        if (pageCount < 1) {
            throw new IllegalArgumentException("pageCount must be at least 1");
        }
        this.bytes = bytes.clone();
        this.pageCount = pageCount;
    }

    /** Defensive copy — mutating the returned array never affects this instance. */
    public byte[] bytes() {
        return bytes.clone();
    }

    public int pageCount() {
        return pageCount;
    }

    public long sizeBytes() {
        return bytes.length;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof RenderedPdf other)) {
            return false;
        }
        return pageCount == other.pageCount && Arrays.equals(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(bytes) + pageCount;
    }

    @Override
    public String toString() {
        return "RenderedPdf[sizeBytes=" + bytes.length + ", pageCount=" + pageCount + "]";
    }
}
