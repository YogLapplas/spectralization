# Spectralization 数学模型与架构

本文档是架构解释和技术参考。读者是未来开发者、调试人员和需要理解系统边界的整合包作者。它不替代玩家手册，也不描述尚未实现的玩法为已完成内容。

核心结论：Spectralization 的光学系统是运行在 Minecraft 世界里的事件驱动 port graph 编译器。方块提供局部端口、局部传输关系和数据 profile；世界事件改变几何层、拓扑层或数据层；编译器生成求解计划；求解结果再导出给传感器、机器、HUD、光束和 spot。

## 1. 为什么这样设计

这个模组需要让玩家搭建光学机器，但它仍然是游戏，不是科研仿真软件。系统必须满足四个目标：

1. 玩家能通过直觉理解大部分行为。
2. 大型网络不会因为每 tick 追光而卡死。
3. 新功能能接入同一套读数、缓存、可视化和日志系统。
4. 出现 bug 时，开发者能定位是哪一层先错了。

因此，Spectralization 不把光学主逻辑写成递归 tracer。Tracer 只保留为 debug oracle、迁移期对照和小网络 fallback。新功能的默认路径是：

```text
world event
-> geometry / topology / data dirty
-> dependency index
-> port graph compile
-> solver plan
-> data solve
-> readout and visualization export
```

运行时 authority 必须保持单一：

```text
world -> port graph -> effective edge gains -> power solve -> readout
```

这条路径是 `authority=gameplay`。legacy trace、profile-state exact 小图校验、debug
oracle 和 example validator 可以继续存在，但它们属于 `authority=reference`、
`authority=debug_oracle` 或 `authority=legacy_compare`，只能记录 mismatch、辅助
调试或 fail test，不能反向改变机器、HUD、过载和最终读数。

## 2. 三层世界模型

### 2.1 几何层

几何层回答“世界里有什么”。它只关心位置、加载状态和候选节点集合。

常见成员：

- 光源。
- 光学元件。
- 传感器。
- 可涂层表面。
- 场源。
- 多方块结构。
- 光纤节点。
- 微缩机器框架。

会改变几何层的事件：

- 方块放置和破坏。
- 区块加载和卸载。
- 活塞或其他移动事件。
- 多方块结构认证结果变化。
- 光纤或微缩机器清空工作区。

几何层变化优先级最高。它可以中断正在进行的数据层求解，因为旧导出可能已经对应错误的世界结构。

### 2.2 拓扑层

拓扑层回答“端口如何连接”。它不是方块网格，而是 port graph。

一个方块不一定是一个节点。更自然的表示是：

- 面可以是节点。
- 轴可以是节点。
- 一个方块可以展开为多个端口。
- 方块状态决定端口之间的 transfer。

拓扑层包含：

- 方向边。
- 连通分量。
- 反馈 SCC。
- chord / cycle 结构。
- 系统边界。
- 微缩后的 macro node。

会改变拓扑层的事件：

- 镜子或分光镜改变状态。
- 透镜架数据改变到足以影响端口关系。
- 涂层让一个表面变成光学面。
- 光纤连接或剪断。
- 微缩机器旋转或 IO 面映射变化。
- 多方块结构进入或离开 error 状态。

如果某个变化只改变材料参数、光源强度或读数配置，它应该停留在数据层，不应该重建拓扑。

### 2.3 数据层

数据层回答“沿着这些端口传递什么”。它包含所有不必改变拓扑的数值。

常见数据：

- 光源 profile。
- 材料 TRA。
- 涂层响应。
- 频率分量。
- 相干 / 散杂通道。
- 功率。
- 光束几何 envelope。
- 泵浦速率和增益参数。
- 场源修正。
- 读数可靠性。
- 缓存结果。

数据层可以慢。只要 dirty 和中断及时，数据层不需要每个世界事件都同步算完。

## 3. Compiler First 规则

新增光学功能前，必须先回答五个问题：

