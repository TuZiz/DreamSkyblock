# 存储重构验证记录

## 2026-11-06 Canvas V6 恢复旧版视觉并移除重复回岛

- V5被用户否决，替换为本地D:/infinite-canvas在Edge连接Agent后的两张实际生成图，模型gpt-image-2.6-flare，画布MWHZrfVgzphwUecBzWxUo。原图、提示词与原生导出在output/imagegen/menu-canvas-v6；176×108 RGB不透明，4倍最近邻预览已目视检查。本地仅修“传送”两字及左上书本槽位，未通篇改写旧中文字。
- 两菜单布局按图片调整；空岛删除T及其独立回岛按钮，I右键脚本逐字保留，市场移到底部中央。其他items和bindings深比较一致；传送、普通副本正式文件字节保持，石址执行dungeon enter lost_altar。
- 安装Invero1.4.17真实PatchedLoader/codec/Kether/JS检查4菜单、15组动作、16脚本通过，日志target/verification/logs/invero-menu-canvas-v6.log；既有Unsafe弃用警告保留。不等于完整BaseMenu或游戏客户端验收。
- 正式与项目两张PNG、两菜单共8目标已备份并原子替换，SHA256回读一致；备份和清单target/verification/diagnostics/menu-canvas-v6-deployment.json。两个prepare脚本同步新源与布局。本轮无JAR变更、不运行Maven，未自动重载菜单/重建资源包/重启服务器；游戏显示及边缘点击体验仍待验证。

## 2026-11-06 恢复旧版统一12像素字形比例

- 用户实服截图否决上轮12/10/8px混用排版。以图二/图三旧版为字号依据，将两图13项文字统一为原生12px Fusion Pixel，不缩放字形；既有图标原像素移动，按图标+文案整体对齐。为容纳完整四字回到空岛，底部两按钮沿用原倒角扩为76px，底行点击区改KKKK TTTT，其余布局、动作、Lore及命令绑定不变。
- 新贴图位于output/imagegen/menu-type-v5；尺寸176×108、RGB不透明、二值mask；13项font_size均为12；允许排字、图标移位和底部按钮范围外像素变化为0。两张4倍最近邻预览已目视检查，prepare.py与重基脚本同步新源/热区。
- Node YAML重复键/9列/映射校验和逐项items/bindings对照通过。安装Invero1.4.17真实PatchedLoader YAML→JSON、action/绑定codec及Kether解析通过：4菜单、16组action、17条脚本。日志target/verification/logs/invero-menu-type-v5.log。探针unchecked和既有Unsafe弃用警告不变。
- 正式传送/普通副本/主菜单YAML保持原字节，石址玩家命令dungeon enter lost_altar保留。仅两张贴图和空岛底行热区更新；无DreamSkyblock代码/JAR变化，不执行Maven。
- 部署明细target/verification/diagnostics/menu-type-v5-deployment.json；未自动重启/重载Invero、重生成CE包或进入游戏，实际客户端字号/点击边缘及用户认可待确认。

## 2026-11-06 双菜单原生文字修复与普通副本入口

- 大图最近邻缩至176×108使中文字形缺笔画。沿用已选两张布局，13项标签用Fusion Pixel原生12/10/8px二值字形重新排字；最终176×108 RGB不透明，标签框外像素变化0。两张4倍最近邻预览已目视检查；未重新生图。prepare.py改为清晰源，历史export-v4.py标明只导出草稿。
- 正式传送.yml增加普通副本入口，新建普通副本.yml：石址，难度新手、服务器常驻，先menu close再playerPerform "dungeon enter lost_altar"，附返回传送菜单。核对本服Dungeon的lost_altar/farming.yml启用且ID一致，语言文件确认名称石址；未修改Dungeon业务或权限。
- 安装Invero1.4.17真实PatchedLoader YAML→JSON、绑定/action codec、Kether解析通过：4菜单、16组action、17条脚本。日志target/verification/logs/invero-crisp-dungeon.log。布局9列/映射/重复键、入口和返回动作、两主菜单YAML字节保留均通过；原Reflex Unsafe和探针unchecked警告仍存在。
- 技能通用Python YAML检查器因当前解释器无PyYAML未运行；使用本地Infinite Canvas的Node YAML解析器（含重复键错误检查）及安装JAR真实解析替代，未向项目安装新依赖。
- 两份YAML和两张贴图的正式/项目共6目标已备份、原子替换并逐文件SHA256回读一致；清单target/verification/diagnostics/crisp-dungeon-deployment.json；备份plugins/DreamSkyblock/backups/crisp-dungeon-menus-20261106-143951-561783。两张正式主页YAML未改，保留所有既有热区、动作和绑定。
- 未自动重启/重载Invero、重新生成CE资源包或执行真实玩家命令；完整BaseMenu、游戏客户端清晰度及点击→实际入场待实测。仅配置与贴图，无Kotlin或JAR改动，不执行Maven或发布JAR。

验证日期：2026-10-14。所有服务端运行在项目 `run/` 下的隔离目录，使用回环地址，没有访问生产玩家数据。

## API 与构建

核对了 Paper 26.2 的 `WorldCreator.ofKey`、`Server.getLevelDirectory`、世界路径及保存 API，读取 CraftServer 与官方旧世界迁移源码，并对 Purpur 2633 实际类做签名检查。证据位于 `target/verification/api/` 和 `run/native-probe/evidence/`。

JDK 25.0.2、Maven 3.9.12、Kotlin 2.3.10、Paper API `26.2.build.126-stable`。执行 `mvn package`；日志为 `target/verification/logs/final-build.log`，测试报告为 `target/surefire-reports/`。33 项测试，0 失败、0 错误；其中 1 项因 Windows 缺少符号链接创建权限跳过。

测试覆盖 SQLite 事务回滚、唯一成员归属、删除墓碑、快照顺序、关闭排空、YAML 迁移、世界源冲突、摘要损坏、备份恢复、目录替换中断点、创建凭据、缺失或半创建世界归档，以及 YAML 注释与未知字段保留。

## 实际服务端流程

| 平台 | 实测版本 | 隔离目录 | 覆盖 |
| --- | --- | --- | --- |
| Purpur | 26.2 build 2633 | `run/native-probe` | 原生 namespace、重复创建、UUID、保存卸载重载、官方旧世界导入 |
| Purpur | 26.2 build 2633 | `run/dream-native` | 最终插件迁移、创建、384 边界、重复加载、延迟卸载、备份恢复、删除归档与恢复、正常重启 |
| Leaf | 26.2 build 124 | `run/leaf-native` | 同样的插件完整流程，额外导入真正的旧 `level.dat` 存档并保留旧源 |
| Leaf | 26.2 build 124 | `run/leaf-flow` | 邀请/接受/离队、仅 `.use` 权限、设置持久化、旧 YAML 不复活删除家园、强制结束进程后的恢复 |

服务层探针为 `src/test/probe/DreamStorageProbe.java`，只在隔离服务端作为独立测试插件安装，不打入正式 JAR。玩家使用代理对象，世界 API、地形修改、保存、卸载与文件/数据库事务使用真实服务端实现。

`run/leaf-flow/first-run-console.log` 含 `DREAM PROBE ALL PASS`，以及成员、Flag、官方旧存档导入的独立通过标记。

异常恢复测试先备份等级 100、PVP 开启和钻石方块；将等级改为 101、PVP 关闭并改为绿宝石方块，再恢复备份。在世界目录事务 COMMITTED、SQLite 恢复尚未执行处注入异常，确认持久待恢复记录后对该隔离 Java 进程执行 `Runtime.halt`。`crash-run-console.log` 含 `DREAM PROBE CRASH CHECKPOINT PASS`，此处的维护失败日志是主动注入的预期结果。再次启动应自动补齐 SQLite，再验证等级 100、PVP 开启、钻石方块、384 边界和原世界 UUID；重启证据为 `recovery-run-console.log` 的 `DREAM PROBE RESTART ALL PASS`。

## 验证边界

- 未连接真实 Minecraft 客户端，未实测玩家传送、GUI 点击、访客在线占用及 PlaceholderAPI 联动。保留现有实现，并检查相关异步回调和人数监听路径。
- 未做长期多人压力、硬件断电或文件系统损坏测试。进程中断测试验证维护事务恢复，不能证明未保存的区块修改不会丢失。
- 未对所有未来 Purpur / Leaf 构建作兼容承诺；Leaf build 124 为实验构建。不支持 Folia / Canvas / Arbor 的区域线程模型。
- 隔离服务器已有 OSHI Windows 性能计数器异常及测试 flat 配置警告；Paper 主动保存提示、Maven 现有 systemPath、弃用 API、shade 元数据重复及测试环境 SLF4J/native-access 警告与本次测试失败分开记录。

## 菜单新增验证（2026-10-14）

最新执行 `mvn -Dtest=HomeMenuConfigTest package`，5 项菜单专项测试全部通过，日志为 `target/verification/logs/help-test.log`。验证 10 个独立 GUI 文件的版本、材质、9 格 Shape、组件和动作映射，非法布局与动作报错、无重复斜体标签、动态文本最终非斜体，以及新建模板移除原版世界但保留旧存档加载定义。新增单帮助菜单迁移验证：旧分类跳转替换为详细 Lore、保留标题/Shape/名称/未知字段，重复迁移结果一致。

在 Leaf 26.2 build 124 的 `run/gui-flow` 中，用 `DreamGuiProbe.java` 运行真实 Bukkit 库存、物品元数据、SQLite 和世界服务；玩家、InventoryView 与点击/拖拽事件为合成对象。最新 `help-final-console.log` 含 `DREAM GUI PROBE ALL PASS`。通过无岛 / 有岛菜单分流、旧模板/设置/帮助配置迁移、单帮助菜单四分类 Lore 与唯一返回操作、预览不创建、创建二次确认与取消、关闭菜单后迟到点击丢弃、拖拽及 Shift 点击取消、合并后的权限设置与无岛只读预览、设置提交后世界生效、邀请人展示及接受、成员二次确认移除、最后一人退出限制、正常退出、分页边界、升级按钮不开子菜单和回岛服务调用。初次帮助探针错误地将空动作计为有效按钮，已修正测试断言并重跑通过；该次记录为 `help-probe-assertion-console.log`。

`DreamMenuTextProbe.java` 调用实际 `MessageService.componentRaw` 渲染 302 个配置文本及动态占位符；Neuron 校验无问题。证据在 `target/verification/neuron/gui-components.json` 和 `target/verification/neuron/help-neuron-check.json`。正式 JAR 为 14,132,597 字节，含 10 个 GUI 文件，只有 `gui/help.yml`，无帮助子菜单、测试探针或服务端 API 类。没有真实客户端截图、玩家数据包驱动的点击 / 拖拽或传送实测；邀请在线玩家列表在隔离服务器仅验证空状态，这些范围保留为未实测。

## 空列表留白修正（2026-10-14）

移除通用列表渲染器的纸张占位分支及不再使用的空状态参数、语言键。无数据和分页未填满的内容槽位保持空白，不注册动作，真实条目、边框与导航保留。

`target/verification/logs/empty-list-test.log` 记录 Maven package 成功，5 项现有菜单测试通过。Leaf 26.2 build 124 的 `run/gui-flow/empty-list-console.log` 包含 `DREAM GUI EMPTY LIST PASS` 和 `DREAM GUI PROBE ALL PASS`，验证空邀请列表、重复列表空槽无物品及动作、部分页面留白、导航和真实邀请接受。玩家与点击事件仍为合成对象，未连接真实客户端复查画面。

初次探针在无关的异步传送完成前断言，日志为 `empty-list-teleport-timing-console.log`；探针改为在原有超时范围内等待完成后重跑通过，未修改传送业务。重新渲染 299 条实际 MessageService 文本；正式 JAR 为 14,132,049 字节，10 个 GUI 配置，无测试类或服务端 API。Neuron 本轮证据为 `target/verification/neuron/empty-list-neuron-check.json`，真实客户端显示和输入保留为未实测。

## 创建与模板预览合并（2026-10-14）

无岛主菜单保留一个创建/预览入口，点击后始终打开模板信息与选择菜单（只有一个模板也不跳过），选择后进入二次确认。旧主菜单同时配置创建和独立预览时，在内存中移除预览按钮及布局符号，保留创建按钮、自定义字段和原磁盘文件。直接 `/home create [template]` 的确认行为保留。

`target/verification/logs/merged-create-test.log` 记录 Maven package 成功，6 项菜单测试通过，新增迁移幂等性、布局留白及自定义字段保留验证。Leaf 26.2 build 124 的 `run/gui-flow/merged-create-console.log` 包含 `DREAM GUI MERGED CREATE PASS` 和 `DREAM GUI PROBE ALL PASS`：旧主菜单双入口合并、单模板预览、二次确认、取消不创建及后续真实创建流程通过。初次全流程探针在设置事务应用到世界之前断言；仅调整探针等待条件后重跑通过，日志保留在 `merged-create-settings-timing-console.log`。

正式 JAR 为 14,137,344 字节，10 个 GUI 配置，无测试类或服务端 API。实际 MessageService 渲染 338 条当前配置文本，本轮对修改的 `main.yml` 提交 21 条最终组件证据。Neuron 报告为 `target/verification/neuron/merged-create-neuron-check.json`；服务端玩家与事件为合成对象，未连接真实客户端检查画面和数据包输入。

## 删除模板预览菜单（2026-10-14，最新流程）

按用户最新要求删除 `template_selection.yml`、菜单枚举、加载与导航路径。创建按钮的 Lore 使用实际模板描述与平台大小介绍虚空草地、树木和资源箱，点击直接进入二次确认。旧主菜单独立预览入口移除，仅有预览入口时转换为创建入口；旧预览引导转换为岛屿介绍，原配置文件和其余自定义字段保留。旧模板预览文件不再加载，世界模板及现有空岛保留。

`target/verification/logs/direct-create-test.log` 记录 Maven package 成功，6 项菜单测试通过；扩展已有迁移测试验证预览单入口转换、描述占位符、原自定义内容保留及源预览菜单删除。Leaf 26.2 build 124 的 `run/gui-flow/direct-create-console.log` 包含 `DREAM GUI DIRECT CREATE PASS` 和 `DREAM GUI PROBE ALL PASS`，验证旧主菜单岛屿 Lore、只加载 9 个菜单、直接确认、取消不创建、正常确认创建和后续菜单流程。

正式 JAR 为 14,137,335 字节，含 9 个 GUI 配置，没有 `gui/template_selection.yml`、测试类或服务端 API。实际 MessageService 渲染 328 条当前配置文本，本轮提交 `main.yml` 的 21 条组件证据；Neuron 报告为 `target/verification/neuron/direct-create-neuron-check.json`。玩家与事件仍为合成对象，未连接真实客户端复查 Lore 排版与数据包输入。

## 无岛聊天提醒（2026-10-14）

新增独立 `NoHomeReminder`，使用一个 Paper 全局主线程定时任务管理在线玩家提醒，首次等待 3 秒、之后每 15 秒发送同一条聊天消息：“你还没有空岛，输入 /home 即可创建！”。`/home` 带点击执行命令事件，不发送 ActionBar。空岛 GUI 打开时暂停倒计时；数据未就绪不提醒，已有归属或无 `.use` 权限的玩家不提醒；退出及插件禁用清理任务和会话。语言重载使用入口共享的 MessageService，新增语言键沿用原有保留注释的补齐流程。

