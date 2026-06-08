package io.github.yoglappland.spectralization.optics.compiler.gain;

import io.github.yoglappland.spectralization.optics.compiler.CompiledPortGraph;
import net.minecraft.world.level.Level;

public interface GainScheduler {
    GainSchedule schedule(Level level, CompiledPortGraph graph);
}
