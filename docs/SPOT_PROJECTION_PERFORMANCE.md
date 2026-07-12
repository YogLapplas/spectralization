# 光斑投影性能基线

本文记录光斑投影的可重复性能基线、典型工作量和后续优化的比较规则。算法语义仍以
[SPOT_PROJECTION_ALGORITHM.md](SPOT_PROJECTION_ALGORITHM.md) 为准；本文中的数字只描述当前实现和当前测试机，不定义玩法结果。

## 1. 基线来源

当前基线来自 2026-07-11 最新完成的一组测试日志：

```text
diagnostics_20260711_162114_UTC.log
optical_compiler_20260711_162114_UTC.log
```

时间更新的 `20260711_170452_UTC` 日志组为空文件，没有性能样本，因此不作为基线。

这次启动中连续运行了两次 `full_suite`。本文以第二次套件
`run_id=b648c5f6-e9ae-4d96-9607-73046ab1d9b3` 为典型进程内稳态样本。第一次套件保留为 JVM、类加载和日志预热参考，不参与稳态优化结论。

所有性能 case 均通过；完整套件结果为 6/6，没有 boundary missing、结构验证不一致或缓存路径错误。

> 该日志组来自 sampled-plane authority 版本。生产代码现已切换为每 cuboid 一个
> analytic polygon sweep，因此这些数字只保留为迁移前基线；必须完成新的
> `partial_geometry` 与 `performance` 实机套件后才能给出 sweep 版本结论。

## 2. 测试输入

公共输入：

- 光源功率：300 SP。
- 相干性：`INCOHERENT`，用于散杂光压力场景。
- 初始半径：0.5 block。
- 投影最大深度：32 blocks。
- 末端白色接收屏：深度 16。
- 兼容遮挡平面配置：5；sweep 版本中该值不再改变生产遮挡，每个 cuboid 的
  `plane_count_effective` 为 1。
- 完整重建 case：每项 5 个样本。
- partial geometry：3 个样本并启用 targeted verbose validation。
- 缓存 case：先进行不计时的几何暖机和外观暖机，再记录 5 个 `appearance_only` 样本。

场景参数：

| Case | Seed | 占用率 | 发散 | Fixtures | 放置方块 | 模式 |
| --- | ---: | ---: | ---: | --- | ---: | --- |
| partial geometry | 42 | 0% | 0.50 | 是 | 118 | 完整重建 + 验证 |
| sparse | 42 | 8% | 0.50 | 是 | 146 | 强制完整重建 |
| mixed | 20260711 | 17% | 0.50 | 是 | 215 | 强制完整重建 |
| dense | -6495254288592929499 | 30% | 0.65 | 是 | 313 | 强制完整重建 |
| cached power | 20260711 | 17% | 0.50 | 是 | 215 | mixed 几何 + 功率变化 |
| cached color | 20260711 | 17% | 0.50 | 是 | 215 | mixed 几何 + 颜色变化 |

功率序列为 75、150、300、450、225 SP。颜色序列使用固定可见光 bin：2、13、23、29、18。

## 3. 典型稳态耗时

`core` 是投影/外观计算本身；`response` 还包含服务器 tick 边界、测试清缓存、刷新调度和同步日志。它们不能混为同一个指标。

| Case | 平均 core | P95 core | 平均 response | P95 response | 光斑数 | 结果 |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| partial geometry | 64.38 ms | 85.14 ms | 83.08 ms | 118.36 ms | 404 | pass |
| sparse rebuild | 27.66 ms | 39.14 ms | 40.70 ms | 54.17 ms | 930 | complete |
| mixed rebuild | 40.93 ms | 45.65 ms | 59.22 ms | 67.43 ms | 2238 | complete |
| dense rebuild | 48.09 ms | 54.69 ms | 81.38 ms | 85.46 ms | 1986 | complete |
| cached power | 1.24 ms | 1.43 ms | 17.67 ms | 19.85 ms | 2238 | complete |
| cached color | 1.69 ms | 2.39 ms | 26.63 ms | 44.49 ms | 2238 | complete |