核对当前 Paper 26.2 依赖的 `GlobalRegionScheduler`、`ScheduledTask`、加入/退出事件及 Adventure ClickEvent 签名。`target/verification/logs/reminder-test.log` 记录 Maven package 成功，6 项现有菜单测试通过。扩展已有 Leaf 26.2 build 124 探针，`run/gui-flow/reminder-console.log` 包含 `DREAM REMINDER CHAT/GUI PAUSE PASS`、`DREAM REMINDER ALL PASS` 和原菜单流程通过标记。实际按 3/15 秒运行，验证首条延迟、精确文本及点击事件、重复启动不重复发送、菜单打开超过提醒间隔仍不发送、关闭后恢复、加入后停止、退出及停止任务清理、无权限和已有岛屿不提醒、ActionBar 调用次数为零。

首次探针编译发现 Adventure 新版 ClickEvent 不再提供旧 `value()`；按实际依赖改为事件等值检查后编译运行通过。正式 JAR 为 14,143,408 字节，9 个 GUI 资源，含提醒运行类且无测试探针或服务端 API。实际 MessageService 渲染 329 条文本，本轮提交 53 条语言组件（含提醒文本），Neuron 报告为 `target/verification/neuron/reminder-neuron-check.json`。玩家、加入/退出事件及客户端接收接口为合成对象，调度器、Bukkit 库存、SQLite 和世界为实际 Leaf 实现；未连接真实客户端复查聊天显示与点击数据包。


## PaiUI 独立对接历史（2026-10-14，已撤回）

按用户明确纠正，最终 main/island 使用居中、紧凑、半透明的游戏菜单，删除网站式导航、大标题宣传和页面留白。主菜单直接创建 / 加入好友 / 帮助；管理菜单集中显示真实归属、等级、边界、成员、Flag、经验进度和传送。升级仍为原功能的未开放说明。与原箱子动作共用调度入口，不接受客户端命令或玩家身份声明；会话令牌、认证发送者、页面、权限、原归属校验通过后才调度动作。请求桥接遵循本地 Pai SDK，先关闭 WebGui 后进入箱子子菜单。退出、外部库存打开、禁用和重载清理会话；关闭页面暂停实际 pdx-core 轮询。

`target/verification/logs/paiui-final-build.log` 的首次完整 Maven package 为39项测试、0失败/错误、1项已有跳过；最终 HTML/CSS 修改后 `paiui-game-package.log` 的6项菜单专项测试和 package 通过。成品14,195,793字节，包含两套原YAML、5个页面资源及独立paiui.yml；不含 Pai/Bukkit/Paper API 或测试探针。

Leaf26.2 build124 的独立 `run/paiui-flow/paiui-console.log` 含 `DREAM PAI PROBE ALL PASS`。玩家 / 页面传输为代理对象，Bukkit库存、SQLite、世界与全局调度为真实实现。覆盖可选Pai依赖缺失及非浏览器客户端回退、真实快照、令牌/页面/发送者/权限/动作校验、重复和迟到操作、Web/箱子互斥、创建确认/邀请/帮助/成员/设置切换、箱子返回Web、真实传送服务、升级不产生业务变更、手动与就绪超时回退、退出/外部库存/stop清理。探针独立测试插件不入正式JAR；不使用正式世界和玩家数据。

使用实际本地1.5.260905资源解析器检查已部署页面及pdx-core路径：`target/verification/api/pai-installed-resources.log`。最终浏览器验证只以常用1366×768游戏窗口为准：`output/playwright/game-menu-results.log` 检查两面板完整居中、8个动作桥接、回退、关闭与轮询停止，无JS异常。桥接测试数据来自隔离服务端真实快照，未打入页面。最终截图 `main-game.png`、`island-game.png`；早期网站布局和多宽度截图仅为被替换的历史证据。本地poller没有stop方法，初次关闭测试发现错误后改用实际pause API并验证通过。

64条语言文本经真实MessageService渲染；Neuron文本无RGB/斜体问题，报告为 `target/verification/neuron/paiui-check.json`。最终仍记为pending：未连接真实Minecraft PaiUI Mod客户端，未验证正式服务器数据包、Mod实际加载和游戏画面透明合成；浏览器与代理测试不能替代该范围。隔离测试出现已有OSHI和flat世界配置警告，不属于本次菜单错误。

已同步正式JAR与5个页面资源到用户leaf目录，并核对源/目标SHA256。原 main.yml / island.yml 哈希未变化，旧JAR备份到 `C:/Users/32633/Desktop/leaf/backups/codex-paiui-20261014-1948/`。部署记录 `target/verification/logs/paiui-deployment.json`；未重启或热重载正式服务器，更新JAR需正常重启后生效。新语言键启动时按现有迁移补齐。

## PaiUI 原箱子显示覆盖（2026-10-14，历史方案，已改用 ArcMenu）

2026-10-15 参考用户 `C:/Users/32633/Downloads/DreamSkyblock.html` 重做欢迎页：暗绿玻璃与金色线条、左侧两行标题和三个纵向入口、右侧浮空岛及模板信息、统一头尾区域。main 使用 1440×900 设计画布按游戏视口94%边界等比缩放，独立 welcome.css 不影响 island 样式。新增 icons/ 下7个手绘SVG（品牌星芒、创建空岛、好友、帮助书册、模板方块、箭头、关闭），不使用字体符号或原生物品作为这几个图标。原生点击20/24/4、无物品渲染与自定义Lore保持；未修改参考文件、原YAML、DreamSkyblock业务或JAR。

本轮 `pai-redesign-bootstrap.json`、`pai-redesign-lore.log` 的本地隔离回归通过；HTML动作/ID、7份SVG XML、JS语法检查通过。实际Pai解析器仍只匹配2个标题，Windows下原resolver读取两页24个本地依赖全部通过（`pai-redesign-dependencies.log`），10个更新资源部署并核对SHA（`pai-redesign-deployment.json`）。旧资源备份于leaf/backups/codex-pai-redesign-20261015。无桌面/浏览器UI操控、无分辨率矩阵、无服务端重启或重载；正式游戏内布局与SVG实际渲染仍待用户确认。本轮只有覆盖资源，未执行无关Java构建。

2026-10-15 后续404修复：上轮给CSS/JS引用增加 `?v=20261015-2` 引起正式Windows packet资源预处理 Bad pathname，日志11:07:56起main/island均发送失败。已撤回两页资源查询参数，固定文件引用；覆盖与按钮代码保留。新增PaiDependencyProbe使用实际WebGuiResourceResolver和Windows canonical path逐项读取两页共12个依赖，全部通过，记录 `target/verification/logs/pai-dependencies-404-fix.log`，部署HTML SHA一致。原先只检查顶层页面存在不足以覆盖完整packet依赖预处理，后续必须验证HTML内的依赖路径。无桌面操控，游戏加载与按钮仍待用户确认。

2026-10-15 修正：实际 NativeWeb 的 getContainerInfo.title 为 NativeWebView，原箱子标题由 Pai 的 title 查询参数保留；不能拿外层窗口名判定菜单已切换。固定动作槽位改为独立初始化，先注册点击再后台读取信息；数据回调未返回、首次容器仍为玩家背包时不会永久禁用按钮，按单飞轮询重试。支持 JSON 字符串回调；关闭/隐藏保留迟到回调防护。面板按游戏视口宽高等比放大，注册放大后矩形；CSS/JS URL 带资源版本避免复用旧脚本。原 YAML 与 DreamSkyblock JAR 未修改，本轮只有覆盖资源，无需 Java 构建。

`target/verification/logs/pai-native-bootstrap-check.json` 使用 Node VM 与正式 pdx-core，覆盖正常数据、数据回调不返回、首次玩家背包后恢复三种情况，均确认 20/24/4 槽位独立启用、仅绑定一次、关闭停止刷新；JS 语法通过。实际 Pai 解析器仍精确匹配两个标题，部署 SHA 核对通过。此前桌面检查记录到了 NativeWebView 的真实返回值和原创建确认子菜单，但用户随后反馈放大版仍卡加载，不能视为最终点击验收。用户明确禁止操控电脑测试后停止一切桌面输入，最终按钮与缩放点击仍待用户游戏内确认；未自动重启/重载服务端。

用户要求直接覆盖箱子显示。已撤回 DreamSkyblock 的独立 Pai 编译依赖、会话接口、WebGui 导航和页面安装逻辑，保留此前无岛聊天提醒。旧独立实现归档在 `target/verification/build-history/paiui-standalone/`。此前未替换的原因是独立入口错误地要求 HTTP 文件服务器运行，而正式 Pai 使用 `webgui.transport: packet`；当前方案不再经过该入口。

仅 `ui/paiui/main.html` 和 `island.html` 带容器标题覆盖 meta。HTML 注册原 main 的 20/24/4 与 island 的 11/13/8/15/22 槽位，使用 `setupSlotInteraction`，不发送自定义服务端命令或业务请求。实际原菜单仍负责权限与归属检查、业务及异步操作。关闭使用原生 close；切换库存保留原生容器生命周期。显示数据取自原物品 Lore，成长进度使用已有 PAPI，不伪造完整成员或 Flag 状态。

本地 Pai 1.5.260905 的真实 `WebViewMetaParser` 精确断言两个 UTF-8 标题且扫描只产生两条覆盖：`target/verification/logs/pai-container-match.log`。`pai-container-browser.log` 记录单个 1366×768 窗口的桥接契约验证：8 个按钮槽位、Lore/PAPI 更新、关闭、隐藏停止轮询、再次显示不重复绑监听、迟到回调和库存 ID 切换；无 JS 运行异常。这里使用 YAML 生成的模拟容器数据与模拟 Mod 点击桥，不能作为正式游戏客户端实测。截图为 `output/playwright/main-container.png`、`island-container.png`。

`pai-container-package.log`：6 项 HomeMenuConfigTest 全部通过，Maven package 成功。成品检查及部署记录为 `pai-container-jar.txt`、`pai-container-deployment.json`。覆盖资源、停止旧独立入口的配置和还原后的同名 JAR 同步到 leaf；原 main.yml/island.yml 哈希保持不变。未自动重启或重载正式服务器；覆盖匹配需 `/paidaxing reload` 后重开 `/home`，新 JAR 正常重启生效。最终状态 pending：正式客户端的资源同步、原生点击、子菜单返回与无 Mod 回退仍待游戏内确认。已有 Maven systemPath 与 shade 重叠元数据警告不属于本次新问题。
## GUI 局部格子更新（2026-10-15）

同一菜单的设置切换、邀请操作和翻页保留原 Inventory，只对比并写入发生变化的格子；空槽同步清除旧物品和动作。页码、动作映射和会话修订号一并更新，旧页面已排队的点击会被丢弃。切换不同菜单仍按原流程打开；异步设置提交成功后重新校验当前容器、家园归属、玩家在线与权限。

执行 `mvn -Dtest=HomeMenuConfigTest package`，6 项菜单测试通过，日志为 `target/verification/logs/partial-gui-build.log`。在隔离 Leaf 26.2 build 124 运行扩展的 `DreamGuiProbe`，`target/verification/logs/partial-gui-console.log` 包含 `DREAM GUI PARTIAL UPDATE ALL PASS` 与原 GUI 全流程通过标记：真实设置事务提交后保留容器且仅 PVP 按钮变化；跟踪 `Inventory.setItem` 验证翻页恰好写入变化格子、内容不变时零写入、显示相同但动作改变时正确更新映射、列表缩短后页码收敛并清空旧项，以及快速排队的旧页面操作不会跳转菜单。邀请目标不在线时也保留当前容器。

实际 MessageService 导出 329 条当前配置文本；Neuron 提交设置菜单的全部 85 条非空组件证据，检查 `check_c06a1980246241df8440` 通过，报告为 `target/verification/neuron/partial-gui-neuron-check.json`。正式 JAR 为 14,148,262 字节，含 9 个 GUI 资源，无服务端 API、JUnit 或探针类。已同步到 `C:\Users\32633\Desktop\leaf\plugins\DreamSkyblock-1.0-SNAPSHOT.jar`，源与目标 SHA-256 均为 `66AAEDA6F5D60E1437497060358008F11C76CB25F09EE4F5FE6A3A00E0C4FB0A`，未重启或重载该服务器。

玩家、InventoryView 和点击事件使用合成对象，Bukkit 库存、物品元数据、SQLite、世界和调度器使用真实 Leaf 实现；未连接真实客户端验证视觉与数据包行为，未操控用户桌面。构建保留既有 Shade 元数据重叠警告；隔离服务器启动出现宿主 OSHI/Windows 系统信息采集警告，GUI 验证通过且服务器正常关闭。

## ArcMenu 3.0.1 主界面（2026-10-15，当前方案）

按用户要求停止 PaiUI 方案；当前 leaf 已无 PaiDaXingPlugins JAR 和页面目录。通过本地 ArcMenu 3.0.1 实际源码与 JAR API 核对，插件没有箱子标题覆盖接口，因此使用可选 ArcMenuApi v1 桥接 `/home` 的 main/island。原 GUI YAML 保留，其他子菜单仍为原箱子；业务动作从原 components 的 action 读取并调用现有 dispatch。玩家权限统一 dreamskyblock.use，描述中包含 arcmenu.use。新增 arcmenus.yml 独立配置和真实岛屿/成员/模板 PAPI 字段。

界面使用 native rectangle/frame/line/text/block 节点；按钮不是网页，不加载 HTML、SVG 或额外 PNG。ArcMenu 当前 TextDisplay/Tooltip 使用 legacyAmpersand，不支持 MiniMessage；新增文本为纯中文，不引入传统颜色码，几何颜色使用 RGB。全局鼠标和 tooltip 皮肤保持原配置（正式服日志显示现有 CraftEngine 外部包注册），本次不更改无关皮肤。

`arcmenu-final-package.log`：6 项 HomeMenuConfigTest 全部通过、Maven package 成功。`arcmenu-documents.log` 使用实际 ArcMenu 3.0.1 MenuParser/SceneCompiler/HitTester：main 42 个原语及 4 个交互区、island 33 个原语及 6 个交互区，所有中心命中正确；YAML 为块式集合。

`arcmenu-isolated-flow.log` 是独立 Leaf 26.2 build124 + PAPI 2.12.3 的验证：synthetic Player/InventoryView 与 Arc API 传输，Bukkit 库存、SQLite、世界、全局调度和原 HomeManager 为实际实现。通过创建确认、原邀请/帮助/成员/设置、箱子返回 Arc、令牌/动作白名单/权限/归属校验、异步迟到不覆盖新菜单、API false 回退箱子、ACTIVE 创建完成后真实传送回调、PAPI 原始信息和停止时路由注销。日志含 ARC HOME PROBE ALL PASS，最终轮无 DreamSkyblock 错误。首次隔离环境缺 PAPI 导致探针类解析失败，补齐同版本可选依赖后重试；第一次流程过早停服影响尚未结束的创建异步任务，修正探针等待 ACTIVE/传送完成并延迟停服后复核通过。宿主 OSHI 计数器警告为既有环境警告。

