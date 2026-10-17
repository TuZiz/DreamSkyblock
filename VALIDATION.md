# 存储重构验证记录

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
