# Spectralization 数学模型与架构

## 0. 核心句

Spectralization 的光学系统是一个运行在 Minecraft 世界里的 port graph 编译器。
方块给出局部端口和局部 scattering map；世界事件改变几何、拓扑或数据；编译器
把网络变成求解计划；求解结果再写回传感器、机器和客户端可视化。

常规功率层写成：

```text
x = T x + s
y = C x
```

这里的 `x` 是端口/边上的 profile，`T` 是由局部元件拼成的传输算子，`s` 是
光源注入，`y` 是传感器和机器读数。

## 0.1 Compiler First 开发规范

Spectralization 的安全路径不是 tracer，而是拓扑编译、事件驱动和缓存导出。
这条规则要放在所有新功能前面。

旧 tracer 的定位只有三个：

- debug oracle：抽样对照编译器结果。
- 日志样本：帮助发现拓扑、读数或可视化差异。
- 旧功能迁移期的临时兼容层。

它不能成为新功能的主运行逻辑，也不能被当成“先放进 tracer 里试试看”的保险。
这样做看起来快，实际会把依赖、缓存、可靠性、反馈、可视化和性能问题全部推迟到
更难改的时候。

新增光学功能时，第一步必须回答四件事：

```text
1. 它在几何层是什么节点、表面、场源或机器？
2. 它在拓扑层暴露哪些端口和潜在连接？
3. 它在数据层提供哪些 profile、scattering map、读数或导出量？
4. 哪些世界事件会让它的 geometry / topology / source / parameter / field dirty？
```

然后再写代码。

光源都是事件驱动 source。它们可以在自己的参数、泵浦、红石、邻居或加载状态改变
时申请刷新，但不能把“每 tick 重算光路”当成工作方式。光源 tick 如果存在，也只能
做极轻的状态同步；真正的网络重建和求解必须进入 `OpticalTraceCache` / compiled
system 的预算队列。

传感器、光束、spot、伤害、致盲、维护任务和机器读数都是导出层。它们读取 compiled
solution 或由 compiled solution 生成的缓存数据。它们不主动追光，也不拥有自己的
递归传播逻辑。

方块材料、涂层、场源、增益介质和空间频率参数都应先进入 profile / surface /
field / gain 的数据接口，再由 port graph 编译器消费。即使最小原型只有一个数值，
也要接到这条路径上。

## 1. Profile 分层

不是所有物理量都进同一个矩阵。

当前主线：

- 频率：`FrequencyKey = SpectralRegion + bin`。
- 功率：非负实数。
- 相干性：相干、散杂/非相干。
- 几何包络：半径、发散角、聚焦距离、散杂度。

预留主线：

- 带宽。
- 调制信道。
- 偏振。
- 相位 / 复振幅。
- 热负载。
- 物质信道索引。

不同 profile 有不同求解器。常规工业光路用功率矩阵；干涉仪和光刻机器可以局部
启用复振幅；光热系统读取吸收功率和 irradiance；虚空存储总线读取信道和稳定度；
光束读取拓扑和导出可视化数据。

频率系统采用活跃 lane，而不是为每一个 bin 复制一整张世界图。几何层、拓扑层、
SCC、chord 和 solver plan 共享；数据层按 `FrequencyKey + CoherenceKind` 分 lane
求解。第一版只激活光源、增益介质或转换元件实际产生的频率，并受
`BeamPacket.MAX_COMPONENTS` 控制。可视化层使用统一的 `SpectralColorMap` LUT，
把可见光 bin 映射到 RGB。

## 2. 三层世界模型

### 几何层

几何层是坐标集合和方块分类：

- 光源。
- 光学元件。
- 传感器。
- 可涂层表面。
- 场源。
- 依赖位置。

方块放置、破坏、活塞移动、区块加载都会触发几何层变化。几何层变化最便宜，
但优先级最高：它可以直接中断正在进行的数据层求解。

### 拓扑层

拓扑层是端口图。

一个方块不一定是一个节点。更自然的模型是：

- 面可以是节点。
- 轴可以是节点。
- 方块是这些节点的元组。
- 元件状态决定这些节点之间的连接和 transfer。