正式成品 14,166,303 字节，9 个原 GUI 资源与 Arc 配置/桥接；无 Arc API、服务端 API、Pai 或测试探针。ArcMenu 源码本地编译依赖为 system scope，不 shade。部署到 leaf 同名 DreamSkyblock-1.0-SNAPSHOT.jar，SHA256 为 E2907AD62A5BE00A6B6B99830F7E1B00DE694672E7B7D39A1CBB265BC51696B8；两份菜单位于 plugins/ArcMenu/menus，arcmenus.yml 位于 plugins/DreamSkyblock。所有部署文件源/目标 hash 一致，原 main.yml/island.yml hash 未变。旧 JAR 备份到 backups/codex-arcmenu-20261015-180138，部署记录 arcmenu-deployment.json。

没有自动重启/重载正式服务器，没有操控用户电脑。正常重启后通过 `/home` 打开；默认 touch 模式准星指向并右键操作。正式客户端展示、位置、输入数据包和 Lore 皮肤未实测，最终记录 pending；隔离 API 传输与场景编译不代表真实游戏客户端已通过。

## ArcMenu 像素背景资源（2026-10-15，当前增量）

用户授权尝试使用 `https://api.zyuou.com/v1` 的 OpenAI 兼容生图接口。`/v1/models` 可返回图像模型列表，但 `gpt-image-2` 返回上游即时额度不可用，其他可用模型返回无可用账户或无效请求，未得到远程图片，也未把密钥写入文件或日志。最终使用 Pillow 在 320×180 逻辑画布上绘制像素画，再以最近邻放大为 960×540，文件为 `target/verification/generated/menu_pixel_v1.png`，部署为 `plugins/ArcMenu/images/dreamskyblock/menu_pixel_v1.png`。

`dreamskyblock-main.yml` 与 `dreamskyblock-island.yml` 各增加一个 `/dreamskyblock/menu_pixel_v1.png` image 节点，原后端按钮、Lore、PAPI 占位符和业务动作保留；主菜单旧的方块岛艺术节点移除，避免与背景图片重复。ArcMenu 通过现有 CraftEngine bridge 注册并合并外部资源包，没有修改 ArcMenu 或 DreamSkyblock JAR，也没有写入 CraftEngine 的内部生成包。

验证：两个 YAML 使用 PyYAML 解析通过；PNG 为 RGB 960×540；使用真实 ArcMenu 3.0.1 `ResourcePackService` 重建资源包通过，日志输出 `PIXEL PACK PASS images=7 texture=960x540 glyph=256x144 font=arcmenu:images codepoint=57362`。未连接真实 Minecraft 客户端，未执行服务器重载或重启；需要服务器按常规重载 ArcMenu/发送 CraftEngine 资源包后在游戏内确认视觉效果。

## ArcMenu 关闭与残留会话修正（2026-10-15）

用户反馈 Esc、关闭按钮无效且出现两个叠加 UI。根因是 DreamSkyblock 关闭按钮先走异步业务路由，且 `/arcmenu reload` 后可能只剩 ArcMenu 表面而不在 DreamSkyblock 会话表中。两个主菜单的关闭按钮已改为 ArcMenu 原生 `close`；DreamSkyblock 打开新菜单前会检查并关闭 ArcMenu 当前表面，Esc 仍通过 `events.close` 回收 DreamSkyblock 会话。未修改 ArcMenu JAR。

使用 Java 25 执行 `mvn -Dtest=HomeMenuConfigTest test`：6 项通过；使用 `mvn -DskipTests package`：构建成功。正式 JAR 已同步到 `C:\Users\32633\Desktop\leaf\plugins\DreamSkyblock-1.0-SNAPSHOT.jar`，源/目标 SHA-256 均为 `DAC611BEC0FFCCFF2407475224BA7404A2CD75A922F4AE7F7E2EA7B00758D850`，备份位于 `backups/codex-arcmenu-close-20261015-201003`。没有自动重启或重载；用户需正常重启服务器后再打开 `/home`，以清理本次已经卡住的旧 ArcMenu 实体。


## 透明边界与 48×48 默认范围（2026-10-17）

新岛默认边长由 100 改为 48，等级增量仍为每级 10，最大 384；配置重载不缩小已存在的较大空岛，边界占位符读取持久化的实际边长。世界保留原生 WorldBorder 的碰撞及建造限制，使用 Player.setWorldBorder 设置仅供显示的远端边界隐藏原版墙体；距边缘 3 格内，每 10 tick 向当前玩家发送局部浅蓝粒子，单边最多 27 个点，转角不超过 54 个，不在整个边界持续铺粒子。支持非成员访客、世界切换、重生清空显示边界后的补设、离岛恢复先前个人边界、退出和停用时清理。

从本地 Paper 26.2 API 源码核对 Player.get/setWorldBorder、Server.createWorldBorder、粒子与调度接口；Leaf 124 的 CraftPlayer 与 CollisionUtil 字节码确认客户端边界字段和 Level.getWorldBorder 的碰撞路径分离。证据保存在 target/verification/api/CraftPlayer-border.txt 与 CollisionUtil-border.txt，运行代码仅用官方 API。

执行 mvn -Dtest=HomeBorderOutlineTest,HomeLevelServiceTest package，7 项专项测试全部通过。target/verification/logs/transparent-border-build.log 为最终构建日志。在独立 run/border-flow 启动 Leaf 26.2 build 124，target/verification/logs/transparent-border-console.log 包含 DREAM BORDER ALL PASS：真实创建 48×48 世界、全局边界与个人显示独立、中心无粒子/接近才提示、访客、升级至 58 后边线同步、跨世界恢复原显示、其他插件后设边界保留，以及退出/停止任务清理。玩家和事件为合成对象，世界、SQLite、WorldBorder 和调度器为真实实现；没有真实客户端视觉、粒子接收或移动碰撞数据包实测，未操控用户电脑。

Neuron 检查 check_43b11f0c9e95401cac3a 通过，报告为 target/verification/neuron/transparent-border-neuron-check.json。最终 JAR 14176885 字节，未打入服务端 API、测试类或测试探针。已同步到 C:\\Users\\32633\\Desktop\\leaf\\plugins\\DreamSkyblock-1.0-SNAPSHOT.jar，源与目标 SHA-256 均为 AD114CF4A3AD6A36B13C9E16DA1F98DFA0F41A1F492831DDCD02AF27AB4536F4。服务器现有 levels.yml 的默认 base-size 从 100 改为 48，其余文本保留；原文件通过原子替换备份到 backups/configuration/levels-before-48-20261017-171415.yml。未自动重启或重载服务器。

构建保留已有 Shade 元数据重叠警告；隔离 Leaf 首次生成主世界时出现其平坦地形 generator-settings 的 No key layers 提示及 Windows OSHI 系统信息警告，未修改插件之外的配置，边界流程通过并正常关闭。


## 等级边界固定表（2026-10-17）

按用户图片将等级边界改为 levels.yml 的 border.sizes 映射：1 级 48×48、2 级 64×64、3 级 96×96、4 级 128×128、5 级 160×160、6 级 192×192、7 级 256×256、8 级 320×320、9 级 384×384；9 级以上使用最后一档，不限制等级本身。原每级加 10 公式停用。旧配置缺少 sizes 时明确日志提示并使用此默认表；已提供的表必须有完整 1-9 级键、有限数值、16-384 范围且不递减，错误配置在配置快照应用前拒绝。保留 sanitize 的不缩小规则，配置重载时已有较小范围按对应等级扩大，已有较大范围不强制缩小；单纯重启仍读取已有空岛存储的实际尺寸。

mvn -Dtest=HomeLevelServiceTest,HomeBorderOutlineTest package 成功，8 项专项测试通过，日志 target/verification/logs/border-tiers-build.log。隔离 Leaf 26.2 build 124 日志 target/verification/logs/border-tiers-console.log 包含 DREAM BORDER ALL PASS，探针改为以等级服务计算 2 级边长 64，验证实际世界边界为 64、粒子边线位于中心偏移 32，旧边线不再显示。真实服务端世界/SQLite/WorldBorder/调度器，玩家和事件为合成对象；没有真实客户端视觉或数据包碰撞实测，未操控用户桌面。原有警告为 Shade 元数据重叠、粒子展示类的 warningTime API 弃用及宿主 OSHI 信息采集警告，本次没有新增编译警告。

Neuron check_c5216988be79493db67b 通过，报告 target/verification/neuron/border-tiers-neuron-check.json。正式 JAR 14180832 字节，levels.yml 与源码一致，没有服务端 API、测试类或探针类。已同步默认 Leaf plugins 目录，源/目标 SHA-256 均为 1C50CA5C2947DE84540E03130EDAE7929777B5E298CAF3B82456C07CD25D0540。运行目录 levels.yml 已原子替换旧默认边界配置段，其他文本保留；原配置备份 C:/Users/32633/Desktop/leaf/plugins/DreamSkyblock/backups/configuration/levels-before-tiers-20261017-172111.yml。未自动重启或重载服务器。


## 当前设施价值等级与外接 API（2026-10-17）

实现 ServicesManager 的 setValue/removeValue/valueRecords/findHomeByWorldKey；原查询方法保留，原 experience 字段与占位符作为当前价值的兼容名称。使用 source + 设施稳定 ID（可为机器 UUID 或坐标 Key）记录绝对价值和单调修订号；同修订同值幂等、同修订不同值报错、旧修订返回 STALE，零值移除记录保留以拒绝迟到放置。累计门槛为 0/100/300/600/1000/1500/2100/2800/3600，等级随当前价值升降，最高 9 级，最高已解锁边界保留。启动时在暴露家园前按门槛重新计算并持久化等级；所有 Bukkit 设置回到现有 Paper 主线程调度路径，不阻塞等待。

设施明细、总值、等级与边界在同一 SQLite 事务保存，失败时内存不应用候选结果。数据库 schema v1 先验证原库，再 VACUUM INTO 备份并校验，事务创建 v2 明细表；旧经验迁移为只读 dreamskyblock-legacy/imported-experience 记录。新 home.yml 备份元数据为 schema 2，明细与移除修订号随世界备份/恢复；兼容读取旧 schema 1。API 请求按空岛串行，与成员/维护事务冲突时异常返回供原修订重试；恢复替换 Home 身份后，旧实例上已排队的请求不能应用到恢复结果。

完整 mvn package 日志 target/verification/logs/home-value-build.log：57 项测试，0 失败/错误，1 项因宿主禁止符号链接而跳过。新增价值逻辑与存储测试通过；覆盖九级边界门槛、650->550 的降级/边界保留、重复与迟到修订、溢出、损坏总值、原子回滚、v1 迁移备份/幂等重开、快照记录、启动重算。最终格式修复后执行 mvn -Dtest=HomeMenuConfigTest,HomeValueLogicTest,HomeValueStorageTest,SqliteHomeRepositoryTest package：27 项全部通过，日志 home-value-final-build.log。规范检查发现原有四处 lore 行内空列表，已改为块式格式；原有 Shade 元数据重叠、Java SQLite native-access、SLF4J 测试无绑定及少量服务端 API 弃用警告保留。

隔离 Leaf 26.2 build 124 的 home-value-console.log 含 DREAM VALUE ALL PASS，真实调用 ServicesManager API，从工作线程和主线程并发登记 550+100 得到 650/Lv4/128 边界；移除 100 得到 550/Lv3/128，全部移除 Lv1 仍保留边界。备份后移除，再恢复到 550/Lv3，设施与修订号一致，旧重试被拒绝。home-value-restart-console.log 与最终 JAR 的 home-value-final-restart-console.log 均含 DREAM VALUE RESTART ALL PASS，验证重启持久性和停用后拒绝写入。玩家传输为合成对象，世界、SQLite、备份文件、WorldBorder、ServicesManager 和调度器为真实 Leaf 实现；未测试真实机器插件集成或真实客户端显示/数据包，不操控用户桌面。

实际 MessageService 导出 331 条当前文本，为改动的 island/help/lang 提交 137 条最终组件，包含新动态占位符。Neuron check_3001346707d24cf786a9 通过；报告 target/verification/neuron/home-value-neuron-check.json。已安装配置验证脚本 ValueInstalledConfigProbe 检查门槛、GUI YAML、更新后的语言和动态占位符通过，日志 home-value-installed-config.log。API 接入约定与示例见 docs/VALUE_API.md，来源插件必须主动对接实际放置/移除/属性变化，并处理跨插件双写重试和恢复后的设施核对；此版本不自动识别未知机器或给所有原版方块计分。

最终 JAR 14221085 字节，未包含服务端/ArcMenu API、测试工具、探针或源码；已同步 C:\Users\32633\Desktop\leaf\plugins\DreamSkyblock-1.0-SNAPSHOT.jar，源/目标 SHA-256 均为 F9AAA8A3E7F5D7EB153247AC72AC677307E2F7F9F6E21B8C2FD09A205F879E85。现有等级/菜单/语言配置在保留其他文本的前提下更新，原文件备份 C:\Users\32633\Desktop\leaf\plugins\DreamSkyblock\backups\configuration\value-api-20261017-174205，后续格式备份 C:\Users\32633\Desktop\leaf\plugins\DreamSkyblock\backups\configuration\value-format-20261017-174551。没有打开或手动改写用户生产 SQLite，数据库备份升级将在正常重启后执行；没有自动重启或重载用户服务器。
## 2026-10-18 树叶、设置分类和中英文命令

- Maven：`mvn -Dtest=HomeMenuConfigTest,HomeSettingsMigrationTest,HomeCommandNamesTest package` 成功，9 项测试全部通过。覆盖 11 份菜单的 Shape/动作、25 个旗标分组完整性、旧设置原文备份、自定义按钮样式和未知根字段、已有子菜单不覆盖、重复迁移幂等、全部中文子命令与管理命令补全隔离。日志：`target/verification/logs/settings-leaves-build.log`。
- 隔离 Leaf 26.2 build 124：`target/verification/logs/settings-leaves-force-console.log` 包含 `DREAM TREE ALL PASS`、`DREAM TREE SCHEDULED PASS`、`DREAM GUI PROBE ALL PASS` 和 `DREAM GUI PARTIAL UPDATE ALL PASS`。使用真实世界、SQLite、库存及调度器，玩家为代理对象。验证 `/回家` 无岛创建入口、有岛直接调用传送，`/is 菜单` 打开菜单，根别名注册、中文补全、普通玩家隐藏管理补全；两个规则子菜单的导航和分类隔离，规则/访客各自切换只变化一个槽位，保留原容器和会话校验。
- 树叶验证包括新树 `persistent=false`、内部随机刻速度 `3`、旧初始树冠修复、相连原木保留树叶、砍树后原生距离传播、孤立树叶原生凋零、范围外装饰不修改、修复标记后新装饰不再处理。为确定性验证凋零，在测试探针中反射调用原生 randomTick；反射仅在未打包的测试代码中。增加区块强制加载票据后验证真实自动 scheduled tick。先前无真实玩家/票据的自动刻测试失败是测试区块未运行，不作为成功证据；最终带临时票据通过，票据随测试释放。
- 渲染：通过现有 MessageService 导出 343 个实际 Component，提交本次变更 YAML 的子集；本地 Neuron 检查 `target/verification/neuron/settings-leaves-check.json` 无问题、无待检查项。默认非斜体、RGB、9 字符布局和局部刷新保留；未改变可选 ArcMenu 主菜单覆盖。
- 打包：最终 JAR 为 14,235,895 字节，含 11 个 GUI 资源，过滤 `${version}` 后本次配置资源与源码一致；没有服务端 API、ArcMenu API、JUnit、测试探针或源码。Kotlin 自带 DebugProbesKt 属于运行库，不是测试插件。Maven 原有本地 systemPath、API 弃用与 shade 元数据重叠警告仍存在；本次新增随机刻调用也沿用项目的已弃用但当前有效 GameRule 常量。
- 交付：已同步 `C:\Users\32633\Desktop\leaf\plugins\DreamSkyblock-1.0-SNAPSHOT.jar`，源/目标 SHA-256 均为 `6530E134B64F954425BAB6EA902969AB758127DEA03DC7DDFAB11CC0747742E1`。未重启或重载用户服务器；旧设置在插件下次启动/配置加载时备份迁移。Maven 原始中间 JAR 已归档至 `target/verification/build-intermediates/`。
- 范围与限制：未使用桌面自动化、未执行真实客户端视觉/数据包操作，也未重复与本次无关的完整价值/备份/迁移矩阵。旧生成器未记录树叶来源，因此首次修复无法区分原初始树冠坐标上由玩家替换的永久橡树叶；只处理这一个小范围，之后以区块 PDC 标记跳过。不会重新生成已有世界或扫描全岛。