```text
1. 几何层：它在世界中占据哪些位置、表面、节点或多方块成员？
2. 拓扑层：它暴露哪些端口、方向边、潜在连接或系统边界？
3. 数据层：它提供或消费哪些 profile、读数、缓存或状态？
4. 事件边界：哪些事件会让 geometry / topology / source / parameter / field dirty？
5. 可靠性：什么时候读数可靠，什么时候进入 unreliable 或 error？
```

然后再写代码。

开发边界：

- 光源是事件驱动 source，不负责每 tick 重算光路。
- 传感器、光束、spot、伤害和机器读数都是导出层。
- 材料、涂层、场源、增益介质和频谱参数先进入数据接口，再由编译器消费。
- 旧 tracer 不能作为新功能的主逻辑。

## 4. 功率模型

常规功率层使用线性系统：

```text
x = T x + s
y = C x
```

变量含义：

- `x`：端口、频率和相干性上的功率未知量；在无反馈精确 profile 求解中也可以包含有限的空间 profile state。
- `T`：由局部元件拼成的传输算子。
- `s`：光源注入。
- `y`：传感器、机器、HUD 和可视化读数。
- `C`：从内部状态到读数的导出算子。

不变量：

- 被动网络不能凭空增加总功率。
- 多输入合流时，输出功率来自输入线性叠加。
- 单输入分流时，输出总功率不能超过输入总功率。
- 光纤混合后的输出总功率必须等于输入总功率，除非发生损耗或烧毁。
- 读数不可靠时，系统应标记 unreliable，而不是输出看似精确的新值。

这个模型服务玩法，而不是追求完整物理。它让玩家能预测“光会从哪里来、往哪里走、强度大概怎么分配”，也让求解器可以缓存和分区。

## 5. Profile 分层

不是所有物理量都进入同一个矩阵。当前主线 profile：

| Profile | 状态 | 作用 |
| --- | --- | --- |
| 频率 `FrequencyKey` | 已接入 | 区分可见光、非可见光和 bin。 |
| 功率 | 已接入 | 常规工业光路的主求解量。 |
| 相干性 `CoherenceKind` | 已接入 | 区分相干光和散杂光。 |
| 几何 envelope | 原型 | 表示半径、发散、聚焦距离和 spot size。 |
| 热负载 | 预留 | 光热系统读取吸收和损伤。 |
| 调制 / 信道 | 预留 | 光纤信号、虚空存储和远程机器。 |
| 复振幅 / 相位 | 局部预留 | 干涉仪、光刻和高阶局部结构。 |

### 5.1 频率 lane

当前可见光第一版使用活跃 lane，而不是为每个 bin 复制整张世界图。

```text
shared geometry
shared topology
shared solver plan
per FrequencyKey + CoherenceKind lane solve
merge readout
```

当前约束：

- 创造光源最多输出 32 个活跃频率分量。
- `BeamPacket.MAX_COMPONENTS` 控制合并后分量上限。
- 光谱仪、光束、spot 读取同一套频率数据。
- 可视化使用统一的 `SpectralColorMap` LUT。

### 5.2 相干光和散杂光

当前设定里，散杂光可以近似为“空间上可被同一套矩阵语言处理的光”。在非增益、非干涉环境下，相同参数的相干光和散杂光应有相同的被动传播行为。

差异来自数据层变量自然导出：

- 相干光可被相干增益结构消费。
- 散杂光可作为种子光或环境光来源。
- 可视化可以根据相干比例决定核心和 halo。
- 未来单模光纤、调制信道和干涉结构可以只接受相干或特定信道。

### 5.3 光束几何 envelope

光束几何服务两个玩法：

1. 让传感器和 spot 读到直观的半径、发散和聚焦信息。
2. 为透镜、光纤耦合和光热吸收留下统一接口。

当前原则：

- 不加入像差。
- 不允许光斑半径收缩到 0。
- 大尺度时行为接近初中物理直觉。
- 焦点、束腰和发散用少量导出参数表达，而不是全场采样。

未来透镜系统可以用类似 q 参数或矩阵语言更新 envelope，但必须保持玩家可读：焦距、束腰半径、发散趋势应能在手册和 UI 中解释。

