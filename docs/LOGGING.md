# Spectralization 日志阅读指南

如果某个日志文件开头提示你阅读本文档，请先读这里。Spectralization 的日志不是普通“方块执行了什么”的流水账，而是用来定位事件如何诱导几何层、拓扑层和数据层变化。

本文档是操作指南和字段参考。读者是正在排查 bug 的开发者、测试者和整合包作者。

## 1. 先问哪一层错了

排查 Spectralization 的问题时，不要先问“哪个方块错了”。先问：

1. 几何层是否正确识别了方块、节点、区块加载状态或多方块结构？
2. 拓扑层是否正确重建了端口、方向边、连通分量、SCC 或系统边界？
3. 数据层是否正确更新了功率、频率、相干/散杂、读数、可靠性和缓存？

如果某一层应该变化但没有变化，常见问题是 dirty 没有传播。  
如果某一层不应该变化但变化了，常见问题是事件边界过宽或缓存 key 不稳定。

## 2. 日志文件

优先级从高到低：

| 文件 | 什么时候读 | 作用 |
| --- | --- | --- |
| `logs/spectralization/diagnostics_*.log` | 默认先读 | 稀疏事件日志，记录 dirty、跨系统边界和异常。 |
| `logs/spectralization/optical_compiler_*.log` | 光学摘要不够时读 | 光学编译器日志，默认只写摘要。 |
| `logs/spectralization/optical_compare.log` | legacy solver 与 topology solver 对照失败时读 | 对比旧 tracer/solver 与新编译器结果。 |
| `latest.log` | 崩溃、warning、加载失败时读 | NeoForge/Minecraft 普通日志。 |

不要默认打开 verbose 光学日志。它会输出大量通常稳定的内部结构，只适合收集短样本。

## 3. 推荐阅读顺序

1. 在 `latest.log` 确认是否有崩溃、异常栈或资源加载失败。
2. 在 `diagnostics_*.log` 找 bug 发生时间附近的 `severity=anomaly`。
3. 如果没有 anomaly，按 `subsystem` 找最近事件，例如 `fiber`、`compact_machine`、`holographic_storage` 或 `optical`。
4. 看事件是否包含 `geometry_changed`、`topology_changed`、`optical_dirty` 或 `fiber_dirty`。
5. 如果涉及光学结果，看 `optical_compiler_*.log` 的摘要：网络规模、source total、receiver total、solver plan 和 reliable 状态。
6. 摘要不够时，再开启 `optical_compiler.debug_log_verbose=true` 收集一次短样本。

## 4. diagnostics 行格式

诊断日志是一行一个 key/value 事件：

```text
time=2026-06-15T00:00:00Z severity=event subsystem=fiber event=connection_added dim=minecraft:overworld tick=123 endpoint_a=0,64,0 endpoint_b=4,64,0 optical_dirty=true
```

重要字段：

| 字段 | 含义 |
| --- | --- |
| `time` | 真实时间，用于和文件时间、崩溃报告对齐。 |
| `severity` | `event` 是普通事件，`transition` 是状态边界变化，`anomaly` 是应优先检查的异常。 |
| `subsystem` | 事件所属系统，例如 `fiber`、`compact_machine`、`holographic_storage`、`optical`。 |
| `event` | 具体事件名。 |
| `dim` | 维度。 |
| `tick` | 游戏时间，用来和 `latest.log` 或光学日志对齐。 |
| `reason` | 外部原因，例如放置、破坏、剪断、过载、延迟刷新、动画清空工作区。 |
| `geometry_changed` | 几何层是否变化。 |
| `topology_changed` | 拓扑层是否变化。 |
| `optical_dirty` | 是否应诱导光学网络 dirty。 |
| `fiber_dirty` | 是否应诱导光纤网络 dirty。 |

很多时候，`geometry_changed`、`topology_changed` 和 `optical_dirty` 比事件名更重要。

## 5. 哪些部分通常稳定

这些部分在输入稳定时大多接近纯函数，不应作为第一嫌疑：

- 从稳定 snapshot 编译 port graph。
- DAG 网络的标量传播。
- 固定输入下的频谱、颜色和 profile 汇总。
- compacted_machine 物品内静态快照的读取。
- 只读 UI 的文本格式化。

如果这些看起来错了，通常先查更早的输入：

- stale snapshot。
- 缓存 key 不完整。
- dirty 没有跨系统传播。
- 结构变化后 readout 复用了旧值。
- 客户端视觉状态没有收到服务端同步。

## 6. 哪些部分最不稳定

这些是事件驱动边界，优先检查：

