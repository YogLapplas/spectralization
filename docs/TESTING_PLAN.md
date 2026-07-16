# Spectralization 本地测试计划

本文档规定本地开发时如何测试 Spectralization。它不是某一次测试报告，而是测试流程和通过标准。每次实现新功能、修复 bug、准备 commit 或 push 前，都应按本计划选择对应测试层级。

## 1. 测试目标

测试的目标不是证明系统永远正确，而是尽早发现三类问题：

1. 构建和资源加载问题：游戏能否启动，方块、物品、模型、材质、语言和配置是否可加载。
2. 事件边界问题：方块放置、破坏、旋转、连接、剪断、压缩和多方块认证是否正确诱导 dirty。
3. 光学结果问题：功率、频率、相干/散杂、颜色、读数、可靠性和可视化是否符合不变量。

Spectralization 的核心是事件驱动的三层光学网络编译器，所以测试必须优先验证几何层、拓扑层和数据层的边界。

## 2. 测试层级

| 层级 | 什么时候跑 | 目标 |
| --- | --- | --- |
| 构建测试 | 每次代码改动后 | 确认 jar 能生成。 |
| 资源冒烟测试 | 新增方块、物品、模型、材质、GUI 后 | 确认游戏能进入世界，物品和方块能显示。 |
| 系统冒烟测试 | 新增或修改玩法后 | 确认主流程能跑通。 |
| 回归测试 | 修复 bug 或改动编译器、光纤、微缩机器、全息存储后 | 确认旧 bug 没复发。 |
| 压力测试 | 改动调度、缓存、渲染或大网络逻辑后 | 确认可玩性能和日志规模。 |
| 发布前测试 | commit/push 或交给 PCL2 测试前 | 确认构建产物、日志和已知风险。 |

## 3. 本地构建测试

每次代码改动后运行：

```powershell
.\gradlew.bat build
```

通过标准：

- Gradle build 成功。
- 生成 jar：

```text
C:\Users\13501\IdeaProjects\spectralization\build\libs\spectralization-1.0.0.jar
```

如果需要在 PCL2 中测试，将 jar 覆盖到：

```text
D:\Release 2.3.0\sins&tec\.minecraft\versions\1.21.1-NeoForge_21.1.233\mods\spectralization-1.0.0.jar
```

构建失败时，不进入游戏测试。先修复编译、数据生成、资源路径或 mixin/注册错误。

## 4. 游戏内冒烟测试

适用场景：

- 新增方块、物品、方块实体、菜单或 screen。
- 修改模型、材质、blockstate、item model 或渲染层。
- 修改注册逻辑、tag、数据生成或配置。

最小流程：

1. 启动客户端并进入测试世界。
2. 打开创造物品栏或使用指令获得新增物品。
3. 放置新增方块，确认模型、材质、朝向、亮度和碰撞箱。
4. 破坏方块，确认不崩溃、不重生、不遗留错误状态。
5. 右键交互，确认 UI 或聊天反馈正常。
6. 退出世界再进入，确认状态能保存和恢复。

通过标准：

- 能进入世界。
- 没有 missing model、missing texture、registry 或 resource reload 错误。
- 方块 item 形式和世界形式都正确。
- 右键、破坏、旋转或红石状态不会崩溃。

## 5. 光学编译器回归测试

适用场景：

- 修改 port graph、solver、readout、profile、频率、相干/散杂、颜色 LUT 或可靠性。
- 修改光源、传感器、镜子、分光镜、透镜、涂层或场源。
- 修复功率跳变、颜色不稳定、读数 stale 或可靠性问题。

基础场景：

1. 单光源 -> PTS。
2. 单光源 -> CMOS。
3. 单光源 -> 光谱仪。
4. 单光源 -> 分光镜 -> 两个传感器。
5. 两个光源 -> 合流 -> 一个传感器。
6. 红宝石激光腔 -> 输出镜 -> 传感器。
7. 环境光收集器 -> 传感器。
8. 涂层表面改变前后各测一次。

内置命令：