重要边界：几何 envelope 的旧模型只适合作为读数层和局部效率层。只要半径、
发散或模式会改变网络内部 transfer，例如光纤入口、孔径、透镜后的耦合边或会
反馈到拓扑的分流结构，就不能先把同频同相干的功率合成一个标量再估计平均半径。

```text
power solution -> envelope readout -> local effective rate / local loss display
```

对于读数和非反馈机器，可以使用一次求解后的 envelope 估计有效效率：

```text
effectivePower = solvedPower * localEfficiency(envelope, machineProfile)
```

这个近似不反向改变网络功率，只改变机器处理速率、显示和诊断信息，因此不会
破坏线性求解。它只允许用于非反馈、非分流、非远端传输的机器读数。

### 5.4 相空间功率分布与多等效

数学语义仍然是：

```text
合流时合并的是相空间功率分布，不是半径/发散的平均值。
几何敏感元件读取分布，对每个 profile component 分别作用，再把结果线性求和。
```

同一个入口前的光应表示为离散正分布：

```text
D = sum_j P_j * delta(g_j)
```

其中 `g_j` 是空间 profile state，`P_j` 是该 state 上的功率。光纤、孔径和
其他几何敏感元件读取的是每个 `g_j`，而不是 `D` 的平均 envelope：

```text
P_accepted = sum_j P_j * accept(g_j)
```

但是实现层必须区分无反馈和反馈。

无反馈图中，功率系统可以把有限 profile key 放进状态：

```text
x[port, frequency, coherence, profileKey]
```

普通传播、透镜和光纤入口分别表现为：

```text
free propagation: profile_j -> profile_k, gain <= 1
lens transform:   profile_j -> profile_k, gain <= 1
fiber input:      profile_j -> fiberGuidedProfile, gain = accept(profile_j) * routeGain
```

这保证多来源合流仍然线性：

```text
T(D_a + D_b) = T(D_a) + T(D_b)
```

反馈图中，不允许让自由传播和透镜在环路里制造无限个 profile key。当前规则是
“多等效”：

```text
profile-sensitive loss is compiled once into edge gain
main feedback solve tracks finite power lanes
readout layer reports representative envelope / profile mode
```

也就是说，光纤接受、孔径裁剪、耦合失配等几何敏感过程在编译边或 collapsed
solver 组矩阵时查 LUT / 局部模型，写成一次 `gain <= 1`：

```text
effectiveGain = sampleGain * equivalentProfileGain
```

进入反馈主求解后，它们不再展开成新的非线性固定点，也不把输出 profile 再反馈
成新的未知量。

推荐的最小 profile state 使用二阶矩描述横向相空间：

```text
R = <r^2>
C = <r * theta>
A = <theta^2>
```

`C` 不能省略。半径和发散相同的两束光可能一个正在收敛、一个正在发散；没有
`C` 时，透镜和自由传播后的结果会被错误合并。工程结构可以拆成：

```java
record ProfileShape(
    double r2,
    double rTheta,
    double theta2,
    double quality,
    double scatter,
    int modeM,
    int modeN
) {}

record WeightedProfileComponent(
    double weight,
    ProfileShape shape
) {}
```

`ProfileShape` 或其离散化 key 进入 solver lane。`WeightedProfileComponent`
用于 UI、调试和局部分布对象。不要同时在 profile component 和 solver unknown
里保存同一份真实功率，避免双重计数。

自由传播和薄透镜可以先用 ABCD 二阶矩近似：

```text
free space d:
R' = R + 2dC + d^2A
C' = C + dA
A' = A

thin lens f:
R' = R
C' = C - R/f
A' = A - 2C/f + R/f^2
```

这些变换只移动 profile state，不凭空增加功率。任何 aperture、吸收、散射、
导光接受或坏品质惩罚都必须写成 `gain <= 1`。

组件数量需要上限。无反馈精确求解里，压缩只能发生在相近 profile 之间；如果
必须压缩到粗 bucket，压缩结果必须保守：它可以降低未来几何敏感元件的接受率，
不能让“好光 + 坏光”被平均成更容易耦合的光。反馈求解里不做这种 profile
膨胀，而是使用已经等效到边 gain 的有限状态。

