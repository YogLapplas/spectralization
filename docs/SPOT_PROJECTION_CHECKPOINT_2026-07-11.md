# 光斑投影阶段检查点（2026-07-11）

本文记录光斑投影系统在性能优化阶段的完整实现状态。算法定义和修改约束仍以
[SPOT_PROJECTION_ALGORITHM.md](SPOT_PROJECTION_ALGORITHM.md) 为唯一权威；本文负责说明当前代码已经做了什么、如何验证、怎样阅读性能数据，以及下一阶段应从哪里继续。

## 1. 本阶段目标与结论

本阶段同时处理了正确性、容量、可视化、测试工具和性能基础设施：

- 修复不同楼梯方块处在同一投影深度时，后方楼梯立面偶尔穿过前方踏面的遮挡错误。
- 将同一 owner 的光斑硬上限统一为 `2^15 = 32768`，并让编译器、网络、客户端缓存和渲染器共享同一约束。
- 把大快照拆成每包 2048 个光斑，客户端只在所有分片到齐后原子替换旧快照，避免半帧和残缺画面。
- 增加边缘羽化、强度透明度、相干光/散杂光颜色与加法混合，使重叠光斑更接近能量叠加。
- 增加 `spot_test` 物品、固定随机场景、结构验证、性能报告，以及功率/颜色变化的缓存回归测试。
- 将投影分成“几何模板”和“外观重建”两条路径。只有功率或颜色变化时，不再扫描方块和重建遮挡几何。
- 当前外观路径已降到毫秒级；完整几何重建仍在几十毫秒级，是下一阶段的主要目标。

这里的投影器不是普通逐像素或逐光线光追。它在一个离散、轴对齐、最大深度固定的体素锥体内，维护仍未被遮挡的纹理区域，并只在世界几何或光学导出发生变化时运行。因此，它在 Minecraft 这种方块世界、低频更新、同一光源需要输出大量稳定光斑的场景中仍有明显的性能潜力。

## 2. 正确性模型

### 2.1 投影对象

光斑纹理的定义域是归一化二维区域。光源沿主传播轴逐层扫描世界，每个可接收表面把自身轮廓映射回纹理域。表面得到的纹理区域为：

```text
当前表面覆盖区域 ∩ 尚未被更前方表面消耗的区域
```

得到光斑后，遮挡面再从剩余区域中减去对应部分。整个过程满足单调遮挡：深度增加时，剩余区域只能不变或缩小，不能重新出现。

### 2.2 楼梯与局部立面

楼梯不能被简化成一个完整立方体。当前实现把踏面、内部立面和沿传播方向可见的侧面分别处理：

- 踏面可以接收光斑，也可以遮挡后方表面。
- 同一个楼梯方块内部，先处理真正位于前方的踏面，再处理内部立面。
- 不同方块即使量化到同一整数深度，也必须按连续的前向坐标比较，不能依赖遍历顺序或方块坐标排序。
- 侧面光斑使用局部几何区间和规范化区域，不把整个楼梯错误地扩张成实心方块。

本阶段修复的核心就是第三条。旧路径只在深度桶内部保持局部顺序；两个不同楼梯处在同一个 `z` 深度时，前方踏面有机会晚于后方立面进入遮挡集合。现在深度桶内的接收面和遮挡面共享连续前向排序，前方踏面会稳定地先消耗纹理区域。

### 2.3 不变量

任何后续优化必须保持：

1. 光斑只是编译结果的显示/readout 导出，不能反写光学功率求解。
2. 每个输出片段的 UV 必须来自原始光斑纹理域，不能因为裁剪而重新铺满纹理。
3. 更后方表面不能恢复已经被前方表面遮挡的区域。
4. 同一几何和同一光学输入必须得到稳定的片段集合和稳定顺序。
5. 缓存路径的结果必须与强制完整重建一致。
6. 超过容量时必须有确定的截断和诊断，不能产生越界包或客户端半快照。

## 3. 运行时数据流