```text
/spectralization opticaltest splitter_lens_splitter
/spectralization opticaltest lens_aperture_clip
/spectralization opticaltest fiber_radius_coupling
/spectralization opticaltest feedback_fiber_radius_loss
/spectralization opticaltest parallel_fiber_same_endpoint
```

第一个命令会生成：

```text
creative light source -> beam splitter -> lens holder -> beam splitter -> beam profiler
```

这是一个带反馈 SCC 的最小回归结构。通过时应同时满足：

- `latest.log` 出现 `Optical test splitter_lens_splitter PASS`。
- `optical_compiler_*.log` 出现 `stage=example_validation`。
- solver 为 `PROFILE_COLLAPSED_EXACT`。
- `profile_mode=collapsed_equivalence`。
- `readout_reliable=true`。
- profiler 读数和 expected 的差值在 tolerance 内。

其他命令覆盖 profile-sensitive gain：

- `lens_aperture_clip`：宽光束经过标准透镜时，孔径裁剪应降低输出功率。
- `fiber_radius_coupling`：基础光纤应让 `radius=0.25` 的输出接近 `radius=0.125` 的四分之一。
- `feedback_fiber_radius_loss`：光纤位于反馈 SCC 内时，宽光束仍应显著低于窄光束。
- `parallel_fiber_same_endpoint`：同端点并联光纤不能降低输出，也不能超过 1.0 总 gain。

必须检查的不变量：

- 被动网络不凭空增加总功率。
- 多输入合流时，输出功率等于输入线性叠加，扣除明确损耗。
- 单输入分流时，输出总功率不能超过输入总功率。
- 同一束混合光在有无传感器时颜色一致。
- 频率到颜色必须使用统一 LUT。
- 拓扑变化后，旧 readout 不应继续被当作 reliable 新值。

建议日志：

```toml
[diagnostics]
event_log = true

[optical_compiler]
debug_log = true
debug_log_verbose = false
```

只有摘要不够时才打开 verbose。

## 6. 光纤系统测试

适用场景：

- 修改光纤连接、寻路、剪断、relay、接口 IO、容量、过载或渲染。

基础场景：

1. 两个接口直接连接，确认输入端到输出端传光。
2. 无法直连时通过 relay 连接。
3. 同一对节点连接多条光纤，确认容量随平行连接自然增加。
4. 拿光纤剪分别点击两端，剪断指定连接。
5. 在光纤路径中放置非空气方块，确认连接断开。
6. 剪断或遮挡后，确认光学网络不再通过这条光纤传光。
7. 多输入单输出：输出功率等于输入合计。
8. 单输入多输出：输出均匀分配。

通过标准：

- 光纤连接始终是 1 对 1。
- relay 不直接参与光学网络。
- 光纤变化会诱导相关光学网络 dirty。
- Jade 连接数显示正确。
- 断开的连接不会继续传光。

关键日志事件：

- `connection_added`
- `connection_removed`
- `connections_removed`
- `overload`

## 7. 全息存储测试

适用场景：

- 修改核心、水晶、screen、多方块认证、容量、存储 UI、同步或合成台。

基础场景：

1. 单核心 + 相邻水晶，确认水晶进入连接状态。
2. 核心周围扩展多个水晶，确认容量和类型容量变化。
3. 放置第二个过近核心，确认 error 状态。
4. screen 贴在已连接水晶上，确认可打开存储 UI。
5. screen 贴错方向或贴在未连接水晶上，确认显示 unconnected。
6. 存入物品、取出物品、右键取半组、右键放一个。
7. 关闭 UI 再打开，确认内容立即显示。
8. 人为制造容量不足，确认 UI 只读锁定。

通过标准：

- 多方块识别不错误吞并其他系统。
- 存储内容和 UI 实时同步。
- 容量不足时能看到内容，但不能存入或取出。
- 核心 UI 和存储终端 UI 的职责不混淆。

关键日志事件：

- `holographic_storage multiblock_refresh`

## 8. 微缩机器测试

适用场景：

