package celia.adwadg.itemglintrelight.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors every vertex the entity layers write: one copy goes to vanilla, one copy is kept in the
 * capture's off-screen batches and replayed into the outline mask after the level has rendered.
 *
 * <p>1.21.1 has no {@code SubmitNodeCollector}, so the mirror has to happen at vertex level. The
 * routing decision is taken per {@link #getBuffer(RenderType)} call, which is what lets a single
 * wrapper serve the held-item layer, the armor layer and a whole stack of armor stands: whichever
 * capture is open at that moment receives the geometry.
 */
final class ThirdPersonBufferSource implements MultiBufferSource {
    private final MultiBufferSource original;
    private final java.util.function.Supplier<MaskSource> target;

    ThirdPersonBufferSource(MultiBufferSource original, java.util.function.Supplier<MaskSource> target) {
        this.original = original;
        this.target = target;
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        VertexConsumer real = this.original.getBuffer(type);
        MaskSource mask = this.target.get();
        if (mask == null) {
            return real;
        }
        return new TeeVertexConsumer(real, mask.getBuffer(type), mask);
    }

    /**
     * Batches for one capture.
     *
     * <p>Every render type owns its own {@link ByteBufferBuilder}. A single shared buffer cannot
     * render a foiled item: the item type and the glint type are requested together and the shared
     * buffer ends the first batch when the second type starts, which is exactly the
     * {@code BufferBuilder: Not building!} failure the GUI pass hit.
     */
    static final class MaskSource {
        private final Map<RenderType, ByteBufferBuilder> buffers = new LinkedHashMap<>();
        private final Map<RenderType, BufferBuilder> builders = new LinkedHashMap<>();
        // View-space bounds accumulated from the mirrored vertices: exactly what the composite
        // region needs, without having to reproduce the item transform chain.
        private float minX = Float.POSITIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float minZ = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;
        private float maxZ = Float.NEGATIVE_INFINITY;
        private boolean hasBounds;

        void vertex(float x, float y, float z) {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
                return;
            }
            this.minX = Math.min(this.minX, x);
            this.minY = Math.min(this.minY, y);
            this.minZ = Math.min(this.minZ, z);
            this.maxX = Math.max(this.maxX, x);
            this.maxY = Math.max(this.maxY, y);
            this.maxZ = Math.max(this.maxZ, z);
            this.hasBounds = true;
        }

        /** @return {minX, minY, minZ, maxX, maxY, maxZ} in view space, or null when empty. */
        float[] bounds() {
            return this.hasBounds
                    ? new float[]{this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ}
                    : null;
        }

        VertexConsumer getBuffer(RenderType type) {
            BufferBuilder builder = this.builders.get(type);
            if (builder == null) {
                ByteBufferBuilder buffer = this.buffers.computeIfAbsent(type, ignored -> new ByteBufferBuilder(262144));
                builder = new BufferBuilder(buffer, type.mode(), type.format());
                this.builders.put(type, builder);
            }
            return builder;
        }

        boolean isEmpty() {
            return this.builders.isEmpty();
        }

        /** Uploads every batch; the caller owns the framebuffer and the matrices. */
        void draw() {
            for (Map.Entry<RenderType, BufferBuilder> entry : this.builders.entrySet()) {
                MeshData mesh = entry.getValue().build();
                if (mesh != null) {
                    entry.getKey().draw(mesh);
                }
            }
            clear();
        }

        void clear() {
            this.builders.clear();
            this.minX = Float.POSITIVE_INFINITY;
            this.minY = Float.POSITIVE_INFINITY;
            this.minZ = Float.POSITIVE_INFINITY;
            this.maxX = Float.NEGATIVE_INFINITY;
            this.maxY = Float.NEGATIVE_INFINITY;
            this.maxZ = Float.NEGATIVE_INFINITY;
            this.hasBounds = false;
            for (ByteBufferBuilder buffer : this.buffers.values()) {
                buffer.clear();
            }
        }

        void close() {
            clear();
            for (ByteBufferBuilder buffer : this.buffers.values()) {
                buffer.close();
            }
            this.buffers.clear();
        }
    }

    /** Forwards every primitive to both consumers so vanilla sees the exact same draw. */
    private static final class TeeVertexConsumer implements VertexConsumer {
        private final VertexConsumer real;
        private final VertexConsumer mask;
        private final MaskSource bounds;

        private TeeVertexConsumer(VertexConsumer real, VertexConsumer mask, MaskSource bounds) {
            this.real = real;
            this.mask = mask;
            this.bounds = bounds;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.real.addVertex(x, y, z);
            this.mask.addVertex(x, y, z);
            this.bounds.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            this.real.setColor(red, green, blue, alpha);
            this.mask.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.real.setUv(u, v);
            this.mask.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.real.setUv1(u, v);
            this.mask.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            this.real.setUv2(u, v);
            this.mask.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            this.real.setNormal(x, y, z);
            this.mask.setNormal(x, y, z);
            return this;
        }
    }
}