```text
光学编译结果
  -> CompiledSpotLayer 判断 owner 是否需要刷新
  -> VoxelSpotProjector 选择完整几何或仅外观路径
  -> SpotProjectionResult（几何模板、外观计划、依赖、统计）
  -> SpotRecord 列表
  -> SpotOverlayPayload 分片
  -> ClientSpotCache 原子组装 owner 快照
  -> SpotRenderEvents 距离裁剪并加法渲染
```

关键边界：

- `OpticalSpotTracker` 将已求解的光学分量转成表面外观；它不负责世界遮挡。
- `VoxelSpotProjector` 负责体素投影、表面轮廓、遮挡减法和几何依赖。
- `CompiledSpotLayer` 负责缓存选择、owner 预算、刷新和诊断。
- 网络层只传输已编译 `SpotRecord`，不在客户端重新推导几何。
- 客户端只负责快照缓存、可见性裁剪和绘制，不决定游戏逻辑。

## 4. 容量、网络和渲染约束

共享常量定义在 `SpotProjectionLimits`：

```text
MAX_SPOTS_PER_OWNER = 32768
SPOTS_PER_PAYLOAD_CHUNK = 2048
MAX_PAYLOAD_CHUNKS = 16
```

容量按 owner 计算，而不是全局一次性截断。服务端先生成确定顺序的结果，再按共享上限截断。网络分片携带快照身份和分片序号；客户端在收到完整集合前保留旧快照，全部到齐后一次性替换。

渲染采用加法混合。透明度由已求解功率映射到有限等级，纹理由中心向边界羽化。渲染器还执行相机距离裁剪，避免已不可见 owner 的全部面进入提交路径。容量提升不等于每帧必须绘制 32768 个面；它只是防止复杂结构过早丢面，实际成本仍由可见性和快照变化控制。

## 5. 缓存架构

### 5.1 完整几何路径

以下变化必须进入完整路径：

- 世界方块或碰撞/轮廓形状变化。
- 投影方向、锥体宽度、发散参数或最大深度变化。
- 可接收面、遮挡面或依赖集合变化。
- 缓存条目不存在或结构校验失败。

完整路径扫描体素深度、生成候选表面、维护剩余区域、构造几何模板，并记录所有会影响结果的世界位置。

### 5.2 仅外观路径

如果几何键和依赖快照未变，只改变功率、频率对应颜色或相干/散杂光分量，则复用：

- 几何模板；
- 模板到唯一表面的 `AppearancePlan`；
- 不可变依赖快照；
- 表面索引和稳定输出顺序。

外观路径只为每个唯一表面构造一次外观，再通过表面索引更新所有模板。`SpotRecord.withAppearanceFrom` 保留位置、朝向、UV 和几何边界，只替换颜色、半径等级和透明度等级。

缓存回归必须同时满足：

```text
appearance_only_samples == requested_samples
geometry_cache_hits == requested_samples
appearance_plan_reused == true
dependency_snapshot_reused == true
validation_mismatches == 0
missing_faces == 0
```

### 5.3 当前失效粒度

当前依赖集合是精确的，但一旦其中任意世界位置改变，整个 owner 的几何条目仍会失效。这保证正确，但没有利用“较远深度变化不应重做更近前缀”的结构，因此完整重建仍是主要耗时。

## 6. 自动测试入口

### 6.1 `spot_test` 物品

测试物品需要权限等级 2：

- 右键：在玩家附近生成当前模式的确定性结构并自动执行测试。
- Shift + 右键：循环切换模式。
- 测试运行中再次右键：显示当前进度，不启动第二个并发套件。

模式：

| 模式 | 内容 | 主要用途 |
| --- | --- | --- |
| `quick` | 小型固定场景 | 注册、生成、投影和基本日志冒烟测试 |
| `partial_geometry` | 楼梯、台阶等局部方块组合 | 遮挡顺序、内部立面和 `missing_faces` 回归 |
| `performance` | sparse、mixed、dense、cached power、cached color | 完整重建与外观缓存性能 |
| `full_suite` | 合并以上测试 | 提交前完整回归 |

