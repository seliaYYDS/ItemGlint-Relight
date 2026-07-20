package celia.adwadg.itemglintrelight.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayDeque;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class UiTrailStarParticles {
    private static final int TILE_SIZE = 48;
    private static final int FRAME_COUNT = 32;
    private static final int ATLAS_COLUMNS = 8;
    private static final int ATLAS_ROWS = FRAME_COUNT / ATLAS_COLUMNS;
    private static final int ATLAS_WIDTH = TILE_SIZE * ATLAS_COLUMNS;
    private static final int ATLAS_HEIGHT = TILE_SIZE * ATLAS_ROWS;
    private static final int ALPHA_LEVELS = 4;
    private static final int MAX_PARTICLES = 18;
    private static final int[] COLOURS = {UiPalette.BRIGHT_BLUE, UiPalette.LIGHT_GREEN, 0xFF2D82B8, 0xFF238A68};
    private static final float[][] POINTS = {{0.0F, -0.94F}, {0.18F, -0.18F}, {0.94F, 0.0F}, {0.18F, 0.18F}, {0.0F, 0.94F}, {-0.18F, 0.18F}, {-0.94F, 0.0F}, {-0.18F, -0.18F}};
    private final ArrayDeque<Particle> particles = new ArrayDeque<>();
    private final Identifier[][] textureIds = new Identifier[COLOURS.length][ALPHA_LEVELS];
    private final DynamicTexture[][] textures = new DynamicTexture[COLOURS.length][ALPHA_LEVELS];
    private float lastX = Float.NaN;
    private float lastY = Float.NaN;
    private float lastSpawnX = Float.NaN;
    private float lastSpawnY = Float.NaN;
    private long nextSpawnTime;

    public UiTrailStarParticles() {
        for (int colour = 0; colour < COLOURS.length; colour++) {
            for (int level = 0; level < ALPHA_LEVELS; level++) {
                createTexture(colour, level);
            }
        }
    }

    public void update(float x, float y) {
        long now = System.nanoTime();
        boolean moved = Float.isNaN(lastX) || distanceSquared(lastX, lastY, x, y) >= 4.0F;
        if (moved && (Float.isNaN(lastSpawnX) || now >= nextSpawnTime && distanceSquared(lastSpawnX, lastSpawnY, x, y) >= 64.0F)) {
            spawn(x, y, now);
            lastSpawnX = x;
            lastSpawnY = y;
            nextSpawnTime = now + ThreadLocalRandom.current().nextLong(80_000_000L, 220_000_001L);
        }
        if (moved) {
            lastX = x;
            lastY = y;
        }
    }

    public void render(GuiGraphics graphics, long now) {
        particles.removeIf(particle -> now - particle.time > particle.lifetime);
        for (Particle particle : particles) {
            long elapsedNanos = now - particle.time;
            float seconds = elapsedNanos / 1_000_000_000.0F;
            float age = Math.min(1.0F, elapsedNanos / (float) particle.lifetime);
            float fade = 1.0F - age * age;
            int alpha = Math.round(255.0F * fade);
            int level = Math.min(ALPHA_LEVELS - 1, Math.max(0, Math.round(alpha / 255.0F * (ALPHA_LEVELS - 1))));
            float rotation = particle.rotation + particle.rotationSpeed * seconds;
            int frame = Math.floorMod(Math.round(rotation / ((float) (Math.PI * 2.0) / FRAME_COUNT)), FRAME_COUNT);
            int sourceX = frame % ATLAS_COLUMNS * TILE_SIZE;
            int sourceY = frame / ATLAS_COLUMNS * TILE_SIZE;
            int drawX = Math.round(particle.x + particle.horizontalSpeed * seconds - particle.size / 2.0F);
            int drawY = Math.round(particle.y + particle.fallSpeed * seconds + particle.gravity * seconds * seconds * 0.5F - particle.size / 2.0F);
            graphics.blit(RenderPipelines.GUI_TEXTURED, textureIds[particle.colour][level], drawX, drawY,
                    sourceX, sourceY, particle.size, particle.size, TILE_SIZE, TILE_SIZE, ATLAS_WIDTH, ATLAS_HEIGHT);
        }
    }

    public void clear() {
        particles.clear();
        lastX = Float.NaN;
        lastY = Float.NaN;
        lastSpawnX = Float.NaN;
        lastSpawnY = Float.NaN;
        nextSpawnTime = 0L;
        for (int colour = 0; colour < COLOURS.length; colour++) {
            for (int level = 0; level < ALPHA_LEVELS; level++) {
                if (textures[colour][level] != null) {
                    Minecraft.getInstance().getTextureManager().release(textureIds[colour][level]);
                    textures[colour][level] = null;
                }
            }
        }
    }

    private void spawn(float x, float y, long now) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (particles.size() == MAX_PARTICLES) {
            particles.removeLast();
        }
        float rotationSpeed = random.nextFloat(3.0F, 8.0F) * (random.nextBoolean() ? 1.0F : -1.0F);
        particles.addFirst(new Particle(
                x + random.nextFloat(-4.0F, 4.0F),
                y + random.nextFloat(-3.0F, 3.0F),
                now,
                random.nextInt(COLOURS.length),
                random.nextInt(8, 23),
                random.nextLong(650_000_000L, 1_800_000_001L),
                random.nextFloat(35.0F, 90.0F),
                random.nextFloat(40.0F, 120.0F),
                random.nextFloat(-12.0F, 12.0F),
                random.nextFloat(0.0F, (float) (Math.PI * 2.0)),
                rotationSpeed
        ));
    }

    private void createTexture(int colour, int level) {
        NativeImage image = new NativeImage(ATLAS_WIDTH, ATLAS_HEIGHT, false);
        float center = (TILE_SIZE - 1) / 2.0F;
        int baseAlpha = Math.round(72.0F + level / (float) (ALPHA_LEVELS - 1) * 183.0F);
        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            float angle = frame * (float) (Math.PI * 2.0) / FRAME_COUNT;
            float cosine = (float) Math.cos(angle);
            float sine = (float) Math.sin(angle);
            int offsetX = frame % ATLAS_COLUMNS * TILE_SIZE;
            int offsetY = frame / ATLAS_COLUMNS * TILE_SIZE;
            for (int pixelY = 0; pixelY < TILE_SIZE; pixelY++) {
                for (int pixelX = 0; pixelX < TILE_SIZE; pixelX++) {
                    float coverage = 0.0F;
                    for (int sampleY = 0; sampleY < 3; sampleY++) {
                        for (int sampleX = 0; sampleX < 3; sampleX++) {
                            float x = (pixelX + (sampleX + 0.5F) / 3.0F - center) / center;
                            float y = (pixelY + (sampleY + 0.5F) / 3.0F - center) / center;
                            if (insideStar(cosine * x + sine * y, -sine * x + cosine * y)) {
                                coverage += 1.0F / 9.0F;
                            }
                        }
                    }
                    int alpha = Math.round(baseAlpha * coverage);
                    image.setPixelABGR(offsetX + pixelX, offsetY + pixelY, toAbgr(alpha << 24 | COLOURS[colour] & 0x00FFFFFF));
                }
            }
        }
        Identifier id = Identifier.fromNamespaceAndPath("itemglintrelight", "ui/trail_star_particle_rewrite_" + colour + "_" + level);
        DynamicTexture texture = new DynamicTexture(() -> "itemglintrelight_trail_star_particle", image);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        texture.upload();
        textureIds[colour][level] = id;
        textures[colour][level] = texture;
    }

    private static boolean insideStar(float x, float y) {
        boolean inside = false;
        for (int current = 0, previous = POINTS.length - 1; current < POINTS.length; previous = current++) {
            float[] currentPoint = POINTS[current];
            float[] previousPoint = POINTS[previous];
            if ((currentPoint[1] > y) != (previousPoint[1] > y)
                    && x < (previousPoint[0] - currentPoint[0]) * (y - currentPoint[1]) / (previousPoint[1] - currentPoint[1]) + currentPoint[0]) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static float distanceSquared(float firstX, float firstY, float secondX, float secondY) {
        float deltaX = firstX - secondX;
        float deltaY = firstY - secondY;
        return deltaX * deltaX + deltaY * deltaY;
    }

    private static int toAbgr(int argb) {
        return argb & 0xFF00FF00 | (argb & 0x00FF0000) >>> 16 | (argb & 0x000000FF) << 16;
    }

    private record Particle(float x, float y, long time, int colour, int size, long lifetime, float fallSpeed, float gravity,
                            float horizontalSpeed, float rotation, float rotationSpeed) {
    }
}
