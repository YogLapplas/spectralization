package io.github.yoglappland.spectralization.optics.geometry;

import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import io.github.yoglappland.spectralization.optics.BeamModel;
import java.util.Objects;

public final class BeamGeometryOps {
    public static final double MIN_RADIUS = BeamEnvelope.DEFAULT_MIN_WAIST_RADIUS;
    private static final double MAX_REASONABLE_RADIUS = 16.0;
    private static final double DIFFRACTION_DIVERGENCE_SCALE = 0.0025;
    private static final double LENS_ANGULAR_NOISE_SCALE = MIN_RADIUS / 8.0;
    private static final double MOMENT_EPSILON = 1.0E-12;
    private static final double FOCUS_EPSILON = 1.0E-6;

    public static BeamGeometrySample sample(BeamEnvelope envelope, double power) {
        double area = spotArea(envelope);
        double irradiance = area <= 0.0 ? 0.0 : Math.max(0.0, power) / area;

        return new BeamGeometrySample(envelope, area, irradiance, visualLevel(irradiance));
    }

    public static BeamEnvelope propagate(BeamEnvelope envelope, double distance) {
        if (!Double.isFinite(distance) || distance < 0.0) {
            throw new IllegalArgumentException("Propagation distance must be finite and non-negative");
        }

        if (distance == 0.0 || envelope.model() == BeamModel.PLANE_WAVE) {
            return envelope;
        }

        BeamMoments propagated = momentsFromEnvelope(envelope, true).propagate(distance);
        BeamEnvelope output = envelopeFromMoments(
                envelope,
                propagated,
                envelope.beamQuality(),
                envelope.apertureFill(),
                envelope.scatter()
        );
        double divergence = Math.max(output.divergence(), effectiveDivergence(output));
        double scatter = clamp01(envelope.scatter() + divergence * distance * 0.01);

        return output.withDivergence(divergence).withScatter(scatter);
    }

    private static double effectiveDivergence(BeamEnvelope envelope) {
        if (envelope.model() == BeamModel.PLANE_WAVE) {
            return envelope.divergence();
        }

        double waistRadius = Math.max(MIN_RADIUS, envelope.waistRadius());
        double scatterFactor = 1.0 + envelope.scatter() * 3.0;
        double diffractionFloor = DIFFRACTION_DIVERGENCE_SCALE
                * Math.max(1.0, envelope.beamQuality())
                * scatterFactor
                / waistRadius;
        return Math.max(envelope.divergence(), diffractionFloor);
    }

    public static BeamEnvelope applyThinLens(
            BeamEnvelope envelope,
            double focalLength,
            double apertureRadius,
            double qualityMultiplier
    ) {
        Objects.requireNonNull(envelope, "envelope");

        if (!Double.isFinite(focalLength) || focalLength <= 0.0) {
            throw new IllegalArgumentException("Lens focal length must be finite and positive");
        }

        if (!Double.isFinite(apertureRadius) || apertureRadius <= 0.0) {
            throw new IllegalArgumentException("Lens aperture radius must be finite and positive");
        }

        if (!Double.isFinite(qualityMultiplier) || qualityMultiplier < 1.0) {
            throw new IllegalArgumentException("Lens quality multiplier must be finite and at least 1");
        }

        if (envelope.model() == BeamModel.PLANE_WAVE) {
            return envelope;
        }

        double inputRadius = Math.max(MIN_RADIUS, envelope.radius());
        double clippedRadius = Math.min(inputRadius, apertureRadius);
        double rawApertureFill = inputRadius / apertureRadius;
        double overfill = Math.max(0.0, rawApertureFill - 1.0);
        double beamQuality = Math.max(1.0, envelope.beamQuality() * qualityMultiplier * (1.0 + overfill * 0.25));
        double scatter = clamp01(envelope.scatter() + (qualityMultiplier - 1.0) * 0.04 + overfill * 0.12);
        BeamEnvelope clipped = new BeamEnvelope(
                envelope.model(),
                clippedRadius,
                Math.min(clippedRadius, Math.max(MIN_RADIUS, envelope.waistRadius())),
                envelope.divergence(),
                envelope.focusDistance(),
                beamQuality,
                clamp01(rawApertureFill),
                scatter,
                envelope.modeM(),
                envelope.modeN()
        );
        BeamMoments transformed = momentsFromEnvelope(clipped, false).thinLens(focalLength);
        double angularNoise = lensAngularNoise(apertureRadius, qualityMultiplier, envelope.scatter(), overfill);

        return envelopeFromMoments(
                clipped,
                transformed.withAngularNoise(angularNoise),
                beamQuality,
                clamp01(rawApertureFill),
                scatter
        );
    }

    public static SpatialModeCoupling passivePropagationCoupling(BeamEnvelope envelope, double distance) {
        return SpatialModeCoupling.ordered(propagate(envelope, distance));
    }

    public static BeamEnvelope degradeQuality(
            BeamEnvelope envelope,
            double beamQualityMultiplier,
            double extraScatter
    ) {
        if (!Double.isFinite(beamQualityMultiplier) || beamQualityMultiplier < 1.0) {
            throw new IllegalArgumentException("Beam quality multiplier must be finite and at least 1");
        }

        return new BeamEnvelope(
                envelope.model(),
                envelope.radius(),
                envelope.waistRadius(),
                envelope.divergence(),
                envelope.focusDistance(),
                envelope.beamQuality() * beamQualityMultiplier,
                envelope.apertureFill(),
                clamp01(envelope.scatter() + extraScatter),
                envelope.modeM(),
                envelope.modeN()
        );
    }