镜子、分光镜、PTS、CMOS、涂层面、未来电动镜和电动透镜都通过 topology
template 展开。普通不透明方块可以没有光学节点；被涂层激活的面可以新增表面
节点。

### 数据层

数据层是所有可以不改变拓扑的数值：

- 材料 TRA。
- 涂层响应。
- 光源 profile。
- 泵浦速率。
- 增益参数。
- 场源修正。
- 求解器输出。
- 读数和可视化导出。

数据层可以慢。只要中断及时，它不需要每次都立刻完成。

## 3. 调度算法

我们的调度算法有两层：网络调度和反馈增益调度。

### 网络调度

一次世界变化不会直接重算整个光路，而是进入层级化队列：

```text
world event
-> classify dirty kind
-> mark affected dependencies
-> enqueue network/system task
-> run within optical budget
-> export readout/visualization
```

优先级：

```text
interrupt
> currently running network
> network with reliable sensor demand
> high power / dangerous network
> player-visible beam network
> recently changed network
> field-affected network
> low-impact background network
```

一旦几何层或拓扑层变化命中正在计算的网络，数据层导出立刻作废。网络被标记为
不可靠，传感器保持最后可靠值。只有新的完整导出完成后，网络才重新可靠。

这个机制解释了为什么高频红石可以被处理：高频变化会不断打断导出层，但不会
强迫系统每次都同步算完一个大网络。

### 传感器注意力

传感器不是被动末端，它决定哪些网络值得算。

网络权重可以由这些项组成：

- 是否有传感器。
- 传感器是否正在输出红石。
- 读数是否不可靠。
- 是否靠近玩家可见光束。
- 是否连接机器控制。
- 光功率量级。
- 是否处在反馈 SCC。
- 是否处在场源影响区。
- 最近是否发生变化。

没有传感器、没有机器读数、也不可见的网络可以延后。它仍然存在于几何/拓扑层，
但数据层不必抢占预算。

### 状态 LUT

高频红石常常在少量状态之间切换。可以缓存：

```text
geometry signature
topology signature
intrinsic data signature
sensor layout
```

如果刚刚算过的状态再次出现，读数层可以直接复用缓存。缓存大小可以按网络规模
给一个启发式上限，而不是固定常数。

## 4. 反馈增益调度

主动增益进入反馈腔时，直接求解会遇到：

```text
rho(T_feedback) >= 1
```

这不是单纯的“矩阵算不动”，而是系统进入了会无限放大的线性区。我们的做法是
在求解前把增益调度成稳定的线性系统。

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

### 参与权重

每个增益源有两个权重：

- 材料权重：这个材料本身适合承担多少增益。
- 图参与权重：它处在多少强反馈路径上。

参与权重来自反馈 SCC 和 chord/cycle 结构。直觉是：一个红宝石如果正好位于
低损耗闭合腔的主路径上，它比旁路上的红宝石更应该获得有效增益预算。

### Softcap 函数族

有效增益不是线性缩放，而是同一族软上限函数。它有两个渐近行为：

```text
小信号: y ~= x
大信号: y -> hardcap
```

每个增益源根据参与权重得到自己的 source cap。softcap 只改变“超出 1 的额外
增益”，不会凭空给无增益路径增加能量。调度后还会估计谱半径；如果仍然越过硬
稳定阈值，就继续收缩 extra log gain，直到图回到稳定区或被标记过驱。

这套方法的意义：

- 玩家造出的腔不会因为稍微过强就完全不可用。
- 求解器看到的仍然是固定线性系统。
- 热失控、镀膜烧蚀、击穿可以读取稳定性余量，而不是重写求解器。

## 5. Solver Plan

编译器先规划，再求解。

已接入：

- `TopologicalScalarSolver`：无环传播。
- `ChordFeedbackScalarSolver` / feedback SCC solver：反馈区。
- `MixedRegionScalarSolver`：混合区域。
- `DebugOracleScalarSolver`：对照。
- `LoopMacroScalarSolver`、`MagnitudeBucketScalarSolver`、`ResidualCorrectionScalarSolver`、
  `SymmetryReductionScalarSolver`、`WeightedBfsAttentionScalarSolver`：框架占位。

长期目标不是选择唯一求解器，而是分区使用：