## 2026-10-18 中文根命令改为 /家

将 /回家 替换为 /家；同步插件描述、帮助与语言资源、旧帮助 Lore 适配和 README。Maven HomeMenuConfigTest/HomeCommandNamesTest package 共7项通过，日志 target/verification/logs/home-chinese-alias-build.log。隔离 Leaf 实际命令注册确认 /家 指向 home、旧 /回家 不再注册；无岛打开创建入口、有岛传送、中文菜单和补全通过，相关 GUI 探针全部通过，日志 target/verification/logs/home-chinese-alias-console.log。实际 MessageService 导出343个组件成功。最终 JAR 已同步默认 Leaf plugins，源/目标 SHA256 0DEEFF447C127AB05DD462D374B8E0E6A1B65F1D939734E322864C987D480319 一致；未重启/重载用户服务器，未做真实客户端操作。

## 2026-10-18 岛内下线后自动回岛

- 独立 `login/HomeLoginReturn` 负责原生玩家 PDC 返回标记与登录会话。标记为 home UUID，成员归属仍查询 SQLite 缓存。自己/团队岛内退出才记录；主世界或他人岛退出清除。已经在自己岛内时保留位置；世界已卸载则复用原有队列加载并回空岛传送点。等待初始化或维护结束；主动传送、离岛、归属或权限变化、快速重连和 stop 使旧会话失效。加载失败保留标记，不循环刷加载错误。
- 所有 Player/PDC/会话操作使用 Paper 主线程；没有增加依赖、数据库表、同步数据库/文件 IO 或 `saveData()`。入口显式启动，停用时先记录在线玩家意图、取消唯一任务并清空会话，再停止世界管理。世界加载完成前再次校验同一登录会话、同一 Home 对象、成员、权限与维护锁；取消不影响世界的后续安全无人卸载。
- 本地匹配 API/服务端证据：`target/verification/api/PlayerList-login-return.txt` 确认 Leaf 26.2 build 124 的 PlayerQuitEvent 调用先于 PlayerList.save；`CraftEntity-login-return.txt` 确认 PDC 使用 BukkitValues 读写。运行代码只使用 Paper API，字节码核对仅用于开发验证。
- Maven `-Dtest=HomeOperationQueueTest,HomeCommandNamesTest,HomeSettingsMigrationTest package` 成功，5 项全部通过，日志 `target/verification/logs/login-return-final-build.log`。原有 systemPath、服务端 API 弃用、Shade 元数据重叠警告保留；本次无新增依赖或 YAML/玩家文案变更。
- 隔离 Leaf 26.2 build 124 的 `login-return-console.log` 包含 `DREAM LOGIN ALL PASS`。真实世界创建、60 秒无人保存卸载、按需重载和 SQLite，真实 Craft PDC 字节序列化/恢复、主线程断言；玩家和 Join/Quit/Teleport 事件为合成对象。覆盖回岛传送点、无标记不拉走、已在岛内不重传、快速退出再登录旧对象零迟到传送、主动传送取消、撤销权限、初始化等待、RECOVERY 等待、成员移除、无效/不同 home UUID 和停用后取消。
- 最终 JAR 重启隔离 Leaf，`login-return-restart-console.log` 包含 `DREAM LOGIN RESTART PASS`：原生空岛存档、SQLite 归属和落盘的 Craft PDC 序列化标记可一起恢复并按需回岛。该探针手工保存/恢复 PDC 字节，不等同真实玩家 `.dat` 文件及客户端重连；真实退出事件到原生存盘路径以匹配服务端字节码核对，尚未进行真实客户端或协议连接测试。首次探针反射入口全部字段触发缺少可选 PlaceholderAPI 类型，已改成独立协调器实例后通过；失败日志 `login-return-probe-reflection-failed.log` 为测试装配问题，未修改生产依赖。
- 正式 JAR 为 14,244,322 字节，无服务端 API、JUnit、探针或源码，保留前轮 `/家`、树叶修复与规则/权限子菜单改动。已更新 `C:\Users\32633\Desktop\leaf\plugins\DreamSkyblock-1.0-SNAPSHOT.jar`，源/目标 SHA-256 均为 `8714A180505C4A5C6939F5B8DE1C42CAD3C78FC8284D756D93C758147A26B10B`。未重启/重载用户服务器，未打开生产 SQLite；探针只使用 run/gui-flow 隔离数据。中间 JAR 已归档到 `target/verification/build-intermediates/`，隔离原 GUI 探针已恢复。
# 2026-10-18：Invero 主菜单

- 主菜单入口优先使用 invero.yml 配置的两个 Invero 菜单，原生创建确认、邀请、成员管理、设置、帮助和传送流程保留。普通玩家继续只需 dreamskyblock.use，未增加 Invero 管理权限要求。
- Invero 使用自身插件加载器解析已安装 1.4.14 的 API，无新增编译或运行依赖。原按钮通过组件白名单转发，回调延期到玩家线程，重验家园与当前窗口；原子菜单返回走同一主入口。
- 全量 62 项中 61 项通过、1 项符号链接环境跳过；最终 8 项配置/菜单相关测试与 Maven package 通过。Invero 实际 YAML→JSON、主字段转换、动作/节点解码、27 段脚本和 1 个 JS 编译通过。
- 配套 Genlite 提供按家园保存的机器全息开关，全部共同主人拥有相同管理权限。两个新版 JAR 已置于服务器 plugins/update，活动 JAR 不变；资源已备份同步。
- 未启动或重载服务器。完整窗口需要真实 Bukkit RegistryAccess，游戏内流程待正常重启后确认；未改变本插件不支持 Folia 的声明。


# 2026-10-19：服务器主菜单定位调整

- 当前方案替代 2026-10-18 的 Invero 两页空岛主页：Invero 只保留 server-main，三行、两个按钮，/menu 或 /菜单打开；主城使用 world 当前出生点，空岛进入 DreamSkyblock 原菜单。
- 原空岛菜单第四行中心增加全息字开关，复用 Genlite 既有成员校验、异步保存与回原菜单流程；DreamSkyblock 的 Invero/Arc 接管均关闭。
- DreamSkyblock 相关 9 项测试及 Maven package 通过；实际 Invero 1.4.14 YAML→JSON、主字段转换、两项动作与命令绑定反序列化、两段 Kether、内嵌 JS 编译及 world 存在/缺失分支模拟通过。34 段原插件文案与 6 段 Invero 文案实际 Adventure 渲染通过。
- 服务端工作区只有一份菜单，旧两份已移到工作区外备份；DreamSkyblock 新 JAR 放 plugins/update，SHA-256 与构建匹配。部署报告 D:/codex/Genlite/target/verification/server-main-deployment.json，备份 D:/codex/Genlite/backups/20261019-server-main-menu。未访问机器/玩家存档。
- 不启动、不重载服务器；完整窗口、绑定注册、主城传送、空岛菜单跳转及全息切换尚待正常重启后游戏内验收。离线脚本模拟不等于实服通过，DreamSkyblock 仍拒绝 Folia。

## 2026-10-19 管理员/控制台为在线玩家打开菜单

- 新增 `/is gui 玩家名或UUID`；`/home gui 玩家名或UUID`、`/家 菜单 玩家名或UUID` 共用入口。指定目标仅允许 `dreamskyblock.admin`；普通玩家无参数 gui 保留。目标必须在线，并继续使用自己的 `.use` 权限及本人归属；有岛进入岛屿菜单，无岛进入创建入口。复用 openMain，因此沿用当前 Invero/ArcMenu/箱子路由，不传送玩家。不新增权限节点、依赖或菜单。管理员/控制台提供在线玩家名补全，普通玩家不提供该参数补全；参数多余、未指定控制台目标、找不到玩家和未就绪均有既有或新增语言反馈。
- Maven `-Dtest=HomeCommandNamesTest,HomeMenuConfigTest package` 成功，8 项测试全部通过，日志 `target/verification/logs/target-gui-build.log`。保留已有 systemPath/API 弃用/Shade 元数据告警。
- 隔离 Leaf26.2 build124 的 `target/verification/logs/target-gui-console.log` 含 `DREAM TARGET GUI ALL PASS`。使用实际注册的 PluginCommand、HomeCommand/HomeGuiManager、配置/SQLite/库存，玩家、控制台及在线玩家目录为代理对象。覆盖管理员/控制台、名称/UUID、中文别名、有岛/无岛、自助、权限拒绝、目标权限、离线、精确名称、无效 UUID、参数数量、初始化等待和补全；所有 GUI 命令均为零传送。没有真实客户端、协议登录或外部 Invero 界面实测，也没有桌面自动化。首次同时启动旧 GUI 探针造成停机时其维护任务被取消，记录于 target-gui-two-probes.log；移出旧探针后单独重跑通过，无该错误，结束后恢复旧探针。
- 新增 gui.command-usage 通过真实 MessageService 输出 RGB/italic=false Component。其他未变语言沿用 text_hash 匹配的既有真实组件；共54个语言字段证据。本地 Neuron 检查 target/verification/neuron/target-gui-check.json 通过。GUI 资源、局部更新机制及现有菜单配置均不修改。
- 正式 JAR 14,255,619 字节；未打包服务端 API、JUnit、测试探针或源码。更新 C:\Users\32633\Desktop\leaf\plugins\DreamSkyblock-1.0-SNAPSHOT.jar，源/目标 SHA-256 均为 4D16584F69109D73E0BB4A639F0B5859D62397E528B345C16D10488A13E8A73A；不新增第二份生产 JAR。中间 JAR 已归档；未重启/重载用户服务器，未访问生产 SQLite。新增语言键由现有语言补全机制在正常启动时写入，保留已有字段与注释。

## 2026-10-19 空岛菜单左上角返回 world 出生点

- `gui/island.yml` 第 0 格新增 COMPASS「返回主城」，动作 `spawn`。通过原菜单点击分发与统一 `dreamskyblock.use` 权限进入独立 WorldSpawnTeleport；每次读取已加载的 `world.spawnLocation` 并调用 Paper `teleportAsync`，不硬编码坐标、不回退其他世界。world 未加载、取消与异常使用语言文件提示；失败回调经玩家调度器回到所有权线程，并复查插件、在线、权限与玩家对象身份。
- 旧 island.yml 在加载阶段幂等补全按钮，只改变左上角、避让已用组件符号，不写回原文件，因此保留中文注释、自定义字段和其他布局。已有 spawn 组件保留。Arc/Invero 共用动作桥接受 spawn 组件；不修改外部菜单布局。测试中修正了既有全息按钮布局断言，使其与工作区实际 `#G      #` 保持一致，未移动该按钮。
- Java25 / Kotlin2.3.10 / Paper26.2.build126，Maven `-Dkotlin.compiler.daemon=false -Dtest=HomeMenuConfigTest package` 通过：9 项测试全部通过；构建日志 `target/verification/logs/world-spawn-build.log`。首次构建因沙箱禁止写 Kotlin daemon 的用户目录失败，关闭编译 daemon 后通过。已有 systemPath、API 弃用、Shade 元数据告警保留，无新增运行依赖。
- 隔离 Leaf26.2 build124 日志 `target/verification/logs/world-spawn-console.log` 含 `DREAM SPAWN ALL PASS`。实际加载旧配置、渲染库存、点击事件和业务服务；玩家、世界目录及异步传送结果使用代理，验证左上角、动态出生点、world 缺失、普通权限、离线、取消、异常及迟到权限变更。预期异常日志来自探针主动注入。首次探针反射插件字段触发可选 PlaceholderAPI 类型缺失，改为直接构造 GUI 协调器后通过；原 GUI 探针已恢复，隔离服务器已正常停止。未进行真实客户端移动/区块加载验收，未启动或重载生产服务器。
- 真实 MessageService 导出组件，`target/verification/neuron/world-spawn-check.json` 本地检查通过，无 issues/pending。裸模板检查无法覆盖统一渲染器默认非斜体，改用真实渲染证据；原帮助文案中的尖括号参数已验证保持字面文本。Neuron begin_task 被工具审批策略拒绝（approval policy=never），没有获得 task_id，因此未伪造 validate_task/finish_task 或回灌。
- 正式 JAR：`target/DreamSkyblock-1.0-SNAPSHOT.jar`，14,260,844 字节，SHA-256 `B1B9C0932B5ED66C1CDD711020BA5983173E93D988A29EBD10E3F38F3DCF231D`。包含新资源/业务类，未包含服务端 API、JUnit、探针或源码；中间 JAR 已归档至 build-intermediates。
- 尝试覆盖默认交付位置 `C:/Users/32633/Desktop/leaf/plugins/DreamSkyblock-1.0-SNAPSHOT.jar` 被文件系统权限拒绝，故生产目录未更新，不能声明源/目标哈希一致。当前沙箱仅允许写项目目录且禁止提权，产物留在 target 待部署。

## 2026-10-19 空岛菜单中央仓库入口

