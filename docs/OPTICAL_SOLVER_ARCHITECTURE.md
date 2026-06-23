# Spectralization Optical Solver Architecture

本文档记录当前光学求解架构的数学模型、程序边界和 review 检查点。它面向未来维护者和 peer review，不是玩家手册。

本文档以 2026-06-23 的实现为准，重点解释从 profile-state 求解转向“多等效”求解后的规则。

## 1. 目标

Spectralization 的光学系统必须满足四个工程目标：

1. 玩家能用直觉理解光路中的功率分配。
2. 反馈结构不能因为 profile 状态展开而卡死或清零。
3. 机器、传感器、HUD、spot、日志都读取同一套 compiled solution。
4. 求解失败时必须能在日志里看到失败发生在拓扑、功率、profile 还是 readout 层。

因此，当前核心策略是：

```text
world blocks
-> port graph
-> gain scheduling
-> finite linear solve
-> readout / visualization / machine output
```

求解期不追踪真实连续光场，也不通过每 tick tracer 迭代得到结果。

## 2. 核心哲学

### 2.0 Authority 分层

光学代码分三类路径：

```text
A. authoritative runtime path
   真正决定机器、HUD、过载、读数的主路径。

B. reference / oracle path
   用来校验主路径，例如 example validator、profile-state exact 小图验证、legacy compare。

C. migration residue
   迁移期兼容旧语义的残留路径。它可以暂时存在，但不能获得 gameplay authority。
```

主路径只有一条：

```text
world
-> port graph
-> effective edge gains
-> power solve
-> readout
```

机器处理、传感器输出、光纤过载和 HUD 主结果只能读取 `authority=gameplay` 的结果。
reference / oracle 可以写日志、做命令验证、提示 mismatch 或 fail test，但不能改变机器输出、
缓存 authority、过载烧毁判断或玩家看到的最终读数。

日志字段必须显式写出：

```text
authority=gameplay
authority=reference
authority=debug_oracle
authority=legacy_compare
```

这样保留复杂校对路径时，reviewer 仍能判断哪条路径拥有玩法权威。

### 2.1 无迭代

主线光学网络的默认语义是线性系统：

```text
x = T x + s
```

其中：

- `x` 是 port 上的功率状态。
- `T` 是由传播、分束、反射、透镜、光纤、微缩机器边界等局部 transfer 拼出的矩阵。
- `s` 是光源注入。

程序可以用线性代数方法解这个系统。即使未来用 GMRES 或其他迭代线性代数库，那也是线性方程数值实现，不是玩法语义上的反馈迭代。当前实现优先使用精确 SCC/稀疏线性求解。

### 2.2 多等效

新的关键规则是“多等效”：

```text
如果一个局部非线性过程已经被抽象成等效损耗，
那么它在全局线性系统中只作为一次常数 gain 出现。
不要再展开等效损耗内部的等效过程。
```

直觉类比：

```text
C + C' 仍然记作 C
```

同理：

```text
fiber acceptance loss
aperture clipping loss
coupling mismatch loss
```

都应该在编译边时写成一次 `edge gain <= 1`。进入主线线性系统后，不再因为光束绕反馈环多次经过同一个面而重新求一个非线性固定点。

这不是忽略物理，而是明确选择游戏中的等效介质模型。

### 2.3 输出端非线性

机器效率只作为输出端读数使用，永远不作为中间 transfer：

```text
solved optical power
-> machine reads envelope / spot / frequency / coherence
-> recipe speed, heat, progress, warning UI
```

它不会把效率结果再注入光学网络，所以不会破坏线性系统。

## 3. Profile 的新边界

旧思路是把 profile 放进主状态：

```text
x[port, frequency, coherence, profileKey]
```

这个思路在无反馈路径上很自然，但在反馈环里会失控。光每绕一次透镜和自由传播，`profileKey` 都可能变化：

```text
profile_0 -> profile_1 -> profile_2 -> ...
```

于是一个很小的拓扑反馈结构会变成大量 profile state，甚至溢出。

当前规则改为：

```text
无反馈图:
  可以使用 PROFILE_STATE_EXACT，保留 profile-state 精确传播。

反馈图:
  使用 PROFILE_COLLAPSED_EXACT。
  profile-sensitive loss 必须已经写进边 gain。
  主求解只追踪有限的功率 lane。
```