性能模式的完整重建场景使用固定种子和占用率；缓存场景会先暖几何，再自动改变光源功率或颜色。固定输入让不同提交之间的数据可比较。

### 6.2 命令

```text
/spectralization spottest random
/spectralization spottest rerun
/spectralization spottest report
/spectralization spottest benchmark
/spectralization spottest clear
/spectralization spotperf report
/spectralization spotperf report <count>
/spectralization spotperf reset
```

物品是日常回归入口；命令用于单独生成、重跑、拉取最近样本和清空统计。除非正在定位物品调度本身，不应手工拼装性能场景。

## 7. 日志与时间判读

一次测试至少会涉及：

- `diagnostics_*.log`：结构生成、套件进度、验证结果、性能摘要和异常。
- `optical_compiler_*.log`：光学编译和光斑导出的编译器上下文。
- `latest.log`：主线程卡顿、网络、资源或崩溃信息。

查看测试时，只读取同一次启动中时间最新的一组 `diagnostics` 与 `optical_compiler` 日志；不要把旧启动的缓存状态和新启动的测试结果拼在一起。

三种时间不能混用：

| 指标 | 起止边界 | 用途 |
| --- | --- | --- |
| `core` / projection time | 进入投影器到得到结果 | 比较算法本身 |
| `response` | 测试请求发出到服务器观察到目标结果 | 发现队列、缓存清理、编译调度和日志成本 |
| 玩家可见延迟 | 操作到客户端完整快照被绘制 | 还包含 tick、分片网络、客户端组装和渲染帧 |

因此，游戏中感觉到的时间高于 `core` 是正常的。若 `core` 稳定而 `response` 很高，应先查测试是否主动执行了全局 `OpticalTraceCache.clear(level)`、服务端 tick 是否拥塞，以及同步日志是否在该样本中首次初始化；不要直接归因于投影算法。

关键事件：

```text
subsystem=spot_projection_test event=suite_started
subsystem=spot_projection_test event=case_started
subsystem=spot_projection_test event=appearance_step
subsystem=spot_projection_test event=case_complete
subsystem=spot_projection_test event=suite_complete
subsystem=spot_projection event=profile
subsystem=spot_projection event=performance_report
subsystem=spot_projection event=geometry_cache_invalidated
```

`case_complete` 是最适合做提交间对比的单行摘要。先确认 `passed=true`、`missing_faces=0`、`validation_mismatches=0`，再比较时间；一个错误但很快的结果没有优化意义。

## 8. 最近一次已验证基线

以下数据来自 2026-07-11 的固定性能套件，测试环境和 JVM 状态会影响绝对值，因此它们是本机回归基线，不是跨机器承诺。

| 场景 | 光斑数 | 平均 core | 平均 response | 结果 |
| --- | ---: | ---: | ---: | --- |
| sparse 完整重建 | 930 | 27.66 ms | 40.70 ms | 通过 |
| mixed 完整重建 | 2238 | 40.93 ms | 59.22 ms | 通过 |
| dense 完整重建 | 1986 | 48.09 ms | 81.38 ms | 通过 |
| mixed，仅功率变化 | 2238 | 1.24 ms | 17.67 ms | 通过 |
| mixed，仅颜色变化 | 2238 | 1.69 ms | 26.63 ms | 通过 |

缓存场景包含 14175 个依赖位置；功率和颜色样本都命中几何缓存并复用了外观计划及依赖快照。完整套件为 6/6 通过，没有结构缺面或强制校验不一致。

这说明外观拆分已经生效：对同一结构改变功率或颜色时，成本不再随体素扫描和遮挡构造增长。下一阶段不应继续在这条路径上做微小优化，而应缩小完整几何失效后的重算范围。

## 9. 已知限制