partial geometry 启用了验证探针，不能和普通完整重建直接比较。它每次执行 165 次 footprint integral，并包含约 3.95 ms 的 side debug audit；普通性能 case 的这两项均为零。

同一进程的第一次套件中，sparse、mixed、dense 的平均 core 分别为 63.14、79.97、61.01 ms。这个差值说明 5 样本的小窗口仍会受 JVM 和首次日志路径影响。项目当前不以冷启动为主要优化目标，因此提交间比较应至少连续执行两次套件，并以第二次作为主要基线。

## 4. 典型工作量

| Case | 扫描 tiles | Projectable tiles | 依赖位置 | Side windows | Side quads | 输出光斑 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| sparse | 14168 | 141 | 14173 | 618 | 512 | 930 |
| mixed | 14168 | 202 | 14175 | 2441 | 1591 | 2238 |
| dense | 19008 | 299 | 19023 | 3532 | 1520 | 1986 |

解释：

- sparse 和 mixed 的扫描范围几乎相同，差别主要来自 projectable tile 和 side window 数量。
- dense 使用更大的发散，扫描范围从约 14.2k 增到 19.0k。
- 输出光斑数不是工作量的单调代理。dense 的输入更多，但因为遮挡、合并和提前消耗纹理区域，最终光斑少于 mixed。
- 依赖数接近扫描 tile 数，说明当前完整几何条目仍按整个检查锥体持有依赖。

## 5. 完整重建阶段耗时

下表为平均值。`side_emit` 是 `side_scan` 内的重要子阶段，不能与 `side_scan` 相加后当作独立总耗时。

| Case | Front pass | Side scan | 其中 side emit | Remaining update | Projection residual |
| --- | ---: | ---: | ---: | ---: | ---: |
| sparse | 10.19 ms | 6.93 ms | 4.01 ms | 2.96 ms | 4.05 ms |
| mixed | 12.32 ms | 15.92 ms | 12.01 ms | 3.64 ms | 5.03 ms |
| dense | 8.20 ms | 21.48 ms | 15.21 ms | 5.86 ms | 6.33 ms |

当前判断：

1. 随着局部方块密度增加，side scan/emit 是最稳定增长的主要成本。
2. front pass 不随方块数量单调增长，因为 earlier occupancy 会改变后续 remaining region 和候选存活数量。
3. dense 的 remaining update 和未归因 residual 也已达到 5–6 ms，值得在 side 路径之后继续拆分。
4. 普通性能 case 的 `footprint_integral_calls=0`、`side_debug_audit_us=0`，说明这些数字不是 verbose 验证构造造成的。

## 6. 外观缓存阶段耗时

mixed 几何包含 2238 个模板、220 个唯一接收表面和 14175 个共享依赖位置。

| Case | Core | Appearance total | Prepare | Surface build | Record update | 外层 residual |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| power | 1.24 ms | 1.20 ms | 0.08 ms | 0.25 ms | 0.86 ms | 0.04 ms |
| color | 1.69 ms | 1.64 ms | 0.07 ms | 0.37 ms | 1.18 ms | 0.06 ms |

两个 case 均满足：

```text
full_rebuild_samples = 0
appearance_only_samples = 5
appearance_templates = 2238
appearance_unique_surfaces = 220
dependencies = 14175
```

`record update` 占外观路径的大部分时间，但整条路径已经只有约 1–2 ms。除非将来模板数达到当前数量级的数倍，否则它不是下一阶段的首要目标。

## 7. 后续优化优先级

### 7.1 第一优先级：用单 sweep 恢复 cuboid 遮挡连通性

当前 sampled occlusion planes 不是连续体积。两个只共用一条棱的 cuboid 仍可能在
离散截面之间留下亮线，因此下一次大改首先是正确性迁移，而不是继续减少局部对象：