- `gui/island.yml` 第四行第八格（从 0 计 slot34）新增 CHEST「中央仓库」，动作 warehouse。复用 Genlite 已有 `genlite:genlite warehouse` 玩家命令，其源码要求玩家位于当前岛屿且属于该岛；不新增库存实现、不修改物品/仓库存档、不自动传送。按钮不要求 OP；dreamskyblock.use 声明 genlite.use 子权限，保留 Genlite 自身成员/就绪校验。插件未启用或命令未处理时使用独立语言键提示。
- 旧菜单加载时幂等补全 slot34，避让已用符号，不写回原 YAML，保留注释、未知字段与其他槽位；保留已有 warehouse 自定义组件。原左上角回主城、全息字及其他动作保留。原生与 Arc/Invero 动作桥均接受 warehouse 组件。
- 新按钮 YAML 无斜体控制标签。HomeMenuRenderer 统一处理物品名与 Lore，对解析后的全部普通子组件递归设置 italic=false，覆盖动态占位符和子组件显式 italic=true；不改变聊天/标题渲染。实际 ItemMeta 名称/Lore 校验通过。
- Maven `-Dkotlin.compiler.daemon=false -Dtest=HomeMenuConfigTest package` 成功，11 项测试全部通过；覆盖坐标、动作、材质、符号冲突、幂等迁移、普通权限继承及渲染入口覆盖嵌套/动态斜体。日志 `target/verification/logs/warehouse-build.log`；沿用已有 systemPath、服务端 API 弃用与 Shade 元数据告警。未增加运行依赖。
- 隔离 Leaf26.2 build124 的 `target/verification/logs/warehouse-console.log` 含 DREAM WAREHOUSE ALL PASS。真实加载旧配置、库存/ItemMeta、点击事件和 GUI 分发；玩家和 Genlite 可用性/命令执行为代理。覆盖 slot34、精确命名空间命令、普通权限、缺插件、命令失败、延期点击权限撤销、窗口替换。未启动真实 Genlite、未进行仓库存取事务或真实客户端验收。隔离服务已停止，旧 GUI 探针已恢复。
- 新增文案使用真实 HomeMenuRenderer ItemMeta/MessageService 组件，其他未变字段沿用 text_hash 匹配的历史证据。`target/verification/neuron/warehouse-check.json` 本地检查通过，无 issues/pending。Neuron begin_task 仍被审批策略拒绝（approval policy=never），未取得 task_id，不声明已远程回灌。
- 正式 JAR `target/DreamSkyblock-1.0-SNAPSHOT.jar`：14,262,620 字节，SHA-256 `0A710DB93CC0B505D1263D33742F64F552446AC61FAE892476074E7430FD9B0F`；资源和权限检查通过，未包含服务端 API、JUnit、源码或探针；中间 JAR 已归档。复制到默认桌面 leaf/plugins 时被文件系统拒绝，生产目录未更新，也未重启/重载用户服务器。当前沙箱不可提权，待在允许写入该目录的环境部署。

## 2026-10-19 世界保存去重

- HomeWorldManager 无人卸载及备份/恢复维护统一依赖 `unloadWorld(world, true)` 原生保存，不再提前 `world.save(true)` 重复完整保存。停止时空世界同样只保存卸载一次；世界仍有玩家或其他插件取消卸载时保留显式保存兜底。新建首次保存、旧数据迁移和自动保存机制保留，不屏蔽日志。必要的手动保存仍可能触发首次性能提示。
- 已核对本地匹配 CraftServer 源码及 Leaf26.2 ServerLevel 保存字节码（`target/verification/api/ServerLevel-save-optimization.txt`）：事件取消先于原生保存，save=true 路径保存并关闭世界；停用周期 autosave 不会阻止该显式路径。未增加 NMS 或运行依赖。
- Maven `-Dtest=HomeOperationQueueTest,NativeWorldArchiveTest,WorldRecoveryTest package` BUILD SUCCESS：12 项，11 通过、1 项因宿主不允许符号链接跳过，零失败/错误。日志 `target/verification/logs/save-optimization-build.log`；已有 systemPath、API 弃用及 Shade 元数据告警保留。
- 隔离 Leaf26.2 build124 日志 `target/verification/logs/save-optimization-console.log` 含 `DREAM SAVE ALL PASS` 并正常停止。实际世界/SQLite 测试覆盖维护、延迟卸载、备份、恢复、空世界停止各一次保存；取消维护卸载零保存、不进入文件事务且保留加载跟踪；取消停止卸载补保存一次。修改后的方块、箱子物品、ArmorStand、实体及世界 PDC 经多次卸载重载和备份恢复保留；autosave=false 时仍落盘；归属关联完整。主动取消维护产生的异常为预期拒绝，Windows OSHI 进程指标权限诊断为环境告警。未测试真实在线玩家的停止分支、断电/磁盘写满或重跑无关迁移矩阵；未操控用户桌面或游戏。
- 本轮未修改 GUI/玩家文本，`target/verification/neuron/save-optimization-check.json` 本地检查通过，无 issues/pending。Neuron task `task_f8f3d321cb7049389914` 的最终结果另见对应远程验证记录。
- 正式 JAR 14,262,641 字节，检查未包含 Bukkit/Paper API、JUnit、探针或源码。已覆盖更新 `C:/Users/32633/Desktop/leaf/plugins/DreamSkyblock-1.0-SNAPSHOT.jar`，源/目标 SHA-256 均为 `7522A3539F4619532EA581E3C5C2A89A94990FCF8DAFDA2A7212AA0D7DA483AA`；未重启或重载生产服务器。本次成功部署包含工作区此前已构建的功能。中间 JAR 已归档，隔离旧 GUI 探针已恢复。

## 2026-10-19 异常关闭后的世界归属恢复

- 每次 SQLite 元数据事务先落盘 PREPARED 恢复快照，SQL 提交后再标记 COMMITTED，创建世界前即有持久的主人 UUID、成员、设置、等级、价值与传送点证据。`data/recovery/homes/<home_id>.properties` 采用版本、Base64 快照及 SHA-256，复用原子替换和文件 flush；普通数据继续以 SQLite 为准。数据库已提交但恢复凭据写入失败时暂停后续写入，明确日志提示修复磁盘后正常重启，避免继续覆盖不确定数据。
- 启动先续接世界替换/删除事务，再核对归属。已有 SQLite 行（含删除墓碑）优先；缺行且凭据已提交、摘要与世界结构有效、成员不冲突时恢复 SQLite 记录，绝不重建世界。CREATING 还要求首次保存完成凭据；PREPARED、RECOVERY、墓碑、损坏/无凭据及冲突全部保留。多个缺失候选共享成员时全部拒绝，避免按遍历顺序抢占归属。待处理原因及首次发现时间持久记录于 `data/recovery/pending-worlds.properties`。
- 启动、归属恢复前、替换/删除世界前创建 SQLite 一致性快照；独立任务每 15 分钟提交一次备份，保留本功能最近 8 份于 `data/backups/recovery/`。沿用现有 SqliteValueMigration 的 VACUUM INTO 官方 SQL 路径，包含已提交 WAL，独立 quick_check/foreign_key_check 通过后原子发布并轮换；不触碰手工备份。所有 SQLite/归属凭据/压缩编码/校验均在原有数据库 worker；定时器只提交异步工作，停用取消 timer 后排空关闭数据库，无新增依赖或运行时 NMS。
- 最终 Maven `-Dkotlin.compiler.daemon=false -Dtest=HomeOwnershipRecoveryTest,SqliteHomeRepositoryTest,HomeValueStorageTest,WorldRecoveryTest,NativeWorldMigrationTest package` 成功；43 项全部通过，无失败/错误/跳过。日志 `target/verification/logs/ownership-recovery-verified-build.log`。覆盖独立 JVM Runtime.halt 真正跳过关闭钩子后 WAL/归属记录存活、提交前回滚/提交后凭据中断、后续写入暂停、删除墓碑、双向成员冲突、摘要损坏、不完整世界、备份写入失败保留现场、运行中一致性备份及仅轮换自己 8 份备份。另一次构建的队列 2 项通过。既有 systemPath/API 弃用、SQLite native access 及 Shade 元数据告警保留。
- 隔离 Leaf26.2 build124 两次启动：`ownership-prepare-console.log` 含 DREAM OWNERSHIP PREPARED，保存真实世界及恢复凭据后正常停止；完整备份隔离 DB（`target/verification/diagnostics/ownership-before-row-loss.db`），只移除测试岛的 SQL 行，保持原生目录及凭据；第二次 `ownership-recover-console.log` 含 DREAM OWNERSHIP ALL PASS，自动恢复同一 home_id，原生世界 UUID/路径、主人、等级、建筑、箱子 11 钻石和世界 PDC 均保留。未打开客户端、未触碰生产 SQLite/世界。
- 最终 JAR 的 `ownership-save-regression-retry-console.log` 含 DREAM SAVE ALL PASS：真实无人卸载、维护、备份/恢复、空世界停用均一次保存，取消维护不进入文件事务，取消停用补一次保存，autosave=false 仍落盘；方块、箱子、ArmorStand、实体/世界 PDC 及归属完整。首轮 `ownership-save-regression-console.log` 因旧探针的默认 60 秒卸载计时器先排队，70 秒总预算耗尽，在 phase11 超时；只修改排除打包的探针，等待默认任务安装后替换为 1 秒计时器并留 120 秒超时后重跑通过，生产保存逻辑未因该探针失败修改。主动取消维护产生的 ERROR、停用补保存警告、Windows OSHI 诊断属于已知测试/环境日志。
- `target/verification/neuron/ownership-recovery-check.json` 本地检查通过，无 issues/pending；无 GUI、权限、玩家文本或菜单渲染变更。Neuron task `task_1c5ff6246ef74ab9a60e` 远程结果引用最终提交证据。
- 正式 JAR 14,287,256 字节，未包含 Bukkit/Paper API、JUnit、测试写入器、探针或源码；已覆盖默认 `C:/Users/32633/Desktop/leaf/plugins/DreamSkyblock-1.0-SNAPSHOT.jar`，源/目标 SHA-256 均为 `19D662EFAF46EDB382C11074DEA103A888A81F6E6DA41D4290A781B8E9067D20`。没有重启/重载生产服务器；旧 GUI 探针已恢复，中间 JAR 已归档。
- 范围限制：没有模拟操作系统断电、整台 Leaf 在脏区块写入中被杀、真实在线玩家停止分支或等待完整 15 分钟定时周期；强制退出实测针对独立 JVM 的 SQLite/WAL 提交边界，原生存档关联恢复采用两次隔离启动验收。用户原有 `401673ef-f628-45bd-88f0-845d7f9e568e` 世界缺少可信归属证据，仍保留原目录；新版本正常启动会列入待处理清单，不能根据目录名擅自绑定给当前玩家。

## 2026-10-23 Invero 主菜单部署

- 两份主页保存在 integrations/invero，并同步至指定 workspace；服务器原配置已备份至 Invero/backups/dreamskyblock-20261023125742。
- Invero 1.4.17 安装 JAR：PatchedLoader YAML→JSON、图标动作反序列化、28 段 Kether 解析及桥接方法签名通过。日志 target/verification/logs/invero-main-runtime.log。
- Maven package 成功，HomeMenuConfigTest / InveroMenuConfigTest / HomeCommandNamesTest 共 14 项通过。日志 target/verification/logs/invero-main-build.log。
- 未执行完整 BaseMenu 打开、客户端非斜体和真实玩家点击验证；未自动重启或重载。

### 同日最终需求修正：独立 Invero 副本

用户最终要求内置有岛和无岛主页均保留，仅额外提供独立有岛 Invero 菜单；前述两主页接管方案已撤回。主菜单路由、Invero 配置与桥接恢复原实现，服务器接管配置关闭。不添加专用打开命令。外部显示改为现有 PAPI，移除会话上下文依赖；外部无岛菜单已移出 workspace 并备份。

最终检查：单菜单布局校验、安装 Invero 1.4.17 实际 YAML→JSON、9 个动作反序列化及 Kether 解析通过（invero-standalone-runtime.log）；Maven 14 项测试通过（invero-standalone-build.log）。没有执行游戏客户端打开/点击、PAPI 实时值与字体样式验收。

## Quest 事件兼容更新（2026-10-23）

正式服JAR缺少 HomeMembershipCommittedEvent 和 HomeMenuOpenedEvent。将原Quest兼容构建中的两事件及成功提交/菜单打开调用点合入当前源码，保留已有命令修改。Java25执行 mvn -o package -Dkotlin.compiler.daemon=false：81项测试，0失败、0错误、1跳过；随后使用 -Dpaper.version=26.3.build.28-alpha -DskipTests 完成正式服版本构建。独立Java25链接探针对最终JAR两事件构造/HandlerList及现用Quest DreamSkyblockBridge方法发现通过；日志 target/verification/logs/quest-event-link.log。JAR已审计无Bukkit/Paper API及探针，部署SHA校验通过；原JAR保留在服务器DreamSkyblock/backups/quest-api-compat-*。部署详情 target/verification/diagnostics/quest-api-deployment.json。正式服未重启，实际事件注册和玩家流程需重启后验收；未改配置或数据库。

## 2026-10-23 家园世界默认不掉饥饿

- 新增独立 HomeHungerListener，通过 HomeWorldManager.isHomeWorld 确认归属世界；Paper 主线程监听 EntityExhaustionEvent 与 FoodLevelChangeEvent，阻止玩家耗竭和食物值下降，允许食物值增加。成员与访客一致生效，不改难度或自然回血规则。
- 当前 paper-api 26.2.build.126-stable 的事件签名已用 javap 核查。HomeHungerListenerTest 两项通过，覆盖家园保护、吃东西、其他世界耗竭及饥饿不变；Maven package 成功。日志 target/verification/logs/home-hunger-build.log。
- 未执行真实客户端跑动、跳跃、自然回血与饥饿药效验收。其他插件直接 setFoodLevel 不经过事件的写入不在此事件保护范围。

## 2026-10-24 登录出生与家园重生

- 从当前 paper-api 26.2.build.126-stable sources/JAR 验证 AsyncPlayerSpawnLocationEvent、配置连接和重生事件；只在异步配置事件线程限时等待，世界API、归属检查与预约释放在主线程。磁盘检查仍使用原世界队列与IO线程，无存档复制/重生成。
- HomeInitialSpawn 负责配置阶段初始出生选择；HomeSpawnService 负责加载与预约；HomeRespawn 负责死亡预加载/默认重生/快速重生回岛，玩家断线、主动传送及停用清理会话。两个内置主页及独立 Invero 副本未改。
- Maven package 成功；HomeOperationQueueTest + HomeHungerListenerTest 共4项通过。日志 target/verification/logs/home-spawn-final-build.log。
- 隔离 Leaf 26.2 build124 真实世界、SQLite和调度器探针：DREAM HOME SPAWN ALL PASS。覆盖未加载岛屿初始出生直接选岛、不经过玩家teleport、配置期间阻止空世界卸载、即时重生加载回岛、已加载岛屿直接重生、有效床保留、断线取消迟到传送。玩家/配置连接为代理，不是实际客户端网络登录。日志 target/verification/logs/home-spawn-leaf-final.log。
- 首轮探针反射读取入口字段需要完整类型解析，隔离服未安装可选PAPI导致失败；只为探针补入已有PAPI后继续。第二轮探针被原60秒卸载任务覆盖短计时并超时phase5，调整探针取消原计时且等待预约释放后重设，最终通过。未通过轮次日志保留；无需为此改变生产卸载语义。
- 未验证实际网络客户端画面、多插件更晚覆盖出生位置、配置阶段30秒超时、主世界全局出生点地形安全。有效家园玩家登录现在默认直接家园，含此前在其他世界退出的玩家。快速重生且家园未加载时仍先使用服务器重生流程，随后异步回岛；不把此路径声称为零过渡。
- 隔离服正常关闭、原测试插件JAR已恢复；未重启/重载生产服务器。已有Windows OSHI计数器诊断和模板/等级迁移警告不归因于此改动；测试新岛首次保存警告沿用既有机制。

## 2026-10-24 透明边界生命周期重同步