- 修改 anchor/core/io_port、框架认证、工作区、压缩、microlized_machine、旋转、面贴图、预览或动画。

基础场景：

1. 放置两个 anchor，确认同轴最近连接和光束显示。
2. 形成长方体框架，确认 core UI 显示框架信息。
3. 框架内没有且仅有一个 core 时，确认系统 valid。
4. 多 core 或无 core，确认 error。
5. 点击 core UI 的工作区显示按钮，确认区域显示不闪烁。
6. 在输出槽启动压缩，确认工作区清空并输出 microlized_machine。
7. 右键输出槽，确认能看到压缩内容摘要。
8. 放置 microlized_machine，旋转它，确认光学 IO 方向立即更新。
9. 修改面颜色，确认外观立即刷新且不诱导光学网络变化。
10. 打开 microlized_machine UI，确认结构预览和 N 标记正确。

光学场景：

1. 压缩直线光路：输入面到输出面。
2. 压缩红宝石激光器：确认主动光源输出。
3. 压缩环境光收集器：确认散杂光输出。
4. 压缩光纤接口相关结构：确认边界行为正确。
5. 将压缩后的机器再次接入外部光路。

通过标准：

- 工作区域和机器占用区域不混淆。
- 压缩前后，只有通过 io_port 的光学边界保留。
- 旋转改变实际光线 IO 方向。
- 旋转后不需要退出世界再进入。
- 压缩动画结束前后不会重复清空或重复输出。

关键日志事件：

- `microlizer network_refresh`
- `microlizer anchor_connection_added`
- `microlizer anchor_connection_removed`
- `microlizer microlizing_started`
- `microlizer work_area_cleared`
- `microlizer microlizing_finished`

## 9. 渲染和性能测试

适用场景：

- 修改 spot、beam overlay、光纤渲染、微缩机器区域显示、透明方块渲染、BER/EBR 或动画粒子。

测试场景：

1. 单个方块近距离观察。
2. 9x9x9 同类方块压力观察。
3. 多条光束和 spot 同时可见。
4. 光纤网络跨多个 relay 渲染。
5. 微缩机器压缩动画播放。
6. 打开和关闭 debug HUD。

通过标准：

- 没有明显空模型、错 UV、黑色滤镜或透明排序灾难。
- 大量实例不会把帧率压到不可玩。
- debug 可视化关闭后成本明显降低。
- 渲染错误不会影响服务器逻辑。

如果某个效果需要大量面数或每帧高成本计算，优先降级视觉效果，而不是牺牲基础玩法。

## 10. 光斑投影自动测试

光斑算法、缓存、网络容量或渲染发生变化时，优先使用注册物品 `spectralization:spot_test`。测试者需要权限等级 2：

- 右键运行当前测试模式。
- 对空气左键切换独立的 `lightweight` / `stress` 负载档位。
- Shift + 右键切换 `smart`、`quick`、`partial_geometry`、`performance`、`direction_matrix`、`random_stress`。
- 套件运行时再次右键只显示进度，不启动重叠任务。

模式覆盖：

1. `smart`：先做 8 次不计入报告的预热，再用 verbose 结构验证把正确性设为硬门槛；通过后分别采集 sparse、mixed、dense 各 12 个样本，以及功率/颜色缓存各 7 个样本。
2. `quick`：物品注册、结构生成、投影刷新和日志冒烟。
3. `partial_geometry`：楼梯、台阶和局部方块的踏面/立面遮挡顺序。
4. `performance`：固定 sparse、mixed、dense 完整重建，以及功率/颜色外观缓存。
5. `direction_matrix`：四方向稳定化预热后，以 4 个固定 seed 和平衡顺序验证输入场景、最终输出覆盖、输出分片及内部 workload。输入/覆盖不一致为失败；只有分片/workload 不一致为黄色性能警告。
6. `random_stress`：运行 1000 个不含固定夹具的匿名纯随机场景；每个场景测量一次完整重建，不记录 seed，只输出聚合进度和最终平均/P50/P95。它使用单光源专用的同步 tick 通道，每个服务器 tick 完成一个场景；多光源/parallel 路径不参与该调度。

