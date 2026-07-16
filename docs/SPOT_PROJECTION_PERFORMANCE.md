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

下一轮联合路径以 `side_visibility_query_strategy=remaining_first_joined_prefix` 标识：
side receiver 先与 remaining polygon region 相交；结果为空时跳过 prefix index，非空时
以 surviving cell bounds 作为 prefix 空间查询和 blocker 筛选域。这个顺序同时减少
prefixWindow 构造、排序和无效 polygon subtraction。verbose validation 仍用原先的
sideWindow-wide prefix scan 生成最终可见覆盖参考，而不是让优化结果自证正确。

随后 front/remaining 联合路径以
`front_sweep_update_strategy=incremental_travel_segments` 和
`remaining_handoff_strategy=front_prefix_region_plus_tail` 标识。front group 不再反复
生成并扣除累计 prefix，而是只扣相邻 travel group 之间的 sweep segment；side pass
完成后，main remaining 接管已经扣过 prefix 的 front 临时 region，再扣最后 group
之后的 tail。形式验证把同一 cuboid 分成 1..32 段，并将分段扣除后的双向覆盖差与
一次 full sweep 扣除比较；实机收益仍以新日志为准。
verbose runtime validation 还会从 pre-depth remaining 加 full blockers 重建参考，并将
它与 prefix-region handoff + tail 的最终双向覆盖差纳入
`remaining_subtract_validation_*`。

下一组联合路径以
`front_receiver_query_strategy=travel_group_batch_reusable_workspace` 和
`remaining_bulk_finalize_strategy=integrated_compaction_before_region` 标识。前者把同一
front travel group 的矩形 receiver 批量交给 remaining index，并复用候选数组；后者
让 bulk subtraction 的输出列表直接进入 incremental compaction，压缩后才构造最终
Region。形式验证逐项比较 128 个 batch/scalar rectangle queries，并比较普通 bulk、
integrated compacted bulk 与 serial subtraction 的面积、probe coverage 和 disjoint cells。

接近当前 CPU 架构上限的一轮以 `polygon_clip_workspace=reused_per_operation`、
`polygon_subtract_output=append_into_reused_buffers` 和
`polygon_vertex_storage=owned_normalized_list` 标识。front batch、remaining bulk、serial
remaining 与 same-depth side prefix 都复用 half-plane point workspace；subtraction 直接
向已有 fragment buffer 追加结果。形式验证用 2048 组随机凸多边形交替复用同一个
workspace，并逐项比较 fresh/reused intersection 与 subtraction 的 polygon 顺序。

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
该模式现在按一 case/服务器 tick 的单光源同步测试通道运行：它保留真实投影、缓存失效、
依赖索引和 owner commit，但不把 production async 的多 tick 调度空隙计入测试墙钟时间。
因此 core 可继续与旧随机基线比较；response 改为同 tick rebuild+commit，只能在该测试模式
内部比较。吞吐验收使用 `cases_per_second` 和 `missed_completion_ticks`，20 TPS 下目标为
约 20 case/s、1000 case 约 50 秒。parallel 多光源基线不受此变化影响。

方向矩阵引入了相对朝向的随机楼梯生成规则，因此首次使用该规则取得的性能
数据应建立新基线，不与旧的绝对世界朝向随机场景直接拼接。

这个协议保护的不是某一个毫秒数字，而是“相同输入、相同输出和相同验证条件下的成本变化”。

## 9. 2026-07-12 串行收尾基线

最终发布候选使用构建哈希
`9A6332EFD6BDF789F5224A7EBD80BB50310BA4A97D7457A5288A37B7227C051C`，
对应最新日志对 `diagnostics_20260712_173050_UTC.log` /
`optical_compiler_20260712_173050_UTC.log`。所有 suite 均通过，诊断与 compiler
异常行均为 0。

### 9.1 匿名随机完整重建

| 负载与轮次 | cases | 平均 core | P50 | P95 | 平均 response | 平均 spots | 结果 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| lightweight 第 1 轮 | 1000 | 8.485 ms | 6.885 ms | 16.990 ms | 35.845 ms | 455.8 | pass |
| lightweight 第 2 轮 | 1000 | 6.955 ms | 6.582 ms | 10.332 ms | 37.495 ms | 452.5 | pass |
| stress | 1000 | 13.589 ms | 12.572 ms | 21.234 ms | 36.732 ms | 1011.1 | pass |