1. 任意六面模型的精确轮廓仍未解决；当前重点是轴对齐体素、楼梯和常见局部形状。
2. 侧面视觉片段不等价于完整遮挡体，不能把所有侧面补丁直接加入全局遮挡集合。
3. 矩形区域减法在复杂轮廓下仍可能产生较多碎片。
4. 模型方块内部沿传播方向的纵向面仍有未解决的通用表达问题，详见算法文档 7.5。
5. `missing_faces` 能定位到方块、面和期望区域，但它仍是结构预言机，不是任意模型的形式化证明。
6. 几何缓存当前按 owner 整体失效，没有深度后缀重算。
7. 性能测试为隔离样本会清理较大范围缓存，`response` 因而可能高于普通游戏更新。
8. `response` 不是客户端端到端计时；网络分片和实际绘制仍需用 `latest.log`、帧分析或客户端专用计时判断。
9. 进程首次创建日志文件或加载类时可能出现一次性尖峰，不应用单个冷样本评价稳态性能。

## 10. 下一阶段：深度切片与后缀失效

完整重建按传播深度从近到远扫描，天然适合保存前缀检查点。计划引入 `DepthSliceSnapshot`：

```text
GeometryCacheEntry
  key
  dependency snapshot
  final geometry templates
  appearance plan
  depth slices[0..maxDepth]

DepthSliceSnapshot
  completed depth
  remaining texture region
  accumulated template end index
  same-depth receiver/occluder facts
  side canonical state required by later depths
  dependency range for this prefix
```

当深度 `k` 的方块改变时：

1. 找到最早受影响的连续投影深度 `k`。
2. 复用 `< k` 的模板和 `k - 1` 检查点。
3. 恢复当时的剩余纹理区域及必要索引。
4. 只重算 `k..maxDepth`。
5. 完成验证后原子替换旧几何条目；中途失败则保留旧条目并退回完整重建。

实现顺序：

1. 先为深度桶生成不可变的形状事实，禁止缓存对象持有可变世界引用。
2. 保存足以继续扫描的最小检查点，不复制整个投影器工作集。
3. 增加 near/middle/far 三种单方块变化基准，确认重算深度随位置缩短。
4. 在测试模式中抽样执行“后缀结果 vs 强制完整重建”比较，要求片段、UV、顺序、依赖和缺面统计全部一致。
5. 稳定后再考虑不同 owner/光源之间的并行。并行任务只能消费主线程构造的不可变世界快照，不能直接并发读取可变关卡状态。

预期收益不是让冷启动消失，而是让普通搭建过程中一次较远方块变化不再支付整个 32 层锥体的扫描成本。

## 11. 维护地图

| 责任 | 主要文件 |
| --- | --- |
| 编译层缓存、预算与诊断 | `CompiledSpotLayer.java` |
| 世界投影与遮挡算法 | `VoxelSpotProjector.java` |
| 投影结果、计划和统计 | `SpotProjectionResult.java` |
| 正确性检查 | `SpotProjectionFormalProof.java` |
| 性能样本聚合 | `SpotProjectionPerformanceTracker.java` |
| 光斑记录及外观替换 | `SpotRecord.java` |
| 共享容量 | `SpotProjectionLimits.java` |
| 网络分片 | `SpotOverlayPayload.java`, `SpectralNetwork.java` |
| 客户端快照和绘制 | `ClientSpotCache.java`, `SpotRenderEvents.java` |
| 自动场景和套件 | `SpotProjectionTestCommand.java`, `SpotTestMode.java` |
| 测试物品 | `SpotTestItem.java` |

## 12. 提交前验证

光斑投影改动至少执行：

1. `full_suite`，确认所有 case 通过且无 `missing_faces`。
2. 比较最新一组日志中的完整重建和外观缓存数据。
3. `./gradlew.bat build`。
4. 将 `build/libs/spectralization-1.0.0.jar` 覆盖到实际测试实例的 `mods` 目录。
5. 比较源 jar 和目标 jar 的 SHA-256，确认测试的确使用本次构建。

如果只修改诊断字段，也必须更新 [LOGGING.md](LOGGING.md)；如果改变场景、通过标准或测试物品行为，也必须更新 [TESTING_PLAN.md](TESTING_PLAN.md)。