负载档位不改变模式、case 数、样本数、repeat 数或匿名 seed 规则：

- `lightweight`（默认）：非正确性专用场景移除固定夹具，并将随机障碍替换为完整方块；用于模拟普通玩法中局部方块不会持续高频变化的负载。
- `stress`：保留偏重楼梯、台阶、栅栏的随机障碍和固定夹具；用于投影算法压力回归。
- `partial_geometry` 以及 smart 的 verbose 正确性 case 在轻量档仍保留局部方块，因为这些 case 的目的就是验证局部几何。

`smart` 的聊天摘要使用 core P50/P95，报告 dense 场景中耗时最大的阶段、P95/P50
稳定性、结构检查数和缓存 P50。相同玩家在同一次服务端生命周期内再次运行时，还会
自动比较 sparse/mixed/dense P50；负百分比表示更快。完整机器可读结果写入
`event=smart_suite_complete`，未通过正确性门槛时不应把该次结果保存为新基线。

日常修改先选择 `lightweight` 负载并运行所需模式；投影算法、局部方块语义或发布候选
发生变化时选择 `stress` 负载。只有专项排查时才分别运行 `partial_geometry` 或 `performance`；
`random_stress` 用于长尾压力测试，不代替固定场景回归基线。
在 20 TPS 且单场景总工作量低于 50 ms 时，它应接近 20 case/s、1000 case 约 50 秒。
最终事件必须包含 `projection_execution=server_tick_serial`、`target_cases_per_tick=1`、
`cases_per_second`、`completion_tick_span` 与 `missed_completion_ticks`。这里的 response 是
同步测试通道的 rebuild+commit，不可与 production async 的玩家响应直接比较。

左键空气事件只在客户端产生，因此客户端仅发送切换意图。服务端必须重新验证主手物品、
权限和套件忙碌状态，并由服务端修改物品 `CUSTOM_DATA` 中的负载字段；客户端不得直接决定负载状态。

命令入口用于细分排查：

```text
/spectralization spottest random
/spectralization spottest rerun
/spectralization spottest report
/spectralization spottest benchmark
/spectralization spottest clear
/spectralization spotperf report
/spectralization spotperf reset
```

自动通过标准：

- 所有 case 的 `passed=true`。
- `missing_faces=0`。
- `validation_mismatches=0`。
- 功率和颜色测试的全部样本都走 `appearance_only`。
- 几何缓存、`AppearancePlan` 和依赖快照均按预期复用。
- 输出没有超过每 owner 32768 条共享上限，网络分片没有缺失或重复。

人工视觉检查仍需覆盖：

1. 同一个楼梯内部，踏面正确阻挡内部立面。
2. 不同楼梯在同一传播深度/同一 `z` 时，前方踏面仍正确阻挡后方立面。
3. 楼梯处在不同深度时，遮挡连续且没有纹理重新铺满。
4. 散杂光模式和较大扩散下没有明显硬边、颜色跳变或透明排序错误。
5. 大快照更新期间旧画面保持完整，客户端只在分片到齐后整体替换。

性能报告必须区分：

- `core`：算法本体。
- `response`：服务器测试请求到实际 commit，包含真实 tick 调度和测试清缓存成本；完成状态在同一 tick 的 projection queue 处理后读取，不包含测试框架下一 tick 才轮询到结果的人工延迟。
- 玩家可见延迟：另含网络和客户端绘制。

提交性能结论前，只读取时间最新且属于同一次启动的一组 `diagnostics_*.log` 与 `optical_compiler_*.log`。完整流程和当前基线见
[SPOT_PROJECTION_CHECKPOINT_2026-07-11.md](SPOT_PROJECTION_CHECKPOINT_2026-07-11.md)。

## 11. 日志采样计划

默认配置：

```toml
[diagnostics]
event_log = true

[optical_compiler]
debug_log = false
debug_log_verbose = false
```