- DAG 区走拓扑传播。
- 小反馈区走 chord。
- 重复小环走 macro。
- 远端弱信号走 residual。
- 大阵列走 symmetry。
- 玩家正在看的区域走高优先级精算。

## 6. 场源系统

场源是数据层修正，不是普通光学元件。

一个 Field Source 需要定义：

- 影响范围。
- 影响 profile。
- 介质修正。
- 是否可见。
- 更新频率。
- dirty 类型。

沙子目前是原型：它让附近光路损耗增大，并且能作为光路可视化触发条件。未来的
烟雾、热场、虚空扰动场、末影场都走同一个接口。

关键优化：

- 不扫描“附近所有方块”。
- 只记录已经依赖这个场的网络。
- 场参数改变时优先更新数据层。
- 场范围改变时才重建几何/拓扑依赖。

## 7. 随机材料系统

随机材料系统属于数据层，但会影响玩家 progression。

数据结构设想：

```text
base material profile
world perturbation seed
measured profile cache
player/material knowledge
```

材料 LUT 给基准响应，世界扰动给谱线偏移。玩家测量后得到本世界的实际 profile。
这让“光谱扫描器”有价值，也让镀膜、超透镜和高阶激光器不只是按 wiki 配方执行。

随机材料不能破坏可重复性：同一个世界、同一个材料、同一个扰动域应该得到稳定
结果。数据包仍然能控制基准范围。

## 8. 涂层、面节点与界面反射

涂层挂在表面数据上。

在拓扑上，可涂方块在未涂前可以没有主动光学节点；涂层后，该面变成一个表面
节点，提供自己的 S map。

界面反射可以用功率级近似表达：

```text
propagation -> interface -> propagation -> interface
```

这接近 TMM 的传播/界面分解，但当前功率层不携带相位，所以它表达的是阻抗不匹配
带来的功率反射，而不表达薄膜干涉条纹。真正需要薄膜干涉时，应使用预计算 LUT
或局部复振幅模块。

红宝石腔被切开后的内部反射可以从这个接口自然出现：材料段之间多了界面，界面
按材料阻抗给出额外反射和损耗。

## 9. 虚空存储总线

虚空存储总线在架构上是 profile 系统的另一种读写层。

它不直接参与普通光功率求解，而读取：

- 信道 id。
- 稳定度。
- 能量抵押。
- 模板索引。
- 远程接口权限。
- 孔径阵列状态。

全息矩阵是本地存储；虚空总线是跨距离寻址；虚空孔径是维持信道的物理结构。
这套系统可以复用编译器的网络识别、可靠性、调度和读数层，但 profile 不再只是
光功率，而是物质信道。

## 10. 微缩化结构

微缩化结构是同一套编译器的游戏化版本。

内部：

```text
2D optical layout -> port graph -> macro transfer map
```

外部：

```text
macro node with logical ports
```

它可以拥有多于六个逻辑端口，因为端口是编译器语言里的对象，不必等同于方块面。
玩家在工作站里搭的二维光路会被压缩成一个 macro node，外部网络只看它的等效
响应。

这也给 loop macro、小环缓存、超透镜和自定义机器一个共同出口。

## 11. Spot 与光束

Spot 是前端导出，不反向改变求解器。

导出数据来自稳定 compiled solution：

- 表面位置。
- 吸收功率。
- 相干/散杂比例。
- 几何包络。
- 频率到颜色。
- 亮度等级。

当前只优先做好完整方块表面。非完整模型的真实贴面需要读取或重建模型几何，
成本和复杂度都高，后面单独处理。

光束线是拓扑层可视化，不是空间频率层。它显示光路存在和方向；真实宽度、
电离空气、丁达尔散射和 spot 由几何/散杂系统另行处理。

## 12. Debug Log

日志是实验仪器。它应该记录“系统为什么这么算”，而不只是报错。

需要覆盖：

- network cache。
- solver plan。
- feedback SCC。
- gain schedule。
- readout reliability。
- field dependencies。
- spot export。
- beam overlay。
- future material randomization。
- future void channel state。

日志有成本。大型高频网络开启详细日志时卡顿是可以接受的 debug 代价；关闭日志时
运行路径必须保持轻。
