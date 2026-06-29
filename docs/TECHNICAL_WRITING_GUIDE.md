# Spectralization 技术写作规范 / Technical writing guide

本文档规定 Spectralization 的技术文档、玩家说明、开发说明、日志说明和数据包说明应如何写。它不是通用中文作文规范，而是为这个模组的特殊架构服务的写作规范。

This guide defines how to write technical documents, player-facing guides, developer notes, logging guides, and data-pack references for Spectralization. It is not a generic prose guide. It is a writing standard for this mod's architecture and gameplay.

## 0. 参考来源 / References

本规范参考了以下英文资料，并只吸收适合 Spectralization 的部分：

This guide adapts practices from these English-language references:

- [Google developer documentation style guide](https://developers.google.com/style)
- [Google style guide highlights](https://developers.google.com/style/highlights)
- [Microsoft Writing Style Guide](https://learn.microsoft.com/en-us/style-guide/welcome/)
- [Kubernetes Documentation Style Guide](https://kubernetes.io/docs/contribute/style/style-guide/)
- [Diataxis documentation framework](https://diataxis.fr/)
- [Red Hat Modular Documentation Reference Guide](https://redhat-documentation.github.io/modular-docs/)
- [Write the Docs: Style Guides](https://www.writethedocs.org/guide/writing/style-guides/)

优先级如下：

Reference priority:

1. Spectralization 项目内规范。
2. 本文档。
3. Google / Microsoft / Kubernetes 等公开技术写作规范。
4. 普通语言工具书或临时判断。

1. Spectralization project-specific rules.
2. This guide.
3. Public technical writing guides such as Google, Microsoft, and Kubernetes.
4. General language references or local judgment.

## 1. 总目标 / Main goal

**CN:** 文档的目标不是炫耀系统有多复杂，而是让读者知道下一步该做什么、为什么这样做、出问题时从哪里查。

**EN:** Documentation should not display complexity for its own sake. It should tell readers what to do next, why the system works that way, and where to look when something breaks.

**CN:** Spectralization 的系统很复杂，所以文档必须主动降低认知负担。先讲玩法目的，再讲架构理由，最后讲数学或代码细节。

**EN:** Spectralization is complex, so documentation must actively reduce cognitive load. Explain the gameplay purpose first, the architecture rationale second, and the math or code details last.

## 2. 文档类型 / Document types

每篇文档只能有一个主要类型。可以链接到其他类型，但不要把四种目的混在同一个段落里。

Each document must have one primary type. It can link to other types, but do not mix all purposes in the same paragraph.

| 类型 | 中文用途 | English purpose |
| --- | --- | --- |
| 教程 / Tutorial | 带新读者完成一条学习路线。 | Teach a new reader by guiding them through a learning path. |
| 操作指南 / How-to | 解决一个具体任务，例如“如何读取日志”。 | Solve one concrete task, such as "how to read logs". |
| 参考 / Reference | 给出准确、完整、稳定的事实，例如配置项、NBT、配方 schema。 | Provide accurate, complete, stable facts such as config keys, NBT, or recipe schemas. |
| 解释 / Explanation | 解释为什么系统这样设计，例如三层编译器架构。 | Explain why the system is designed this way, such as the three-layer compiler architecture. |

Spectralization 推荐映射：

Recommended mapping for Spectralization:

- README：项目入口和当前可玩内容。
- TODO：路线图和状态。
- 架构文档：解释。
- 日志文档：操作指南 + 参考。
- 数据包文档：参考 + 示例。
- 玩家手册：教程 + 操作指南。

- README: project entry point and current playable content.
- TODO: roadmap and status.
- Architecture docs: explanation.
- Logging docs: how-to plus reference.
- Data-pack docs: reference plus examples.
- Player handbook: tutorials plus how-to guides.

## 3. 读者顺序 / Audience order

默认读者顺序如下：

Default audience priority:

1. 正在游玩的玩家。
2. 正在调包或写数据包的整合包作者。
3. 正在定位 bug 的开发者。
4. 正在扩展系统的未来开发者。

1. Players who are actively playing.
2. Pack authors who configure data packs or modpacks.
3. Developers who diagnose bugs.
4. Future developers who extend the system.

**CN:** 同一个事实对不同读者有不同表达。玩家需要“这样玩会发生什么”；开发者需要“哪一层 dirty，哪个缓存失效”。

**EN:** The same fact needs different wording for different readers. Players need to know what happens in play. Developers need to know which layer becomes dirty and which cache is invalidated.

## 4. 语气 / Voice and tone

**CN:** 使用直接、清楚、稳定的语气。可以温和，但不要营销腔。可以承认复杂，但不要把复杂当作卖点。

**EN:** Use a direct, clear, stable voice. The tone can be friendly, but not promotional. Acknowledge complexity when needed, but do not treat complexity as the selling point.

**CN:** 中文优先使用短句。英文优先使用 active voice、present tense 和 second person。

**EN:** Prefer short sentences in Chinese. In English, prefer active voice, present tense, and second person.

示例：

Examples:

- 推荐：`放置光纤接口后，系统会标记相关光网络为 dirty。`
- 避免：`在某些情况下，也许可能会导致整个系统似乎被某种方式重新计算。`
- Preferred: `After you place a fiber port, the system marks the related optical network as dirty.`
- Avoid: `In some cases, it may possibly cause the entire system to somehow seem recalculated.`

## 5. 中英文一致性 / Chinese-English consistency

中英文版本必须共享同一个结构。不要写两篇内容接近但顺序不同的文章。

Chinese and English versions must share the same structure. Do not write two similar documents with different ordering.

规则：

Rules:

- 每个标题在中英文中表达同一个概念。
- 每条规则使用同一个编号。
- 中文可以更自然，英文可以更直接，但不得改变约束强度。
- 中文里的“必须、应该、可以、不要”要分别对应 English 的 `must`, `should`, `can/may`, `do not`。
- 如果中文加入例外，英文也必须加入同一个例外。
- 如果英文加入警告，中文也必须加入同一个警告。
- 术语第一次出现时写成 `中文术语（English term）`，之后按文档语言使用本地术语。

- Each heading must express the same concept in both languages.
- Each rule must use the same number in both languages.
- Chinese can be more natural, and English can be more direct, but the requirement level must not change.
- Chinese `必须`, `应该`, `可以`, and `不要` correspond to English `must`, `should`, `can/may`, and `do not`.
- If Chinese adds an exception, English must include the same exception.
- If English adds a warning, Chinese must include the same warning.
- On first use, write terms as `中文术语 (English term)`, then use the local term for the document language.

一致性检查：

Consistency checklist:

- 标题数量一致。
- 规则数量一致。
- 示例表达同一种行为。
- 警告和限制没有只出现在一种语言里。
- 数值、路径、配置键、类名完全相同。

- The heading count is the same.
- The rule count is the same.
- Examples describe the same behavior.
- Warnings and limits do not appear in only one language.
- Numbers, paths, config keys, and class names are identical.

## 6. Spectralization 专用结构 / Spectralization-specific structure

写一个系统时，优先使用这个顺序：

Use this order when documenting a system:

1. 玩家为什么会接触它。
2. 它解决什么玩法问题。
3. 它如何映射到几何层、拓扑层、数据层。
4. 哪些事件会让它 dirty 或重新认证。
5. 它的可靠状态和错误状态是什么。
6. 它和其他系统的边界在哪里。
7. 配置、日志、数据包或调试入口。

1. Why players encounter it.
2. Which gameplay problem it solves.
3. How it maps to the geometry, topology, and data layers.
4. Which events make it dirty or trigger validation.
5. What its reliable and error states are.
6. Where its boundaries with other systems are.
7. Config, logging, data-pack, or debug entry points.

不要先从类名、矩阵或实现技巧开始，除非文档本身是纯参考。

Do not begin with class names, matrices, or implementation tricks unless the document is pure reference.

## 7. 架构说明 / Architecture writing

**CN:** 任何新光学功能的文档必须说明它属于哪一层，尤其要说明它是否改变拓扑。

**EN:** Documentation for any new optical feature must state which layer it belongs to, especially whether it changes topology.

推荐模板：

Recommended template:

```text
几何层：这个系统在世界中占据哪些位置或节点？
拓扑层：它暴露哪些端口、方向边或连通关系？
数据层：它提供或消费哪些 profile、读数、缓存或状态？
事件边界：哪些世界事件会改变几何、拓扑或数据？
可靠性：什么时候读数可靠，什么时候进入 unreliable/error？
```

```text
Geometry layer: Which positions or nodes does this system occupy in the world?
Topology layer: Which ports, directed edges, or connectivity does it expose?
Data layer: Which profiles, readouts, caches, or states does it provide or consume?
Event boundary: Which world events change geometry, topology, or data?
Reliability: When are readouts reliable, and when does the system become unreliable or error?
```

## 8. 数学说明 / Math writing

**CN:** 数学不是第一入口。先写直觉，再写变量，再写不变量，最后写失败模式。

**EN:** Math is not the first entry point. Start with intuition, then variables, then invariants, then failure modes.

数学段落必须包含：

Math sections must include:

- 这个模型服务的玩法目的。
- 每个变量的单位或范围。
- 不变量，例如“输出总功率必须等于输入总功率”。
- 数值保护，例如 soft cap、hard cap、半径下限、可靠性降级。
- 玩家是否能直接看到这个变量。

- The gameplay purpose of the model.
- The unit or range of each variable.
- Invariants, such as "total output power must equal total input power".
- Numerical protections, such as soft caps, hard caps, minimum radius, or reliability fallback.
- Whether players can directly observe the variable.

## 9. 玩法说明 / Gameplay writing

**CN:** 玩家说明先讲收益，再讲代价，再讲失败反馈。不要先讲内部算法。

**EN:** Player-facing docs explain the benefit first, the cost second, and the failure feedback third. Do not start with the internal algorithm.

推荐句式：

Recommended pattern:

```text
做 X 可以让你 Y，但它会消耗 Z。如果条件不满足，机器会显示 A，并且不会执行 B。
```

```text
Doing X lets you Y, but it consumes Z. If the condition is not met, the machine shows A and does not perform B.
```

## 10. 日志说明 / Logging writing

日志文档必须帮助读者定位“哪一层先错了”。

Logging docs must help readers identify which layer failed first.

每个日志事件至少说明：

Each log event should explain:

- 事件名。
- 触发原因。
- 相关子系统。
- 是否改变几何层。
- 是否改变拓扑层。
- 是否诱导光学网络 dirty。
- 哪些字段用于和 `latest.log` 或 `optical_compiler_*.log` 对齐。

- Event name.
- Trigger reason.
- Related subsystem.
- Whether geometry changed.
- Whether topology changed.
- Whether it marks an optical network dirty.
- Which fields align the event with `latest.log` or `optical_compiler_*.log`.

## 11. 术语 / Terminology

术语必须稳定。中文文档可以使用中文术语，但代码标识、配置键、NBT key 和日志字段必须保持英文原样。

Terminology must be stable. Chinese documents may use Chinese terms, but code identifiers, config keys, NBT keys, and log fields must remain in English.

| 中文 | English | 说明 |
| --- | --- | --- |
| 几何层 | geometry layer | 世界位置、加载状态、节点集合。 |
| 拓扑层 | topology layer | 端口图、方向边、连通关系、SCC。 |
| 数据层 | data layer | 功率、频率、读数、缓存、可靠性。 |
| 读数 | readout | 传感器、机器、HUD 或 UI 读取的导出结果。 |
| 散杂光 | diffuse light | 模组设定中的非相干或散杂通道。 |
| 相干光 | coherent light | 可进入相干增益、干涉或未来信号系统的通道。 |
| 微缩机器 | microlizer | 压缩后的结构和它的世界方块。 |
| 全息存储 | holographic storage | 全息矩阵、核心、水晶和 screen 构成的存储系统。 |

## 12. Markdown 规则 / Markdown rules

**CN:** 标题使用句首大写或自然中文标题，不要为了装饰使用过多符号。

**EN:** Use sentence-style headings. Do not add decorative punctuation to headings.

规则：

Rules:

- 命令、路径、类名、字段名、配置键使用反引号。
- 连续步骤使用有序列表。
- 平行信息使用无序列表。
- 二维对照信息使用表格。
- 链接文字必须描述目标，不要写“点这里”。
- 图片必须有 alt text。
- 示例配置必须标明语言，例如 `toml`、`json`、`text`。

- Use code font for commands, paths, class names, field names, and config keys.
- Use ordered lists for sequences.
- Use unordered lists for parallel information.
- Use tables for two-dimensional comparisons.
- Link text must describe the target. Do not write "click here".
- Images must have alt text.
- Example config blocks must include a language tag, such as `toml`, `json`, or `text`.

## 13. 状态标记 / Status labels

使用统一状态标记，避免读者误以为原型已经是最终玩法。

Use consistent status labels so readers do not mistake prototypes for final gameplay.

| 标记 | 中文含义 | English meaning |
| --- | --- | --- |
| `[x]` | 已实现并至少经过基础验证。 | Implemented and at least basically verified. |
| `[~]` | 原型可用，但仍需平衡、性能或体验调整。 | Prototype works, but still needs balance, performance, or UX work. |
| `[ ]` | 尚未实现。 | Not implemented yet. |
| `[!]` | 已知问题或需要重新设计。 | Known issue or needs redesign. |
| `[hold]` | 暂时保留，不进入当前阶段。 | Kept for later, outside the current phase. |

## 14. 示例和反例 / Examples and non-examples

**CN:** 当规则可能被误解时，必须同时给出一个正确示例和一个反例。

**EN:** When a rule can be misunderstood, include both a good example and a non-example.

示例：

Example:

```text
推荐：光纤连接被剪断后，相关端点会标记光网络为 dirty。
反例：光纤连接被剪断后，系统会更新。
```

```text
Good: After a fiber connection is cut, the related endpoints mark the optical network dirty.
Avoid: After a fiber connection is cut, the system updates.
```

## 15. 更新流程 / Update workflow

提交文档前检查：

Before committing documentation:

1. 读者能否在前两段知道文档用途？
2. 是否说明当前功能状态？
3. 是否把玩法、架构、数学、代码细节分开？
4. 是否避免了只在一种语言里出现的限制或承诺？
5. 路径、配置键、类名、日志字段是否准确？
6. 新术语是否进入术语表或在首次出现时定义？

1. Can readers understand the purpose within the first two paragraphs?
2. Does the document state the current feature status?
3. Are gameplay, architecture, math, and code details separated?
4. Are limits or promises present in both languages when the document is bilingual?
5. Are paths, config keys, class names, and log fields accurate?
6. Are new terms added to the terminology table or defined on first use?

## 16. 不要做的事 / Do not

- 不要把未来功能写成已经存在的玩法。
- 不要把调试 fallback 写成主架构。
- 不要用“显然、简单、只要”来跳过玩家需要理解的步骤。
- 不要把中文写成英文直译腔。
- 不要为了中英逐字对应牺牲可读性；保持规则和承诺一致即可。
- 不要在玩家文档中先堆叠类名、矩阵和内部字段。

- Do not describe future features as existing gameplay.
- Do not describe debug fallbacks as the main architecture.
- Do not use phrases like "obvious", "simple", or "just" to skip steps readers need.
- Do not write Chinese as a literal translation of English.
- Do not sacrifice readability for word-by-word bilingual matching. Keep rules and commitments consistent.
- Do not begin player-facing docs with class names, matrices, or internal fields.
