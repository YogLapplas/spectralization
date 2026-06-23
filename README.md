# Spectralization

Spectralization 是一个 NeoForge 1.21.1 科技模组实验。它的核心玩法不是把矿物送进一串机器，而是让玩家用光谱、端口、镀膜、反馈腔、场源、光纤、全息存储和微缩机器设计自己的工业系统。

当前版本已经越过最早的验证阶段。第一代光学网络编译器、红宝石激光器、涂层系统、光纤、热力熔炼、全息存储矩阵和微缩机器都已经有可玩的原型。接下来的目标是把这些系统收束成中期玩法：微缩化光路工作站、超材料/光刻路线、远程机器交互和虚空存储总线。

## 当前状态

面向玩家的可用内容：

- 基础光学元件：创造光源、镜子、动态镜、分光镜、透镜架、CMOS、贯通传感器、光束分析仪和光谱仪。
- 光学材料和表面：红宝石块、银块、涂银玻璃、金/银涂料、砂纸、高级刷子、创造刷子和世界表面涂层。
- 红宝石激光器原型：萤石提供散杂种子光和泵浦，红宝石把合适的种子转为相干红宝石线，银块和涂银玻璃形成初级腔。
- 光束和 spot 可视化：真理头盔、皮革帽、光纤卷和剪刀暂时承担 debug 可视化，光束与光斑共用频率到 RGB 的 LUT。
- 光纤系统第一版：接口、relay、自动寻路连接、光输入输出、并行光纤容量、剪断光纤和 Jade 连接数显示。
- 光热工业原型：光热发电机、环境光收集器、高级环境光收集器、热力熔炼炉和熔融流体。
- 全息存储矩阵第一版：核心、水晶、screen 多方块识别，容量由水晶和暴露面决定，提供存储终端、核心信息 UI 和内置合成台。
- 微缩机器第一版：anchor/core/io_port 框架识别，工作区域可视化，压缩输出物品，以及压缩后方块对世界光路的面到面映射和主动光源输出。

仍处在原型期的内容：

- spot 渲染成本偏高。
- 光束 HUD 已回到相干光稳定可视化路径；散杂光束宽度/profile 可视化需要重新设计后再接入。
- 多源、多网络、网络切分/合并需要长期回归。
- 全息存储和微缩机器的 UI 与数值仍需整理。
- 生存模式的早期光源、材料路线和数据包文档还不完整。

## 核心架构

Spectralization 的光学系统是事件驱动、准静态的 port graph 编译器，不是逐 tick tracer。系统分三层：

- 几何层：世界里有哪些光学节点、方块、表面、场源和多方块结构。
- 拓扑层：这些节点暴露哪些端口、方向边、连通分量、SCC 和反馈关系。
- 数据层：材料、涂层、频率、功率、相干/散杂、增益、读数、可靠性和可视化导出。

光源 tick 不负责重算光路。它只能申请刷新或同步轻量状态。真正的网络重建、求解和导出进入预算队列；几何层和拓扑层变化可以中断数据层求解。传感器、光束、spot、机器读数、机器效率和过载判断都从 compiled solution 导出。

这条规则也约束新功能：新光源、新传感器、新材料、新可视化和新机器行为必须优先接入拓扑编译器、缓存和 readout 层。主运行路径是 `world -> port graph -> effective edge gains -> power solve -> readout`。旧 tracer、profile-state exact solver 和示例验证器可以保留为 reference / oracle / legacy compare，但不能反向改变 gameplay authority。

当前光学求解器遵循两条哲学：

- 无迭代：游戏求解期只做一次稀疏线性求解，不把反馈环留给逐 tick 收敛。
- 多等效：半径、发散、光纤耦合、孔径裁剪等几何敏感效应先折叠成边的 effective gain，再进入同一套线性系统。显示层可以解释 profile 变差，功率层只接受保守损耗。

## 当前开发主线

当前短期主线是稳定光学 HUD、spot 和 profile 可视化导出，然后继续推进微缩化结构、超材料和光刻路线。微缩机器不是普通机器外壳，而是把一段局部光路、端口图和求解结果压缩成一个 macro node。压缩后的方块仍然是光学元件，也可能是光源，因此可以继续参与更外层的光学网络和未来的再次压缩。

这一设计让玩家先在可控工作区学习光学结构，再把结构装进更小的机器。它也为后续的超材料、光刻、远程机器交互和虚空存储总线提供共同接口。

超材料的长期展示方向是组合式视觉语法，而不是为每一种结果手绘完整贴图。计划把一个超材料拆成 4 个视觉 trait，每个 trait 有 8 种样式：基底、晶格、掺杂和相位/方向层。客户端按 trait 叠加 32 张基础 overlay，就能表达 4096 种可区分超材料，同时让玩家读出材料结构而不是记忆随机图标。

## 文档

- [TODO.md](TODO.md)：路线图、当前阶段目标和各系统状态。
- [docs/MATH_AND_ARCHITECTURE.md](docs/MATH_AND_ARCHITECTURE.md)：光学编译器、三层架构、profile、求解器、可靠性和关键数学模型。
- [docs/LOGGING.md](docs/LOGGING.md)：诊断日志、光学编译器日志和排错阅读顺序。
- [docs/TESTING_PLAN.md](docs/TESTING_PLAN.md)：本地构建、游戏内验证、回归测试和发布前检查计划。
- [docs/TECHNICAL_WRITING_GUIDE.md](docs/TECHNICAL_WRITING_GUIDE.md)：技术写作规范，用于后续严肃文档。
- [docs/AI_UI_DESIGN_SPEC.md](docs/AI_UI_DESIGN_SPEC.md)：给 AI 代理使用的 Minecraft 机器 UI 设计规范，强调少文字、pending 状态和原版元素语法。
- [docs/WORLDVIEW.md](docs/WORLDVIEW.md)：玩法、世界观、工业线和终局幻想。
- [docs/CONDENSED_SPACETIME.md](docs/CONDENSED_SPACETIME.md)：微缩机器设定与设计记录。
- [advancement plan/Spectralization_advancement.json](advancement%20plan/Spectralization_advancement.json)：当前导入的模组流程蓝图。

## 日志入口

如果要排查光学网络、光纤、微缩机器或全息存储问题，先阅读 [docs/LOGGING.md](docs/LOGGING.md)。

常用日志文件：

```text
logs/spectralization/diagnostics_*.log
logs/spectralization/optical_compiler_*.log
logs/spectralization/optical_compare.log
latest.log
```

默认先看 `diagnostics_*.log`，确认是哪一层先变化：几何层、拓扑层还是数据层。只有摘要不够时，才打开 verbose 光学编译器日志。

光学编译器日志会标出结果身份：`authority=gameplay` 是机器、HUD、过载和传感器使用的主结果；`authority=reference` / `debug_oracle` / `legacy_compare` 只用于校对。profile 求解如果降级，会写出 `profile_mode=collapsed_fallback` 或 `profile_mode=collapsed_due_to_overflow`，并同时给出 `profile_fallback` / `profile_overflow`。

游戏内快速验证入口：

```text
/spectralization opticaltest splitter_lens_splitter
/spectralization opticaltest lens_aperture_clip
/spectralization opticaltest fiber_radius_coupling
/spectralization opticaltest feedback_fiber_radius_loss
/spectralization opticaltest parallel_fiber_same_endpoint
```

## 构建

Windows：

```powershell
.\gradlew.bat build
```

其他环境：

```bash
./gradlew build
```

构建产物：

```text
build/libs/spectralization-1.0.0.jar
```