```text
one OpticalCollisionBox
  -> one precomputed CuboidSweep
  -> broadPhaseBounds + fullShape + prefixShape(travel)
```

性能约束与正确性约束同等重要：

- sweep 每个 cuboid 只构造一次；
- 不能在每个 side/front candidate 查询中重新遍历所有 cuboid；
- 一个 sweep 通常应替代当前每 cuboid 多个 sampled plane window，而不是叠加在其上；
- downstream 和 same-depth prefix 必须共享 sweep 数据及索引；
- 中间平面不再决定拓扑正确性；
- 比较新旧实现时必须同时报告 blocker/sweep 对象数、remaining 碎片数、spots 和完整重建耗时。

单 sweep 的 axis-aligned bounds 仅用于 broad phase；解析凸多边形才是遮挡 authority。
性能比较必须同时确认斜边仍存在，不能通过把 polygon 退化成 bounds 获得提速。
当前基线仍属于 sampled-plane 实现；在 sweep 落地并建立新基线前，不能把两者的
绝对窗口数或耗时直接视为同一种工作量。

### 7.2 sweep 之后：完整几何的 side 路径

目标是减少产生相同 side windows 和 side quads 所需的候选访问、临时集合及重复形状计算。任何优化都必须保持：

- side candidate 集合不缺面；
- same-depth prefix 顺序不变；
- receiving cuboid 自身排除规则不变；
- canonical region 与 legacy 验证结果一致；
- 输出顺序、UV 和依赖集合不变。

当前待实机复测的一批实现只缩短对象生命周期，不改变上述语义：

- 半径求值直接计算与完整 envelope 传播相同的二阶矩结果，避免构造只为读取
  `radius` 的临时 `BeamEnvelope`；
- 每个 source output 只准备一次半径传播的 `xx / xt / tt` 系数；每个 depth slice
  使用轻量 offset view，side candidate、解析 travel 区间、travel subdivision 和
  analytic cuboid sweep 共享该全局求值器；
- side interval 把原 cross-section 谓词化简为单一半径阈值，并在腰点两侧直接求解
  `xx + 2*z*xt + z*z*tt = threshold²`。生产路径已移除固定 16 点采样和 24 轮
  边界二分；形式证明完成 132995 次解析区间 membership 检查。该批次尚未取得
  新的两轮实机日志，因此不能先写成确定性能收益；
- 一个 side candidate 只有在产生首个 visible window 时才查找 surface
  appearance，并在该候选的后续窗口中复用结果。

这批改动已经加入 full-envelope / radius-only 等价性验证，但在取得新的两轮
`performance` 日志前不记录为确定性能收益，也不替换本页基线。

### 7.3 按深度复用几何

当前真实游戏更新已经保留 owner 几何条目的深度前缀：依赖位置变化会标记最早受影响深度，`DepthSliceSnapshot` 恢复 `k - 1` 的 remaining-region 证书、模板前缀和依赖前缀，只重算 `k..end`。现有 full-rebuild case 仍故意删除完整条目，不与后缀样本混算。下一项验证工作是增加 near、middle、far 三组局部变更基准，并记录：

```text
earliest_invalidated_depth
reused_depth_slices
rebuilt_depth_slices
snapshot_restore_us
suffix_projection_us
forced_full_compare_mismatches
```

### 7.4 解析多边形 remaining region 的查询基线

解析 sweep 斜边版本在 `diagnostics_20260711_234522_UTC.log` 中连续完成两轮
匿名 1000 场景压力测试，均为 1000/1000 通过：

| 轮次 | core 平均 | P50 | P95 | response 平均 | 平均 spots |
| --- | ---: | ---: | ---: | ---: | ---: |
| 第一轮 | 25.45 ms | 22.72 ms | 43.47 ms | 84.33 ms | 1389.78 |
| 第二轮（热态） | 23.82 ms | 22.82 ms | 34.02 ms | 37.34 ms | 1395.39 |