- HomeBorderDisplay 在登录、换世界、传送、重生以及真实世界边界大小/中心变化后，合并同玩家待执行任务，下一 tick 重发本插件拥有的隐藏个人边界。补上服务端仍缓存隐藏边界、客户端却被原生世界初始化边界包覆盖时的遗漏；不改真实边界尺寸和粒子算法，不持续强制发包，不覆盖其他插件后来设置的边界。退出和停用取消待重发任务。
- 本地 Paper 26.2 build126 API 与 Leaf 26.2 build124 字节码已检查：CraftPlayer 个人边界 getter 返回缓存，setter 可重发同一对象；PlayerList 世界初始化存在发送真实世界边界的路径。证据 target/verification/api/craftplayer-border-bytecode.txt、playerlist-border-bytecode.txt。
- Maven package 成功，HomeBorderOutlineTest 4项通过；日志 target/verification/logs/border-resync-build.log。隔离 Leaf 探针 DREAM BORDER ALL PASS，覆盖非空缓存边界在重生/传送后重新发送、48真实边界、64升级后的粒子范围、访客、其他插件边界所有权、进出世界和退出/停止清理；日志 target/verification/logs/border-resync-leaf.log。测试使用代理玩家和真实 Leaf 世界/调度器，未验证实际网络客户端画面。
- 最终 JAR 已检查不含服务端 API 和测试探针，并同名覆盖指定 plugins 目录，SHA-256 一致；部署详情 target/verification/diagnostics/border-resync-deployment.json。原生产 JAR 已备份，隔离服原插件 JAR 已恢复；未重启/重载生产服。已有 Shade 重叠资源、测试服 OSHI 环境诊断不属于本次新增问题。

## 2026-10-25 家园虚空救援

- 续接未完成的 HomeVoidListener，默认在受管理家园世界生效，成员与访客一致。移动低于世界 minHeight-8 时保留 X/Z 和视角，返回 maxHeight-2 后自然下落（标准主世界为 Y318）；落入原位置仍无地面的区域会再次循环救援。其他世界不变。VOID 伤害事件提供无移动包时的传送兜底，传送失败不取消原伤害。
- 救援下降期间重置 fallDistance，阻止该次 FALL 伤害；着地、飞行、滑翔、入水结束下降保护。主动传送、取消或改写救援移动、退出、死亡、换世界、卸载世界及禁用时清理状态。监听器不执行IO或创建线程。
- JDK25 Maven 离线 package 成功，HomeVoidListenerTest 共6项通过，零失败/错误/跳过。覆盖同列顶部和视角、其他世界、普通移动、一次摔落保护、虚空伤害成功/失败、救援取消/改写、主动传送及退出/停用。日志 target/verification/logs/home-void-build.log。已有 systemPath/API弃用/Shade元数据告警保留。本次没有真实网络客户端或完整下落落地实服验证，不声明复刻模组的全部细节。
- Neuron task_a3a56e48cb304d5f968d，check_94759f955a1941cabd23 无规则错误；实际客户端验证未执行，状态 pending。未改 GUI/YAML/玩家文案。
- 正式JAR已审计包含HomeVoidListener，不含服务端API、JUnit、测试类、探针或源码；中间JAR归档到 target/verification/build-intermediates/home-void-original.jar。已同名更新 C:/Users/32633/Desktop/leaf/plugins/DreamSkyblock-1.0-SNAPSHOT.jar，旧JAR备份到插件 DreamSkyblock/backups/home-void-20261025/before-home-void.jar。源/目标SHA-256均为 B1FA23D1E39631BD28514EF96D5ED958D03399460E858C40DD8CA6B1FC21A1C7，部署详情 target/verification/diagnostics/home-void-deployment.json。没有重启或重载生产服务器。
# CraftEngine 创建空岛像素菜单

- 仅覆盖 `main.yml` 与 `create_confirm.yml`，运行图片均为 176×108 RGBA，合计 10,468 字节；标题字体 height=108/ascent=13，横向偏移 -8，不覆盖玩家背包。
- `mvn -Dtest=HomeMenuConfigTest package`：12 项通过；新增可选 `item-model` 的合法资源 ID 校验，保留现有普通菜单渲染行为。构建保留原有 Shade 重复清单警告。
- 独立 Purpur 26.2-2633 / Java 25 / CraftEngine 26.9.2：原生渲染两个 45 格菜单，透明物品模型、悬停名称与指定槽位动作通过；实际渲染入口导出 42 项 Component。
- 隔离 CE 工作流 generate/validate/zip 通过，产物包含两个字体字形、空物品模型和两个 PNG；打包前后尺寸及 RGBA 内容一致。日志：`C:/Users/32633/AppData/Local/DreamSkyblock-creation-ui/creation-ui-native.log`。测试服已正常停止；测试服首次平坦世界配置与 Windows 性能计数器诊断不属于此次插件失败。
- Neuron `task_ec7a28c66d3343d8b2d6` / `check_09d006a7e97749879a84` 通过，无 issues/pending；报告与 Component 位于 `target/verification/neuron/creation-ui-*`。
- CE 源文件与两份 GUI 已同步指定目录，8 文件 SHA-256 一致；重复安装不写文件。旧 GUI 备份：`C:/Users/32633/Desktop/leaf/plugins/DreamSkyblock/backups/creation-ui-20261025-212726-874201`。
- 正式 JAR 已同步 `C:/Users/32633/Desktop/leaf/plugins/DreamSkyblock-1.0-SNAPSHOT.jar`，SHA-256：`73D317C476DC9BE5DD7A5BE280F6BC0BEDD4DA5855EAC271071A136453FB3704`；不包含 Bukkit/Paper API 和源码。
- 未重启或重载正式服；玩家下载新资源包后的客户端最终显示与实际点击仍需确认。操作说明见 `integrations/craftengine/island-creation/README.md`。
## 空岛信息合并与帮助中心全息字入口

- 内置有岛主页将当前价值、下一等级门槛、剩余价值和最高边界合并至“我的空岛信息”，移除独立升级按钮；全息字开关位于帮助中心。帮助动作仅接受 HELP 菜单，有岛成员保持原 Genlite 调用，无岛玩家获得现有语言提示。
- `HomeInformationMigration` 对旧菜单做内存迁移，不重写磁盘注释；保留自定义 Lore、嵌套字段并避让帮助菜单已用槽位和符号，重复迁移结果一致。源资源与服务器 GUI 配置均已更新。
- `mvn -Dtest=HomeMenuConfigTest,HomeInformationMigrationTest package`：14 项通过。隔离 Purpur 26.2 原生菜单、真实渲染入口、无岛提示、成员 Genlite 不可用分支和旧主页动作拒绝通过；隔离服未安装 Genlite，未执行其实际开关功能。探针最初早于异步初始化执行，及反射需要 PAPI 类型，补充等待和隔离服 PAPI 依赖后通过；均为探针环境修正。
- Invero 源文件与正式 workspace 的“空岛菜单.yml”同步合并信息、移除旧全息字按钮；帮助按钮仍调用内置 `dreamskyblock:home help`。保留用户期间新增的 Q/K（任务中心/玩家市场）。安装的 Invero 1.4.17 实际 PatchedLoader YAML→JSON、动作反序列化与 Kether 检查通过：源副本 7 动作，正式副本 9 动作。未执行完整 Invero BaseMenu 或真实客户端验收；未改动 workspace 的主菜单。
- Neuron `task_8fdad4952c004fd4a289` / `check_206cf9e457424ac49217` 通过，无 issues/pending。报告在 `target/verification/neuron/menu-reorganization-report.json`；原生日志在 `C:/Users/32633/AppData/Local/DreamSkyblock-creation-ui/menu-reorganization.log`。
- 三份配置已部署，原文件及原 JAR 备份在 `C:/Users/32633/Desktop/leaf/plugins/DreamSkyblock/backups/menu-reorganization-20261025-221231`。正式 JAR 被运行中的服务器锁定，因此新 JAR 暂存 `plugins/update/DreamSkyblock-1.0-SNAPSHOT.jar`，与 Maven 产物 SHA-256 一致：`0eee125fc9e7031d0ad760b161aec9e3fc30ac62e967358668c855fc933957c5`。服务器配置 `settings.update-folder=update`，需下次重启生效；未自动重启或重载服务器。
- D 盘空间不足，原 target 产物完整移至 `C:/Users/32633/AppData/Local/DreamSkyblock-build-target`，项目 target 使用目录联接保持原验证路径；没有清理或删除用户文件。
## 空岛信息按钮集中管理入口

- 保留“我的空岛信息”的统计 Lore，点击打开独立三行 `gui/management.yml`，提供成员管理、空岛设置、帮助中心及返回入口；主页移除三个独立按钮。新增菜单只允许有岛玩家进入，继续使用 `.use` 权限与现有子菜单动作。
- 旧配置通过 `HomeManagementMigration` 在内存中归拢；迁移旧自定义按钮文本和嵌套字段，保留已经定制的管理菜单样式，重复加载不追加重复提示，不改写磁盘注释。
- 16 项 JUnit 与 Maven package 通过；隔离 Purpur 26.2 原生验证了 `performMenuAction(info)` → MANAGEMENT → MEMBERS / SETTINGS / HELP 三条实际导航路径，统计保留，原独立按钮不再映射。实际渲染入口导出45项本次相关 Component，Neuron `task_0bd7081d436b4045abc3` / `check_edce6f26317b4a4a856a` 通过。未执行真实客户端操作；日志在 `C:/Users/32633/AppData/Local/DreamSkyblock-creation-ui/management.log`。
- 同步 Invero 源副本和 workspace“空岛菜单.yml”。沿用已验证命令桥 `dreamskyblock:home menu-action info`；保留用户最后调整的 W/Q/K 位置与 `ismenu` 绑定。实际 Invero1.4.17 YAML→JSON / 动作codec / Kether 解析通过（7动作），未执行完整 Invero BaseMenu 客户端验证。
- 两份内置配置（island、management）、Invero工作区和更新 JAR 共4文件部署并 SHA-256 校验；旧文件备份：`C:\Users\32633\Desktop\leaf\plugins\DreamSkyblock\backups\management-20261025-223604`。正式服运行中，新 JAR 继续放在 `plugins/update/DreamSkyblock-1.0-SNAPSHOT.jar`，SHA-256 `81b51fe9cd8974924d4388d5ba221ec5ee4df3bf1e58b9a9f92397dcb498c59a`，需下次重启生效；没有自动重启或重载服务器。

## Invero 两个菜单 V5 像素皮肤（2026-10-27）

- `integrations/craftengine/invero-menus` 保存 CE 包、两份 workspace 菜单、可重复准备与带 SHA-256 防覆盖检查的安装器。运行图片 176×108，中央按钮保持 76×18 并居中。沿用用户选定 V5 图片，不再生图；原命令绑定、功能动作、名称、Lore 和 PAPI 逐项相等。
- 本地 Invero 1.4.17 已验证 `item_model` → `ItemMeta.setItemModel`，透明 PAPER 使用 `minecraft:empty`。发现现代 NMS 标题把 Adventure 转 legacy 再 literal，字体丢失；最小补丁仅改变 NMSImplModern 标题的三个调用点为 JSON 往返。两个菜单显式使用 virtual 模式，不改全局 Invero 设置和 DreamSkyblock 导航。
- 两菜单静态检查零错误；安装 JAR 的 YAML→JSON、动作 codec、Kether 共 11 动作通过，内嵌 JS 编译及主城世界存在/缺失分支模拟通过。隔离 Leaf 26.3 / Java25 / CE26.9.2 / 修复版 Invero1.4.17 成功加载完整 BaseMenu、验证标题字体/白色原生转换与修复类加载。CE generate/validate/zip 通过；ZIP 中两 PNG 像素与源一致，字体高度108、ascent13、负空格-8、空模型正确。日志：`target/verification/logs/invero-skins-native.log`。首次缺少映射资产导致超时，复制本地既有缓存后解决；两次探针反射接收者与颜色序列化断言错误修正后通过。Windows OSHI 性能计数器警告为环境问题，菜单测试无新增加载错误。
- 共9文件安装并核验 SHA-256。原菜单和原 Invero JAR 完整备份在 `C:/Users/32633/Desktop/leaf/plugins/DreamSkyblock/backups/invero-pixel-menus-20261027-192935-808003`。Invero 修复 JAR 暂存 `plugins/update/Invero-1.4.17.jar`，下次重启加载；未重启或重载正式服。生效需重启后执行 `ce reload pack`，玩家接受更新包。
- 尚未执行真实 Minecraft 客户端显示/悬停/逐按钮点击与动态物品 Component 验收。Neuron `task_db86275c566443a69fa7` / `check_392e51339d1c4d6085d2` 无 issues，因客户端验收未执行保留 pending，不标 verified。原 DreamSkyblock JAR 与代码本轮不变，无需 Maven 重建。

## CraftEngine 两个 DreamSkyblock 资源目录合并（2026-11-01）

- 将正式服 `resources/dreamskyblock-creation` 与 `resources/dreamskyblock-invero-menus` 合并到 `resources/dreamskyblock`；保留 dreamskyblock / dreamskyblock_menus 两个资源命名空间、4个图片 ID、字体、模型及所有贴图字节。Genlite 等目录不变。
- 10个资源文件逐文件 SHA-256 一致；显式资源 ID 无冲突、贴图路径全部可解析，重复运行合并器不产生写入。旧目录完整归档：`C:/Users/32633/Desktop/leaf/plugins/DreamSkyblock/backups/ce-pack-merge-20261101-201400-341828`，含 manifest。
- 两个安装脚本统一使用同一目标目录和共享 `integrations/craftengine/pack.yml`，避免覆盖为不同的包信息或重新生成拆分目录；安装说明同步。
- 本次仅整理资源目录和元信息，未修改菜单、图片或插件代码，不执行 Maven 构建；未重启/重载正式服，也未重新生成完整 CE 包或执行客户端验收。生效时执行 `ce reload pack`。

## 非空岛世界上线位置保留（2026-11-04）

- 根因：HomeInitialSpawn 对每个有空岛的玩家都无条件调用出生预加载，覆盖服务器提供的主城等世界位置。现在传入事件原出生世界 UUID，HomeSpawnService 在 global 调度器内确认它是玩家自己已加载空岛，才继续模板选择和预加载。普通世界上线保留原位置；空岛卸载后的 PDC 登录兜底和死亡重生路径不变。
- `mvn -Dtest=HomeSpawnServiceTest package` 通过，6项测试覆盖非岛世界、自己已加载岛、卸载岛、无岛玩家、重生入口及实际 AsyncPlayerSpawnLocationEvent 保持位置。服务测试使用调度器/世界替身执行真实方法，不属于真人重登实测。构建保留已有 shade 重叠元信息警告，无新增编译或测试失败。
- 正式 JAR 已同名更新到 `C:/Users/32633/Desktop/leaf/plugins/DreamSkyblock-1.0-SNAPSHOT.jar`；SHA-256 `67680F157105A95EAD3E3E111094DA2480832306F4040D34FAD704B41DF86DE7` 与构建产物一致。旧包备份 `plugins/DreamSkyblock/backups/login-world-20261104-154557`。检查最终 JAR 不含 Bukkit/Paper API、JUnit、源码或本次测试类。未自动重启服务器，重启后生效。
- Neuron task_4815871ab0044e83b69b 已提交真实测试/构建/部署证据，因未执行真实玩家重登保留 pending。没有修改 GUI、语言、配置及数据库格式。


## 2026-11-04 主城主菜单移除返回主城

- Infinite Canvas（Edge已打开画布）使用gpt-image-2.5-flare参考现有图生成，导出176×108，三按钮均76×18；原图/提示词/预览见output/imagegen/server-menu-no-spawn。
- YAML唯一键解析、5×9布局和映射、H条目删除、I/Q/K完整条目及绑定/标题与部署前一致：通过。
- 正式Invero主菜单与CraftEngine主菜单图已备份同步，逐文件SHA-256回读一致；空岛菜单和贴图未变化。
- 无代码/JAR变动，不运行Maven。未重启/重载正式服；安装版Invero完整加载、客户端资源包和三个按钮实际点击仍待游戏验收。
- 备份：C:\Users\32633\Desktop\leaf\plugins\DreamSkyblock\backups\server-menu-no-spawn-20261104-165301