UI 可以显示平均等效半径，但真实损耗不能依赖 UI 平均值。机器效率也只属于
readout / recipe 层，不能把效率结果再写回光学网络。

## 6. 求解计划

编译器先规划，再求解。当前主路径是 `ProfileLanePowerSolver`：

- 无反馈图：`PROFILE_STATE_EXACT`，可以保留有限 profile-state 精确传播。
- 有反馈图：`PROFILE_COLLAPSED_EXACT`，profile 敏感损耗必须折叠进矩阵边权。
- 空图或无有效 source：`NONE`。

无反馈图如果 profile state 溢出或 exact solve 失败，可以降级到 collapsed，但必须
在日志中标记 `profile_overflow` 或 `profile_fallback`。降级结果可用于游戏读数，
但不能被误读为完整 profile-state 精确解。

旧的 scalar planner 仍然参与 region、SCC 和日志解释。已接入或预留的求解器：

- `TopologicalScalarSolver`：DAG 区域的拓扑传播。
- `ChordFeedbackScalarSolver`：反馈 SCC 的 chord 求解。
- `MixedRegionScalarSolver`：混合区域。
- `DebugOracleScalarSolver`：对照和验证。
- `LoopMacroScalarSolver`：小环等效缓存占位。
- `MagnitudeBucketScalarSolver`：量级分池占位。
- `ResidualCorrectionScalarSolver`：弱信号残差占位。
- `SymmetryReductionScalarSolver`：重复子图压缩占位。
- `WeightedBfsAttentionScalarSolver`：注意力调度占位。

长期目标不是选择唯一求解器，而是分区使用：

- DAG 区走拓扑传播。
- 小反馈区走 chord。
- 重复小环走 macro。
- 远端弱信号走 residual。
- 大阵列走 symmetry。
- 玩家正在看的区域走更高优先级。

无论底层使用哪种数值实现，玩法语义都不应退回“每 tick 追光直到看起来收敛”。
线性方程数值迭代可以作为实现手段；非线性反馈迭代不是主模型。

## 7. 反馈增益调度

主动增益进入反馈腔时，直接求解可能遇到：

```text
rho(T_feedback) >= 1
```

这表示线性系统进入无限放大区，不是普通数值误差。处理原则是：求解前先把增益调度成稳定线性系统。

流程：

```text
port graph
-> feedback SCC
-> gain source discovery
-> participation analysis
-> source cap
-> effective gain softcap
-> spectral radius check
-> scheduled graph
-> solver
```

当前代码对应：

- `StableFeedbackGainScheduler`
- `GainSourceCollector`
- `GainParticipationAnalyzer`
- `EffectiveGainSoftcap`
- `SpectralRadiusEstimator`
- `GainGraphRewriter`

### 7.1 参与权重

每个增益源有两个权重：

- 材料权重：材料本身适合承担多少增益。
- 图参与权重：它位于多少强反馈路径上。

直觉：位于低损耗闭合腔主路径上的红宝石，比旁路上的红宝石更应该获得有效增益预算。

### 7.2 Softcap

有效增益使用软上限函数族。它的渐近行为是：

```text
小信号: y ~= x
大信号: y -> hardcap
```

规则：

- softcap 只改变额外增益，不给无增益路径凭空加能量。
- 每个增益源根据参与权重得到 source cap。
- 调度后仍要估计谱半径。
- 如果仍越过硬稳定阈值，继续收缩 extra log gain。
- 如果无法稳定，系统进入 overdriven 或 unreliable，而不是输出无限功率。

这套方法让玩家造出的腔不会因为稍微过强就完全不可用，同时也为热失控、镀膜烧蚀和光学击穿预留稳定性余量。

## 8. 调度、缓存与可靠性

一次世界变化不会直接重算整个光路，而是进入层级化队列：

```text
world event
-> classify dirty kind
-> mark affected dependencies
-> enqueue network/system task
-> run within optical budget
-> export readout/visualization
```

优先级方向：