第二轮之后的代表性完整重建为 47.59 ms，其中 `side_scan_us=23.73 ms`、
`remaining_update_us=7.97 ms`、`front_pass_us=6.39 ms`；remaining 峰值为
222 cells / 851 vertices，精确查询累计检查 134474 cells / 511207 vertices。
这是 depth-local region index 与严格凸 cell compaction 之前的直接比较基线。

新实现必须保持 spots、依赖、方向覆盖签名、斜边和所有 correctness gate；性能判断
同时读取 `remaining_index_*` 与 `remaining_compaction_*`，确认提速来自候选减少，
而不是少算 polygon 或填平斜边。

第一轮 region query index 的热态结果为 core 20.49 ms、P95 28.95 ms；代表性
完整重建中 cell/vertex 精确检查下降约 99.1%，但 `remaining_blocker_apply_us`
仍为 5.73 ms。cell-major blocker spatial join 的随后热态 1000 场景结果为
core 14.98 ms、P50 14.58 ms、P95 18.92 ms；相对 query-index 基线分别下降
26.9%、26.1% 和 34.7%，平均 spots 保持在 1004.42。代表性完整重建中
`remaining_blocker_apply_us=2.86 ms`，其中 index build/query、exact subtraction、
compaction 分别约为 0.06/0.11/1.53/0.89 ms。新增字段为：

```text
remaining_blocker_index_build_us
remaining_blocker_index_query_us
remaining_blocker_subtract_us
remaining_compaction_us
remaining_blocker_index_candidates
remaining_blocker_exact_tests
```

当前未实测的下一批仅收紧对象生命周期：生产 front 路径不再构造 verbose-only
矩形与 debug 去重集合；单碰撞箱方块直接采用其完整 front/side 面；side interval
不再重复复制；compaction 在保持原候选发现顺序的前提下即时执行贪心合并；blocker
没有改变 cell 时不再分配 replacement list；sweep/compaction 共用的凸包入口改用
预分配列表与静态排序器，且无空 blocker 的常态直接复用输入列表。下一次比较以 14.98 ms / 18.92 ms
作为热态基线，并确认 spots 约 1000、覆盖签名和全部 validation gate 没有倒退。

随后一次两轮匿名压力测试的热态结果为平均 16.22 ms、P50 15.08 ms、P95
23.76 ms。固定代表场景却从 16.26 ms 降到 15.81 ms，其中 side candidate 下降
23.8%、front pass 下降 6.6%；remaining blocker apply 上升 7.8%，未归属 residual
上升 22.8%。因此下一批把精确 half-plane subtraction 的 inside/outside 双遍历合并
为单遍历，并移除内部新建 snapshot 数组与最终 dependency accumulator 的重复复制；
新增 snapshot build/finalize 计时，用来区分真实 polygon 成本与缓存证书收尾成本。

### 7.4.1 blocker subtraction 缓冲与增量 compaction

解析 side travel 后的热态 1000 场景结果为平均 15.01 ms、P50 14.12 ms、P95
22.30 ms。代表场景中 `side_travel_split_us` 已降到 0.22 ms，而
`remaining_blocker_apply_us` 为 6.44 ms，其中 exact subtraction 约 4.01 ms、
compaction 约 1.66 ms，因此下一批实现集中在 remaining transaction：

- cell-major subtraction 在所有 source cell 之间复用两块 fragment list，只交换
  缓冲区引用，不改变 blocker 或 fragment 顺序；
- convex compaction 使用 versioned incremental edge queue，只为 replacement cell
  更新共享边候选，同时保留 shared-edge 与 hull-area exact gate；
- profile 通过 `remaining_subtraction_buffers=reused_pair` 和
  `remaining_compaction_strategy=incremental_edge_queue` 标识新路径。

形式证明当前为 `polygon_sweep=1615`，其中包含 2×2 cell 的三级增量 merge cascade。
在取得新的两轮实机压力测试前，这一实现不计为确定性能收益。

### 7.4.2 矩形 remaining 查询与 side prefix 临时对象