`random_stress` 每个场景只执行一次强制完整重建且不记录 seed，因此轮次之间的
场景复杂度和 JVM 状态都会波动。提交基线以第二轮 lightweight 为主，同时保留第一轮
和 stress 数字，不从匿名随机轮次单独宣称小幅收益。

### 9.2 最后一轮固定 stress smart 套件

| case | 平均 core | P50 | P95 | spots |
| --- | ---: | ---: | ---: | ---: |
| sparse | 14.972 ms | 14.996 ms | 18.332 ms | 912 |
| mixed | 13.018 ms | 11.716 ms | 19.973 ms | 1141 |
| dense | 15.576 ms | 15.421 ms | 20.641 ms | 1045 |
| cached power | 0.221 ms | 0.202 ms | 0.314 ms | 1141 |
| cached color | 0.300 ms | 0.272 ms | 0.510 ms | 1141 |

smart validation 完成 647 次结构验证，`boundary_missing=0`、
`structural_mismatches=0`。dense workload 为 3496 tiles、641 projectable tiles、
683 side windows、439 side quads、3511 dependencies；覆盖签名为
`edd4fcc3ae0b676e`，分片签名为 `65c9fe9d45f18bd4`。

dense 平均阶段耗时为：front pass 4.736 ms、side scan 3.746 ms（其中 side emit
2.797 ms）、remaining 2.594 ms、projection residual 0.824 ms。最后一个稳定串行
热点已从 side candidate 转移到 stress partial-geometry 的 front pass；这也是进入
不可变世界快照与光源/输出方向级并行前的收尾点，而不是继续改写 depth 递推。

optical compiler 共记录 695 次 lane execution；去除首个 108.581 ms 冷启动后，
热态平均 0.099 ms、P50 0.088 ms、P95 0.149 ms、最大 0.734 ms。聊天栏 response
仍受 Minecraft tick 调度影响，不能替代 projection core 指标。

### 9.3 本轮实现范围

- remaining-bounded 统一 front/side depth scan，并以 8192 次轴向形式检查证明不漏 tile；
- 单 optical cuboid 的 side candidate 直通路径，multi-box 继续使用完整暴露面并集；
- side rejection key/detail 仅在 verbose validation 中构造；
- side remaining 查询、prefix subtraction、sweep extrema 与 sweep prefilter 工作区按 depth 复用；
- front receiver 批量查询、增量 sweep segment、prefix-region + tail remaining handoff；
- reusable polygon clipping/subtraction workspace 与 integrated remaining compaction；
- depth suffix snapshot/cache、appearance-only plan 复用和对应性能诊断；
- `spot_test` 的模式与 lightweight/stress 负载分离，以及更清晰的 smart/random 报告。

扫描范围级的大数组预留在长随机测试后被撤销。最终代码只为实际 side candidate、
appearance surface 和输出记录提供工作量相关初始容量，避免为了单次固定 case 的扩容
收益增加 1000 次随机重建中的 GC 压力。
## 10. 2026-07-12 outgoing-node async checkpoint

完整与 suffix projection 现在可以按 outgoing node 进入专用有界 worker pool；depth、front、
side 与 polygon transaction 均保持串行。主线程按 tick budget 增量准备 node，每次准备最多
捕获一个 eager cone snapshot；appearance-only 与 validation 仍在主线程完成。

在新的游戏内数字被接受前，`0aa46c1e4b645357fa6a4d6498ef9ee45c212e14` 的串行
random stress P50/P95 约 12.572/21.234 ms、固定 dense P50/P95 约
15.421/20.641 ms 仍是对照。新的报告必须同时给出 snapshot、queue wait、aggregate worker、
commit、end-to-end、最大 tick stall、stale/coalesced/rejected 和 snapshot block count；只比较
worker core 不能证明服务器线程收益。

第一轮 3x3 数据已经同时触发两个条件：稳态 snapshot P50/P95 为 7.25/10.43 ms，九光源
每轮会重复覆盖约 `9 * 25216` 个高度重叠的位置；225 个稳态 job 被拆成 118 个 dispatch，
平均 wave width 只有 1.91。worker 本身的稳态 P50/P95 为 28.21/42.37 ms，但 1/9 光源
response P50 已是 150.3/715.0 ms，因此主成本是主线程 snapshot 与 tick 分批，而不是 depth
递推。