- 方块放置、破坏、邻居更新顺序。
- 区块加载/卸载导致的几何成员变化。
- 光纤连接、剪断、遮挡、过载烧毁。
- 微缩机器锚点连接、框架认证、旋转、压缩清空工作区。
- 全息存储多方块识别。
- 光纤或微缩机器变化后是否诱导光学网络 dirty。
- readout cache 是否在拓扑变化后复用了旧值。
- reliable / unreliable 状态变化。

## 7. 光学编译器日志

光学编译器日志由配置控制：

```toml
[optical_compiler]
debug_log = true
debug_log_verbose = false
```

默认摘要应足够回答：

- 网络是否被识别。
- 网络规模是否合理。
- 哪些 source 被纳入。
- solver plan 是否合理。
- readout 是否 reliable。
- 输出总功率是否符合输入。
- profile 是精确状态传播还是反馈等效折叠。

只有需要检查以下内容时，才开启 verbose：

- SCC 列表。
- edge 列表。
- chord 系统。
- solver region。
- per-lane 结果。
- HUD segment。
- readout output 细节。

开启方式：

```toml
[optical_compiler]
debug_log = true
debug_log_verbose = true
```

收集完样本后关掉 verbose。

关键字段：

| 字段 | 含义 |
| --- | --- |
| `authority=gameplay` | 该结果属于主运行时路径，可决定机器、HUD、读数和过载。 |
| `authority=reference` | 该结果用于校验或过渡对照，不能单独决定 gameplay。 |
| `authority=debug_oracle` | 调试 oracle，例如 observed trace。 |
| `authority=legacy_compare` | 旧路径对照日志，用于发现语义漂移。 |
| `solver=PROFILE_STATE_EXACT` | 无反馈或有限 profile 状态图的精确求解。 |
| `solver=PROFILE_COLLAPSED_EXACT` | 反馈图使用“多等效”折叠后的精确线性求解。 |
| `profile_mode=state_exact` | profile key 参与有限状态传播。 |
| `profile_mode=collapsed_equivalence` | profile 敏感损耗已经乘入矩阵边权，反馈主求解不再展开 profile。 |
| `profile_mode=collapsed_due_to_overflow` | 无反馈 profile-state 图过大，降级到 collapsed 求解。 |
| `profile_mode=collapsed_fallback` | 无反馈 exact profile solve 失败，降级到 collapsed 求解。 |
| `profile_overflow=true` | profile state 构图超过上限。 |
| `profile_fallback=true` | 本次结果不是首选 profile-state 求解路径。 |
| `readout_reliable=true` | 机器、传感器和可视化可以使用该次读数。 |
| `readout_reliable=false` | 读数处于过渡或失败状态，不应作为新结果判断。 |
| `residual` | 线性求解残差；应接近 0。 |

反馈结构的正常样子通常是：

```text
feedback_sccs>0
solver=PROFILE_COLLAPSED_EXACT
profile_mode=collapsed_equivalence
readout_reliable=true
```

如果看到 `feedback_sccs>0` 但 profile state 数量持续膨胀，优先怀疑有新的几何敏感
过程绕过了等效矩阵边权，重新进入了反馈主求解。

如果看到 `profile_overflow=true` 或 `profile_fallback=true`，机器读数不一定错误，
但该次结果已经从精确 profile-state 路径降级。调试半径、发散、透镜孔径或光纤
耦合时，应先把结构缩小到 `profile_mode=state_exact` 或使用专门的
`/spectralization opticaltest ...` 命令做对照。

读日志时先看 `authority`。`reference`、`debug_oracle` 和 `legacy_compare` 可以
提示 bug，但它们不应直接改变机器输出、缓存 authority、过载烧毁判断或玩家最终读数。

## 8. 功率和颜色问题

症状：

- 两束光合并后功率翻倍或四倍。
- 光束越走功率越高。
- 有无传感器时颜色不同。
- 光束透明度或颜色不稳定。

阅读顺序：

1. 在 `diagnostics_*.log` 找最近的 `fiber`、`compact_machine` 或 `optical` 事件。
2. 确认结构变化后有 `optical_dirty=true`。
3. 在 `optical_compiler_*.log` 对比 source total 和 receiver total。
4. 确认混合后的颜色是否来自统一的 `SpectralColorMap` / 频率合并函数。
5. 如果涉及光纤，确认连接是 1 对 1，且没有断开的连接仍在导光。
6. 如果涉及微缩机器，确认旋转或面映射后触发了光学网络刷新。

判断标准：