上一批实机热态结果为平均 14.33 ms、P50 13.52 ms、P95 19.97 ms。代表场景的
`side_remaining_intersect_us=1.64 ms`、`side_patch_emit_us=1.06 ms`，因此下一批保持
polygon authority 不变，只收紧 receiver 数据流：

- remaining 与 axis-aligned receiver window 相交时，完全落在窗口 bounds 内的 convex
  cell 直接复用，只有边界 cell 执行 half-plane clipping；
- same-depth prefix subtraction 使用两块 polygon fragment buffer，避免为每个
  occluder 重建中间 `Region` 的 bounds、area 和 immutable cell snapshot；
- 非 allocation/debug 路径不再生成 visible polygon bounds list 或单 polygon bounds；
- polygon patch 临时列表不再执行发送前的第二次 immutable copy。

对应 profile 标识为 `remaining_intersection_strategy=rectangle_full_cell_passthrough`、
`side_prefix_subtraction_buffers=reused_pair` 和 `side_debug_bounds=on_demand`。形式验证
将矩形专用 intersection 与通用 polygon intersection 比较，并在 4096 个 canonical
side visibility 随机场景中比较双缓冲 prefix subtraction 与逐 blocker `Region` 参考。
实机结果出来前不预先记录收益。

### 7.5 暂不优先

- 冷启动和第一套件的 JVM 暖机。
- 已降到 1–2 ms 的 appearance-only 路径。
- 客户端 quad 提交；现有证据仍指向服务端投影生成。
- 不同光源之间的并行；可以做，但应在不可变输入快照边界清晰后再接入。

## 8. 优化比较协议

每次声称性能改善前：

1. 使用 `spot_test` 的 `partial_geometry` 运行一次，再将 `performance` 连续运行两次。
2. 只读取这次启动中最新的一组 diagnostics / optical compiler 日志。
3. 先确认 6/6 通过、`boundary_missing=0`、`structural_mismatches=0`。
4. 缓存 case 必须保持 5/5 `appearance_only`、0 次 full rebuild。
5. 主要比较第二套件的平均 core 和 P95 core；response 单独报告。
6. 同时报告 spots、tiles、projectable tiles、dependencies、side windows 和 side quads，防止通过少算结果伪造提速。
7. 5 样本下小于约 10% 的变化先视为待复现，不立即写成确定收益。
8. 若改变测试场景、采样边界或日志计时范围，建立新基线，不和旧数字直接拼接。

判断世界方向是否影响性能时，使用 `direction_matrix`，不要手工依次转向运行
四套普通测试。该模式会先以四个方向各 16 个样本稳定化，再让 4 个不同 seed 各自经过四个方向，并用
平衡顺序抵消执行位置。每个 seed 的 `scene_signature` 与最终输出覆盖签名必须在
四方向一致，这是正确性门槛。输出分片签名和 spots、dependencies、tiles、
projectable tiles、side windows、side quads 是性能可比性指标；它们不一致时套件
以黄色 `pass_with_asymmetry` 完成，方向耗时仍需连同 workload 差异一起解释。

测试屏幕和清理体积在 2026-07-11 后扩大到完整覆盖深度 16 的默认扩散锥；这会改变
spots、projectable tiles 和绝对耗时。扩大前的随机/方向数字只能作为历史参考，不能
与新基线直接计算性能收益。

需要观察随机结构长尾时使用 `random_stress`。它运行 1000 个匿名纯随机场景，
每个场景只测一次强制完整重建；seed 不记录，结果只按每 100 次进度和最终
平均/P50/P95 聚合。该模式适合发现长尾和异常分布，不替代固定 seed 的提交间基线。

方向矩阵引入了相对朝向的随机楼梯生成规则，因此首次使用该规则取得的性能
数据应建立新基线，不与旧的绝对世界朝向随机场景直接拼接。

这个协议保护的不是某一个毫秒数字，而是“相同输入、相同输出和相同验证条件下的成本变化”。
