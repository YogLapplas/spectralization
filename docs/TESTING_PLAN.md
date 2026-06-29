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

## 10. 日志采样计划

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

## 11. 发布前检查

准备 commit 或 push 前检查：

1. 是否运行了 `.\gradlew.bat build`？
2. jar 是否生成在 `build\libs\spectralization-1.0.0.jar`？
3. 是否需要覆盖到 PCL2 mods 目录？
4. 是否排除了临时文件，例如 `work/` 和重复图片？
5. 是否有新增配置、日志字段或数据包格式需要写文档？
6. 是否有已知风险需要在提交说明或 TODO 中记录？

如果只修改文档，可以不跑 build，但提交说明中应明确“只改文档，未运行 build”。

## 12. 后续自动化方向

当前本地测试以人工游戏内验证为主。后续应逐步补自动化：

- 材料 LUT 插值单元测试。
- `EffectiveGainSoftcap` 单元测试。
- SCC / chord 求解器固定图测试。
- 可靠性门控测试。
- 光纤连接图测试。
- 全息存储容量计算测试。
- 微缩机器边界泛函测试。
- 资源路径和模型引用检查。

自动化测试不能替代游戏内测试。它负责保护数学和数据结构；游戏内测试负责保护注册、资源、交互、渲染和事件边界。
