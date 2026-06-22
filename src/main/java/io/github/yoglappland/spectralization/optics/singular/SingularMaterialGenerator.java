package io.github.yoglappland.spectralization.optics.singular;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class SingularMaterialGenerator {
    public static final int SIZE = 32;
    public static final int PIXELS = SIZE * SIZE;
    public static final int FRAME_COUNT = 8;

    private static final long SOURCE_HASH_SALT = 0xA4093822299F31D0L;
    private static final long FALLBACK_WORLD_SALT = 0x082EFA98EC4E6C89L;
    private static final double TAU = Math.PI * 2.0;
    private static final Family[] FAMILIES = Family.values();

    public static long seedFor(long worldSeed, String sourceId) {
        String normalizedSource = normalizeSource(sourceId);
        return mix64(worldSeed ^ fnv1a64(normalizedSource) ^ SOURCE_HASH_SALT);
    }

    public static long fallbackSeed(String sourceId) {
        return seedFor(FALLBACK_WORLD_SALT, sourceId);
    }

    public static Traits traits(long seed) {
        int hash = hashSeed(seed);
        Rng rng = new Rng(hash);
        Family family = pickFamily(rng);
        return new Traits(
                family,
                16 + (int) (unit(seed, 1) * 80.0),
                8 + (int) (unit(seed, 2) * 88.0),
                (int) Math.floor(unit(seed, 3) * 360.0),
                24 + (int) (unit(seed, 4) * 72.0)
        );
    }

    public static Visual generate(long seed) {
        return generateStrip(seed).frame(0);
    }

    public static VisualStrip generateStrip(long seed) {
        State state = createState(seed);
        Traits traits = new Traits(
                state.shape().type(),
                16 + (int) (unit(seed, 1) * 80.0),
                8 + (int) (unit(seed, 2) * 88.0),
                (int) Math.floor(unit(seed, 3) * 360.0),
                24 + (int) (unit(seed, 4) * 72.0)
        );
        boolean[] solid = new boolean[PIXELS];
        for (int index = 0; index < PIXELS; index++) {
            solid[index] = state.shape().mask()[index] != 0;
        }

        Visual[] frames = new Visual[FRAME_COUNT];
        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            double phase = frame / (double) FRAME_COUNT * TAU;
            frames[frame] = new Visual(seed, traits, renderFrame(state, phase), solid);
        }
        return new VisualStrip(seed, traits, frames, solid);
    }

    private static State createState(long seed) {
        int hash = hashSeed(seed);
        Rng rng = new Rng(hash);
        Shape shape = buildShape(hash, rng);
        double[] turing = buildTuring(hash, rng, shape.mask());
        Palette palette = createPalette(rng);
        double driftAngle = rng.next() * TAU;
        Drift drift = new Drift(Math.cos(driftAngle), Math.sin(driftAngle), lerp(1.1, 2.4, rng.next()), lerp(4.8, 9.6, rng.next()));
        double organicWeight = lerp(0.32, 0.58, rng.next());
        double facetAngle = (Integer.toUnsignedLong(hash >>> 8) / 16777215.0) * TAU;
        Fields fields = new Fields(
                new double[PIXELS],
                new double[PIXELS],
                new double[PIXELS],
                new double[PIXELS],
                new double[PIXELS],
                new double[PIXELS],
                shape.ownerMap().clone(),
                new byte[PIXELS],
                new byte[PIXELS],
                new byte[PIXELS]
        );

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int index = y * SIZE + x;
                double nx = (x + 0.5 - 16.0) / 16.0;
                double ny = (y + 0.5 - 16.0) / 16.0;
                int owner = shape.ownerMap()[index] & 0xFF;
                Element element = owner > 0 && owner <= shape.elements().size() ? shape.elements().get(owner - 1) : null;
                double lx = nx;
                double ly = ny;
                int localHash = hash;
                if (element != null) {
                    double cos = Math.cos(element.rotation());
                    double sin = Math.sin(element.rotation());
                    double dx = x + 0.5 - element.cx();
                    double dy = y + 0.5 - element.cy();
                    lx = (dx * cos + dy * sin) / Math.max(0.001, element.rx());
                    ly = (-dx * sin + dy * cos) / Math.max(0.001, element.ry());
                    localHash = hash ^ owner * 0x9E3779B9;
                }

                double radius = Math.hypot(lx, ly);
                double angle = Math.atan2(ly, lx);
                if (shape.type() == Family.CELESTIAL_BODY) {
                    double orbit = angle * (element != null ? element.arms() : 2.0)
                            + radius * (element != null ? element.swirl() : 6.0)
                            + (element != null ? element.phase() : 0.0);
                    double orbitalNoise = fbm(
                            Math.cos(angle) * 2.2 + radius * 2.0,
                            Math.sin(angle) * 2.2 - radius * 1.5,
                            localHash ^ 0x4455,
                            3
                    );
                    fields.band()[index] = angle * 0.62 + radius * (element != null ? element.freq() * 0.24 : drift.freq() * 0.2) + orbitalNoise * 0.42;
                    fields.orbit()[index] = orbit + orbitalNoise * 1.15;
                    fields.radial()[index] = radius * (element != null ? element.swirl() : 6.0) + angle * 0.55;
                    fields.mineral()[index] = clamp(
                            fbm(lx * 8.0 + 2.2, ly * 8.0 - 4.0, localHash ^ 0x7788, 5) * 0.62
                                    + (0.5 + 0.5 * Math.sin(orbit)) * 0.38,
                            0.0,
                            1.0
                    );
                    fields.facet()[index] = 0.5 + 0.5 * Math.sin(
                            orbit + radius * 4.0
                                    + Math.sin(angle * 2.0 + (element != null ? element.facetAngle() : facetAngle)) * 0.8
                    );
                } else if (shape.type() == Family.CRYSTAL_CLUSTER) {
                    fields.band()[index] = lx * (element != null ? element.driftX() : drift.x())
                            + ly * (element != null ? element.driftY() : drift.y())
                            + fbm((x + 0.2) * 0.18, (y - 1.4) * 0.18, localHash ^ 0x4455, 3) * 0.32;
                    fields.orbit()[index] = angle * (element != null ? element.arms() : 4.0)
                            + radius * (element != null ? element.swirl() : 4.8)
                            + (element != null ? element.phase() : 0.0);
                    fields.radial()[index] = radius * (element != null ? element.freq() : drift.freq());
                    fields.mineral()[index] = fbm(lx * 10.0 + 2.2, ly * 10.0 - 4.0, localHash ^ 0x7788, 5);
                    double angleForFacet = element != null ? element.facetAngle() : facetAngle;
                    fields.facet()[index] = 0.5 + 0.5 * Math.sin(
                            (lx * Math.cos(angleForFacet) + ly * Math.sin(angleForFacet))
                                    * (element != null ? element.freq() : 9.0)
                                    + (element != null ? element.phase() : 0.0)
                                    + Math.floor((lx - ly) * 3.0)
                    );
                } else {
                    fields.band()[index] = nx * drift.x() + ny * drift.y()
                            + fbm((x + 0.2) * 0.18, (y - 1.4) * 0.18, hash ^ 0x4455, 3) * 0.42;
                    fields.orbit()[index] = angle * 2.0 + radius * 5.0;
                    fields.radial()[index] = radius * 6.0;
                    fields.mineral()[index] = fbm((x + 2.2) * 0.23, (y - 4.0) * 0.23, hash ^ 0x7788, 5);
                    fields.facet()[index] = 0.5 + 0.5 * Math.sin(
                            (nx * Math.cos(facetAngle) + ny * Math.sin(facetAngle)) * 6.0
                                    + Math.floor((nx - ny) * 2.0)
                    );
                }

                fields.micro()[index] = fbm((x - 1.3) * 0.62, (y + 1.8) * 0.62, localHash ^ 0x9911, 3);
                fields.neighbors()[index] = (byte) countNeighbors(shape.mask(), x, y);
                fields.halo()[index] = (byte) (isOuterBorder(x, y) || shape.mask()[index] != 0 ? 0 : edgeDistance(shape.mask(), x, y, 2));
                if (shape.mask()[index] != 0 && owner > 0) {
                    int left = x > 0 ? shape.ownerMap()[index - 1] & 0xFF : owner;
                    int right = x < SIZE - 1 ? shape.ownerMap()[index + 1] & 0xFF : owner;
                    int up = y > 0 ? shape.ownerMap()[index - SIZE] & 0xFF : owner;
                    int down = y < SIZE - 1 ? shape.ownerMap()[index + SIZE] & 0xFF : owner;
                    int upLeft = x > 0 && y > 0 ? shape.ownerMap()[index - SIZE - 1] & 0xFF : owner;
                    int upRight = x < SIZE - 1 && y > 0 ? shape.ownerMap()[index - SIZE + 1] & 0xFF : owner;
                    int downLeft = x > 0 && y < SIZE - 1 ? shape.ownerMap()[index + SIZE - 1] & 0xFF : owner;
                    int downRight = x < SIZE - 1 && y < SIZE - 1 ? shape.ownerMap()[index + SIZE + 1] & 0xFF : owner;
                    fields.seam()[index] = (byte) (
                            (left != 0 && left != owner) || (right != 0 && right != owner)
                                    || (up != 0 && up != owner) || (down != 0 && down != owner)
                                    || (upLeft != 0 && upLeft != owner) || (upRight != 0 && upRight != owner)
                                    || (downLeft != 0 && downLeft != owner) || (downRight != 0 && downRight != owner)
                                    ? 1 : 0
                    );
                }
            }
        }

        Mode[] modes = shape.type() == Family.CELESTIAL_BODY
                ? new Mode[]{Mode.ORBIT, Mode.PULSE, Mode.HYBRID, Mode.SHIMMER}
                : new Mode[]{Mode.DRIFT, Mode.PULSE, Mode.HYBRID, Mode.SHIMMER};
        Mode defaultMode = modes[Integer.remainderUnsigned(hash, modes.length)];
        return new State(hash, shape, turing, palette, drift, organicWeight, fields, defaultMode);
    }

    private static int[] renderFrame(State state, double timePhase) {
        int[] argb = new int[PIXELS];
        byte[] mask = state.shape().mask();
        double[] turing = state.turing();
        Palette palette = state.palette();
        Fields fields = state.fields();
        Drift drift = state.drift();
        List<Element> elements = state.shape().elements();
        Mode mode = state.shape().type() == Family.CELESTIAL_BODY || state.defaultMode() != Mode.ORBIT ? state.defaultMode() : Mode.DRIFT;

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int index = y * SIZE + x;
                if (isOuterBorder(x, y)) {
                    continue;
                }

                if (mask[index] == 0) {
                    int halo = fields.halo()[index] & 0xFF;
                    if (halo > 0) {
                        int color = halo == 1
                                ? mixColor(palette.cold(), palette.pearl(), 0.16)
                                : palette.odd();
                        argb[index] = ((halo == 1 ? 76 : 16) << 24) | color;
                    }
                    continue;
                }

                double nx = (x + 0.5 - 16.0) / 16.0;
                double ny = (y + 0.5 - 16.0) / 16.0;
                int owner = fields.owner()[index] & 0xFF;
                Element element = owner > 0 && owner <= elements.size() ? elements.get(owner - 1) : null;
                double localPhase = timePhase * drift.speed();
                double driftWave = 0.5 + 0.5 * Math.sin(fields.band()[index] * drift.freq() + localPhase);
                double orbitWave = driftWave;
                double radialWave = 0.5 + 0.5 * Math.sin(fields.radial()[index] + localPhase * 0.4);
                double flowWave = driftWave;
                double movingMineral = fields.mineral()[index];
                double pulse = 0.5 + 0.5 * Math.sin(timePhase + turing[index] * 7.2 + movingMineral * 3.1);
                double shimmer = 0.5 + 0.5 * Math.sin(timePhase * 2.1 + fields.micro()[index] * 9.0 + hash2(x, y, state.hash()) * 3.0);
                double pattern = movingMineral * (1.0 - state.organicWeight()) + turing[index] * state.organicWeight();
                double ridgePhase = mode == Mode.DRIFT ? timePhase * 1.2 : timePhase * 0.45;
                double light = 0.72 + (1.0 - Math.hypot(nx, ny)) * 0.18
                        + (-nx * 0.18 - ny * 0.30)
                        + (fields.micro()[index] - 0.5) * 0.20
                        + pulse * 0.08;
                double facetMotion = fields.facet()[index];
                double crystalGlint = 0.0;

                if (state.shape().type() == Family.CRYSTAL_CLUSTER) {
                    localPhase = timePhase * (element != null ? element.speed() : drift.speed()) + (element != null ? element.phase() : 0.0);
                    driftWave = 0.5 + 0.5 * Math.sin(fields.band()[index] * (element != null ? element.freq() : drift.freq()) + localPhase);
                    orbitWave = 0.5 + 0.5 * Math.sin(fields.orbit()[index] + localPhase * 0.55);
                    radialWave = 0.5 + 0.5 * Math.sin(fields.radial()[index] * 0.8 - localPhase * 0.35);
                    flowWave = driftWave;
                    movingMineral = fields.mineral()[index];
                    if (element != null) {
                        double cos = Math.cos(element.rotation());
                        double sin = Math.sin(element.rotation());
                        double dx = x + 0.5 - element.cx();
                        double dy = y + 0.5 - element.cy();
                        double lx = (dx * cos + dy * sin) / Math.max(0.001, element.rx());
                        double ly = (-dx * sin + dy * cos) / Math.max(0.001, element.ry());
                        double slide = localPhase * 0.18;
                        int localHash = state.hash() ^ owner * 0x9E3779B9;
                        double mineralSlide = fbm(
                                (lx + element.driftX() * slide) * 10.0 + 2.2,
                                (ly + element.driftY() * slide) * 10.0 - 4.0,
                                localHash ^ 0x7788,
                                5
                        );
                        facetMotion = 0.5 + 0.5 * Math.sin(
                                (lx * Math.cos(element.facetAngle()) + ly * Math.sin(element.facetAngle())) * element.freq()
                                        + element.phase()
                                        + Math.floor((lx - ly) * 3.0)
                                        + localPhase * 1.15
                        );
                        crystalGlint = 0.5 + 0.5 * Math.sin(
                                fields.band()[index] * element.freq() * 1.35
                                        + fields.facet()[index] * TAU * 1.8
                                        - localPhase * 1.7
                                        + hash2(x, y, localHash) * 1.2
                        );
                        movingMineral = clamp(fields.mineral()[index] * 0.50 + mineralSlide * 0.36 + facetMotion * 0.14, 0.0, 1.0);
                        flowWave = driftWave * 0.62 + facetMotion * 0.18 + crystalGlint * 0.14 + orbitWave * 0.06;
                    } else {
                        facetMotion = 0.5 + 0.5 * Math.sin(fields.facet()[index] * TAU + localPhase);
                        crystalGlint = facetMotion;
                    }
                    pulse = 0.5 + 0.5 * Math.sin(localPhase * 0.92 + turing[index] * 7.2 + movingMineral * 3.1);
                    shimmer = 0.5 + 0.5 * Math.sin(
                            timePhase * (1.75 + (element != null ? element.tint() * 0.8 : 0.35))
                                    + fields.micro()[index] * 9.0
                                    + hash2(x, y, state.hash()) * 3.0
                    );
                    pattern = movingMineral * (1.0 - state.organicWeight()) + turing[index] * state.organicWeight();
                    ridgePhase = mode == Mode.DRIFT ? localPhase * 1.2 : localPhase * 0.45;
                    light = 0.70 + (1.0 - Math.hypot(nx, ny)) * 0.17
                            + (-nx * 0.18 - ny * 0.30)
                            + (fields.micro()[index] - 0.5) * 0.18
                            + pulse * 0.07
                            + (facetMotion - 0.5) * 0.20
                            + (crystalGlint - 0.5) * 0.22;
                } else if (state.shape().type() == Family.CELESTIAL_BODY) {
                    int spinDir = element != null ? element.spinDir() : 1;
                    localPhase = timePhase * (element != null ? element.speed() : drift.speed()) * spinDir + (element != null ? element.phase() : 0.0);
                    double orbitalPhase = fields.orbit()[index] + localPhase * 1.65;
                    double bandPhase = fields.band()[index] * (element != null ? element.freq() : drift.freq()) + localPhase;
                    double radialPhase = fields.radial()[index] * 1.18 - localPhase * 0.82;
                    driftWave = 0.5 + 0.5 * Math.sin(bandPhase);
                    orbitWave = 0.5 + 0.5 * Math.sin(orbitalPhase);
                    radialWave = 0.5 + 0.5 * Math.sin(radialPhase);
                    flowWave = orbitWave * 0.68 + radialWave * 0.32;
                    movingMineral = fields.mineral()[index];
                    if (element != null) {
                        double spin = localPhase * 0.28;
                        double cosSpin = Math.cos(spin);
                        double sinSpin = Math.sin(spin);
                        double dx = (x + 0.5 - element.cx()) / Math.max(0.001, element.rx());
                        double dy = (y + 0.5 - element.cy()) / Math.max(0.001, element.ry());
                        double ax = dx * cosSpin - dy * sinSpin;
                        double ay = dx * sinSpin + dy * cosSpin;
                        int localHash = state.hash() ^ owner * 0x85EBCA6B;
                        movingMineral = clamp(fbm(ax * 8.0 + 2.2, ay * 8.0 - 4.0, localHash, 4) * 0.52 + flowWave * 0.48, 0.0, 1.0);
                    }
                    pulse = 0.5 + 0.5 * Math.sin(localPhase * 0.92 + turing[index] * 7.2 + movingMineral * 3.1 + orbitWave * 1.2);
                    shimmer = 0.5 + 0.5 * Math.sin(
                            timePhase * (1.75 + (element != null ? element.tint() * 0.8 : 0.35))
                                    + fields.micro()[index] * 9.0
                                    + hash2(x, y, state.hash()) * 3.0
                    );
                    pattern = movingMineral * (1.0 - state.organicWeight()) + turing[index] * state.organicWeight();
                    ridgePhase = mode == Mode.ORBIT ? localPhase * 1.45 : localPhase * 0.55;
                    light = 0.68 + (1.0 - Math.hypot(nx, ny)) * 0.15
                            + (-nx * 0.16 - ny * 0.26)
                            + (fields.micro()[index] - 0.5) * 0.16
                            + pulse * 0.06
                            + (fields.facet()[index] - 0.5) * 0.14
                            + (flowWave - 0.5) * 0.24;
                }

                boolean isCrystalCluster = state.shape().type() == Family.CRYSTAL_CLUSTER;
                if (mode == Mode.ORBIT) {
                    pattern = pattern * 0.28 + flowWave * 0.58 + driftWave * 0.14;
                } else if (mode == Mode.DRIFT) {
                    pattern = isCrystalCluster
                            ? pattern * 0.26 + flowWave * 0.38 + facetMotion * 0.20 + crystalGlint * 0.16
                            : pattern * 0.46 + driftWave * 0.54;
                } else if (mode == Mode.PULSE) {
                    pattern = isCrystalCluster
                            ? pattern * 0.42 + pulse * 0.32 + facetMotion * 0.16 + crystalGlint * 0.10
                            : pattern * 0.56 + pulse * 0.36 + orbitWave * 0.08;
                } else if (mode == Mode.SHIMMER) {
                    pattern = isCrystalCluster
                            ? pattern * 0.38 + shimmer * 0.26 + flowWave * 0.18 + facetMotion * 0.10 + crystalGlint * 0.08
                            : pattern * 0.50 + shimmer * 0.30 + (state.shape().type() == Family.CELESTIAL_BODY ? flowWave : driftWave) * 0.20;
                } else if (isCrystalCluster) {
                    pattern = pattern * 0.30 + flowWave * 0.28 + pulse * 0.20 + facetMotion * 0.12 + crystalGlint * 0.10;
                } else {
                    pattern = pattern * (state.shape().type() == Family.CELESTIAL_BODY ? 0.34 : 0.42)
                            + (state.shape().type() == Family.CELESTIAL_BODY ? flowWave * 0.34 : driftWave * 0.30)
                            + pulse * (state.shape().type() == Family.CELESTIAL_BODY ? 0.22 : 0.28)
                            + (state.shape().type() == Family.CELESTIAL_BODY ? driftWave * 0.10 : 0.0);
                }

                boolean edge = (fields.neighbors()[index] & 0xFF) < 8;
                boolean seam = state.shape().type() == Family.CRYSTAL_CLUSTER && fields.seam()[index] != 0;
                double ridge = 0.5 + 0.5 * Math.sin(pattern * Math.PI * 8.0 + ridgePhase);
                double cell = smoothstep(0.35, 0.78, pattern);
                int color = mixColor(palette.shadow(), palette.core(), clamp(0.34 + movingMineral * 0.22 + pattern * 0.44, 0.0, 1.0));
                double warmMotion = state.shape().type() == Family.CELESTIAL_BODY || isCrystalCluster ? flowWave : driftWave;
                color = mixColor(
                        color,
                        palette.warm(),
                        clamp(cell * (state.shape().type() == Family.CELESTIAL_BODY ? 0.42 : 0.48)
                                + warmMotion * (state.shape().type() == Family.CELESTIAL_BODY ? 0.26 : 0.16), 0.0, 0.86)
                );
                color = mixColor(
                        color,
                        palette.cold(),
                        clamp((1.0 - ridge) * (state.shape().type() == Family.CELESTIAL_BODY ? 0.28 : 0.34)
                                + pulse * (state.shape().type() == Family.CELESTIAL_BODY ? 0.14 : 0.18)
                                + (state.shape().type() == Family.CELESTIAL_BODY ? radialWave * 0.16 : 0.0)
                                + (isCrystalCluster ? facetMotion * 0.10 : 0.0), 0.0, 0.72)
                );
                color = mixColor(
                        color,
                        palette.odd(),
                        clamp(ridge * 0.26 + shimmer * 0.18 + Math.abs(turing[index] - 0.5) * 0.22
                                + (isCrystalCluster ? facetMotion * 0.06 + crystalGlint * 0.08 : 0.0), 0.0, 0.62)
                );

                if (element != null && state.shape().type() != Family.LIVING_BIOMASS && state.shape().type() != Family.ABERRANT_BRANCH) {
                    color = mixColor(color, element.tint() > 0.5 ? palette.warm() : palette.cold(), 0.08 + element.tint() * 0.06);
                }
                if (state.shape().type() != Family.LIVING_BIOMASS && state.shape().type() != Family.ABERRANT_BRANCH) {
                    color = facetMotion > 0.58
                            ? mixColor(color, palette.pearl(), (facetMotion - 0.58) * (isCrystalCluster ? 0.34 : 0.22))
                            : mixColor(color, palette.shadow(), (0.58 - facetMotion) * (isCrystalCluster ? 0.24 : 0.18));
                }
                if (isCrystalCluster && crystalGlint > 0.62 && !edge) {
                    color = mixColor(color, palette.pearl(), (crystalGlint - 0.62) * 0.34);
                }
                if (hash2(x, y, state.hash() ^ 0xF173) > 0.976 && !edge) {
                    color = mixColor(color, palette.pearl(), 0.68);
                }

                color = shadeColor(color, clamp(light, 0.42, 1.44));
                if (seam) {
                    color = mixColor(mixColor(color, palette.shadow(), 0.58), palette.pearl(), 0.12);
                }
                if (edge) {
                    color = nx < -0.2 || ny < -0.34
                            ? mixColor(color, palette.pearl(), 0.22)
                            : mixColor(color, palette.shadow(), 0.56);
                }

                argb[index] = ((edge ? 238 : 255) << 24) | color;
            }
        }
        return argb;
    }

    private static Shape buildShape(int seed, Rng rng) {
        Family family = pickFamily(rng);
        return switch (family) {
            case ABERRANT_BRANCH -> buildAberrantBranchShape(seed, rng);
            case LIVING_BIOMASS -> buildBiomassShape(seed, rng);
            case CRYSTAL_CLUSTER -> buildCrystalClusterShape(seed, rng);
            case CELESTIAL_BODY -> buildCelestialShape(seed, rng);
        };
    }

    private static Family pickFamily(Rng rng) {
        return FAMILIES[Math.min(FAMILIES.length - 1, (int) Math.floor(rng.next() * FAMILIES.length))];
    }

    private static Shape buildAberrantBranchShape(int seed, Rng rng) {
        double[] field = new double[PIXELS];
        int clusters = 2 + (int) Math.floor(rng.next() * 4.0);
        double baseAngle = rng.next() * TAU;

        for (int cluster = 0; cluster < clusters; cluster++) {
            double clusterAngle = baseAngle + TAU * cluster / clusters + lerp(-0.42, 0.42, rng.next());
            double radial = lerp(3.5, 9.4, rng.next());
            double cx = clamp(16.0 + Math.cos(clusterAngle) * radial, 5.0, 27.0);
            double cy = clamp(16.0 + Math.sin(clusterAngle) * radial, 5.0, 27.0);
            double rootAngle = rng.next() * TAU;
            stampEllipse(field, cx, cy, lerp(2.5, 4.9, rng.next()), lerp(1.6, 3.5, rng.next()), rootAngle, lerp(1.05, 1.45, rng.next()));

            int arms = 3 + (int) Math.floor(rng.next() * 4.0);
            for (int arm = 0; arm < arms; arm++) {
                double px = cx;
                double py = cy;
                double heading = rootAngle + TAU * arm / arms + lerp(-0.75, 0.75, rng.next());
                int steps = 4 + (int) Math.floor(rng.next() * 7.0);
                double curl = lerp(-0.3, 0.3, rng.next());
                for (int stepIndex = 0; stepIndex < steps; stepIndex++) {
                    double t = stepIndex / Math.max(1.0, steps - 1.0);
                    heading += lerp(-0.42, 0.42, rng.next()) + Math.sin(t * TAU + cluster) * curl;
                    double step = lerp(1.15, 2.15, rng.next()) * lerp(1.0, 0.65, t);
                    px = clamp(px + Math.cos(heading) * step, 2.5, SIZE - 2.5);
                    py = clamp(py + Math.sin(heading) * step, 2.5, SIZE - 2.5);
                    double radius = lerp(2.2, 0.78, t) * lerp(0.78, 1.18, rng.next());
                    stampEllipse(field, px, py, radius * 1.12, radius * 0.78, heading, lerp(0.74, 1.16, rng.next()));
                    if (rng.next() < 0.22 && t > 0.22) {
                        double branchHeading = heading + (rng.next() < 0.5 ? -1.0 : 1.0) * lerp(0.75, 1.35, rng.next());
                        stampEllipse(
                                field,
                                clamp(px + Math.cos(branchHeading) * lerp(1.8, 3.6, rng.next()), 2.5, SIZE - 2.5),
                                clamp(py + Math.sin(branchHeading) * lerp(1.8, 3.6, rng.next()), 2.5, SIZE - 2.5),
                                radius * 0.75,
                                radius * 0.55,
                                branchHeading,
                                0.68
                        );
                    }
                }
            }
        }

        byte[] mask = new byte[PIXELS];
        byte[] next = new byte[PIXELS];
        double threshold = lerp(0.43, 0.66, rng.next());
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int index = y * SIZE + x;
                double nx = (x + 0.5 - 16.0) / 16.0;
                double ny = (y + 0.5 - 16.0) / 16.0;
                double grain = fbm((x + 0.5) * 0.18, (y + 0.5) * 0.18, seed ^ 0x9E51, 4);
                double fracture = fbm((x + 6.0) * 0.54, (y - 3.0) * 0.54, seed ^ 0x7157, 3);
                double rim = smoothstep(0.82, 1.18, Math.sqrt(nx * nx + ny * ny));
                mask[index] = (byte) (field[index] + (grain - 0.5) * 0.48 + (fracture - 0.5) * 0.18 - rim * 0.22 > threshold ? 1 : 0);
            }
        }
        for (int pass = 0; pass < 2; pass++) {
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    int index = y * SIZE + x;
                    int n = countNeighbors(mask, x, y);
                    next[index] = (byte) (mask[index] != 0 ? (n >= 2 ? 1 : 0) : (n >= 6 ? 1 : 0));
                }
            }
            System.arraycopy(next, 0, mask, 0, PIXELS);
        }

        byte[] ownerMap = new byte[PIXELS];
        fillOwnerFromMask(mask, ownerMap, 1);
        keepComponents(mask, ownerMap, 7, 9);
        if (visiblePixels(mask) < 210) {
            stampEllipse(field, 16.0, 16.0, 7.4, 5.6, rng.next() * TAU, 1.1);
            for (int index = 0; index < PIXELS; index++) {
                if (field[index] > threshold) {
                    mask[index] = 1;
                }
            }
            fillOwnerFromMask(mask, ownerMap, 1);
            keepComponents(mask, ownerMap, 7, 9);
        }

        List<Element> elements = List.of(makeFlowSpec("branch", 16.0, 16.0, 11.0, 10.0, baseAngle, rng, "aberrant"));
        return new Shape(Family.ABERRANT_BRANCH, mask, ownerMap, elements);
    }

    private static Shape buildBiomassShape(int seed, Rng rng) {
        double[] field = new double[PIXELS];
        double stretchX = lerp(0.78, 1.13, rng.next());
        double stretchY = lerp(0.78, 1.13, rng.next());
        int lobes = 4 + (int) Math.floor(rng.next() * 4.0);
        double phaseA = rng.next() * TAU;
        double phaseB = rng.next() * TAU;
        double cohesion = lerp(0.64, 0.82, rng.next());
        double branchiness = lerp(0.18, 0.42, rng.next());

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double nx = (x + 0.5 - 16.0) / 16.0;
                double ny = (y + 0.5 - 16.0) / 16.0;
                double angle = Math.atan2(ny, nx);
                double r = Math.sqrt((nx * nx) / stretchX + (ny * ny) / stretchY);
                double outlineNoise = fbm(Math.cos(angle) * 1.9 + 3.5, Math.sin(angle) * 1.9 - 1.5, seed ^ 0x51A7, 4);
                double wave = Math.sin(angle * lobes + phaseA) * 0.07 + Math.sin(angle * (lobes + 3) + phaseB) * 0.035;
                double boundary = cohesion + wave + (outlineNoise - 0.5) * 0.17;
                int index = y * SIZE + x;
                field[index] += (boundary - r) * 1.9;
                field[index] += (fbm((nx + 1.8) * 1.7, (ny + 1.2) * 1.7, seed ^ 0x91FA, 4) - 0.5) * 0.34;
            }
        }

        int branchCount = 2 + (int) Math.floor(rng.next() * 4.0);
        for (int branch = 0; branch < branchCount; branch++) {
            double x = 16.0 + Math.cos(TAU * branch / branchCount + rng.next() * 0.8) * lerp(3.0, 7.0, rng.next());
            double y = 16.0 + Math.sin(TAU * branch / branchCount + rng.next() * 0.8) * lerp(3.0, 7.0, rng.next());
            double heading = rng.next() * TAU;
            int steps = 3 + (int) Math.floor(rng.next() * 5.0);
            for (int step = 0; step < steps; step++) {
                double t = step / Math.max(1.0, steps - 1.0);
                heading += lerp(-0.44, 0.44, rng.next());
                x = clamp(x + Math.cos(heading) * lerp(1.1, 2.0, rng.next()), 3.0, 29.0);
                y = clamp(y + Math.sin(heading) * lerp(1.1, 2.0, rng.next()), 3.0, 29.0);
                double radius = lerp(2.2, 0.95, t);
                stampEllipse(field, x, y, radius * 1.15, radius * 0.72, heading, branchiness);
            }
        }

        double[] sorted = field.clone();
        Arrays.sort(sorted);
        int target = (int) Math.round(lerp(340.0, 510.0, rng.next()));
        double threshold = sorted[clampInt(PIXELS - 1 - target, 0, PIXELS - 1)];
        byte[] mask = new byte[PIXELS];
        byte[] next = new byte[PIXELS];
        for (int index = 0; index < PIXELS; index++) {
            mask[index] = (byte) (field[index] >= threshold ? 1 : 0);
        }
        for (int pass = 0; pass < 2; pass++) {
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    int index = y * SIZE + x;
                    int n = countNeighbors(mask, x, y);
                    next[index] = (byte) (mask[index] != 0 ? (n >= 3 ? 1 : 0) : (n >= 6 ? 1 : 0));
                }
            }
            System.arraycopy(next, 0, mask, 0, PIXELS);
        }
        clearOuterBorder(mask);
        List<int[]> components = keepHybridComponents(mask);
        if (components.isEmpty() || visiblePixels(mask) < 240) {
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    double nx = (x + 0.5 - 16.0) / 16.0;
                    double ny = (y + 0.5 - 16.0) / 16.0;
                    if (nx * nx + ny * ny < 0.48) {
                        mask[y * SIZE + x] = 1;
                    }
                }
            }
            clearOuterBorder(mask);
            keepHybridComponents(mask);
        }
        byte[] ownerMap = new byte[PIXELS];
        fillOwnerFromMask(mask, ownerMap, 1);
        List<Element> elements = List.of(makeFlowSpec("biomass", 16.0, 16.0, 10.5, 10.5, rng.next() * TAU, rng, "living"));
        return new Shape(Family.LIVING_BIOMASS, mask, ownerMap, elements);
    }

    private static Shape buildCrystalClusterShape(int seed, Rng rng) {
        byte[] mask = new byte[PIXELS];
        byte[] ownerMap = new byte[PIXELS];
        double[] depthMap = new double[PIXELS];
        Arrays.fill(depthMap, -999.0);
        List<Element> crystals = new ArrayList<>();
        double rotationStep = Math.PI / 12.0;
        double rotation = Math.round((rng.next() * TAU) / rotationStep) * rotationStep;
        double baseAngle = rotation + lerp(-0.45, 0.45, rng.next());
        double anchorX = lerp(14.8, 17.2, rng.next());
        double anchorY = lerp(14.8, 17.2, rng.next());
        String mainFamily = rng.next() < 0.45 ? "hex" : "diamond";
        crystals.add(makeCrystalClusterSpec(
                mainFamily,
                anchorX,
                anchorY,
                lerp(6.6, 8.7, rng.next()) * lerp(1.0, 1.22, rng.next()),
                lerp(4.9, 6.9, rng.next()),
                baseAngle,
                rng,
                "core"
        ));

        int count = 4 + (int) Math.floor(rng.next() * 2.0);
        for (int index = 1; index < count; index++) {
            int side = index % 2 == 0 ? 1 : -1;
            double spread = (index / Math.max(1.0, count - 1.0) - 0.5) * lerp(1.1, 2.2, rng.next());
            double angle = baseAngle + side * lerp(0.35, 1.25, rng.next()) + spread;
            double distance = lerp(3.1, 6.1, rng.next()) + index * 0.12;
            String family = rng.next() < 0.55 ? "diamond" : "hex";
            double elongation = lerp(0.96, 1.26, rng.next());
            crystals.add(makeCrystalClusterSpec(
                    family,
                    anchorX + Math.cos(angle) * distance + lerp(-1.1, 1.1, rng.next()),
                    anchorY + Math.sin(angle) * distance + lerp(-1.1, 1.1, rng.next()),
                    lerp(4.6, 7.8, rng.next()) * elongation,
                    lerp(3.5, 6.0, rng.next()) / Math.sqrt(elongation),
                    baseAngle + lerp(-0.9, 0.9, rng.next()),
                    rng,
                    "cluster"
            ));
        }

        if (rng.next() < 0.35) {
            crystals.add(makeCrystalClusterSpec(
                    "blade",
                    anchorX + Math.cos(baseAngle + Math.PI) * lerp(3.0, 4.8, rng.next()),
                    anchorY + Math.sin(baseAngle + Math.PI) * lerp(2.4, 3.8, rng.next()),
                    lerp(2.7, 4.0, rng.next()),
                    lerp(2.0, 3.2, rng.next()),
                    baseAngle + lerp(-0.5, 0.5, rng.next()),
                    rng,
                    "shard"
            ));
        }

        for (int id = 0; id < crystals.size(); id++) {
            addCrystalClusterBody(mask, ownerMap, depthMap, crystals.get(id), id);
        }
        carveCrystalClusterNotches(mask, ownerMap, rng);
        removeIsolatedPixels(mask, ownerMap);
        List<int[]> components = keepComponents(mask, ownerMap, 1, 8);
        if (components.isEmpty() || visiblePixels(mask) < 150) {
            Arrays.fill(mask, (byte) 0);
            Arrays.fill(ownerMap, (byte) 0);
            Arrays.fill(depthMap, -999.0);
            crystals.clear();
            crystals.add(makeCrystalClusterSpec("diamond", 16.0, 16.0, 12.2, 9.4, rotation, rng, "fallback"));
            crystals.add(makeCrystalClusterSpec("hex", 13.2, 18.0, 4.8, 3.4, rotation - 0.65, rng, "fallback"));
            for (int id = 0; id < crystals.size(); id++) {
                addCrystalClusterBody(mask, ownerMap, depthMap, crystals.get(id), id);
            }
            keepComponents(mask, ownerMap, 1, 8);
        }
        return new Shape(Family.CRYSTAL_CLUSTER, mask, ownerMap, List.copyOf(crystals));
    }

    private static Shape buildCelestialShape(int seed, Rng rng) {
        byte[] mask = new byte[PIXELS];
        byte[] ownerMap = new byte[PIXELS];
        double[] depthMap = new double[PIXELS];
        Arrays.fill(depthMap, -999.0);
        List<Element> bodies = new ArrayList<>();
        List<Hole> holes = new ArrayList<>();
        int mode = (int) Math.floor(rng.next() * 4.0);
        double rotation = rng.next() * TAU;
        double cx = lerp(14.6, 17.4, rng.next());
        double cy = lerp(14.6, 17.4, rng.next());

        if (mode == 1) {
            bodies.add(makeCelestialSpec("ring", cx, cy, lerp(10.0, 13.2, rng.next()), lerp(5.0, 7.0, rng.next()), rotation, rng, "main-ring"));
            bodies.add(makeCelestialSpec("sphere", cx + lerp(-0.8, 0.8, rng.next()), cy + lerp(-0.8, 0.8, rng.next()), lerp(4.1, 5.8, rng.next()), lerp(3.8, 5.4, rng.next()), rotation + 0.2, rng, "planet"));
            if (rng.next() < 0.75) {
                holes.add(new Hole(cx, cy, lerp(2.1, 3.1, rng.next()), lerp(1.8, 2.7, rng.next()), rotation));
            }
        } else if (mode == 2) {
            bodies.add(makeCelestialSpec("vortex", cx, cy, lerp(9.0, 12.0, rng.next()), lerp(8.0, 11.2, rng.next()), rotation, rng, "spiral"));
            bodies.add(makeCelestialSpec("sphere", cx + Math.cos(rotation) * lerp(1.0, 2.2, rng.next()), cy + Math.sin(rotation) * lerp(1.0, 2.2, rng.next()), lerp(2.4, 3.5, rng.next()), lerp(2.2, 3.3, rng.next()), rotation, rng, "core"));
            if (rng.next() < 0.55) {
                holes.add(new Hole(cx + lerp(-1.1, 1.1, rng.next()), cy + lerp(-1.1, 1.1, rng.next()), lerp(1.2, 2.0, rng.next()), lerp(1.2, 2.0, rng.next()), rng.next() * TAU));
            }
        } else if (mode == 3) {
            bodies.add(makeCelestialSpec("void", cx, cy, lerp(8.0, 10.8, rng.next()), lerp(7.2, 10.2, rng.next()), rotation, rng, "event-halo"));
            bodies.add(makeCelestialSpec("ring", cx + lerp(-0.8, 0.8, rng.next()), cy + lerp(-0.8, 0.8, rng.next()), lerp(7.8, 10.0, rng.next()), lerp(4.5, 6.0, rng.next()), rotation + lerp(-0.45, 0.45, rng.next()), rng, "accretion"));
            holes.add(new Hole(cx, cy, lerp(2.6, 4.2, rng.next()), lerp(2.4, 3.8, rng.next()), rng.next() * TAU));
        } else {
            bodies.add(makeCelestialSpec("sphere", cx, cy, lerp(7.6, 10.4, rng.next()), lerp(7.0, 10.0, rng.next()), rotation, rng, "planet"));
            if (rng.next() < 0.7) {
                bodies.add(makeCelestialSpec("ring", cx, cy, lerp(9.4, 12.6, rng.next()), lerp(4.8, 6.4, rng.next()), rotation + lerp(-0.5, 0.5, rng.next()), rng, "orbit"));
            }
            if (rng.next() < 0.5) {
                holes.add(new Hole(cx + lerp(-3.2, 3.2, rng.next()), cy + lerp(-3.2, 3.2, rng.next()), lerp(1.0, 1.8, rng.next()), lerp(1.0, 1.8, rng.next()), rng.next() * TAU));
            }
        }

        if (rng.next() < 0.42) {
            double moonAngle = rotation + lerp(1.6, 4.7, rng.next());
            bodies.add(makeCelestialSpec(
                    "sphere",
                    cx + Math.cos(moonAngle) * lerp(6.2, 8.6, rng.next()),
                    cy + Math.sin(moonAngle) * lerp(4.4, 6.8, rng.next()),
                    lerp(1.5, 2.4, rng.next()),
                    lerp(1.4, 2.2, rng.next()),
                    rotation + rng.next(),
                    rng,
                    "moon"
            ));
        }

        for (int id = 0; id < bodies.size(); id++) {
            addCelestialBody(mask, ownerMap, depthMap, bodies.get(id), id, seed);
        }
        for (Hole hole : holes) {
            carveCelestialHole(mask, ownerMap, hole);
        }
        removeIsolatedPixels(mask, ownerMap);
        clearOuterBorder(mask, ownerMap);
        List<int[]> components = keepCelestialComponents(mask, ownerMap, mode == 0 ? 2 : 1, 8);
        if (components.isEmpty() || visiblePixels(mask) < 120) {
            Arrays.fill(mask, (byte) 0);
            Arrays.fill(ownerMap, (byte) 0);
            Arrays.fill(depthMap, -999.0);
            bodies.clear();
            bodies.add(makeCelestialSpec("sphere", 16.0, 16.0, 8.6, 8.2, rotation, rng, "fallback"));
            bodies.add(makeCelestialSpec("ring", 16.0, 16.0, 11.0, 5.4, rotation + 0.25, rng, "fallback-ring"));
            for (int id = 0; id < bodies.size(); id++) {
                addCelestialBody(mask, ownerMap, depthMap, bodies.get(id), id, seed);
            }
            clearOuterBorder(mask, ownerMap);
            keepCelestialComponents(mask, ownerMap, 1, 8);
        }
        return new Shape(Family.CELESTIAL_BODY, mask, ownerMap, List.copyOf(bodies));
    }

    private static Element makeFlowSpec(String family, double cx, double cy, double rx, double ry, double rotation, Rng rng, String role) {
        double tangent = rotation + Math.PI * 0.5 + lerp(-0.55, 0.55, rng.next());
        int arms = "vortex".equals(family) ? 4 + (int) Math.floor(rng.next() * 4.0)
                : "ring".equals(family) ? 3 + (int) Math.floor(rng.next() * 3.0)
                : 2 + (int) Math.floor(rng.next() * 3.0);
        return new Element(
                family,
                role,
                cx,
                cy,
                rx,
                ry,
                rotation,
                rng.next() * TAU,
                Math.cos(tangent),
                Math.sin(tangent),
                lerp(0.72, 1.9, rng.next()),
                rng.next() < 0.5 ? -1 : 1,
                lerp(5.4, 12.0, rng.next()),
                rotation + lerp(-0.8, 0.8, rng.next()),
                rng.next(),
                arms,
                arms + 2 + (int) Math.floor(rng.next() * 3.0),
                "vortex".equals(family) ? lerp(6.4, 13.2, rng.next()) : lerp(4.8, 9.8, rng.next()),
                rng.next() * TAU,
                lerp(0.34, 0.62, rng.next()),
                lerp(0.58, 0.86, rng.next()),
                lerp(0.09, 0.17, rng.next()),
                new Plane[0]
        );
    }

    private static Element makeCrystalClusterSpec(String family, double cx, double cy, double rx, double ry, double rotation, Rng rng, String role) {
        Element spec = makeFlowSpec(family, cx, cy, rx, ry, rotation, rng, role);
        int arms = "blade".equals(family) ? 2 : "hex".equals(family) ? 6 : 4;
        return spec.withCrystalFields(makeRegularCrystalPlanes(family, rotation, rng), arms, arms + 2, lerp(3.4, 6.0, rng.next()));
    }

    private static Element makeCelestialSpec(String family, double cx, double cy, double rx, double ry, double rotation, Rng rng, String role) {
        double tangent = rotation + Math.PI * 0.5 + lerp(-0.55, 0.55, rng.next());
        int arms = "vortex".equals(family) ? 4 + (int) Math.floor(rng.next() * 4.0)
                : "ring".equals(family) ? 3 + (int) Math.floor(rng.next() * 3.0)
                : 2 + (int) Math.floor(rng.next() * 3.0);
        return new Element(
                family,
                role,
                cx,
                cy,
                rx,
                ry,
                rotation,
                rng.next() * TAU,
                Math.cos(tangent),
                Math.sin(tangent),
                lerp(0.7, 1.9, rng.next()),
                rng.next() < 0.5 ? -1 : 1,
                lerp(5.4, 12.0, rng.next()),
                rotation + lerp(-0.8, 0.8, rng.next()),
                rng.next(),
                arms,
                arms + 2 + (int) Math.floor(rng.next() * 3.0),
                "vortex".equals(family) ? lerp(6.4, 13.2, rng.next()) : lerp(4.8, 9.8, rng.next()),
                rng.next() * TAU,
                lerp(0.34, 0.62, rng.next()),
                lerp(0.58, 0.86, rng.next()),
                lerp(0.09, 0.17, rng.next()),
                new Plane[0]
        );
    }

    private static Plane[] makeRegularCrystalPlanes(String family, double rotation, Rng rng) {
        List<Plane> planes = new ArrayList<>();
        if ("diamond".equals(family)) {
            double wide = lerp(0.74, 0.82, rng.next());
            double tall = lerp(0.72, 0.8, rng.next());
            addPlane(planes, rotation + Math.PI * 0.25, wide);
            addPlane(planes, rotation + Math.PI * 0.75, tall);
            addPlane(planes, rotation + Math.PI * 1.25, wide);
            addPlane(planes, rotation + Math.PI * 1.75, tall);
        } else if ("hex".equals(family)) {
            for (int index = 0; index < 6; index++) {
                addPlane(planes, rotation + index * Math.PI / 3.0, index % 2 == 0 ? lerp(0.78, 0.84, rng.next()) : lerp(0.72, 0.8, rng.next()));
            }
        } else {
            addPlane(planes, rotation, lerp(0.76, 0.86, rng.next()));
            addPlane(planes, rotation + Math.PI, lerp(0.76, 0.86, rng.next()));
            addPlane(planes, rotation + Math.PI * 0.5, lerp(0.5, 0.58, rng.next()));
            addPlane(planes, rotation + Math.PI * 1.5, lerp(0.5, 0.58, rng.next()));
        }
        if (rng.next() < 0.16) {
            double chipAngle = rotation + Math.floor(rng.next() * planes.size()) * TAU / Math.max(4, planes.size()) + Math.PI / Math.max(4, planes.size());
            addPlane(planes, chipAngle, lerp(0.68, 0.76, rng.next()));
        }
        return planes.toArray(Plane[]::new);
    }

    private static void addPlane(List<Plane> planes, double angle, double limit) {
        planes.add(new Plane(Math.cos(angle), Math.sin(angle), limit));
    }

    private static void addCrystalClusterBody(byte[] mask, byte[] ownerMap, double[] depthMap, Element crystal, int id) {
        int reach = (int) Math.ceil(Math.max(crystal.rx(), crystal.ry()) * 1.18 + 2.0);
        int minX = clampInt((int) Math.floor(crystal.cx() - reach), 1, SIZE - 2);
        int maxX = clampInt((int) Math.ceil(crystal.cx() + reach), 1, SIZE - 2);
        int minY = clampInt((int) Math.floor(crystal.cy() - reach), 1, SIZE - 2);
        int maxY = clampInt((int) Math.ceil(crystal.cy() + reach), 1, SIZE - 2);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                int index = y * SIZE + x;
                double d = crystalSignedDistance(x, y, crystal);
                if (d < -0.015) {
                    continue;
                }
                mask[index] = 1;
                if (d > depthMap[index]) {
                    depthMap[index] = d;
                    ownerMap[index] = (byte) (id + 1);
                }
            }
        }
    }

    private static double crystalSignedDistance(int x, int y, Element crystal) {
        double cos = Math.cos(crystal.rotation());
        double sin = Math.sin(crystal.rotation());
        double dx = x + 0.5 - crystal.cx();
        double dy = y + 0.5 - crystal.cy();
        double lx = (dx * cos + dy * sin) / crystal.rx();
        double ly = (-dx * sin + dy * cos) / crystal.ry();
        double minDistance = 999.0;
        for (Plane plane : crystal.planes()) {
            minDistance = Math.min(minDistance, plane.limit() - (lx * plane.x() + ly * plane.y()));
        }
        return minDistance;
    }

    private static void carveCrystalClusterNotches(byte[] mask, byte[] ownerMap, Rng rng) {
        if (rng.next() > 0.35) {
            return;
        }
        int notches = 1 + (int) Math.floor(rng.next() * 2.0);
        for (int notch = 0; notch < notches; notch++) {
            double angle = rng.next() * TAU;
            double cx = 16.0 + Math.cos(angle) * lerp(2.0, 5.5, rng.next());
            double cy = 16.0 + Math.sin(angle) * lerp(2.0, 5.5, rng.next());
            double cutRot = angle + lerp(-0.7, 0.7, rng.next());
            double cutRx = lerp(1.5, 2.7, rng.next());
            double cutRy = lerp(0.8, 1.3, rng.next());
            double cos = Math.cos(cutRot);
            double sin = Math.sin(cutRot);
            for (int y = 1; y < SIZE - 1; y++) {
                for (int x = 1; x < SIZE - 1; x++) {
                    int index = y * SIZE + x;
                    if (mask[index] == 0) {
                        continue;
                    }
                    double dx = x + 0.5 - cx;
                    double dy = y + 0.5 - cy;
                    double lx = (dx * cos + dy * sin) / cutRx;
                    double ly = (-dx * sin + dy * cos) / cutRy;
                    if (Math.abs(lx) + Math.abs(ly) < 0.95 && edgeDistance(mask, x, y, 3) > 0) {
                        mask[index] = 0;
                        ownerMap[index] = 0;
                    }
                }
            }
        }
    }

    private static double celestialStrength(Element body, int x, int y, int seed) {
        double cos = Math.cos(body.rotation());
        double sin = Math.sin(body.rotation());
        double dx = x + 0.5 - body.cx();
        double dy = y + 0.5 - body.cy();
        double lx = (dx * cos + dy * sin) / Math.max(0.001, body.rx());
        double ly = (-dx * sin + dy * cos) / Math.max(0.001, body.ry());
        double radius = Math.hypot(lx, ly);
        double angle = Math.atan2(ly, lx);
        double edgeNoise = (fbm(lx * 2.8 + 4.0, ly * 2.8 - 1.5, seed ^ 0x4488, 3) - 0.5) * 0.09;

        if ("sphere".equals(body.family())) {
            double dimple = Math.sin(angle * 3.0 + body.phase()) * 0.04;
            return 1.03 - radius + edgeNoise + dimple;
        }
        if ("ring".equals(body.family())) {
            double band = 1.0 - Math.abs(radius - body.ringRadius()) / body.thickness();
            double brightArc = 0.62
                    + 0.24 * Math.sin(angle * body.arms() + body.phase())
                    + 0.14 * Math.sin(angle * body.secondaryArms() - radius * 3.0 + body.branchPhase());
            return band * brightArc + edgeNoise * 0.6;
        }
        if ("vortex".equals(body.family())) {
            double spiral = 0.5 + 0.5 * Math.sin(angle * body.arms() + radius * body.swirl() + body.phase());
            double secondary = 0.5 + 0.5 * Math.sin(angle * body.secondaryArms() - radius * body.swirl() * 1.28 + body.branchPhase());
            double brokenArc = 0.76 + 0.24 * Math.sin(angle * Math.max(2, body.arms() - 1) + radius * 5.2 + body.branchPhase());
            double arm = smoothstep(0.48, 0.82, spiral) * (1.08 - radius) * brokenArc;
            double fineArm = smoothstep(0.62, 0.92, secondary) * smoothstep(1.08, 0.28, radius) * body.branchStrength();
            double core = smoothstep(0.38, 0.0, radius) * 0.62;
            return Math.max(arm, Math.max(fineArm, core)) + edgeNoise;
        }

        double outer = smoothstep(1.08, 0.62, radius);
        double inner = smoothstep(0.24, 0.48, radius);
        double broken = 0.72
                + 0.18 * Math.sin(angle * body.secondaryArms() + body.phase())
                + 0.10 * Math.sin(angle * body.arms() - radius * 4.0 + body.branchPhase());
        return outer * inner * broken + edgeNoise;
    }

    private static void addCelestialBody(byte[] mask, byte[] ownerMap, double[] depthMap, Element body, int id, int seed) {
        int reach = (int) Math.ceil(Math.max(body.rx(), body.ry()) * 1.25 + 2.0);
        int minX = clampInt((int) Math.floor(body.cx() - reach), 1, SIZE - 2);
        int maxX = clampInt((int) Math.ceil(body.cx() + reach), 1, SIZE - 2);
        int minY = clampInt((int) Math.floor(body.cy() - reach), 1, SIZE - 2);
        int maxY = clampInt((int) Math.ceil(body.cy() + reach), 1, SIZE - 2);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                int index = y * SIZE + x;
                double strength = celestialStrength(body, x, y, seed);
                if (strength <= 0.03) {
                    continue;
                }
                mask[index] = 1;
                if (strength > depthMap[index]) {
                    depthMap[index] = strength;
                    ownerMap[index] = (byte) (id + 1);
                }
            }
        }
    }

    private static void carveCelestialHole(byte[] mask, byte[] ownerMap, Hole hole) {
        double cos = Math.cos(hole.rotation());
        double sin = Math.sin(hole.rotation());
        int minX = clampInt((int) Math.floor(hole.cx() - hole.rx() - 1.0), 1, SIZE - 2);
        int maxX = clampInt((int) Math.ceil(hole.cx() + hole.rx() + 1.0), 1, SIZE - 2);
        int minY = clampInt((int) Math.floor(hole.cy() - hole.ry() - 1.0), 1, SIZE - 2);
        int maxY = clampInt((int) Math.ceil(hole.cy() + hole.ry() + 1.0), 1, SIZE - 2);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                int index = y * SIZE + x;
                if (mask[index] == 0) {
                    continue;
                }
                double dx = x + 0.5 - hole.cx();
                double dy = y + 0.5 - hole.cy();
                double lx = (dx * cos + dy * sin) / hole.rx();
                double ly = (-dx * sin + dy * cos) / hole.ry();
                if (lx * lx + ly * ly < 1.0) {
                    mask[index] = 0;
                    ownerMap[index] = 0;
                }
            }
        }
    }

    private static double[] buildTuring(int seed, Rng rng, byte[] mask) {
        double[] a = new double[PIXELS];
        double[] b = new double[PIXELS];
        double[] nextA = new double[PIXELS];
        double[] nextB = new double[PIXELS];
        double feed = lerp(0.032, 0.048, rng.next());
        double kill = lerp(0.057, 0.064, rng.next());
        int iterations = 52 + (int) Math.floor(rng.next() * 34.0);
        double angle = rng.next() * TAU;
        double dx = Math.cos(angle);
        double dy = Math.sin(angle);
        double freq = lerp(5.5, 9.5, rng.next());
        double startPhase = rng.next() * TAU;

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int index = y * SIZE + x;
                a[index] = 1.0;
                b[index] = 0.0;
                if (mask[index] == 0) {
                    continue;
                }
                double nx = (x + 0.5 - 16.0) / 16.0;
                double ny = (y + 0.5 - 16.0) / 16.0;
                double wave = 0.5 + 0.5 * Math.sin((nx * dx + ny * dy) * freq + startPhase);
                double spot = fbm((x + 1.7) * 0.24, (y - 2.4) * 0.24, seed ^ 0xB17E, 4);
                double starter = clamp(wave * 0.24 + spot * 0.58 - 0.35, 0.0, 1.0);
                b[index] = starter;
                a[index] = 1.0 - starter * 0.42;
            }
        }

        for (int iteration = 0; iteration < iterations; iteration++) {
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    int index = y * SIZE + x;
                    if (mask[index] == 0) {
                        nextA[index] = 1.0;
                        nextB[index] = 0.0;
                        continue;
                    }
                    double av = a[index];
                    double bv = b[index];
                    double lapA = -av
                            + (sample(a, mask, x - 1, y, 1.0) + sample(a, mask, x + 1, y, 1.0)
                            + sample(a, mask, x, y - 1, 1.0) + sample(a, mask, x, y + 1, 1.0)) * 0.2
                            + (sample(a, mask, x - 1, y - 1, 1.0) + sample(a, mask, x + 1, y - 1, 1.0)
                            + sample(a, mask, x - 1, y + 1, 1.0) + sample(a, mask, x + 1, y + 1, 1.0)) * 0.05;
                    double lapB = -bv
                            + (sample(b, mask, x - 1, y, 0.0) + sample(b, mask, x + 1, y, 0.0)
                            + sample(b, mask, x, y - 1, 0.0) + sample(b, mask, x, y + 1, 0.0)) * 0.2
                            + (sample(b, mask, x - 1, y - 1, 0.0) + sample(b, mask, x + 1, y - 1, 0.0)
                            + sample(b, mask, x - 1, y + 1, 0.0) + sample(b, mask, x + 1, y + 1, 0.0)) * 0.05;
                    double reaction = av * bv * bv;
                    nextA[index] = clamp(av + (lapA - reaction + feed * (1.0 - av)), 0.0, 1.0);
                    nextB[index] = clamp(bv + (0.48 * lapB + reaction - (kill + feed) * bv), 0.0, 1.0);
                }
            }
            double[] swapA = a;
            a = nextA;
            nextA = swapA;
            double[] swapB = b;
            b = nextB;
            nextB = swapB;
        }

        double min = 1.0;
        double max = 0.0;
        for (int index = 0; index < PIXELS; index++) {
            if (mask[index] == 0) {
                continue;
            }
            min = Math.min(min, b[index]);
            max = Math.max(max, b[index]);
        }
        double span = Math.max(0.0001, max - min);
        for (int index = 0; index < PIXELS; index++) {
            b[index] = mask[index] != 0 ? clamp((b[index] - min) / span, 0.0, 1.0) : 0.0;
        }
        return b;
    }

    private static double sample(double[] values, byte[] mask, int x, int y, double fallback) {
        if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) {
            return fallback;
        }
        int index = y * SIZE + x;
        return mask[index] != 0 ? values[index] : fallback;
    }

    private static Palette createPalette(Rng rng) {
        int mode = (int) Math.floor(rng.next() * 6.0);
        double base = rng.next() * 360.0;
        if (mode == 0) {
            return new Palette(
                    hslToRgb(base + 4.0, 44.0, 13.0),
                    hslToRgb(base, 74.0, 34.0),
                    hslToRgb(base + 8.0, 78.0, 46.0),
                    hslToRgb(base - 8.0, 62.0, 58.0),
                    hslToRgb(base + 16.0, 72.0, 42.0),
                    hslToRgb(base + 6.0, 28.0, 84.0)
            );
        }
        if (mode == 1) {
            return new Palette(
                    hslToRgb(base - 18.0, 42.0, 13.0),
                    hslToRgb(base, 78.0, 38.0),
                    hslToRgb(base + 28.0, 84.0, 54.0),
                    hslToRgb(base - 34.0, 76.0, 54.0),
                    hslToRgb(base + 52.0, 82.0, 46.0),
                    hslToRgb(base + 18.0, 34.0, 84.0)
            );
        }
        if (mode == 2) {
            return new Palette(
                    hslToRgb(base + 8.0, 44.0, 13.0),
                    hslToRgb(base, 78.0, 39.0),
                    hslToRgb(base + 24.0, 84.0, 54.0),
                    hslToRgb(base + 180.0, 76.0, 56.0),
                    hslToRgb(base + 204.0, 82.0, 48.0),
                    hslToRgb(base + 180.0, 30.0, 86.0)
            );
        }
        if (mode == 3) {
            return new Palette(
                    hslToRgb(base + 6.0, 42.0, 13.0),
                    hslToRgb(base, 76.0, 38.0),
                    hslToRgb(base + 120.0, 84.0, 54.0),
                    hslToRgb(base + 240.0, 78.0, 56.0),
                    hslToRgb(base + 145.0, 82.0, 46.0),
                    hslToRgb(base + 30.0, 34.0, 86.0)
            );
        }
        if (mode == 4) {
            return new Palette(
                    hslToRgb(258.0 + rng.next() * 12.0, 44.0, 13.0),
                    hslToRgb(278.0 + rng.next() * 18.0, 78.0, 40.0),
                    hslToRgb(145.0 + rng.next() * 22.0, 84.0, 55.0),
                    hslToRgb(194.0 + rng.next() * 18.0, 82.0, 58.0),
                    hslToRgb(320.0 + rng.next() * 18.0, 84.0, 52.0),
                    hslToRgb(166.0, 36.0, 86.0)
            );
        }
        return new Palette(
                hslToRgb(232.0 + rng.next() * 12.0, 42.0, 12.0),
                hslToRgb(210.0 + rng.next() * 18.0, 64.0, 38.0),
                hslToRgb(280.0 + rng.next() * 16.0, 64.0, 54.0),
                hslToRgb(188.0 + rng.next() * 18.0, 78.0, 62.0),
                hslToRgb(320.0 + rng.next() * 10.0, 48.0, 60.0),
                hslToRgb(200.0, 26.0, 88.0)
        );
    }

    private static void stampEllipse(double[] field, double cx, double cy, double rx, double ry, double angle, double strength) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        int reach = (int) Math.ceil(Math.max(rx, ry) + 2.0);
        int minX = clampInt((int) Math.floor(cx - reach), 0, SIZE - 1);
        int maxX = clampInt((int) Math.ceil(cx + reach), 0, SIZE - 1);
        int minY = clampInt((int) Math.floor(cy - reach), 0, SIZE - 1);
        int maxY = clampInt((int) Math.ceil(cy + reach), 0, SIZE - 1);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double dx = x + 0.5 - cx;
                double dy = y + 0.5 - cy;
                double lx = dx * cos + dy * sin;
                double ly = -dx * sin + dy * cos;
                double d = (lx * lx) / (rx * rx) + (ly * ly) / (ry * ry);
                if (d > 1.35) {
                    continue;
                }
                field[y * SIZE + x] += (1.0 - smoothstep(0.12, 1.35, d)) * strength;
            }
        }
    }

    private static int visiblePixels(byte[] mask) {
        int total = 0;
        for (byte value : mask) {
            total += value != 0 ? 1 : 0;
        }
        return total;
    }

    private static int countNeighbors(byte[] mask, int x, int y) {
        int count = 0;
        for (int oy = -1; oy <= 1; oy++) {
            for (int ox = -1; ox <= 1; ox++) {
                if (ox == 0 && oy == 0) {
                    continue;
                }
                int nx = x + ox;
                int ny = y + oy;
                if (nx < 0 || ny < 0 || nx >= SIZE || ny >= SIZE) {
                    continue;
                }
                if (mask[ny * SIZE + nx] != 0) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int edgeDistance(byte[] mask, int x, int y, int radius) {
        for (int r = 1; r <= radius; r++) {
            for (int oy = -r; oy <= r; oy++) {
                for (int ox = -r; ox <= r; ox++) {
                    if (Math.abs(ox) != r && Math.abs(oy) != r) {
                        continue;
                    }
                    int nx = x + ox;
                    int ny = y + oy;
                    if (nx < 0 || ny < 0 || nx >= SIZE || ny >= SIZE) {
                        continue;
                    }
                    if (mask[ny * SIZE + nx] != 0) {
                        return r;
                    }
                }
            }
        }
        return 0;
    }

    private static void clearOuterBorder(byte[] mask) {
        clearOuterBorder(mask, null);
    }

    private static void clearOuterBorder(byte[] mask, byte[] ownerMap) {
        for (int index = 0; index < SIZE; index++) {
            int top = index;
            int bottom = (SIZE - 1) * SIZE + index;
            int left = index * SIZE;
            int right = index * SIZE + SIZE - 1;
            mask[top] = 0;
            mask[bottom] = 0;
            mask[left] = 0;
            mask[right] = 0;
            if (ownerMap != null) {
                ownerMap[top] = 0;
                ownerMap[bottom] = 0;
                ownerMap[left] = 0;
                ownerMap[right] = 0;
            }
        }
    }

    private static List<int[]> componentsOf(byte[] mask) {
        byte[] seen = new byte[PIXELS];
        int[] stack = new int[PIXELS];
        List<int[]> components = new ArrayList<>();
        for (int start = 0; start < PIXELS; start++) {
            if (mask[start] == 0 || seen[start] != 0) {
                continue;
            }
            int[] component = new int[PIXELS];
            int componentSize = 0;
            int stackSize = 0;
            stack[stackSize++] = start;
            seen[start] = 1;
            while (stackSize > 0) {
                int index = stack[--stackSize];
                component[componentSize++] = index;
                int x = index % SIZE;
                int y = index / SIZE;
                int left = x == 0 ? -1 : index - 1;
                int right = x == SIZE - 1 ? -1 : index + 1;
                int up = y == 0 ? -1 : index - SIZE;
                int down = y == SIZE - 1 ? -1 : index + SIZE;
                stackSize = pushNeighbor(mask, seen, stack, stackSize, left);
                stackSize = pushNeighbor(mask, seen, stack, stackSize, right);
                stackSize = pushNeighbor(mask, seen, stack, stackSize, up);
                stackSize = pushNeighbor(mask, seen, stack, stackSize, down);
            }
            components.add(Arrays.copyOf(component, componentSize));
        }
        components.sort((left, right) -> Integer.compare(right.length, left.length));
        return components;
    }

    private static int pushNeighbor(byte[] mask, byte[] seen, int[] stack, int stackSize, int index) {
        if (index >= 0 && mask[index] != 0 && seen[index] == 0) {
            seen[index] = 1;
            stack[stackSize++] = index;
        }
        return stackSize;
    }

    private static List<int[]> keepHybridComponents(byte[] mask) {
        List<int[]> components = componentsOf(mask);
        if (components.isEmpty()) {
            return components;
        }
        List<int[]> keep = new ArrayList<>();
        int[] main = components.get(0);
        keep.add(main);
        byte[] mainSet = new byte[PIXELS];
        for (int index : main) {
            mainSet[index] = 1;
        }

        for (int index = 1; index < components.size() && keep.size() < 3; index++) {
            int[] component = components.get(index);
            if (component.length < 10 || component.length > main.length * 0.22) {
                continue;
            }
            boolean close = false;
            for (int pixel : component) {
                int x = pixel % SIZE;
                int y = pixel / SIZE;
                if (edgeDistance(mainSet, x, y, 5) > 0) {
                    close = true;
                    break;
                }
            }
            if (close) {
                keep.add(component);
            }
        }

        Arrays.fill(mask, (byte) 0);
        for (int[] component : keep) {
            for (int index : component) {
                mask[index] = 1;
            }
        }
        return keep;
    }

    private static List<int[]> keepComponents(byte[] mask, byte[] ownerMap, int maxComponents, int minSize) {
        clearOuterBorder(mask, ownerMap);
        List<int[]> components = componentsOf(mask).stream()
                .filter(component -> component.length >= minSize)
                .limit(maxComponents)
                .toList();
        byte[] keep = new byte[PIXELS];
        for (int[] component : components) {
            for (int index : component) {
                keep[index] = 1;
            }
        }
        for (int index = 0; index < PIXELS; index++) {
            mask[index] = keep[index];
            if (mask[index] == 0) {
                ownerMap[index] = 0;
            }
        }
        return components;
    }

    private static List<int[]> keepCelestialComponents(byte[] mask, byte[] ownerMap, int maxComponents, int minSize) {
        List<int[]> components = componentsOf(mask).stream()
                .filter(component -> component.length >= minSize)
                .limit(maxComponents)
                .toList();
        byte[] keep = new byte[PIXELS];
        for (int[] component : components) {
            for (int index : component) {
                keep[index] = 1;
            }
        }
        for (int index = 0; index < PIXELS; index++) {
            mask[index] = keep[index];
            if (mask[index] == 0) {
                ownerMap[index] = 0;
            }
        }
        return components;
    }

    private static void fillOwnerFromMask(byte[] mask, byte[] ownerMap, int owner) {
        for (int index = 0; index < PIXELS; index++) {
            ownerMap[index] = (byte) (mask[index] != 0 ? owner : 0);
        }
    }

    private static void removeIsolatedPixels(byte[] mask, byte[] ownerMap) {
        byte[] next = mask.clone();
        for (int y = 1; y < SIZE - 1; y++) {
            for (int x = 1; x < SIZE - 1; x++) {
                int index = y * SIZE + x;
                if (mask[index] != 0 && countNeighbors(mask, x, y) <= 1) {
                    next[index] = 0;
                }
            }
        }
        System.arraycopy(next, 0, mask, 0, PIXELS);
        for (int index = 0; index < PIXELS; index++) {
            if (mask[index] == 0) {
                ownerMap[index] = 0;
            }
        }
    }

    private static int hashSeed(long seed) {
        return fnv1a(Long.toUnsignedString(seed, 16));
    }

    private static int fnv1a(String text) {
        int hash = 0x811C9DC5;
        for (int index = 0; index < text.length(); index++) {
            hash ^= text.charAt(index);
            hash *= 0x01000193;
        }
        hash += hash << 13;
        hash ^= hash >>> 7;
        hash += hash << 3;
        hash ^= hash >>> 17;
        hash += hash << 5;
        return hash;
    }

    public static String normalizeSource(String sourceId) {
        if (sourceId == null || sourceId.isBlank()) {
            return "spectralization:primordial";
        }
        return sourceId.toLowerCase(Locale.ROOT);
    }

    public static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return value;
    }

    private static long fnv1a64(String text) {
        long hash = 0xCBF29CE484222325L;
        for (int index = 0; index < text.length(); index++) {
            hash ^= text.charAt(index);
            hash *= 0x100000001B3L;
        }
        return hash;
    }

    private static double unit(long seed, int salt) {
        return ((mix64(seed + salt * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53);
    }

    private static double hash2(int x, int y, int seed) {
        int hash = seed ^ x * 0x27D4EB2D ^ y * 0x165667B1;
        hash ^= hash >>> 15;
        hash *= 0x85EBCA6B;
        hash ^= hash >>> 13;
        hash *= 0xC2B2AE35;
        hash ^= hash >>> 16;
        return Integer.toUnsignedLong(hash) / 4294967296.0;
    }

    private static double valueNoise(double x, double y, int seed) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        double xf = x - xi;
        double yf = y - yi;
        double sx = smooth(xf);
        double sy = smooth(yf);
        double n00 = hash2(xi, yi, seed);
        double n10 = hash2(xi + 1, yi, seed);
        double n01 = hash2(xi, yi + 1, seed);
        double n11 = hash2(xi + 1, yi + 1, seed);
        return lerp(lerp(n00, n10, sx), lerp(n01, n11, sx), sy);
    }

    private static double fbm(double x, double y, int seed, int octaves) {
        double amplitude = 0.5;
        double frequency = 1.0;
        double sum = 0.0;
        double norm = 0.0;
        for (int octave = 0; octave < octaves; octave++) {
            sum += valueNoise(x * frequency, y * frequency, seed + octave * 1009) * amplitude;
            norm += amplitude;
            amplitude *= 0.52;
            frequency *= 2.03;
        }
        return sum / norm;
    }

    private static int hslToRgb(double h, double s, double l) {
        double hue = ((h % 360.0) + 360.0) % 360.0 / 360.0;
        double sat = clamp(s, 0.0, 100.0) / 100.0;
        double lum = clamp(l, 0.0, 100.0) / 100.0;
        double q = lum < 0.5 ? lum * (1.0 + sat) : lum + sat - lum * sat;
        double p = 2.0 * lum - q;
        return rgb(
                (int) Math.round(hslChannel(p, q, hue + 1.0 / 3.0) * 255.0),
                (int) Math.round(hslChannel(p, q, hue) * 255.0),
                (int) Math.round(hslChannel(p, q, hue - 1.0 / 3.0) * 255.0)
        );
    }

    private static double hslChannel(double p, double q, double t) {
        double value = t;
        if (value < 0.0) {
            value += 1.0;
        }
        if (value > 1.0) {
            value -= 1.0;
        }
        if (value < 1.0 / 6.0) {
            return p + (q - p) * 6.0 * value;
        }
        if (value < 0.5) {
            return q;
        }
        if (value < 2.0 / 3.0) {
            return p + (q - p) * (2.0 / 3.0 - value) * 6.0;
        }
        return p;
    }

    private static double smooth(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double clamp(double value, double min, double max) {
        return Math.min(max, Math.max(min, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }

    private static boolean isOuterBorder(int x, int y) {
        return x == 0 || y == 0 || x == SIZE - 1 || y == SIZE - 1;
    }

    private static int rgb(int red, int green, int blue) {
        return (clampChannel(red) << 16) | (clampChannel(green) << 8) | clampChannel(blue);
    }

    private static int clampChannel(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static int mixColor(int left, int right, double amount) {
        double t = clamp(amount, 0.0, 1.0);
        int red = (int) Math.round(lerp(red(left), red(right), t));
        int green = (int) Math.round(lerp(green(left), green(right), t));
        int blue = (int) Math.round(lerp(blue(left), blue(right), t));
        return rgb(red, green, blue);
    }

    private static int shadeColor(int color, double shade) {
        return rgb(
                (int) Math.round(red(color) * shade),
                (int) Math.round(green(color) * shade),
                (int) Math.round(blue(color) * shade)
        );
    }

    public static int red(int argb) {
        return argb >> 16 & 0xFF;
    }

    public static int green(int argb) {
        return argb >> 8 & 0xFF;
    }

    public static int blue(int argb) {
        return argb & 0xFF;
    }

    public static int alpha(int argb) {
        return argb >>> 24;
    }

    private enum Mode {
        DRIFT,
        PULSE,
        HYBRID,
        SHIMMER,
        ORBIT
    }

    public enum Family {
        ABERRANT_BRANCH("aberrant_branch"),
        LIVING_BIOMASS("living_biomass"),
        CRYSTAL_CLUSTER("crystal_cluster"),
        CELESTIAL_BODY("celestial_body");

        private final String id;

        Family(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public String translationKey() {
            return "singular_material.spectralization.family." + id;
        }
    }

    public record Traits(Family family, int resonance, int entropy, int phase, int chroma) {
    }

    public record Visual(long seed, Traits traits, int[] argb, boolean[] solid) {
        public boolean solid(int x, int y) {
            return x >= 0 && y >= 0 && x < SIZE && y < SIZE && solid[y * SIZE + x];
        }

        public boolean visible(int x, int y) {
            return x >= 0 && y >= 0 && x < SIZE && y < SIZE && alpha(argb[y * SIZE + x]) > 0;
        }

        public int color(int x, int y) {
            return argb[y * SIZE + x];
        }
    }

    public record VisualStrip(long seed, Traits traits, Visual[] frames, boolean[] solid) {
        public Visual frame(int frame) {
            return frames[Math.floorMod(frame, frames.length)];
        }
    }

    private record Palette(int shadow, int core, int warm, int cold, int odd, int pearl) {
    }

    private record Drift(double x, double y, double speed, double freq) {
    }

    private record Shape(Family type, byte[] mask, byte[] ownerMap, List<Element> elements) {
    }

    private record State(
            int hash,
            Shape shape,
            double[] turing,
            Palette palette,
            Drift drift,
            double organicWeight,
            Fields fields,
            Mode defaultMode
    ) {
    }

    private record Fields(
            double[] band,
            double[] mineral,
            double[] micro,
            double[] facet,
            double[] orbit,
            double[] radial,
            byte[] owner,
            byte[] seam,
            byte[] halo,
            byte[] neighbors
    ) {
    }

    private record Plane(double x, double y, double limit) {
    }

    private record Hole(double cx, double cy, double rx, double ry, double rotation) {
    }

    private record Element(
            String family,
            String role,
            double cx,
            double cy,
            double rx,
            double ry,
            double rotation,
            double phase,
            double driftX,
            double driftY,
            double speed,
            int spinDir,
            double freq,
            double facetAngle,
            double tint,
            int arms,
            int secondaryArms,
            double swirl,
            double branchPhase,
            double branchStrength,
            double ringRadius,
            double thickness,
            Plane[] planes
    ) {
        Element withCrystalFields(Plane[] planes, int arms, int secondaryArms, double swirl) {
            return new Element(
                    family,
                    role,
                    cx,
                    cy,
                    rx,
                    ry,
                    rotation,
                    phase,
                    driftX,
                    driftY,
                    speed,
                    spinDir,
                    freq,
                    facetAngle,
                    tint,
                    arms,
                    secondaryArms,
                    swirl,
                    branchPhase,
                    branchStrength,
                    ringRadius,
                    thickness,
                    planes
            );
        }
    }

    private static final class Rng {
        private int state;

        private Rng(int seed) {
            state = seed;
        }

        private double next() {
            state += 0x6D2B79F5;
            int result = (state ^ (state >>> 15)) * (1 | state);
            result ^= result + (result ^ (result >>> 7)) * (61 | result);
            return Integer.toUnsignedLong(result ^ (result >>> 14)) / 4294967296.0;
        }
    }

    private SingularMaterialGenerator() {
    }
}
