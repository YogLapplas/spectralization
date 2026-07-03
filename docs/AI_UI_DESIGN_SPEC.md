# Spectralization AI UI 设计规范

本文档是给 AI 代理使用的 UI 规范。它不是普通审美指南，也不是玩家手册。它的目的只有一个：当 AI 为 Spectralization 设计或重做 Minecraft 机器 UI 时，先把自己限制在 Minecraft 原版和经典科技模组已经证明可读的界面语法里，避免生成文字过多、状态缺失、pending 语义混乱的前端。

核心规则：

```text
默认界面表达结构和操作，不解释系统。
解释进入 tooltip、Jade、debug panel 或文档。
pending、invalid、locked、unreliable 和 output_blocked 必须是一等状态。
```

## 1. 参考来源

本规范参考以下资料，并只吸收适合 Spectralization 的部分：

- [Nielsen Norman Group: 10 Usability Heuristics](https://www.nngroup.com/articles/ten-usability-heuristics/)：用于启发式评估，特别是系统状态可见、和真实世界匹配、一致性、错误预防、识别优先于回忆、极简设计、帮助用户恢复错误。
- [Nielsen Norman Group: Progressive Disclosure](https://www.nngroup.com/videos/progressive-disclosure/)：复杂信息默认隐藏，只在需要时呈现。
- [Microsoft: Progressive Disclosure Controls](https://learn.microsoft.com/en-us/windows/win32/uxguide/ctrl-progressive-disclosure-controls)：把高级信息、复杂设置和解释性内容放在可展开区域、独立标签页或 tooltip 中，同时避免不可发现和界面不稳定。
- [Official Mekanism Wiki: Machine Configuration](https://wiki.aidancbrady.com/wiki/Tutorials/Machine_Configuration)：统一机器 GUI、颜色编码 slot、侧面配置按钮、mouseover 信息、红石控制、升级面板。
- [Thermal Expansion GUI](https://technicpack.fandom.com/wiki/Thermal_Expansion_GUI)：颜色编码 slot、右侧 tab、侧面配置、红石设置、能量信息 tab。
- [Team CoFH: Thermal Expansion Augments](https://teamcofh.com/docs/1.12/thermal-expansion/augments/)：增强物品通过 GUI tab 安装，普通机器界面和高级扩展界面分离。
- [Ender IO Redstone Conduit Wiki](https://github.com/SleepyTrousers/EnderIO-1.5-1.12/wiki/Redstone-Conduit)：输入/输出模式分离、颜色信道、filter slot。
- [PneumaticCraft: Repressurized CurseForge](https://www.curseforge.com/minecraft/mc-mods/pneumaticcraft-repressurized)：机器状态不是单一能量条，压力、体积和阈值共同影响行为。
- [Enigmatica 6 PneumaticCraft guide](https://wiki.enigmatica.net/enigmatica6/gameplay/how-to.../pneumaticcraft-safe-compressor-usage)：安全状态、红石模式和启动默认行为必须清楚，否则机器会在玩家看不懂时进入危险状态。
- [Mekanism issue #6970](https://github.com/mekanism/Mekanism/issues/6970)：小字号 tooltip 在中文环境下尤其容易不可读。Spectralization 不应把长文本塞进小区域。

这些资料给出的共同结论是：Minecraft 科技模组机器 UI 不是网页应用。它依靠 slot、图标、箭头、条、颜色、tab、tooltip 和玩家已有肌肉记忆来表达功能。

## 2. AI 必须遵守的不可变规则

### 2.1 主界面少字

默认状态下，机器 UI 上应该几乎没有文字。理想目标是：鼠标不接触 UI 时，没有解释性文字。

允许默认可见：

- 机器标题，只有在当前 screen 框架已经自然显示标题时才保留。
- ItemStack、slot、ghost slot。
- 箭头、条、图标、开关状态、颜色 swatch、端口面。
- 简短状态图标，例如 ready、pending、error、locked、unreliable。

默认禁止可见：

- 解释机器如何工作的句子。
- 资源名称标签，例如 `heat`、`power`、`spectrum`、`progress`，除非没有图标会造成误解。
- 大段数值。
- 多行说明。
- “请放入 X 以开始”这种教程句子。

精确数据进入 tooltip。教程进入手册。诊断进入 Jade 或 debug panel。

### 2.2 先状态，后布局

AI 写任何 UI 前，必须先写 State Contract。没有 State Contract，不允许改布局。

最小状态集合：

| 状态 | 含义 |
| --- | --- |
| `empty` | 必要输入、模板、结构或资源缺失。 |
| `ready` | 条件满足，机器可执行主操作。 |
| `pending` | 正在扫描、认证、编译、压缩、烧录、曝光或等待服务器结果。 |
| `invalid` | 条件错误，当前不能执行。 |
| `locked` | 可查看但不可交互，例如容量过载、权限不足、只读。 |
| `unreliable` | 读数来自旧可靠值，或求解尚未完成。 |
| `output_blocked` | 产物无法进入输出槽或外部目标。 |
| `unsafe` | 继续运行可能损坏、过热、过压、过驱动或爆炸。 |

每个状态必须回答：

```text
主视觉是什么？
哪些 slot 可交互？
哪些按钮可点？
是否保留旧读数？
是否显示进度？
tooltip 写什么？
Jade/debug 是否补充更多原因？
```

### 2.3 pending 是一等状态

AI 最容易漏掉 pending。Spectralization 的 pending 包括：

- 光学网络等待预算队列。
- readout 暂时 unreliable。
- 微缩机器正在播放压缩动画。
- 工作区还没清空。
- 多方块结构等待重新认证。
- 光刻机正在曝光。
- 超材料设计正在评估目标包络。
- 掺杂炉正在升温、保温、冷却或结晶。
- UI 已打开，但服务端快照还没同步。

pending 状态下，UI 必须稳定：

- 不允许 slot、按钮或文字因为 pending 反复跳动。
- 不允许主操作按钮看起来可点但点击无效。
- 不允许显示看似最终的数值。
- 可以显示动画条、旋转图标、扫描线或 dimmed ghost result。
- 如果保留旧读数，必须用 `unreliable` 或 pending 图标标明。

### 2.4 原版元素优先

AI 应优先重排已有 Minecraft 元素，不要发明网页式控件。

优先使用：

- 18x18 物品槽。
- ghost slot。
- 输出槽锁定 overlay。
- 原版风格进度箭头。
- 火焰、热条、压力条、能量条、光谱条。
- 小型 icon button。
- 颜色 swatch。
- 侧边 tab。
- 模式切换小按钮。
- tooltip。

避免使用：

- 大按钮文字。
- 卡片式说明块。
- 表格式主界面。
- 长 label。
- 分栏网页布局。
- 解释性空状态。
- 装饰性假 slot。

### 2.5 不许画假 slot

如果一个矩形看起来像物品槽，它必须是实际 menu slot 或明确的 ghost slot。

禁止：

- 绘制装饰槽但点击区域在别处。
- slot 视觉和实际 hitbox 不一致。
- pending 时把真实 slot 替换成不可点击贴图，除非 tooltip 和视觉都清楚表达 locked。

LDLib2-backed UI 必须遵守：可见 `ItemSlot` 就是菜单点击来源。

### 2.6 图标按钮优先

按钮文案不得承担主要信息传达。

推荐：

- 旋转：箭头图标。
- 开始：三角形或压缩图标。
- 暂停：暂停图标。
- 重置：回转箭头。
- 配置：扳手或面配置图标。
- 锁定：锁图标。
- 红石模式：红石火把/粉尘图标。
- 详细信息：小 `i` 或放大镜图标。

按钮 tooltip 可以解释动作。按钮本体不要写长词。

### 2.7 数字只在需要时出现

默认界面用条和颜色传达量级。精确数字放在 hover tooltip。

允许默认显示数字的例外：

- 玩家必须实时比较阈值，例如压力阈值、曝光次数、频道编号。
- 数字本身是主要操作对象，例如频率 bin 或端口编号。
- 没有数字会导致危险状态不可判断。

即使允许数字，也应短：`4.5 bar`、`26`、`3/6`。不要在主界面写完整句。

## 3. AI UI 工作流

### 3.1 修改前必须输出 State Contract

AI 在动手改 UI 前，应先写下这个结构。这个结构可以进入 PR 描述，也可以写在任务笔记里。

```text
Machine:
Primary player action:

Slots:
- input:
- additive:
- template:
- output:
- upgrades:

Resources:
- energy:
- heat:
- optical_power:
- spectrum:
- pressure:

States:
- empty:
  visual:
  disabled:
  tooltip:
- ready:
  visual:
  enabled:
  tooltip:
- pending:
  visual:
  disabled:
  tooltip:
- invalid:
  visual:
  disabled:
  tooltip:
- locked:
  visual:
  disabled:
  tooltip:
- unreliable:
  visual:
  held_value:
  tooltip:
- output_blocked:
  visual:
  disabled:
  tooltip:

Text budget:
- visible when idle:
- visible on hover:
- debug/Jade only:
```

### 3.2 先选原型，再布局

AI 必须先判断这个 UI 属于哪种原型：

| 原型 | 适用系统 | 推荐布局 |
| --- | --- | --- |
| 原版熔炉型 | 简单加工、热力熔炼 | 输入左、输出右、进度箭头中、燃料/热条侧边。 |
| 原版酿造台型 | 多输入围绕一个过程 | 多个输入围绕中心反应，输出下方或右侧。 |
| Thermal 型 | 常规机器 + 配置 tabs | 主加工界面干净，右侧 tab 放侧面、红石、能量、增强。 |
| Mekanism 型 | 复杂机器 + 统一配置 | 左上配置入口，slot 颜色编码，资源条 hover 显示数字。 |
| EnderIO conduit 型 | 输入/输出模式和频道 | 输入输出分 tab，颜色 channel 和 filter slot 是核心。 |
| PneumaticCraft 型 | 压力/安全/阈值 | 量表和危险区优先，安全升级和红石阈值必须可见。 |
| Spectralization 光学型 | 光谱、端口、微缩、光刻 | slot + 光谱条 + 端口面 + 状态图标，详细曲线只在 hover/debug。 |

AI 不应从空白画布开始。

### 3.3 再排元素

布局顺序：

1. 玩家背包固定在底部。
2. 主机器流程放上半区。
3. 输入、处理、输出形成清楚方向。
4. 能量、热、光、压力等资源条贴边。
5. 配置、增强、诊断放侧边 tab 或小图标按钮。
6. 错误和 pending 用状态图标覆盖在相关元素附近。
7. tooltip 补充文本。

### 3.4 最后运行 UI 审计

AI 完成 UI 后必须自检：

```text
[ ] 鼠标不接触 UI 时，是否没有解释性文字？
[ ] 所有 pending 状态是否有视觉表现？
[ ] pending 时是否禁止误操作？
[ ] invalid 和 output_blocked 是否能区分？
[ ] unreliable 是否保留旧值并显式标记？
[ ] 是否有装饰性假 slot？
[ ] slot 视觉矩形是否等于实际点击矩形？
[ ] 主操作是否能从 slot、箭头、条和图标看懂？
[ ] 精确数字是否进入 tooltip？
[ ] 中文 tooltip 是否短到可读？
[ ] 是否复用了原版或经典科技模组的元素语法？
[ ] 是否没有网页式卡片、长段说明和大文本按钮？
```

## 4. Minecraft 机器 UI 元素库

### 4.1 Slot

Slot 是最高优先级信息元素。玩家会先看 slot，再看文字。

建议颜色语义：

| 颜色/样式 | 含义 |
| --- | --- |
| 蓝/青 | 输入、冷端、可接收。 |
| 橙/黄 | 输出、热端、产物。 |
| 红 | 错误、危险、阻塞、副产物或高热。 |
| 紫 | 模板、频道、虚空、特殊材料。 |
| 绿 | 增强、已安装、安全、有效。 |
| 灰 | disabled、空、未配置。 |

颜色必须和 tooltip 及侧面配置一致。

### 4.2 Ghost slot

Ghost slot 用于表达“这里需要什么”而不真正持有物品。

允许：

- 配方预览。
- 模板需求。
- 输出预览。
- 缺失输入提示。

禁止：

- 用 ghost slot 承担错误说明。
- 让 ghost slot 看起来可取出。

### 4.3 Progress arrow

加工方向优先用箭头表达。

规则：

- 左到右：常规加工。
- 下到上：注入、升华、抽取。
- 中心向外：分解、切割、多输出。
- 外向中心：合成、掺杂、压缩。

箭头可以填充、闪烁或变灰来表达状态。不要在箭头旁写长解释。

### 4.4 Resource bar

资源条用形状和颜色表达类型：

| 资源 | 推荐表现 |
| --- | --- |
| FE | 竖向能量条，蓝/青。 |
| Heat | 火焰或温度条，橙到红。 |
| Optical power | 发光条或亮度填充，白/谱色。 |
| Spectrum | 横向细谱条，不写频率名。 |
| Pressure | 圆表或竖条，安全区/危险区清晰。 |
| Stability | 环形或盾形条，绿色稳定、黄警告、红危险。 |
| Channel | 小色块、频率 bin、端口色。 |

资源条 hover 后显示精确值、单位和阈值。

### 4.5 Status icon

状态图标放在主过程附近，不要占大面积。

| 状态 | 图标建议 |
| --- | --- |
| `ready` | 小勾或稳定亮点。 |
| `pending` | 旋转/扫描/沙漏/流动条。 |
| `invalid` | 红色三角或叉。 |
| `locked` | 锁。 |
| `unreliable` | 断续波形、虚线眼睛或灰色读数。 |
| `output_blocked` | 输出槽红框或堵塞 icon。 |
| `unsafe` | 热警告、过压、裂纹、红闪。 |

状态原因放 tooltip。

### 4.6 Side tabs

侧边 tab 用于从主界面移走复杂信息。

推荐 tabs：

- Side config。
- Redstone。
- Upgrades。
- Details。
- Diagnostics。
- Port map。
- Spectrum。
- Recipe。

主界面不要同时展开所有 tab。

## 5. 文本预算

### 5.1 默认界面

目标：

```text
idle_visible_explanatory_text = 0
```

可接受上限：

```text
machine_title <= 1
short numeric counters <= 2
state words <= 0
```

如果必须显示状态词，优先使用短词：

```text
READY
SCAN
LOCK
ERR
HOT
```

但中文环境下仍优先使用图标，不用英文短词。

### 5.2 Tooltip

Tooltip 是主要解释层。

规则：

- 每个 tooltip 最好 1 到 3 行。
- 第一行是对象名或状态名。
- 第二行是当前值或缺失原因。
- 第三行才是操作提示。
- 不要超过 4 行，除非是 deliberate inspection panel。
- 中文不要用长复合句。

推荐：

```text
光谱匹配不足
当前 62%，需要 80%
更换模板或调整激光频率
```

避免：

```text
当前机器由于输入光谱与目标模板所要求的谱线匹配程度不足，所以无法启动光刻流程，请检查你的光源、镜头、材料以及所有相关参数。
```

### 5.3 Debug panel

Debug panel 可以有文字，但必须是刻意打开的。

允许内容：

- solver id。
- reliability。
- last reliable tick。
- dirty kind。
- source total。
- receiver total。
- SCC/gain source/saturation 摘要。
- port mapping。

这些内容不属于默认机器 UI。

## 6. Spectralization 专用 UI 状态

### 6.1 光学读数状态

光学读数 UI 必须区分：

| 状态 | UI 行为 |
| --- | --- |
| no beam | 读数为 0，条为空。 |
| reliable | 正常显示。 |
| unreliable | 保留最后可靠值，但加灰/虚线/警告图标。 |
| pending solve | 显示 pending 图标，不显示新精确值。 |
| overdriven | 显示危险图标，tooltip 说明过驱动或稳定性不足。 |

不要把 unreliable 显示成新的可靠结果。

### 6.2 多方块状态

多方块 UI 必须区分：

| 状态 | UI 行为 |
| --- | --- |
| missing | 框架不存在或未连接。 |
| scanning | 正在刷新结构。 |
| valid | 可操作。 |
| invalid | 有明确错误原因。 |
| occupied | 工作区被占用或输出槽被占用。 |
| pending clear | 动画中，工作区尚未清空。 |

错误原因放 tooltip 或 Jade，不放主界面长文本。

### 6.3 配方/加工状态

加工 UI 必须区分：

| 状态 | UI 行为 |
| --- | --- |
| missing input | 缺输入 slot ghost。 |
| missing catalyst | 催化/掺杂 slot ghost。 |
| missing template | 模板 slot ghost。 |
| no recipe | 输出 ghost 为空，状态 icon invalid。 |
| output blocked | 输出槽红框。 |
| insufficient resource | 对应资源条闪/变灰。 |
| running | progress arrow 填充。 |
| paused | progress 保持，暂停 icon。 |

## 7. 当前主线机器的 UI 约束

### 7.1 微缩机器核心

主界面应该表达：

- 框架是否有效。
- 工作区是否可压缩。
- 输出槽是否可用。
- 压缩是否 pending。

默认可见元素：

- 一个框架状态 icon。
- 一个工作区/框架小示意图或 3D 小预览。
- 一个输出 slot。
- 一个压缩按钮图标。
- 一个压缩进度条。
- 玩家背包。

默认不显示：

- 方块数量长文本。
- 类型数量长文本。
- 工作区坐标。
- 错误句子。

这些信息进入 tooltip/Jade/debug。

### 7.2 已微缩机器

主界面应该表达：

- 快照预览。
- 当前朝向。
- 哪些面是 IO。
- 哪些面颜色。
- 旋转操作。

默认可见元素：

- 结构预览。
- 六面颜色 swatch。
- 旋转箭头按钮。
- IO 面小标记。
- 状态 icon。

默认不显示：

- `size`、`blocks`、`types`、`mapping` 的文字行。
- transfer/source 数字，除非进入 details tab。

建议拆分：

- `Preview` tab：结构预览和旋转。
- `Ports` tab：六面 IO 和颜色。
- `Details` tab：size、block count、transfer/source count。
- `Diagnostics` tab：dirty/reliable/solver。

### 7.3 超材料设计台

主界面应该表达：

- 目标模板。
- 材料预算。
- 可实现包络是否覆盖目标。
- 设计是否成功。
- 输出模板或记忆结果。

默认可见元素：

- 目标模板 slot。
- 材料/粉末/晶体输入 slot 组。
- 包络匹配条或二维小图标。
- 成功/失败状态 icon。
- 输出 ghost/result slot。

默认不显示：

- 完整参数表。
- 长公式。
- 材料响应曲线数据。

hover 显示：

- 当前匹配度。
- 卡住的参数，例如折射率、损耗、带宽、孔径。
- 推荐动作一句话。

### 7.4 掺杂系统

主界面应该表达：

- 基底。
- 掺杂剂。
- 热/光/时间条件。
- 当前相或品质。
- 输出晶体。

默认可见元素：

- 基底 slot。
- 掺杂剂 slot。
- 催化/坩埚/容器 slot。
- 热条。
- 光谱匹配条。
- 时间/结晶进度箭头。
- 输出 slot。

必须覆盖：

- 温度不足。
- 温度过高。
- 光谱不匹配。
- 掺杂比例不对。
- 冷却 pending。
- 输出 blocked。

不要把掺杂解释写在 UI 上。

### 7.5 光刻系统

主界面应该表达：

- 晶圆/基板。
- 光刻胶或表面处理。
- 掩膜。
- 光源质量。
- 曝光进度。
- 显影/刻蚀结果。

默认可见元素：

- 基板 slot。
- 掩膜/template slot。
- 材料/药剂 slot。
- 光谱条。
- focus/beam quality 小 meter。
- 曝光进度条。
- 输出 slot。

必须覆盖：

- 光源不足。
- 光谱不匹配。
- 光束质量不足。
- 掩膜缺失。
- 基板未处理。
- 正在曝光。
- 曝光后等待显影。
- 输出 blocked。

详细焦距、半径、发散角、匹配曲线进入 hover 或 profiler。

## 8. AI 生成 UI 的禁止项

AI 不得生成：

- 解释性空状态卡片。
- 大标题 + 副标题式网页布局。
- 长按钮文字。
- 包含多段说明的主界面。
- 真实 slot 之外的装饰 slot。
- 需要阅读段落才能知道怎么操作的机器 UI。
- 未定义 pending 的 screen。
- 只有 ready 状态的 mockup。
- 仅通过颜色表达错误而没有形状/icon 的状态。
- 小字号中文长 tooltip。
- 每个数值都默认显示的 dashboard。
- 把 debug 日志放进普通玩家 UI。
- 与菜单点击区域不一致的 LDLib2 元素。

## 9. AI 提交前审计模板

AI 完成 UI 改动后，应在最终说明或 PR 描述里填这个模板：

```text
UI audit:
- Machine:
- Primary interaction:
- Vanilla/modded archetype used:
- Idle visible text:
- Hover-only information:
- Debug/Jade-only information:
- Covered states:
  [ ] empty
  [ ] ready
  [ ] pending
  [ ] invalid
  [ ] locked
  [ ] unreliable
  [ ] output_blocked
  [ ] unsafe
- Slot hitboxes match visuals:
- No fake slots:
- Pending disables unsafe actions:
- Error reason route:
- Exact numeric route:
- Chinese tooltip length checked:
```

## 10. 简短判定标准

如果 AI 不确定一个 UI 是否合格，问三个问题：

1. 鼠标不碰 UI 时，玩家能否通过 slot、箭头、条、图标理解机器现在的大概状态？
2. 鼠标 hover 后，玩家能否知道为什么不能运行或下一步该做什么？
3. 不看 tooltip 时，界面是否仍然像 Minecraft 机器，而不是网页设置页？

三个都是“是”，才继续实现。

## 11. 和现有 LDLib2 工作流的关系

本规范约束 AI 的设计行为。[LDLIB2_UI_WORKFLOW.md](LDLIB2_UI_WORKFLOW.md) 约束 LDLib2 模板、元素 id、fallback rect 和运行时绑定。

实现顺序：

1. 先读本文档。
2. 写 State Contract。
3. 选择原版或经典科技模组原型。
4. 再读 `LDLIB2_UI_WORKFLOW.md`。
5. 编辑 `.ui.nbt` 或 Java fallback。
6. 运行 UI 审计。

如果两份文档冲突，以“机器 UI 默认几乎无文字、真实 slot 等于点击 slot、pending 一等状态”为最高优先级。