当前实现已进入共享 immutable section snapshot：`LevelTraceCache` 持有最多 256 个 16^3
copy-on-write page，job 保存 page 引用与精确 coverage bitset，commit 按 section version 验证。
日志新增 resolved/reused block 与实际 section-cache size。partial dispatch 的 fallback 从 100 ms
改为 4 tick；普通 wave 优先等到 worker width 或当前准备集合完整。下一轮仍需同时报告 snapshot
P50/P95、resolved/reused 比例、dispatch width 分布、worker 与 response，不能只看 core 下降。

第二轮 3x3 日志 `a6716268-a09f-497b-b3fe-57df38cce6ca` 完成 90/90 case、450 个
full rebuild，fingerprint mismatch、stale 和 coalesced 都为 0。共享 section page 将 snapshot
P50/P95 从 7.25/10.43 ms 降到 0.457/0.919 ms，facts reuse 为 98.806%，cache 峰值 53/256；
稳态 dispatch 平均 3.082、最大 7，四光源 case 已稳定达到 width 4。整批 response P50/P95
相应降到 248.95/361.28 ms。

该轮同时暴露两个调度问题，不能据此接受最终性能：executor 共拒绝 23 次已被
`availableSlots()` 认可的提交；completion queue 又在同一 tick 无预算地提交全部 ready batch，
使稳态 tick stall P50/P95/max 达到 3.58/26.70/41.97 ms。当前修复以原子 in-flight 计数作为
唯一准入上限，预启动固定 worker，并让 queue capacity 覆盖完整 in-flight 上限；parallel suite
将任何 `projection_submit_rejected > 0` 判为失败。server commit 现在按 8 ms optical-solver
deadline 和历史 commit EWMA 跨 tick 排队，下一轮必须同时确认 rejection 为 0、commit spread
有界增加且 tick stall 回落。cycle 4-5 的两样本 warmup median 改为两者平均，不再使用 lower
nearest-rank percentile。

第三轮日志 `diagnostics_20260713_000547_UTC.log` 在同一启动中完成两套 90/90；最新 run
`24ca9a76-f7a3-45f0-ac7c-409ec078ac07` 的 450 个 job 全部为 full rebuild，submission
rejection、fingerprint mismatch、stale 与 worker failure 均为 0。snapshot P50/P95 为
0.497/0.848 ms，facts reuse 99.286%，四个固定 worker、pool size 4、max in-flight/queue 8/8
均被日志直接确认。

commit deadline 将同 tick commit 最大值降到 1，使稳态 tick-stall P50/P95/max 从上一轮
3.58/26.70/41.97 ms 变为 4.00/13.37/20.77 ms；代价是 commit tick spread 随 source count
增长，九光源为 9 tick，response P50/P95 上升到 350.22/549.75 ms。单 owner commit
P50/P95/max 为 5.28/8.33/30.62 ms，因此下一优化点从 worker/snapshot 转移到 commit 内部。

当前候选增加三条不改变发布语义的快路径：单 outgoing-node batch 省去 dependency/allocation
临时聚合副本；set-equal dependency snapshot 保留 reverse index；tracker 在 ordered compiled
input 与已安装 owner 完全一致时，于排序、长签名和 payload 构造前返回。日志把 commit 拆为
assembly、trace update、dependency index、owner publish，并记录 dependency/owner reuse；新的
90-case 实机结果出来前不预先声明收益。

### 10.1 2026-07-15 并行发布基线

最新配对日志为 `diagnostics_20260715_055008_UTC.log` / `optical_compiler_20260715_055008_UTC.log`。
stress random-stress 完成 1000/1000：core 平均 11.748 ms、P50 10.268 ms、P95 17.913 ms、
response 平均 100.602 ms、spots 平均 1010.015。相对 2026-07-12 匿名 stress 数字，core
平均/P50/P95 指示性下降 13.54%/18.32%/15.64%；两轮种子不同，因此不是固定输入收益证明。

