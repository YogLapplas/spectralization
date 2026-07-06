# Spectralization 架构不变量账本

本文档记录项目必须长期维持的全局不变量。它不是玩家手册，也不是普通开发规范；它的用途是把“我们以为大家都记得”的架构公理写成可审查、可测试、可逐步机器化的清单。

完整形式化证明暂时不是目标。当前目标是先建立轻量守门人：每条核心公理都应该至少有一个文档位置、一个代码所有者，以及在可行时接入 `gradlew check` 的自动校验。

## 1. 权威路径唯一

**公理**

玩法结果只来自权威光学路径：

```text
world -> port graph -> material gain / saturation rewrite -> power solve -> readout
```

reference、debug oracle、legacy compare 和小图 validator 可以记录、对照、失败测试，但不能驱动机器、HUD、烧毁、最终传感器读数或缓存 authority。

**当前保护**

- 文档：`README.md`、`docs/MATH_AND_ARCHITECTURE.md`、`docs/OPTICAL_SOLVER_ARCHITECTURE.md`
- 代码：`CachedOpticalSystem.usableForGameplay`
- Gradle 任务：`verifyOpticalAuthority`

**自动校验内容**

- `ReceiverOutput.apply` 只能由 cache authority wrapper 调用。
- `CachedOpticalTrace` 不能暴露直接写 readout 的 apply helper。
- direct trace 只有在没有 system authority 且自身 solution reliable 时才能作为 reliable readout。
- system readout 必须同时满足 `usableForGameplay` 与 `solution.reliableForReadout()`。
- legacy tracer、trace validator、debug oracle 只能出现在显式白名单路径。
- 日志里的 authority marker 必须保留 `gameplay`、`reference`、`debug_oracle`、`legacy_compare` 四类。

**缺口**

当前主要依靠 boolean 和调用约定。未来应引入类型级边界，例如 `GameplayOpticalSolution` 与 `ReferenceOpticalSolution`，让 debug/reference 结果不能被误传进 gameplay apply path。

## 2. 光谱本体必须单源化

**公理**

`FrequencyKey` 表示某个 `SpectralRegion` 内的合法 bin。bin 的范围、区域顺序、可见光颜色 LUT、JEI 图表背景、光谱仪、spot/beam 颜色都必须共享同一套语义。

可见光 bin 的方向是：

```text
infrared -> red -> orange -> yellow -> green -> cyan -> blue -> violet -> ultraviolet
```

红外不能接到紫色端，紫外不能接到红色端。

**当前保护**

- `FrequencyKey` 构造时拒绝负数和越界 bin。
- `SpectralColorMap.VISIBLE_BINS` 来自 `SpectralRegion.VISIBLE.defaultBins()`。
- `SpectralParameterChart` 使用各 region 自己的 `defaultBins()` 采样。
- Gradle 任务：`verifySpectralOntology`

**自动校验内容**

- `SpectralRegion` 顺序不被无意改变。
- region id 唯一，bin 数为正。
- `FrequencyKey` 必须检查 `bin < region.defaultBins()`。
- 可见光命名 bin 必须按红到紫递增。
- `VISIBLE_SRGB` 长度必须等于可见光 bin 数。
- 红、绿、蓝、紫和两端非可见光的定性颜色不能接反。
- JEI 光谱图不能使用固定的每区 bin 数。
- 材料增益 LUT 的内部采样数不能超过 IR/V/UV 的 region bin 数。

## 3. 被动系统不生功率

**公理**

没有主动增益或显式光源时，系统不能凭空增加总功率。

```text
single input split: sum(outputs) <= input
multi input merge: output = linear sum - losses
passive feedback: no generated power
```

**当前保护**

主要依赖设计文档、手动 opticaltest、日志检查，以及 Gradle 任务：`verifyPassiveOptics`。

**自动校验内容**

- `opticaltest` 必须保留 splitter、lens aperture、fiber coupling、parallel fiber、passive feedback 和 beam expander 的固定示例。
- 被动 coherent 示例的 profiler 输出不能超过输入源功率。
- coherent power 必须等于 total power，stray power 必须为 0。
- passive feedback 示例必须使用 `PROFILE_COLLAPSED_EXACT`，不能通过 fallback 或 overflow 伪装成功。
- 无反馈几何示例必须保持 `PROFILE_STATE_EXACT`。
- 并联光纤合流只能做线性叠加并受输入源功率约束，不能凭空变强。
- collapsed feedback 中的 profile gain 必须 clamp 到 `[0, 1]`，负的 passive gain 必须在 solver 入口被丢弃。

**缺口**

当前自动化仍是源代码守门，保证世界内 opticaltest 的公理没有被删掉或放松。后续应把这些固定图做成真正可离线运行的世界回归，而不是只检查命令验证器本身。

## 4. Profile 多等价规则

**公理**

无反馈图可以保留有限 profile state；反馈 SCC 中不能无限展开 profile。几何敏感损耗必须折叠为一次等效 edge gain。

```text
no feedback: PROFILE_STATE_EXACT
feedback:    PROFILE_COLLAPSED_EXACT
```

**当前保护**

`ProfileLanePowerSolver` 和 opticaltest 覆盖了部分场景。

**缺口**

需要把 profile mode、fallback、overflow、readout reliability 做成自动回归，而不是只靠游戏内命令和日志阅读。

## 5. 饱和是材料边界，不是全局调度

**公理**

固体增益介质的非线性来自材料局部饱和和烧毁边界，不来自旧的 rho 稳定调度，也不是最终输出 clamp。

局部边的语义是：

```text
passive edge: O = I * P
active edge:  O = min(I * P * G, I * P + P * S * (1 - 1 / G))
```

其中 `S` 是材料饱和功率，不是系统预算。

**当前保护**

`MaterialGainScheduler`、`GainGraphRewriter`、`SaturatingEdgeGain`、`ProfileLanePowerSolver`、`GainMediumOverloadMonitor`。

**缺口**

需要固定 SCC 样例验证：饱和反馈腔输出必须有限，不能因为“不稳定”变成 0，也不能跳过烧毁判断。

## 6. Readout 是导出层

**公理**

传感器、机器、HUD、spot、beam overlay 和 JEI 展示只读 compiled solution。它们可以影响 UI、配方进度、热量或玩家反馈，但不能把自己的读数写回光学网络。

**当前保护**

文档和代码分层基本存在。

**缺口**

未来应增加静态扫描：readout 包、client screen、HUD、receiver output 不允许调用会改变 port graph、source graph 或 gameplay optical solution 的入口。

## 当前落地状态

| 不变量 | 自动化状态 | 当前入口 |
| --- | --- | --- |
| 光谱本体单源化 | 已接入 `check` | `verifySpectralOntology` |
| 权威路径唯一 | 已接入 `check` 的静态边界扫描；待类型化 | `verifyOpticalAuthority` |
| 被动系统不生功率 | 已接入 `check` 的 opticaltest 契约扫描；待离线固定图回归 | `verifyPassiveOptics` |
| Profile 多等价 | 待固定图回归 | `ProfileLanePowerSolver` |
| 材料饱和有限输出 | 待固定图回归 | `SaturatingEdgeGain` |
| Readout 只导出 | 待静态扫描 | readout/compiler 边界 |