    public static double spotArea(BeamEnvelope envelope) {
        double radius = Math.max(MIN_RADIUS, envelope.radius());
        return Math.PI * radius * radius;
    }

    public static int visualLevel(double irradiance) {
        if (!Double.isFinite(irradiance) || irradiance <= 0.0) {
            return 0;
        }

        return Math.min(8, 1 + (int) Math.floor(Math.log10(1.0 + irradiance) * 2.0));
    }

    public static int widthLevel(BeamEnvelope envelope) {
        double radius = Math.max(MIN_RADIUS, envelope.radius());
        return Math.min(8, Math.max(1, (int) Math.ceil(radius * 8.0)));
    }

    public static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }

        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double lensAngularNoise(
            double apertureRadius,
            double qualityMultiplier,
            double inputScatter,
            double overfill
    ) {
        double apertureFactor = 1.0 / Math.max(0.25, apertureRadius);
        double scatterFactor = 1.0 + clamp01(inputScatter) * 2.0;
        double overfillFactor = 1.0 + Math.min(4.0, overfill) * 0.5;
        return LENS_ANGULAR_NOISE_SCALE * qualityMultiplier * apertureFactor * scatterFactor * overfillFactor;
    }

    private static BeamMoments momentsFromEnvelope(BeamEnvelope envelope, boolean applyDivergenceFloor) {
        double radius = Math.max(MIN_RADIUS, Math.min(MAX_REASONABLE_RADIUS, envelope.radius()));
        double waistRadius = Math.min(radius, Math.max(MIN_RADIUS, envelope.waistRadius()));
        double divergence = Math.max(0.0, envelope.divergence());

        if (divergence <= MOMENT_EPSILON && Math.abs(envelope.focusDistance()) > FOCUS_EPSILON && radius > waistRadius) {
            divergence = Math.sqrt(Math.max(0.0, radius * radius - waistRadius * waistRadius))
                    / Math.abs(envelope.focusDistance());
        }

        if (applyDivergenceFloor) {
            divergence = Math.max(divergence, effectiveDivergence(envelope));
        }

        double zFromWaist = -envelope.focusDistance();

        if (divergence <= MOMENT_EPSILON) {
            zFromWaist = 0.0;
        } else {
            double maxAbsZ = Math.sqrt(Math.max(0.0, radius * radius - waistRadius * waistRadius)) / divergence;
            zFromWaist = Math.copySign(Math.min(Math.abs(zFromWaist), maxAbsZ), zFromWaist);
        }

        double tt = divergence * divergence;
        double xt = tt * zFromWaist;
        return new BeamMoments(radius * radius, xt, tt);
    }

    private static BeamEnvelope envelopeFromMoments(
            BeamEnvelope template,
            BeamMoments moments,
            double beamQuality,
            double apertureFill,
            double scatter
    ) {
        double xx = Math.max(0.0, moments.xx());
        double tt = Math.max(0.0, moments.tt());
        double xt = moments.xt();
        double radius = Math.sqrt(xx);
        double waistSquared;
        double focusDistance;

        if (tt <= MOMENT_EPSILON) {
            waistSquared = xx;
            focusDistance = 0.0;
        } else {
            double zFromWaist = xt / tt;
            waistSquared = xx - xt * xt / tt;
            focusDistance = -zFromWaist;
        }

        radius = Math.min(MAX_REASONABLE_RADIUS, Math.max(MIN_RADIUS, radius));
        double waistRadius = Math.min(radius, Math.max(MIN_RADIUS, Math.sqrt(Math.max(0.0, waistSquared))));
        double divergence = Math.sqrt(tt);
        BeamModel model = modelFor(focusDistance, divergence);

        return new BeamEnvelope(
                model,
                radius,
                waistRadius,
                divergence,
                focusDistance,
                Math.max(1.0, beamQuality),
                clamp01(apertureFill),
                clamp01(scatter),
                template.modeM(),
                template.modeN()
        );
    }

    private static BeamModel modelFor(double focusDistance, double divergence) {
        if (divergence <= MOMENT_EPSILON) {
            return BeamModel.COLLIMATED;
        }

        if (focusDistance < -FOCUS_EPSILON) {
            return BeamModel.DIVERGING;
        }

        return BeamModel.FOCUSED;
    }

    private record BeamMoments(double xx, double xt, double tt) {
        private BeamMoments {
            if (!Double.isFinite(xx) || !Double.isFinite(xt) || !Double.isFinite(tt) || xx < 0.0 || tt < 0.0) {
                throw new IllegalArgumentException("Beam moments must be finite and non-negative on the diagonal");
            }
        }

        private BeamMoments propagate(double distance) {
            double outputXx = xx + 2.0 * distance * xt + distance * distance * tt;
            double outputXt = xt + distance * tt;
            return new BeamMoments(Math.max(0.0, outputXx), outputXt, tt);
        }

        private BeamMoments thinLens(double focalLength) {
            double outputXt = xt - xx / focalLength;
            double outputTt = tt - 2.0 * xt / focalLength + xx / (focalLength * focalLength);
            return new BeamMoments(xx, outputXt, Math.max(0.0, outputTt));
        }

        private BeamMoments withAngularNoise(double angularNoise) {
            if (!Double.isFinite(angularNoise) || angularNoise <= 0.0) {
                return this;
            }

            return new BeamMoments(xx, xt, tt + angularNoise * angularNoise);
        }
    }

    private BeamGeometryOps() {
    }
}
