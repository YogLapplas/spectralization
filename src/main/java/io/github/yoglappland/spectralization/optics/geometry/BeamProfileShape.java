package io.github.yoglappland.spectralization.optics.geometry;

import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import io.github.yoglappland.spectralization.optics.BeamModel;
import java.util.Objects;

public record BeamProfileShape(
        double r2,
        double rTheta,
        double theta2,
        double quality,
        double scatter,
        int modeM,
        int modeN,
        boolean guided
) {
    private static final double MOMENT_EPSILON = 1.0E-12D;
    private static final double FOCUS_EPSILON = 1.0E-6D;
    private static final double MAX_REASONABLE_RADIUS = 16.0D;
    private static final double DIFFRACTION_DIVERGENCE_SCALE = 0.0025D;
    private static final double LENS_ANGULAR_NOISE_SCALE = BeamGeometryOps.MIN_RADIUS / 8.0D;

    public BeamProfileShape {
        if (!Double.isFinite(r2) || r2 < 0.0D) {
            throw new IllegalArgumentException("Profile radius moment must be finite and non-negative");
        }

        if (!Double.isFinite(rTheta)) {
            throw new IllegalArgumentException("Profile radius-angle moment must be finite");
        }

        if (!Double.isFinite(theta2) || theta2 < 0.0D) {
            throw new IllegalArgumentException("Profile angular moment must be finite and non-negative");
        }

        if (!Double.isFinite(quality) || quality < 1.0D) {
            throw new IllegalArgumentException("Profile quality must be finite and at least 1");
        }

        if (!Double.isFinite(scatter) || scatter < 0.0D || scatter > 1.0D) {
            throw new IllegalArgumentException("Profile scatter must be finite and between 0 and 1");
        }

        modeM = Math.max(0, modeM);
        modeN = Math.max(0, modeN);

        double maxAbsCorrelation = Math.sqrt(Math.max(0.0D, r2 * theta2));
        if (maxAbsCorrelation <= MOMENT_EPSILON) {
            rTheta = 0.0D;
        } else {
            rTheta = Math.max(-maxAbsCorrelation, Math.min(maxAbsCorrelation, rTheta));
        }
    }

    public static BeamProfileShape fromEnvelope(BeamEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");

        double radius = Math.max(BeamGeometryOps.MIN_RADIUS, Math.min(MAX_REASONABLE_RADIUS, envelope.radius()));
        double waistRadius = Math.min(radius, Math.max(BeamGeometryOps.MIN_RADIUS, envelope.waistRadius()));
        double divergence = Math.max(0.0D, envelope.divergence());

        if (divergence <= MOMENT_EPSILON && Math.abs(envelope.focusDistance()) > FOCUS_EPSILON && radius > waistRadius) {
            divergence = Math.sqrt(Math.max(0.0D, radius * radius - waistRadius * waistRadius))
                    / Math.abs(envelope.focusDistance());
        }

        double zFromWaist = -envelope.focusDistance();

        if (divergence <= MOMENT_EPSILON) {
            zFromWaist = 0.0D;
        } else {
            double maxAbsZ = Math.sqrt(Math.max(0.0D, radius * radius - waistRadius * waistRadius)) / divergence;
            zFromWaist = Math.copySign(Math.min(Math.abs(zFromWaist), maxAbsZ), zFromWaist);
        }

        double theta2 = divergence * divergence;
        return new BeamProfileShape(
                radius * radius,
                theta2 * zFromWaist,
                theta2,
                envelope.beamQuality(),
                envelope.scatter(),
                envelope.modeM(),
                envelope.modeN(),
                false
        );
    }

    public static BeamProfileShape collimated(double radius) {
        double clampedRadius = Math.max(BeamGeometryOps.MIN_RADIUS, Math.min(MAX_REASONABLE_RADIUS, radius));
        return new BeamProfileShape(
                clampedRadius * clampedRadius,
                0.0D,
                0.0D,
                BeamEnvelope.IDEAL_BEAM_QUALITY,
                BeamEnvelope.DEFAULT_SCATTER,
                0,
                0,
                false
        );
    }

    public BeamProfileKey toKey() {
        return BeamProfileKey.fromShape(this);
    }

    public BeamEnvelope toEnvelope() {
        double radius = Math.sqrt(Math.max(0.0D, r2));
        double divergence = Math.sqrt(Math.max(0.0D, theta2));
        double waistSquared;
        double focusDistance;

        if (theta2 <= MOMENT_EPSILON) {
            waistSquared = r2;
            focusDistance = 0.0D;
        } else {
            double zFromWaist = rTheta / theta2;
            waistSquared = r2 - rTheta * rTheta / theta2;
            focusDistance = -zFromWaist;
        }

        radius = Math.min(MAX_REASONABLE_RADIUS, Math.max(BeamGeometryOps.MIN_RADIUS, radius));
        double waistRadius = Math.min(radius, Math.max(BeamGeometryOps.MIN_RADIUS, Math.sqrt(Math.max(0.0D, waistSquared))));
        BeamModel model = modelFor(focusDistance, divergence);

        return new BeamEnvelope(
                model,
                radius,
                waistRadius,
                divergence,
                focusDistance,
                Math.max(1.0D, quality),
                BeamEnvelope.DEFAULT_APERTURE_FILL,
                BeamGeometryOps.clamp01(scatter),
                modeM,
                modeN
        );
    }

    public BeamProfileShape propagate(double distance) {
        if (!Double.isFinite(distance) || distance < 0.0D) {
            throw new IllegalArgumentException("Profile propagation distance must be finite and non-negative");
        }

        if (distance == 0.0D) {
            return this;
        }

        double effectiveTheta2 = effectiveTheta2ForPropagation();
        BeamProfileShape propagated = PhaseSpaceMap.freeSpace(distance).apply(new BeamProfileShape(
                r2,
                rTheta,
                effectiveTheta2,
                quality,
                scatter,
                modeM,
                modeN,
                guided
        ));
        double outputScatter = BeamGeometryOps.clamp01(scatter + Math.sqrt(Math.max(0.0D, effectiveTheta2)) * distance * 0.01D);
        return new BeamProfileShape(
                propagated.r2(),
                propagated.rTheta(),
                propagated.theta2(),
                quality,
                outputScatter,
                modeM,
                modeN,
                guided
        );
    }

    public BeamProfileTransfer thinLens(double focalLength, double apertureRadius, double qualityMultiplier) {
        if (!Double.isFinite(focalLength) || focalLength <= 0.0D) {
            throw new IllegalArgumentException("Lens focal length must be finite and positive");
        }

        if (!Double.isFinite(apertureRadius) || apertureRadius <= 0.0D) {
            throw new IllegalArgumentException("Lens aperture radius must be finite and positive");
        }

        if (!Double.isFinite(qualityMultiplier) || qualityMultiplier < 1.0D) {
            throw new IllegalArgumentException("Lens quality multiplier must be finite and at least 1");
        }

        double apertureR2 = apertureRadius * apertureRadius;
        double clippedR2 = Math.min(r2, apertureR2);
        double apertureGain = r2 <= apertureR2 || r2 <= MOMENT_EPSILON ? 1.0D : apertureR2 / r2;
        double momentScale = r2 <= MOMENT_EPSILON ? 1.0D : clippedR2 / r2;
        double clippedRTheta = rTheta * Math.sqrt(Math.max(0.0D, momentScale));
        double rawApertureFill = Math.sqrt(Math.max(0.0D, r2)) / apertureRadius;
        double overfill = Math.max(0.0D, rawApertureFill - 1.0D);
        double outputQuality = Math.max(1.0D, quality * qualityMultiplier * (1.0D + overfill * 0.25D));
        double outputScatter = BeamGeometryOps.clamp01(scatter + (qualityMultiplier - 1.0D) * 0.04D + overfill * 0.12D);
        double angularNoise = lensAngularNoise(apertureRadius, qualityMultiplier, scatter, overfill);
        BeamProfileShape transformed = PhaseSpaceMap.thinLens(focalLength).apply(new BeamProfileShape(
                clippedR2,
                clippedRTheta,
                theta2,
                outputQuality,
                outputScatter,
                modeM,
                modeN,
                false
        ));
        BeamProfileShape output = new BeamProfileShape(
                transformed.r2(),
                transformed.rTheta(),
                transformed.theta2() + angularNoise * angularNoise,
                outputQuality,
                outputScatter,
                modeM,
                modeN,
                false
        );
        return BeamProfileTransfer.of(output.toKey(), apertureGain);
    }

    public BeamProfileTransfer apertureClip(double apertureRadius) {
        if (!Double.isFinite(apertureRadius) || apertureRadius <= 0.0D) {
            throw new IllegalArgumentException("Aperture radius must be finite and positive");
        }

        double apertureR2 = apertureRadius * apertureRadius;
        double clippedR2 = Math.min(r2, apertureR2);
        double apertureGain = r2 <= apertureR2 || r2 <= MOMENT_EPSILON ? 1.0D : apertureR2 / r2;
        double momentScale = r2 <= MOMENT_EPSILON ? 1.0D : clippedR2 / r2;
        double clippedRTheta = rTheta * Math.sqrt(Math.max(0.0D, momentScale));
        double rawApertureFill = Math.sqrt(Math.max(0.0D, r2)) / apertureRadius;
        double overfill = Math.max(0.0D, rawApertureFill - 1.0D);
        BeamProfileShape output = new BeamProfileShape(
                clippedR2,
                clippedRTheta,
                theta2,
                Math.max(1.0D, quality * (1.0D + overfill * 0.12D)),
                BeamGeometryOps.clamp01(scatter + overfill * 0.06D),
                modeM,
                modeN,
                false
        );
        return BeamProfileTransfer.of(output.toKey(), apertureGain);
    }

    public BeamProfileShape guidedOutput(double coreRadius, double acceptance) {
        double radius = Math.max(BeamGeometryOps.MIN_RADIUS, coreRadius);
        double angular = Math.max(0.0D, acceptance);
        return new BeamProfileShape(
                radius * radius,
                0.0D,
                angular * angular,
                quality,
                scatter,
                0,
                0,
                true
        );
    }

    private static double lensAngularNoise(
            double apertureRadius,
            double qualityMultiplier,
            double inputScatter,
            double overfill
    ) {
        double apertureFactor = 1.0D / Math.max(0.25D, apertureRadius);
        double scatterFactor = 1.0D + BeamGeometryOps.clamp01(inputScatter) * 2.0D;
        double overfillFactor = 1.0D + Math.min(4.0D, overfill) * 0.5D;
        return LENS_ANGULAR_NOISE_SCALE * qualityMultiplier * apertureFactor * scatterFactor * overfillFactor;
    }

    private double effectiveTheta2ForPropagation() {
        double waistSquared;

        if (theta2 <= MOMENT_EPSILON) {
            waistSquared = r2;
        } else {
            waistSquared = r2 - rTheta * rTheta / theta2;
        }

        double waistRadius = Math.max(
                BeamGeometryOps.MIN_RADIUS,
                Math.sqrt(Math.max(0.0D, waistSquared))
        );
        double scatterFactor = 1.0D + BeamGeometryOps.clamp01(scatter) * 3.0D;
        double divergenceFloor = DIFFRACTION_DIVERGENCE_SCALE
                * Math.max(1.0D, quality)
                * scatterFactor
                / waistRadius;
        return Math.max(theta2, divergenceFloor * divergenceFloor);
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
}