parallel suite 完成 90/90，450 个测量 job 全部 full rebuild；fingerprint mismatch、boundary missing、
structural mismatch、stale、submission rejection 和 worker failure 均为 0。四 worker、max in-flight/
queue 8/8 得到实机确认。steady 汇总为 worker P50/P95 26.125/36.280 ms，response P50/P95
251.202/449.067 ms。单光源 worker/response P50 为 17.743/99.058 ms；九光源为
26.990/400.653 ms。response 的约 50 ms 阶梯和非单调 source-count 曲线表明主要延迟来自 tick
准备与 commit 调度，而不是 worker depth transaction。

459 次 async commit 的 snapshot 平均/P95 为 0.554/1.127 ms；平均每 job 复用 24706.7 / 25216
个 snapshot block。commit P50/P95 为 0.890/2.450 ms，但最大 31.708 ms，几乎全部记入
assembly；同一日志的 diagnostics 单次写入最大值为 31.847 ms，说明该尖峰被同步文件 I/O
污染。steady tick-stall P50/P95/max 为 1.220/3.627/24.794 ms，累计 commit budget deferred
为 287。九光源最终 14484 quads 的客户端渲染平均约 4.2--5.0 ms、最大 6.68 ms；这是极端
场景的次级成本，不能与普通单源负载混为一谈。

### 10.2 当前待实测 commit/logging 收尾

当前代码把所有 Spectralization 文件日志送入一个 1024-entry 有界单线程 writer；调用线程只做
事件格式化和入队，UTF-8 编码及文件写入离开 server tick。队列按接受顺序写入，饱和时丢弃诊断
而不阻塞 gameplay，并通过 enqueued/completed/pending/max-pending/dropped/failed 计数暴露。

`projection_commit_assembly_us` 现在排除 profile/budget 事件格式化与入队，后者单列为
`projection_commit_diagnostics_us`；总 `projection_commit_us` 与 budget EWMA 仍包含两者。单节点
batch 还按实际 primary/side 可见记录数预分配去重 map，避免约 1600-record 压力场景从默认容量
反复扩容。下一轮实机必须重新运行 stress random-stress 和完整 parallel suite，并同时确认：

- `diagnostic_writes_dropped=0`、`diagnostic_writes_failed=0`；
- assembly、diagnostics、total commit 的 P50/P95/max；
- same-tick commit 分布、commit spread、budget deferred 与 tick-stall；
- 90/90、fingerprint、stale/rejected 及全部结构门禁不退化。

在这些新日志出来前，不预先宣称 commit 或端到端 response 已改善。

### 10.3 2026-07-15 单 tick 随机通道与并行调度复测

最新配对日志为 `diagnostics_20260715_173537_UTC.log` /
`optical_compiler_20260715_173537_UTC.log`。stress random-stress 完成 1000/1000：wall time
50.400 s、19.841 case/s、completion span 1006 ticks、missed completion ticks 7；core
平均/P50/P95 为 11.780/10.231/20.356 ms，response 平均/P95 为 14.379/25.712 ms，平均
spots 999.943。该结果确认单光源测试通道已接近一 tick 一 case；7 个 missed tick 是压力场景中
单次主线程工作跨过测试调度点，而不是旧版固定四 tick 轮询。

parallel suite 完成 90/90，steady worker P50/P95 为 24.177/33.770 ms，response P50/P95
为 201.147/301.384 ms；fingerprint mismatch、missing face、structural mismatch、submission
rejection、diagnostic dropped/failed 与 compiler alarm 均为 0。async batch 的 snapshot
P50/P95 为 0.362/0.605 ms，worker P50/P95 为 25.135/36.750 ms，commit P50/P95 为
0.659/1.106 ms，其中 assembly P50/P95 0.377/0.575 ms、diagnostics P50/P95
0.135/0.364 ms。主线程阶段本身已经较轻。

真正限制 response 的是实机配置 `optical_solver.budget_micros=2000` 与 1 ms commit estimate
下限的组合：3/5/7/9 source 的 steady commit spread P50 分别为 1/2/3/4 tick，恰好形成约
50 ms 的响应阶梯；parallel suite 累计记录 195 个 budget-deferred tick。当前候选因此把 snapshot
preparation 与 commit 放入独立的 4 ms 软窗口，并把同 tick commit 上限设为 worker width 4。
它与通用 2 ms solver 窗口从同一时刻计时而非相加；单输出 preparation 还会在同一步跳过后续
非输出 graph tail。下一轮实机应重点确认 4-source commit spread 接近 0、5--8 source 不超过
1--2 tick、9-source 不超过 2 tick，同时保持 max tick stall、90/90 与 freshness 门禁不退化。

