package io.github.yoglappland.spectralization.optics.compiler;

public enum ScalarSolverKind {
    NONE,
    TOPOLOGICAL_DAG,
    FEEDBACK_SCC_EXACT,
    FEEDBACK_CHORD,
    MIXED_REGION,
    ITERATIVE_FIXED_POINT,
    WEIGHTED_BFS_ATTENTION,
    MAGNITUDE_BUCKET,
    RESIDUAL_CORRECTION,
    LOOP_MACRO,
    SYMMETRY_REDUCTION,
    DEBUG_ORACLE
}