## 2026-11-04 主菜单按图一统一画风

- 原Infinite Canvas画布使用gpt-image-2.5-flare，以用户图一为实际风格参考，生成灰米石格/立体灰绿按钮/棕框；修正上版平涂背景。原稿与对比见output/imagegen/server-menu-island-style。
- 176×108导出、三个76×18按钮位置、并排目视检查、项目/正式贴图SHA-256回读一致：通过。两个Invero菜单YAML及空岛贴图hash保持不变。
- 仅贴图和生成来源/文档变动，不执行Maven；未重载正式服，客户端资源包生效效果仍待实测。
- 备份：C:\Users\32633\Desktop\leaf\plugins\DreamSkyblock\backups\server-menu-island-style-20261104-170341


## 2026-11-04 两个UI背景完全不透明

- 主菜单已有alpha全255，保留原文件；空岛图alpha原为3–255，去掉透明通道并同步项目/正式CE纹理，原RGB逐像素保持。
- 两张纹理176×108、alpha全255、部署SHA-256回读一致、Invero菜单YAML不变、导出脚本语法：通过。prepare导出统一RGB以防透明回退。
- 输出及检查见output/imagegen/opaque-menus，备份：C:\Users\32633\Desktop\leaf\plugins\DreamSkyblock\backups\opaque-menus-20261104-183230。
- 无代码/JAR变动，不执行Maven；未重载资源包/游戏验收。


## 2026-11-04 服务器主菜单覆盖格子背景

- 修正上次仅检查alpha的范围：用户截图中的主菜单格子已烘焙进RGB，改为空岛菜单同款实底；三个按钮裁剪区及标题字形像素保持。
- 原生176×108、alpha全255、按钮像素一致、标题字形保持、项目/正式SHA-256回读一致：通过。Invero两个YAML及空岛图未变。
- prepare主菜单源已更新，防止恢复旧格子版。输出和报告：output/imagegen/server-menu-solid-background。
- 备份：C:\Users\32633\Desktop\leaf\plugins\DreamSkyblock\backups\server-menu-solid-background-20261104-183831；未重载资源包/游戏验收，无JAR变更不执行Maven。

## 2026-11-04 Invero旧虚拟窗口刷新串屏

- 本地Invero 1.4.17字节码确认：InventoryPacket.open$lambda$7每20 tick发送全库存，窗口isViewing仅依赖关闭时未恢复的closed字段；虚拟容器共用119。旧窗口可持续覆盖新窗口按钮，与截图中服务器背景出现中央仓库提示一致。
- PatchInveroWindowRefresh只替换该回调一处检查为WindowManagerKt.isRegistered；不全局修改isViewing，避免破坏先注销再关窗的现有逻辑。保留已安装Adventure标题补丁。
- 实际JAR回调字节码提取测试（平台边界替身）：原版旧任务覆盖复现；补丁活动窗口刷新、切换取消、关闭取消均通过，Java -Xverify:all通过。测试替身不能代表游戏验收。
- 仅集成补丁工具/JAR变化，不修改DreamSkyblock Kotlin或资源，不运行无关Maven构建。两JAR条目差异和部署SHA-256结果见target/verification/diagnostics/invero-refresh-deployment.json。
- 修复版暂存plugins/update/Invero-1.4.17.jar，原JAR已备份；完整重启后生效。未自动重启/重载服务器，真实客户端闪烁仍待验证。

## 2026-11-04 用户澄清悬停闪动：避免活动按钮周期重发

- 用户澄清：只有鼠标所在按钮闪动，没有发白。上一节旧窗口覆盖是可复现缺陷，不能据此认定用户悬停闪动已解决。
- 补充核对实际updatePlayerItems(true)：即使活动窗口也每秒调用全库存update，反复发送所有按钮。改为沿用update(int...)仅同步玩家背包范围，保留首次打开/主动菜单刷新。原旧窗口取消补丁继续保留。
- 从原/新JAR提取实际定时回调及updatePlayerItems字节码，平台边界替身回归、Java -Xverify:all通过：原版全库存重发复现；新版本连续5次只发送45–80背包槽位、36项映射保持、悬停菜单物品保持、初始不发包，以及旧窗口切换/关闭取消通过。
- 仅InventoryPacket.class变化，其余JAR条目保持；正式update目录旧暂存包备份后原子替换并核验SHA-256，详情见target/verification/diagnostics/invero-hover-deployment.json。Invero workspace和CE图片未修改，无需改图或重新打资源包。
- 尚未重启服务器或实测Minecraft客户端，悬停闪动消除仍为pending；本轮不涉及DreamSkyblock构建，不运行无关Maven。

## 2026-11-05 创造模式离岛恢复飞行

- 修正HomeProtectionListener离岛无条件allowFlight=false，以及HomeWorldSettings将岛屿飞行开关直接覆盖所有游戏模式的问题。统一HomeFlightSettings：创造/旁观始终允许飞行；生存/冒险按岛屿开关处理，禁飞时结束当前飞行；不主动让站立玩家起飞。
- 跨世界传送在MONITOR且ignoreCancelled=true处理，已取消传送不再改变飞行。沿用现有玩家事件及世界设置执行线程，无新任务或状态缓存。
- Maven -Dtest=HomeFlightSettingsTest package成功，3项测试覆盖四种模式、飞行许可恢复、正在飞行的保留/撤销。测试使用Player代理，未执行真实玩家跨世界验收。已有shade的module-info/MANIFEST重复警告仍存在。
- 正式JAR包含新策略类，未打入Paper/Bukkit API、测试或源码；14337450字节。自动更新plugins/DreamSkyblock-1.0-SNAPSHOT.jar，SHA-256=A06829078B6A4A982EB128CD71BD8A5243F1458794822CD8D8F33225F3E795A4，与产物一致。
- 原JAR备份在plugins/DreamSkyblock/backups/creative-flight-20261105-184832；未重启或重载服务器，新代码待重启生效。部署报告target/verification/diagnostics/creative-flight-deployment.json。

## 2026-11-06 /is gui统一到现有Invero空岛菜单

- 用户最新要求替代旧“独立副本、不接管有岛主页”约定。原正式invero.yml禁用且指向不存在的旧菜单名，导致/is gui仍用内置箱子。
- 默认/正式配置启用Invero，island-menu指向空岛菜单；main-menu空字符串表示保留无岛创建流程。配置解析支持中文菜单ID并拒绝路径/扩展名/空岛ID留空；桥接对空main-menu直接保留内置创建页。业务功能子菜单、现有Invero按钮和贴图不变。
- 安装Invero1.4.17及其实际cache依赖javap确认PlatformFactory.getAPI(String)、InveroAPI.getMenuManager、InveroMenuManager.getMenu(String,boolean)、Menu.open(Player,Map)。未更改这些API调用。
- Maven InveroMenuConfigTest/ HomeMenuConfigTest/ HomeFlightSettingsTest共18测试通过，package成功；正式配置YAML解析并解析到与/ismenu相同的5行布局菜单通过（当前正式菜单未显式配置rows/virtual，保留现有设置）。既有shade重复manifest/module-info警告保留。
- 正式同名JAR和invero.yml已备份部署；14337560字节，SHA-256=2118E915EE8BB29DB330651598C3329B123E45B70AFF0E2AFA000049281F2336，目标与构建产物一致；未打包Paper/Bukkit API/测试源码。
- 备份plugins/DreamSkyblock/backups/invero-unified-entry-20261106-111004；部署报告target/verification/diagnostics/invero-route-deployment.json。未自动重启/重载，实际玩家命令→Invero打开及子菜单返回流程仍待重启后验收。

## 2026-11-06 2.6生图模型与信息右键回岛

- 本地Infinite Canvas在Edge中将XToken两个模型由2.5改为gpt-image-2.6-flare/sunburst，默认生图设为2.6-flare；画布重载后工具回读current/model列表确认，使用2.6-flare实际生成服务器V4成功。
- 用户改选空岛V2，弃用V3中央双按钮；服务器V4保留中间三条竖排，任务改左上独立小方块。生成图已下载、目视检查并最近邻导出176×108不透明RGB和4倍预览，见output/imagegen/teleport-menu-preview/v4-export.json。新传送按钮命令未给，贴图和新热区未部署，避免出现无效入口。
- 独立完成空岛信息右键回岛：仅I.action.right及两条Lore提示变化，右键脚本与原T.action.left逐字相同，左键与其他条目保留；正式和项目workspace SHA256=5bbfa2c823c7b1e8e974f3819bf3787dbcbf6f36777d9f13dcfbe8f2e34e77e1一致，备份路径见target/verification/diagnostics/info-right-click.json。
- 安装Invero1.4.17实际PatchedLoader YAML→JSON、命令绑定/点击action codec、Kether解析通过：两菜单、11组action、12条脚本（含新增right）；未执行真实玩家点击、完整BaseMenu或游戏渲染。既有Reflex Unsafe弃用警告保留。
- 本轮无DreamSkyblock Kotlin/资源JAR变化，不运行Maven；未自动重启/重载服务器。实际菜单右键行为和新增贴图业务均待验证/补齐命令。

## 2026-11-06 新增传送空壳菜单并部署双菜单

- 用户答复：新增Invero传送.yml，不需要功能。新增三行箱子，仅边框和装饰指南针，无action、传送命令、地点或bindings；两个主菜单P按钮只用menu open '传送'打开该菜单。
- 安装版Invero1.4.17 ActionMenu字节码确认open子动作；真实PatchedLoader YAML→JSON、绑定/点击codec和Kether解析通过：3菜单、13组action、14脚本。无完整BaseMenu/玩家运行；既有Unsafe弃用警告保留。
- 主菜单点击区左上Q任务、中间P传送/I家园/K市场；空岛保留原顶部W/I、中央改P传送、服务Q/C/S、底部K市场/T回岛；既有条目、绑定、信息I右键回岛均逐项相等，空壳无action已检查。
- 两张原生176×108不透明RGB贴图、3份YAML备份后同步正式与项目镜像，逐文件SHA-256回读一致。备份plugins/DreamSkyblock/backups/teleport-menus-20261106-134514；完整清单target/verification/diagnostics/teleport-menus-deployment.json。
- 贴图使用已选生成图的最近邻原生尺寸导出，生成字样原生细节和热区边缘手感待客户端确认。未自动重载Invero、重新生成/加载CE资源包或重启服务器；本轮仅菜单/贴图，不构建DreamSkyblock JAR。

## 用户选定截图恢复（V7）
- V6遭用户否决；用户指定9cd63784/73ed29e5两张旧截图。恢复server-menu-solid-background与opaque-menus/island-menu原生176×108 RGB，不重新生成整图；仅改中央传送字样和主菜单左上任务小块，保留原标题/家园/市场/服务文字与边框底板。
- 源及预览output/imagegen/menu-favorite-v7；prepare脚本同步。I右键回岛保留，无T重复入口，普通副本/石址未变。真实Invero解析4菜单15动作16脚本通过，8目标备份原子同步和SHA256一致，详见target/verification/diagnostics/menu-favorite-v7-deployment.json。
- 未自动重载或重建CE包，无Kotlin/JAR变更；新局部修订尚未用户认可/游戏验收。截图风格获认可与修改后成品获认可分别记录。

## V8 原生像素贴图部署
用户确认注意像素大小并实施。运行PNG为176×108 RGB不透明，704×432仅最近邻预览，未将生成大图当作运行贴图。两处传送补正原生12px二值字形；native-validation.json检查文字框外像素不变。素材output/imagegen/menu-regenerated-v8。任务热区移到首行第二格，其余动作/绑定与传送副本文件保留。Invero1.4.17真实解析4菜单15动作16脚本通过；8目标已备份原子同步并SHA256一致，清单target/verification/diagnostics/menu-regenerated-v8-deployment.json。本轮无JAR修改，不构建，不自动重载/重建CE包，实际客户端显示与点击验收待完成。

## V9 文字间距与残影修复
用户实服截图反馈字挤和奇怪阴影。原生176×108标题12px/正文10px，每字1px额外间隔；13标签使用二值字形，不绘制文字阴影，清理字框残影；脚本验证框外像素变化0。预览已目视检查。4张正式/项目PNG备份后原子替换、SHA256一致；4份菜单字节不变，不新增脚本/动作，不需重新执行Invero解析。报告target/verification/diagnostics/menu-type-v9-deployment.json。未自动重载/生成推送CE包，实际游戏渲染待确认。

## Xerr 12px字体替换（V10）
用户否决V9字体，授权Xerr生成包字体。核对quest_ui_position_light.json和legacy/quest_ui_255_plain.json指向fusion_pixel_quest_12_sc.ttf，size12/oversample1。运行图13标签原生12px，不缩字形，不额外字距，无文字阴影；框外像素差0，176×108RGB，4倍预览目视检查。4目标备份原子部署hash一致，4菜单字节保持，报告target/verification/diagnostics/menu-xerr-v10-deployment.json。未修改Xerr字体配置和HUD，不重载服务器/重建CE包。客户端验收待完成。

## 传送及普通副本四行小卡片
用户要求两页至少4×9且为后续分类/副本留位置。最终两页均176×90RGB，左上54×44卡片，右侧留白；普通副本卡片进入子菜单，石址卡片执行原dungeon enter lost_altar，信息在Lore，左下返回。CE独立teleport-menus.yml使用新glyph E913/E914、height90、ascent13；图标/原生Xerr12px标签经4倍最近邻目视检查。实际Invero解析4菜单16组动作17脚本通过（日志invero-dungeon-skins.log），无完整BaseMenu/客户端运行。10目标备份原子同步及SHA256一致，原两主页菜单字节保持；报告target/verification/diagnostics/dungeon-skins-deployment.json。未自动重载或生成/推送CE资源包，游戏验收待完成；无DreamSkyblock代码/JAR变更、不构建。

## 六卡片位与备用菜单
传送/普通副本调整5×9/176×108RGB，3×2六个52×30小卡位，原生Xerr12px，仅首卡业务，其他五个空白无动作，返回在底部。新版导出export-six-cards.py及准备脚本同步。实际解析4菜单16动作17脚本通过，10目标备份原子同步hash一致，报告dungeon-skins-deployment.json。
备用菜单新增4×9/176×90RGB，用户确认主城按钮先无命令（无action），大厅dungeon lobby、家园复用原主页动作。实际解析1菜单2动作2脚本通过，6目标备份同步hash一致，4原菜单不变，报告backup-menu-deployment.json。新图像CE配置独立；未重建/推送CE包或重载/重启，客户端验收待完成。

## 备用菜单标题与按钮缩小
文件备用菜单.yml不改名，贴图显示标题服务器菜单。按钮70×16/图标10×10，Xerr12px字形，176×90RGB四行。原动作/配置布局保持（主城未绑定、大厅dungeon lobby、家园复用主页）。目视检查原生四倍最近邻预览；实际解析1菜单2动作2脚本通过，6目标备份同步SHA256一致，其他4菜单不变。backup-menu-deployment.json有清单。未重载或重建CE包，客户端验收待完成。