### 10.4 2026-07-15 4 ms 投影窗口与 worker 工作区复用

最新配对日志为 `diagnostics_20260715_191912_UTC.log` /
`optical_compiler_20260715_191912_UTC.log`。lightweight 与 stress parallel suite 均完成
90/90；fingerprint、missing face、structural mismatch、submission rejection、diagnostic
dropped/failed 与 compiler alarm 均为 0。两组 suite 自身的 stale 都为 0；同一启动会话更早
场景切换产生的两个累计 stale discard 不属于这两组测试。stress 仍只报告
`warmup_unstable`，不是正确性失败。

lightweight steady worker P50/P95 为 6.396/9.178 ms，response P50/P95 为
99.634/150.043 ms；stress steady worker P50/P95 为 29.303/41.470 ms，response P50/P95
为 101.160/200.549 ms。相对上一组 `183828` 相同测试，stress worker 平均从 33.187 ms
降到 32.179 ms，约下降 3.0%。其中 `plane_window_us` 从 5.788 降到 4.647 ms，约下降
19.7%；`side_prefix_query_us` 从 1.078 降到 0.899 ms，约下降 16.6%。`front_pass_us`
与 `side_scan_us` 分别为 8.916 与 11.768 ms，基本持平；P95 的细小回升属于目前的实机
波动范围，不能据此声称尾延迟改善。

这组结果验证了 full cuboid sweep 凸包工作区和 side prefix 查询容器复用确实命中了各自
局部阶段，但总时间已经受到 Amdahl 限制。当前下一候选继续保持 sweep polygon authority、
front-order 和原 cuboid 顺序不变：front prefix/segment 与 side same-depth prefix 改为复用
调用方持有的 depth-local 凸包工作区。`CuboidSweep` 不保存任何可变缓冲；每个输出 polygon
仍拥有独立的 normalized vertex list。形式证明逐项比较 fresh 与 reused prefix/segment 的
顶点顺序和完整 window 元数据。

在新的相同负载实机日志产生前，不宣称这一轮已经降低 `front_pass_us`、
`side_prefix_query_us` 或 worker 总时间。下一次比较必须继续保持 spots、dependencies、
tiles、projectable tiles、side windows、side quads 和全部结构门禁。

### 10.5 2026-07-16 lightweight 工作区复测与下一候选

最新配对日志为 `diagnostics_20260716_172604_UTC.log` /
`optical_compiler_20260716_172604_UTC.log`。本组只包含 lightweight parallel，没有
stress suite。90/90 case 全部通过；225 个 steady full-rebuild profile 全部记录
`sweep_prefix_segment_workspace=caller_owned_reused_per_depth`。fingerprint、boundary
missing、structural mismatch、stale、submission rejection、diagnostic dropped/failed、
compiler overflow/fallback/nonconverged/unstable/unreliable 均为 0。

suite summary 的 worker P50/P95 为 6.228/8.433 ms，相对上一组文档基线
6.396/9.178 ms 分别下降约 2.6%/8.1%；response P50/P95 为 100.250/151.613 ms，
与 99.634/150.043 ms 基本相同。steady raw worker profile 平均 6.578 ms；其中
`side_scan_us`、`plane_window_us`、`remaining_update_us`、`front_pass_us` 平均为
2.833、1.290、1.015、0.504 ms，`side_prefix_query_us` 已降为 0.284 ms。commit
P50/P95 为 0.412/0.608 ms，snapshot P50/P95 为 0.492/0.766 ms。response 仍主要由
tick 阶梯决定：1--4 source 约 52 ms，5--8 source 约 100--103 ms，9 source 约
152 ms。

下一轮待实测候选同时处理当前两个局部热点，但不改变 polygon authority：

- full sweep 在 hull 前先用同一 endpoint/waist radius extrema 计算保守 bounds；
  与 remaining bounds 无正面积交集的候选跳过排序、凸包和精确查询，其余候选仍执行
  原有 exact polygon prefilter；