也就是说，`ProfileKey` 不再默认参与反馈求解状态空间。

## 4. 当前求解器语义

### 4.1 PROFILE_STATE_EXACT

适用场景：

- DAG 或无反馈结构。
- 需要沿路径传播 profile，并且不会形成无限 profile state。

语义：

```text
state = port + frequency + coherence + profileKey
edge transforms profileKey and gain
solve finite state graph exactly
```

用途：

- 直线路径。
- 普通非反馈透镜链。
- profile readout。
- 未来无反馈的局部高精度验证。

### 4.2 PROFILE_COLLAPSED_EXACT

适用场景：

- 任何包含 feedback SCC 的 graph。
- profile-state 构建失败或溢出的 fallback。

语义：

```text
state = port + frequency + coherence + representative profile
transition gain = edge sample gain * equivalent profile-sensitive loss
profile itself is not repeatedly transformed around the feedback loop
```

这个 solver 仍然是精确线性求解，不是迭代近似。

它保留 `powerByLane`，让 beam profiler、machine readout 和 envelope layer 还能读取频率、相干性和代表性 profile。

注意这里的 “edge gain” 不是只指 `PortGraphEdge.sampleGain`。在 collapsed feedback 中，矩阵边权必须额外乘一次局部 profile 等效损耗：

```text
effectiveGain(edge, lane)
  = edge.sampleGainFor(lane.frequency)
  * equivalentProfileGain(edge, lane.profile)
```

`equivalentProfileGain` 只读代表性 profile，返回 `gain <= 1`。它可以捕获光纤入口接受率、透镜孔径裁剪、耦合失配等 radius-based loss，但不能把输出 profile 再塞回反馈状态。

如果无反馈图因为 profile state 数量过大或 exact profile solve 失败而降级到 collapsed solver，日志必须显式标记：

```text
profile_mode=collapsed_due_to_overflow
profile_overflow=true
```

或：

```text
profile_mode=collapsed_fallback
profile_fallback=true
```

这种结果仍可用于游戏读数，但调试时应按降级结果处理。

### 4.3 NONE / scalar

`NONE` 表示没有有效 source 或 graph 为空。其他 scalar solver kind 表示旧标量计划仍可参与规划和日志，但当前光学主路径会优先通过 `ProfileLanePowerSolver` 输出带 lane 的 solution。

## 5. 程序入口

主要代码位置：

```text
src/main/java/io/github/yoglappland/spectralization/optics/compiler/ProfileLanePowerSolver.java
src/main/java/io/github/yoglappland/spectralization/optics/compiler/ScalarSolverKind.java
src/main/java/io/github/yoglappland/spectralization/optics/compiler/OpticalCompilerDebugLogger.java
src/main/java/io/github/yoglappland/spectralization/command/OpticalExampleValidator.java
```

当前关键分支：

```java
if (graph.feedbackSccCount() > 0) {
    return solveProfileCollapsedExact(graph, sources, solverPlan);
}
```

这个分支是模型规则，不是临时性能优化。

## 6. 光纤和孔径

光纤接受率和孔径裁剪是最容易把系统拖回非线性固定点的问题。当前规则：

```text
fiber acceptance = LUT(profileBucket, fiberParams)
aperture loss    = LUT(profileBucket, apertureParams)
```

它们在编译 edge 时计算一次，写入：

```text
edge.sampleGain
edge.sampleGainByFrequency
```

或者在 collapsed solver 组矩阵时作为 `equivalentProfileGain(edge, lane.profile)` 乘入。无论在哪一层完成，进入线性方程的最终边权必须已经包含该损耗。

当前光纤的责任划分：

```text
remoteOutputPorts:
  route topology + route transmission + output split only

profileTransferForEdge:
  radius acceptance + angular acceptance + guided output profile

matrix edge:
  route/split gain * profile transfer gain
```

不要把光纤入口 acceptance 同时写入 `remoteOutputPorts` 和 `profileTransferForEdge`，否则主求解、过载和日志会重复计算。

对玩家表达为：