```text
interrupt
> currently running network
> reliable sensor demand
> high power / dangerous network
> player-visible beam network
> recently changed network
> field-affected network
> background network
```

可靠性规则：

- 几何层或拓扑层命中正在计算的网络时，当前数据导出作废。
- 网络被标记为 unreliable。
- 传感器保留最后可靠值，或显示不可靠状态。
- 只有新的完整导出完成后，网络才重新 reliable。

缓存可使用的签名：

```text
geometry signature
topology signature
intrinsic data signature
sensor layout
```

高频红石和机器状态切换通常在少量状态之间往返。状态 LUT 可以复用刚算过的结果，但必须尊重 dirty 边界。

## 9. 场源、材料和涂层

### 9.1 场源

场源是数据层修正，不是普通光学元件。一个 Field Source 应定义：

- 影响范围。
- 作用 profile。
- 介质修正。
- 是否可见。
- 更新频率。
- dirty 类型。

沙子目前是原型：它能提升附近潜在光路的损耗，并作为光路可视化触发条件。未来烟雾、粉尘、热场、虚空扰动场和磁/电偏置场应走同一接口。

### 9.2 材料和随机材料

材料响应通过 LUT 和插值进入数据层。未来随机材料系统应基于：

```text
base material profile
world perturbation seed
measured profile cache
player/material knowledge
```

约束：

- 同一世界、同一材料、同一扰动域得到稳定结果。
- 数据包给基准范围，世界扰动给个性。
- 玩家测量后获得本世界材料 profile。
- 随机性不能破坏调试和复现。

### 9.3 涂层和界面

涂层挂在表面数据上。未涂前，普通方块可以没有主动光学节点；涂层后，该面可以变成表面节点并提供自己的 transfer。

界面反射可用功率级传播/界面分解表达：

```text
propagation -> interface -> propagation -> interface
```

这接近 TMM 的结构，但当前功率层不携带相位，所以它表达阻抗不匹配导致的功率反射，不表达薄膜干涉条纹。需要薄膜干涉时，应使用预计算 LUT 或局部复振幅模块。

## 10. 系统边界

### 10.1 光纤

光纤是光学网络之外的几何/拓扑辅助网络。它存储连接，提供光输入输出边界，并在连接变化时诱导相关光学网络 dirty。

当前规则：

- 连接是 1 对 1。
- 多条平行光纤自然增加同一对节点之间的容量。
- 混合光束必须使用统一颜色和功率合并函数。
- 剪断、遮挡、过载烧毁必须切断光纤连接并诱导光学网络变化。
- 光纤线缆是世界内可见物体，不受 beam HUD 开关限制；自由空间诊断光束仍然受 HUD 和查看器限制。

relay 属于光纤网络的几何和拓扑层，不应直接参与光学网络。

内部接口规则：

- 光纤接口把外部光学端口映射为远端外部光学端口，编译器看到的是一条线性 transfer edge。
- 光纤路径本身保存端点、路径、容量、占用和渲染状态，不保存每 tick 的求解状态。
- relay 只负责路径锚定、可视化和连接维护，不产生吸收、反射、增益或混合节点。
- 未来的损耗、容量、模式标签和信道标签应作为 transfer profile 参数进入数据层，而不是把导光接受参数、弯曲模式或多模耦合写成新的非线性求解器。
- 如果接口、relay、遮挡或剪断改变路径，光纤网络先 dirty，再诱导相关光学网络 dirty。

光纤端口导光匹配模型：

```text
D_in = sum_j P_j * delta(profile_j)
routeGain = transmissionPerBlock ^ routeLength
          * bendTransmissionPerRightAngle ^ bendUnits

P_guided = sum_j P_j * accept(profile_j, fiberInputProfile) * routeGain
x[fiberOut, frequency, coherence, fiberGuidedProfile] += P_guided
```

`accept(profile_j, fiberInputProfile)` 只依赖该 component 的相空间状态和光纤入口
profile，不依赖当前总功率。长度和弯折也只依赖静态路线。因此光纤仍然是线性
transfer edge。未进入 guided mode 的功率被当作接口附近的倏逝场和包层损耗，
不作为新的自由空间光源进入求解。