- front/side rectangle query 的四次 half-plane clipping 复用两块 mutable point
  buffer，只构造最终 immutable Polygon；完全包含 cell 仍直接复用。

形式证明分别验证保守 bounds 包含精确 sweep polygon，以及 final-only rectangle
clip 与 generic polygon intersection 的逐顶点相等。在新的 lightweight 与 stress
实机日志产生前，不宣称 `plane_window_us`、`side_region_intersect_us` 或总 worker
时间已经改善。

### 10.6 2026-07-16 最终并行优化验收

本轮收尾采用的最新配对日志为 `diagnostics_20260716_174728_UTC.log` /
`optical_compiler_20260716_174728_UTC.log`。同一启动中先后完成 stress 与 lightweight
parallel suite，两组均为 90/90 pass、225 个 steady full-rebuild profile。fingerprint
mismatch、boundary missing、structural mismatch、suite 内 stale、submission rejection、
diagnostic dropped/failed 与 compiler overflow/fallback/nonconverged/unstable/unreliable
均为 0。stress 的 `warmup_unstable=true` 只描述 cycle 4--5 与 6--10 的预热漂移，不是
正确性失败。

| load | suite worker P50 | suite worker P95 | response P50 | response P95 | raw worker average |
| --- | ---: | ---: | ---: | ---: | ---: |
| lightweight | 5.494 ms | 8.051 ms | 99.137 ms | 150.010 ms | 5.791 ms |
| stress | 27.202 ms | 38.512 ms | 104.226 ms | 202.630 ms | 29.577 ms |

相对 10.5 的 lightweight raw average 6.578 ms，最终平均下降约 12.0%；suite worker
P50/P95 相对 6.228/8.433 ms 下降约 11.8%/4.5%。相对 10.4 的 stress raw average
32.179 ms，最终平均下降约 8.1%；suite worker P50/P95 相对 29.303/41.470 ms
下降约 7.2%/7.1%。response 没有等比例下降，仍主要表现为 Minecraft tick、worker wave
与 commit budget 的约 50 ms 阶梯。

最终两个局部候选均在全部 steady profile 中启用：

```text
sweep_bounds_prefilter=before_hull
rectangle_clip_materialization=final_polygon_only
```

lightweight 每个 profile 在 hull 前平均跳过 24.622 个 sweep，约占候选的 6.153%；
stress 平均跳过 4.533 个，约占 0.350%。该 broad phase 在轻量场景收益更明显，压力场景
仍主要依赖构造 polygon 后的 exact remaining prefilter。

与前一文档基线相比：

- lightweight `front_intersect_us` 下降约 30.8%，`side_region_intersect_us` 下降约
  25.2%，`side_scan_us` 下降约 14.8%，`remaining_update_us` 下降约 9.3%；
- stress `front_intersect_us` 下降约 22.0%，`side_region_intersect_us` 下降约
  21.1%，`side_scan_us` 下降约 8.4%，`remaining_update_us` 下降约 7.6%。

客户端最终九光源场景也给出了当前渲染边界：lightweight 的 3168 active quads 通常约
1.1--1.3 ms/frame；stress 的约 14485 active quads 约 5.2 ms/frame。静态普通场景仍以
投影生成和更新延迟为主，但大量同时可见光斑已经会占用明显帧预算。客户端 retained scene、
持久化 buffer 与跨 owner 增量更新不在本轮实现范围，统一记录在
`SPOT_PROJECTION_ARCHITECTURE_V2.md`。

最终代码审计在该日志之后补了两个不改变默认 4 worker / 8 in-flight 性能路径的边界修复：

- 波次宽度与同 tick commit 上限改用执行器夹紧后的实际 worker 数，避免合法的
  `max_in_flight < workers` 配置等待不可达到的宽度；
- chunk load/unload 在 enqueue projection refresh 前保守地失效受影响 owner 的 geometry
  cache，避免错误复用 `appearance_only`。

这两个收尾修复由 `verifySpotProjectionContinuity` 静态守门和完整 Gradle check/build
保护；本节的实机毫秒数据仍来自上述修复前、默认配置相同的最终成对日志，不据此重新声明
额外性能收益。