```text
光束太宽，耦合效率下降。
光束太散，耦合效率下降。
孔径太小，裁剪损耗上升。
```

不向玩家暴露真实数值孔径或波导模场求解。

## 7. 透镜

透镜在无反馈图中可以继续改变 profile：

```text
free propagation -> profile transform
thin lens        -> profile transform
```

在反馈图中，透镜的 profile-sensitive 后果不再无限展开。它对主功率系统的影响必须已经表现为等效 transfer gain。

这意味着：

```text
透镜仍然影响显示层 envelope。
透镜仍然可影响局部等效损耗 LUT。
透镜不在反馈主求解中制造无限个 profileKey。
```

## 8. 传感器和机器

传感器、机器、HUD、spot 都是 readout：

```text
compiled solution
-> readout layer
-> receiver output
```

它们可以读取：

- total power
- coherent power
- stray power
- frequency distribution
- representative envelope
- solver reliability
- profile mode

它们不能把读数结果反向写回光学网络。

## 9. 日志字段

`logs/spectralization/optical_compiler_*.log` 中和本模型相关的字段：

```text
solver=PROFILE_STATE_EXACT
solver=PROFILE_COLLAPSED_EXACT
profile_mode=state_exact
profile_mode=collapsed_equivalence
readout_reliable=true/false
feedback_sccs=N
beta1=N
lanes=N
residual=N
```

判断方式：

```text
feedback_sccs=0 + profile_mode=state_exact
  正常无反馈 profile-state 求解。

feedback_sccs>0 + profile_mode=collapsed_equivalence
  正常反馈多等效求解。

readout_reliable=false
  不要相信机器/传感器的新读数。

residual > epsilon
  线性系统或输入 graph 有问题。
```

## 10. 游戏内验证命令

当前命令：

```text
/spectralization opticaltest splitter_lens_splitter
/spectralization opticaltest lens_aperture_clip
/spectralization opticaltest fiber_radius_coupling
/spectralization opticaltest feedback_fiber_radius_loss
/spectralization opticaltest parallel_fiber_same_endpoint
```

它会生成：

```text
creative light source
-> beam splitter
-> lens holder
-> beam splitter
-> beam profiler
```

该结构故意包含反馈：

```text
graph=14 nodes / 17 edges
feedback_scc=1
beta1=3
```

通过标准：

```text
Optical test splitter_lens_splitter PASS
profiler == expected
solver == PROFILE_COLLAPSED_EXACT
profile_mode == collapsed_equivalence
readout_reliable == true
```

截至 2026-06-23 的本地验证读数：

```text
profiler=0.303716673
expected=0.303716673
```

该命令还会写入 `stage=example_validation`，用于 review 和回归定位。

新增的 profile 回归重点：

```text
lens_aperture_clip:
  宽光束通过标准透镜时，功率应乘 aperture² / radius² 近似裁剪因子。

fiber_radius_coupling:
  radius=0.125 和 radius=0.25 进入基础光纤时，宽光束输出应接近窄光束的 0.25。

feedback_fiber_radius_loss:
  光纤在反馈 SCC 内时，宽光束输出必须显著低于窄光束，且 solver 应为 collapsed。

parallel_fiber_same_endpoint:
  同端点并联光纤不能比单条更差，也不能超过 1.0 总 gain。
```

## 11. Review 检查点

review 光学求解改动时，优先检查：

1. 是否把新的非线性过程写进主 feedback solve。
2. 是否让机器输出效率反向污染光学网络。
3. 是否在反馈图里重新展开 profileKey。
4. 是否有 `gain > 1` 的被动边。
5. 是否在 readout 不可靠时继续显示新数值。
6. 是否有日志能说明 solver kind、profile mode、residual 和 readout reliability。
7. 是否更新了 `opticaltest` 或新增了等价验证命令。

## 12. 已知限制

当前模型有意不做以下内容：

- 真实相位和干涉条纹。
- 完整连续光场采样。
- 光纤真实数值孔径系统。
- feedback 内部的 profile 非线性固定点求解。
- 机器效率对光学网络的反向作用。

这些限制是设计边界，不是 bug。

未来如果需要更高精度，应优先扩展局部 LUT 和 readout 层，而不是把主求解器重新变成 profile-state feedback 展开。