- 被动网络不能凭空增加总功率。
- 多输入合流必须线性叠加。
- 单输入分流的输出总功率不能超过输入总功率。
- 同一束混合光在有无传感器时应使用同一颜色合并函数。

## 9. 光纤问题

重点事件：

- `connection_added`
- `connection_removed`
- `connections_removed`
- `overload`

关键字段：

- `endpoint_a`
- `endpoint_b`
- `remaining_parallel`
- `dirty_endpoints`
- `optical_dirty`

排查步骤：

1. 确认连接是否被记录为正确的一对端点。
2. 确认剪断、遮挡或过载后是否出现 `connection_removed` 或 `connections_removed`。
3. 确认这些事件包含 `optical_dirty=true`。
4. 如果光纤已经断开但两个端口仍然传光，优先查光纤系统到光学系统的 dirty 边界。

注意：relay 属于光纤网络的几何和拓扑层，不应直接参与光学网络。

## 10. 微缩机器问题

重点事件：

- `compact_machine network_refresh`
- `compact_machine anchor_connection_added`
- `compact_machine anchor_connection_removed`
- `compact_machine compression_started`
- `compact_machine work_area_cleared`
- `compact_machine compression_finished`

排查步骤：

1. 先看 `network_refresh`，确认框架连接数量、valid frame 和 error state 是否合理。
2. 如果 anchor 不可见或无法破坏，查最近的 `anchor_connection_added` / `anchor_connection_removed`。
3. 如果压缩结果错误，查 `compression_started` 的工作区范围和 `payload_blocks`。
4. 如果压缩后光路错误，查 `work_area_cleared` 附近是否标记 `optical_dirty=true`。
5. 如果旋转后光路不更新，查 compacted_machine 的 block update 是否诱导相关光学网络 dirty。

`work_area_cleared` 是世界中方块真正被删除的时刻。压缩后才出现的问题，优先查这个 tick 附近。

## 11. 全息存储问题

重点事件：

- `holographic_storage multiblock_refresh`

关键字段：

- `affected_cores`
- `changed_reports`
- `valid_reports`
- `removed_reports`
- `visual_positions`

排查步骤：

1. 确认核心间距规则没有让后放置核心进入 error。
2. 确认水晶是否被同一个系统识别。
3. 确认 screen 是否贴在已经连接的水晶上。
4. 如果能看到存储内容但不能交互，先确认 multiblock report 没进入 error。
5. 如果打开 UI 后内容不同步，再查 menu 初始化和客户端同步。

## 12. 场源和材料问题

症状：

- 环境光谱不连续。
- 原版光源影响全局性能。
- 材料响应看起来不随频率变化。
- 场源影响不刷新或刷新过度。

排查步骤：

1. 确认自然光是否只通过环境光收集器采样进入散杂光。
2. 确认创造光源默认频谱是可见白光连续谱。
3. 确认材料响应来自 LUT 或 profile，而不是机器内部硬编码。
4. 如果只是数据层变化，确认没有误触发拓扑重建。
5. 如果场范围变化，确认相关 dependency 被刷新。

## 13. 可靠性问题

症状：

- 传感器读数跳回旧值。
- UI 显示看起来滞后。
- 高频红石下读数偶尔不更新。

判断：

- 如果网络刚被中断，保持最后可靠值是正确行为。
- 如果完整导出完成后仍不更新，可能是 readout cache 或客户端同步问题。
- 如果拓扑变化后仍显示可靠，可能是 dirty 没有传播。

排查字段：

- `reliable`
- `unreliable`
- `readout`
- `optical_dirty`
- `topology_changed`

## 14. 记录新日志事件的规则

新增日志事件时，至少回答：

1. 这个事件属于哪个 `subsystem`？
2. 它是普通 `event`、状态 `transition`，还是 `anomaly`？
3. 它是否改变几何层？
4. 它是否改变拓扑层？
5. 它是否应诱导光学网络 dirty？
6. 哪些字段能和 `latest.log` 或 `optical_compiler_*.log` 对齐？

推荐字段：

```text
reason
pos
from
to
changed
geometry_changed
topology_changed
optical_dirty
fiber_dirty
reliable
```

不要把每 tick 状态写进 diagnostics。Diagnostics 应记录稀疏事件、边界变化和异常。

## 15. 快速结论

最快排查路径：

```text
latest.log
-> diagnostics anomaly
-> subsystem events
-> layer dirty fields
-> optical compiler summary
-> verbose sample only if needed
```

如果不知道从哪里开始，先找 `optical_dirty=true` 应该出现却没出现的地方。很多 Spectralization bug 都发生在跨系统边界，而不是求解器本身。