定位光学问题时：

```toml
[diagnostics]
event_log = true

[optical_compiler]
debug_log = true
debug_log_verbose = false
```

深挖短样本时：

```toml
[optical_compiler]
debug_log = true
debug_log_verbose = true
```

采样要求：

- verbose 只在短时间内开启。
- 每次提交 bug log 时，记录世界操作步骤和大致 tick/时间。
- 优先保留 `diagnostics_*.log`、`optical_compiler_*.log` 和 `latest.log`。
- 看日志前先读 [LOGGING.md](LOGGING.md)。

## 12. 发布前检查

准备 commit 或 push 前检查：

1. 是否运行了 `.\gradlew.bat build`？
2. jar 是否生成在 `build\libs\spectralization-1.0.0.jar`？
3. 是否需要覆盖到 PCL2 mods 目录？
4. 是否排除了临时文件，例如 `work/` 和重复图片？
5. 是否有新增配置、日志字段或数据包格式需要写文档？
6. 是否有已知风险需要在提交说明或 TODO 中记录？

如果只修改文档，可以不跑 build，但提交说明中应明确“只改文档，未运行 build”。

## 13. 后续自动化方向

当前本地测试以人工游戏内验证为主。后续应逐步补自动化：

- 材料 LUT 插值单元测试。
- 饱和增益边和材料过载单元测试。
- SCC / chord 求解器固定图测试。
- 可靠性门控测试。
- 光纤连接图测试。
- 全息存储容量计算测试。
- 微缩机器边界泛函测试。
- 资源路径和模型引用检查。

自动化测试不能替代游戏内测试。它负责保护数学和数据结构；游戏内测试负责保护注册、资源、交互、渲染和事件边界。

## 14. 光斑串行收尾发布协议

使用 `spot_test` 时：右键运行当前模式；Shift + 右键循环测试模式；对空气左键只切换
独立的 `lightweight` / `stress` 负载，不改变模式的 case 数、样本数或 repeat 数。

光斑算法提交前的最小游戏内序列：

1. lightweight `random_stress` 连续两次；每次必须 1000/1000 pass，以第二次作为热态随机基线。
2. stress `random_stress` 一次；必须 1000/1000 pass，并同时记录平均/P50/P95 与 spots。
3. stress `smart` 至少一次；必须 7/7 pass、647 validation checks、`boundary_missing=0`、`structural_mismatches=0`。
4. 对固定 sparse/mixed/dense 同时记录覆盖签名、分片签名、spots、tiles、projectable、dependencies、side windows 和 side quads。
5. cached power/color 必须全部走 appearance-only，完整几何重建数为 0。
6. 只读取本次启动中时间最新且时间戳匹配的一组 diagnostics / optical_compiler 日志。
7. 运行 `./gradlew.bat build`，覆盖实际游戏 `mods/spectralization-1.0.0.jar`，并核对源/目标 SHA-256。

`random_stress` 的 seed 匿名且每场景只测一次，因此用于发现长尾和持续分配压力；固定
smart case 才是提交间同工作量比较。`response` 包含 Minecraft tick、测试调度、compiler、
网络与日志外围延迟，算法优化以 `elapsed/core` 及 profile 阶段字段为准。

## 15. 光斑并行化验证

启用 `spot_projection_parallel_enabled` 后，先用 targeted/verbose validation 比较 live
与 eager snapshot 串行结果，再运行 worker 数 1、2、4 的同一固定场景。完成顺序可以不同，
但 owner 最终快照、fragment/UV 顺序、dependency、geometry cache 与输出 fingerprint 必须一致。

必须覆盖运行中修改功率、envelope、投影依赖方块、删除 source、清理 level cache、连续
generation、队列满和服务器关闭。旧 generation 只能被丢弃，不能修改 cache 或发布 owner。
性能报告同时记录 snapshot、queue wait、worker、commit、end-to-end 和最大 tick stall；
1 到 9 个同时 dirty 的 stress source 分开报告，不能把 worker 时间算入服务器同步预算。