实现边界：

- 无反馈结构可以把 `fiberGuidedProfile` 作为有限 profile state 继续传播。
- 反馈结构必须把接受率、长度损耗和弯折损耗折叠成一次等效矩阵边权。
- 光纤输出的代表性导波 profile 用于 readout 和后续局部 LUT，不在反馈环里无限刷新。
- 路线拓扑和长度损耗由 `remoteOutputPorts` 提供；半径/发散接受率由
  `profileTransferForEdge` 提供；最终矩阵边权是二者乘积。
- 同端点并联路线先对 `routeGain * acceptance` 求和再 clamp，不能把低接受率路线
  平均成更容易耦合的光。

该模型不作为玩家面对的真实波导参数系统。玩家侧只表达为导光匹配：光斑太宽、
光束太散、路线太长或弯折太强会造成端口损耗。半径和发散影响的是光纤入口的
接受率；光进入光纤后会重置为光纤自己的标准导波 profile，不继续按自由空间
envelope 传播，也不在光纤内部追踪多半径模式。

### 10.2 全息存储

全息存储是多方块存储系统。核心只保存索引或哈希，实际物品数据由外部存储结构关联。

当前规则：

- 核心、水晶和 screen 组成矩阵。
- 水晶提供物品容量。
- 暴露面提供类型容量。
- 容量不足时 UI 仍可查看内容，但进入只读锁定。
- 频道倍率接口已预留，当前倍率固定为 1。

它的多方块认证属于几何层；容量和频道属于数据层；存取 UI 属于读数/交互层。

### 10.3 微缩机器

微缩机器是“泛函”而不是普通数值。压缩前的内部结构被编译成边界响应：

```text
internal optical network
-> boundary IO ports
-> macro transfer map
-> compacted_machine block
```

外部世界只看到压缩后的方块和它的面到面响应。内部保留快照、端口图和可展示数据。旋转会改变实际光线 IO 方向，因此必须诱导相关光学网络 dirty。

这套模型允许压缩后的机器继续被再次压缩。

### 10.4 虚空存储总线

虚空存储总线是未来 profile 系统的另一种读写层。它不直接参与普通光功率求解，而读取：

- 信道 id。
- 稳定度。
- 能量抵押。
- 模板索引。
- 远程接口权限。
- 孔径阵列状态。

全息矩阵是本地存储；虚空总线是跨距离寻址；虚空孔径是维持信道的物理结构。

## 11. 可视化和读数

光束、spot、HUD、Jade 和机器 UI 都是导出层。它们读取 compiled solution 或诊断状态，不反向改变求解器。

导出数据可包含：

- 表面位置。
- 端口方向。
- 吸收功率。
- 总功率。
- 频率分量。
- 相干 / 散杂比例。
- 几何 envelope。
- 可靠性。

光束线是拓扑层可视化，不是完整空间光场。Spot 是表面读数和光热/几何反馈的可视化，不应成为主传播逻辑。

## 12. 日志与调试

日志记录“系统为什么这么算”，而不只是报错。默认阅读顺序见 [LOGGING.md](LOGGING.md)。

日志应覆盖：

- 事件如何诱导 geometry / topology / data dirty。
- 网络如何被识别。
- solver plan 如何选择。
- 反馈增益如何被调度。
- readout 何时 reliable / unreliable。
- 光纤、微缩机器和全息存储如何诱导光学网络变化。

详细日志有成本。日常应使用摘要 diagnostics；只有需要定位 SCC、edge、lane、readout output 或 solver region 时才开启 verbose。

## 13. 实现检查清单

新增或重构光学功能时，提交前检查：

1. 是否说明它属于几何层、拓扑层还是数据层？
2. 是否只在必要时重建拓扑？
3. 是否会在结构变化后标记相关网络 dirty？
4. 是否复用统一频率、颜色、功率和 readout 接口？
5. 是否定义 reliable / unreliable / error 行为？
6. 是否避免把 tracer 当成主逻辑？
7. 是否有足够日志定位跨系统边界问题？