## 移除预留位空框
用户澄清六位置仅为空间预留，不应显示空方框。两页只保留左上真实卡片，其他5处恢复背景；原卡片、图标、标题、坐标、字体、176×108五行及返回保持。导出与目视检查通过；4PNG备份原子同步hash一致；5份正式和项目菜单YAML hash全部保持，因此不重跑未变的Invero解析。报告target/verification/diagnostics/no-empty-frames-deployment.json。未自动重载或重建CE包，客户端效果待应用后确认。

## 2026-11-09 空岛右上角设置齿轮
用户确认预览后，176×108 RGB 贴图同步项目与正式 CraftEngine 包。仅14×14区域变化，2目标备份与SHA256回读通过，全部Invero YAML未改；prepare.py引用新版本。报告 target/verification/diagnostics/island-settings-deployment.json。标题区无原版物品槽，当前齿轮只显示，可点击布局待用户选择。未构建JAR（无代码变更）、未生成/推送资源包、未游戏验证。

## 2026-11-09 空岛设置可点击版
用户允许齿轮下移到首行最右slot8，layout首行WWWW IIIG，信息按钮缩短保留左右键。G执行已核对HomeCommand.kt的dreamskyblock:home settings。原生176×108 RGB，仅93..166/19..39局部变化，14×14齿轮完全位于第九格；已目视检查4倍最近邻预览。Invero1.4.17真实YAML→JSON/动作codec/Kether通过1菜单9动作10脚本；现有Unsafe警告与本次无关。两贴图与两菜单备份部署SHA256通过，报告target/verification/diagnostics/island-settings-clickable-deployment.json。未改插件代码、不构建JAR；未重载或生成/推送资源包，未游戏验收。

## 2026-11-09 游戏设置语义与信息图标修正
用户纠正齿轮为游戏设置，不是空岛规则。新增独立Invero游戏设置.yml，genlite holograms玩家动作与返回空岛入口；G改为menu open游戏设置。Genlite-1.0.0.jar存在，plugin.yml声明genlite.use默认true。信息按钮恢复12×12草方块、原生12px文字，首行WWWWIIIIG，I原左右键保持；局部区域外像素不变、原生和最近邻4倍已目视检查。真实Invero1.4.17加载/动作codec/Kether通过2菜单11动作12脚本；6目标备份原子同步SHA256一致，报告target/verification/diagnostics/game-settings-fix-deployment.json。未修改JAR、不构建；未重载Invero、生成推送资源包、游戏点击或Genlite命令实测。

## 2026-11-09 游戏设置任务信息追踪计分板开关
游戏设置第二行改为H/Q左右两个开关，Q玩家执行用户指定quest hud toggle；H和B动作语义深比较保持。实际Invero1.4.17 YAML→JSON/动作codec/Kether通过1菜单3动作3脚本。正式workspace、项目镜像、暂存源3文件备份原子同步SHA256通过。报告target/verification/diagnostics/quest-hud-toggle-deployment.json。未自动重载、未游戏执行命令；无贴图或代码变更，无需资源包或JAR构建。

2026-11-09 游戏设置像素版已部署：复用用户指定 Quest 备份原卡与木框，5×9、底板176×108、卡片40×73；仅绘制全息显示、任务追踪，其他位置留白。两按钮仍由玩家执行 genlite holograms / quest hud toggle。前者是当前空岛共享设置，后者是个人任务HUD。每10 tick在玩家EntityScheduler读取真实缓存，状态变化才更新标题；未就绪显示灰色横线，关闭/离线/会话替换取消任务。Quest1.0.0私有runtime/preferences字段耦合已通过安装JAR签名检查，升级需重验。正式与项目镜像20目标已备份原子同步、SHA256回读一致。制作/部署入口 integrations/craftengine/invero-menus/game-settings/；当前导出 output/imagegen/game-settings-release/，旧game-settings-fix部署脚本停用，避免恢复旧菜单。

验证：安装Invero1.4.17真实YAML→JSON、动作/节点codec、Kether解析、Nashorn编译通过（1菜单3动作2节点3脚本）；post_open/close真实事件codec通过；Nashorn配测试替身验证ON/OFF/unknown、异步状态刷新、不重复发标题、错误限次日志和任务取消。7张PNG与配置引用已核对，mixed原生最近邻预览已目视检查。报告 target/verification/diagnostics/game-settings-pixel-deployment.json。仅配置和贴图更新，未修改/构建JAR；未重启、未执行在线重载、未生成或推送CE资源包、未游戏客户端验证。需执行 menu reload、ce reload pack 并接受最新资源包；物品最终斜体状态和实际热区仍待游戏验证。

2026-11-09 开关灰色横线修正：实服截图确认底板和unknown卡已加载。安装Invero的HandlerKt.updateTitle在isTitleUpdating为true时直接返回；初始菜单打开会锁两tick，旧脚本首tick更新却提前写Settings__State，存在丢失首次状态包且不重试的问题。刷新首延迟改为4tick，并在标题锁定期间跳过且不提交状态；setTitle成功返回后才记状态，下一轮补发。保留原贴图与两个命令。增加真实YAML解码后脚本源一致性检查、Nashorn初次/后续标题锁的延后补发回归；安装Invero解析和脚本测试通过。20个live/project目标备份、SHA256校验同步完成，manifest为target/verification/diagnostics/game-settings-pixel-deployment.json。本轮未在线重载、未客户端复测，仍待重新打开菜单确认；贴图未改，无需重建CE资源包。 

2026-11-09 Xerr字体统一已同步leaf与项目镜像：标题/卡片保持原生12px Xerr字体，返回文字改为同一字体直接绘制，取消旧返回牌字形缩放；仅底板footer像素变化，六张状态卡与旧版逐像素相同。7张PNG格式/尺寸与变化范围检查通过，14个目标备份同步SHA256一致。清单target/verification/diagnostics/game-settings-font-deployment.json。已目视检查mixed-4x.png；未重建推送资源包、未游戏验证。

2026-11-09 副本大厅服务器菜单补传送：备用菜单.yml扩为5×9/176×108，四个70×16按钮按传送→主城→副本大厅→家园排列；P执行menu open 传送，S/D/H条目与部署前逐项一致，主城仍无命令。其他四个菜单字节保持。真实Invero解析1菜单3动作3脚本通过，6个正式/项目目标备份原子部署SHA256一致，清单target/verification/diagnostics/backup-menu-teleport-deployment.json。像素预览已目视检查；未在线重载或生成推送资源包，客户端待确认。需menu reload、ce reload pack并接受最新资源包。

2026-11-09 当前有效版本：服务器菜单改为2×2，5×9/176×108RGB，卡片62×34（为槽位和文字边界从草案64×36微调），奶油底细棕边、绿色细线点缀。图标上/名称下：末影珍珠传送、指南针主城、遗迹大厅、草方块家园。Xerr原生12px字形，无文字缩放或阴影。热区PPPP SSSS两行/空一行/DDDD HHHH两行；三个原入口内容和传送命令保持。真实Invero加载codec/Kether1菜单3动作3脚本通过，预览目视检查并修正文字与底边相交，6正式/镜像目标备份同步SHA256一致，其他四菜单字节保持。清单target/verification/diagnostics/backup-menu-grid-deployment.json。旧纵向图/脚本保存在preview-vertical-history.png与export-vertical-history.py。未在线重载或生成推送资源包、未游戏验证，需menu reload与ce reload pack并接受最新资源包。

2026-11-09 用户指定主城命令warp spawn：备用菜单S左键先menu close，再playerPerform warp spawn，Lore更新返回服务器主城。prepare-backup-menu.mjs默认命令同步。2×2布局、贴图及其他条目保持；安装Invero真实解析1菜单4动作4脚本通过，两份workspace备份原子同步SHA256一致，清单target/verification/diagnostics/backup-spawn-deployment.json。未执行游戏内传送、未在线重载；无需重建资源包。

2026-11-09 用户确认卷轴单格按钮移左下并落地：按钮14×14、坐标(12,90)，完整位于slot36，J条目打开独立五行空玩法.yml。空菜单全AIR、不加虚构玩法或返回业务按钮。保留所有原条目，仅末行layout改J  KKK三个尾空格。正式与项目2PNG/4YAML共6目标备份原子同步SHA256一致，部署清单target/verification/diagnostics/gameplay-entry-deployment.json；prepare.py同步最终图和布局，避免重建回退。真实Invero1.4.17解析2菜单10动作11脚本通过，PNG176×108RGB、仅左下14×14变化、已目视检查。未执行在线重载、CE生成推送资源包或Minecraft客户端验证；需menu reload、ce reload pack并接受最新资源包。

2026-11-09 卷轴上移微调：按用户截图将(12,90)上移2px到(12,88)，尺寸14px不变，两张正式/项目贴图备份同步SHA256一致。变化范围限左下14×16（含旧位置清除）；菜单及动作未改，不重新构建JAR。已目视检查，实际客户端待应用CE资源包确认。顶端2px装饰边跨槽36上边界，图标主要可点区域保持槽36；未扩张任务按钮热区。清单target/verification/diagnostics/gameplay-offset-deployment.json。

## 2026-11-10 Dungeon 常驻副本重生协作

HomeRespawn 在死亡和重生时识别 Dungeon 的 `dungeon:permanent_death_spawn` 字符串 PDC 记录，跳过回岛会话并释放旧预约。Dungeon 同时改为在重生 MONITOR 阶段消费记录，避免 HIGHEST 注册顺序冲突。两边需同步更新。

本次 Maven package 成功，104 项测试通过。Dungeon 的 Leaf26.3 隔离服探针通过真实新 JAR 反射调用 HomeRespawn 两个处理器，验证有标记死亡不进入回岛准备；该探针未运行整个空岛插件与数据库。正式 plugins 和 plugins/update 已备份部署，SHA256一致；正式服务器尚未重启，玩家联测待确认。

## 2026-11-11 队伍中央仓库名称

新增只读 API findHomeOwnerName(UUID)，返回内存 Home.ownerName 持久化创建者名称，不做磁盘或网络查询。HomeView 构造契约未改。Maven test package 成功：104项，1项已有 native archive 测试跳过；javap 确认接口存在。与正式 JAR 对比只有接口、实现及同源文件行号变化的 HomeView/HomePoint class 不同。正式 JAR 被运行中的 JVM 占用，备份后暂存 plugins/update，与 PlayerVault 配套下次启动生效；未重启、未实服验证。备份 leaf/.codex-backups/warehouse-owner-name-20261111-162827。

## 2026-11-12 服务器菜单视觉统一
- 范围仅备用菜单的项目/leaf两份backup-menu.png；5行2×2四入口、全部命令和YAML保持原样。
- 原生176×108 RGB、按钮70×27、原生Xerr12px，4倍最近邻预览已目视检查；按钮保持在原物品槽热区内。
- Node YAML检查现有5×9布局/4入口和CE glyph height108/ascent13/纹理引用通过。部署脚本校验其他workspace YAML和菜单PNG未变；2目标原子替换SHA256一致。清单：target/verification/diagnostics/server-menu-style-deployment.json。
- 未运行Maven（无代码改动）；未在线重载、未生成推送资源包、未Minecraft客户端验收。需ce reload pack及客户端接受新包。

## 2026-11-12 服务器菜单布局跟随主菜单
- 用户纠正2×2布局，已按正式主菜单server-menu.png改为中轴紧凑纵排；四按钮74×16，原生176×108 RGB/Xerr12px。预览已目视检查。
- 仅变更备用菜单layout和对应PNG；深比较确保全部items、命令与其余字段保持。真实Invero1.4.17加载/动作codec/Kether解析通过：1菜单4动作4脚本。
- 几何检查每个按钮完整落在对应槽热区；四目标备份原子同步SHA256一致，其他正式YAML/PNG哈希保持。见target/verification/diagnostics/server-menu-layout-deployment.json。
- 无Java/Kotlin变更，不执行Maven。未在线重载/重建推送资源包/游戏验收。

## 2026-11-12 游戏设置玩家文案清理
删除H/Q按钮关于灰色横线尚未就绪的技术说明，以及任务按钮设置保存后自动同步的说明；保留实际功能、共享设置说明与点击提示。以最新leaf配置重基，项目镜像和staged同步；准备脚本清除同类文案避免重新生成。YAML解析及深比较通过，仅正式H/Q Lore改变，动作/节点/事件保持；三目标备份原子更新SHA256一致。清单target/verification/diagnostics/settings-copy-cleanup.json。纯文案变更，未构建、未游戏验证；Invero自动载入或menu reload生效，无需重建资源包。

## 2026-11-13 云端副本大厅UI缺失
- 直接SSH核对D:/Purpur-server：备用菜单标题E915存在，菜单布局/命令完整；CraftEngine缺configuration/backup-menu.yml与textures/gui/backup-menu.png。generated及hosted两个ZIP均无E915 provider，E914普通副本资源完整，确认是大厅服务器菜单缺资产。
- 已补传且校验两文件SHA256；云端菜单SHA256保持7EFA7AA288403BC0AE859AAC49E7DFE08ACCA02BF66DEF01A6EB111A7FEA70CC。配置height108/ascent13/E915与菜单引用及176×108贴图匹配，预览已目视检查。
- 记录target/verification/diagnostics/cloud-ui/restore-report.json；云端manifest D:/Purpur-deploy-backups/backup-menu-ui-20261113-153208/manifest.json。原目标均不存在，无旧资源覆盖。
- 未重启/热加载/执行reload；需ce reload pack重建并推送，客户端接受新包后验收。无业务代码/JAR变更，不运行Maven。

## 2026-11-20 Canvas 适配进度

- Canvas 26.3 build 944 本地 API 签名核对通过。异步卸载结果接入空岛维护与旧存档迁移，存档事务等待卸载结果。部分传送和内置菜单点击改由玩家实体调度器执行。
- Maven test 统计 104 项，0 失败、0 错误、1 跳过。日志见 target/verification/logs/canvas-port-preparation.log。
- 初始平台写入、首次存档以及其他玩家生命周期调度仍待迁移。未在 Canvas 26.3-1001 运行，保留原启动限制，未打包部署。

## 2026-11-20 Canvas 26.3 适配

- 启用 Folia 加载声明。区域线程核心须提供 Canvas 异步世界卸载接口。Canvas 按区块生成初始平台，首次卸载保存后验证存档并提交创建标记，再重新加载。Paper 保留同步保存路径。
- Maven package 成功，104 项测试中 103 项通过、1 项跳过。JAR 已核实声明 folia-supported: true，未包含 Bukkit、Paper 或 Canvas API。
- 相同 JAR 在隔离 Canvas 26.3-944 和 Purpur 26.2-2633 启用，完成创建、首次保存、重开读草方块、维护卸载。探针使用代理玩家，未验证真实玩家登录、回岛或 GUI 点击。Canvas 26.3-1001 下载未完成，未实测该构建。
- 日志见 target/verification/logs/canvas-port-final.log、canvas-944-final-runtime.log、canvas-port-purpur-final.log。构建 SHA-256 为 63185775a7d8a80499a1ebeebc11c8a4ece87876b1de903b527e6d03c99c4388。
- 本地 leaf 旧 JAR 锁定，新 JAR 待更新。云端 Purpur 已校验并暂存 plugins/update/DreamSkyblock-1.0-SNAPSHOT.jar，备份 D:/Purpur-deploy-backups/ad37358b954f4c02ac73576ba423c030。未重启正式实例。