`spot_test` 的 `parallel` 模式只建一次 `29 x 29` 固定 dense 场地，并把九个 source 按横向和
竖向间隔都为 2 格的 `3 x 3` 网格放置。九个 source block 始终保留，测试只通过功率切换活动
前缀，依次运行 `1 -> 9`，整套序列重复 10 次。不同 source 的 cone 共同照射同一批随机障碍和
终端墙，允许 owner spot 大量重叠。测量轮不得清理 `OpticalTraceCache`：必须只强制 spot geometry
full rebuild 并 enqueue projection refresh。

全部投影工作 idle 后，source-count case 之间额外等待 5 个 quiet tick；完整 cycle 之间等待 20
个 quiet tick。cycle 1 标记为 cold，2-3 为 warming，4-5 为 stabilizing，6-10 为 steady。正式
P50/P95 只使用 cycle 6-10。每个 source count 同时比较 cycle 4-5 与 cycle 6-10 的 response
median 和 worker median；只有两者漂移都超过 10% 时才记录 `warmup_unstable`，避免单个 tick
边界异常快样本被误判为 JVM 预热。报告必须包含 job/worker 时间、整批
response、最大 in-flight、最大 queue depth、submit/commit tick spread、聚合 cache mode 与稳定
ordinal output fingerprint。所有测量 job 都必须为 `full_rebuild`，不得混入 suffix 或
appearance-only。

共享 section snapshot 启用后，额外检查：首轮允许 resolved block 较多，稳态相邻 source 应以
reused block 为主；`resolved + reused == snapshot_blocks`；section cache 不得超过 256；同 section
方块变化必须让旧 job stale，跨 section 邻接 shape 变化必须更新两侧 section。正式 parallel
日志还要统计 dispatch width，四 worker 稳态应出现 width 4，不能再由大量 width 1/2 wave
掩盖 snapshot 串行化。

并行 suite 的每个 case 必须记录相对本次测量起点的 `projection_submit_rejected` 和
`projection_commit_budget_deferred`。任何 submit rejection 都使 case 失败；executor 满只能
保留 pending work 到后续 tick，不能由 server thread 执行。completed queue 允许跨 tick 保留，
但 commit 必须遵守独立的 spot-projection main-thread deadline，并同时检查
`projection_commit_tick_spread` 与最大 tick stall。snapshot preparation 与 commit 共用这个软预算，
每 tick 最多发布一个执行器实际 worker-width；当 `max_in_flight < workers` 时必须使用夹紧后的
实际 worker 数，不能等待一个永远无法同时运行的配置宽度。既不能因通用 solver budget 过小把
worker 结果重新串行化，也不能
在一个 tick 无界集中发布全部 source。

warmup 漂移中的 median 使用通常定义：奇数样本取中项，偶数样本取两个中项平均。尤其 cycle
4-5 的两个样本不能再用 nearest-rank P50 取较小值。正式 P50/P95 报告仍保留既有 percentile
定义，median 只用于 stabilizing-vs-steady 预热判定。

commit 优化后的 `async_batch_commit` 必须提供 `projection_commit_assembly_us`、
`projection_commit_diagnostics_us`、`projection_commit_trace_update_us`、
`projection_commit_dependency_index_us`、
`projection_commit_owner_publish_us` 及 dependency/owner reuse 标志。稳定强制 full rebuild
允许复用完全相同的 dependency reverse index 和已安装 owner wire snapshot，但仍必须产生新的
source-bound performance sample；方块删除后的 partial owner 不得被相同输入快路径误认为完整结果。

异步日志写入启用后，parallel 与 random-stress 验收还必须确认
`diagnostic_writes_dropped=0`、`diagnostic_writes_failed=0`。`pending` 可以短暂非零，但测试结束后的
最新完成事件必须实际出现在文件中；不能因为 writer queue 尚未落盘而读取前一轮尾部。比较 commit
优化时同时报告 assembly 与 diagnostics，避免把文件系统尖峰算成 SpotLayer 组装回归。
