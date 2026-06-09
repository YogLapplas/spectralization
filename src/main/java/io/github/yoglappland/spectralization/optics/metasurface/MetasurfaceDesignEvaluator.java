package io.github.yoglappland.spectralization.optics.metasurface;

public interface MetasurfaceDesignEvaluator {
    MetasurfaceEnvelope envelopeFor(MaterialBudget budget);

    default MetasurfaceDesignResult evaluate(MetasurfaceTarget target, MaterialBudget budget) {
        MetasurfaceEnvelope envelope = envelopeFor(budget);
        return new MetasurfaceDesignResult(target, budget, envelope, envelope.contains(target));
    }
}
