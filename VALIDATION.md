# 存储重构验证记录

验证日期：2026-10-14。所有服务端运行在项目 `run/` 下的隔离目录，使用回环地址，没有访问生产玩家数据。

## API 与构建

核对了 Paper 26.2 的 `WorldCreator.ofKey`、`Server.getLevelDirectory`、世界路径及保存 API，读取 CraftServer 与官方旧世界迁移源码，并对 Purpur 2633 实际类做签名检查。证据位于 `target/evidence/` 和 `run/native-probe/evidence/`。

JDK 25.0.2、Maven 3.9.12、Kotlin 2.3.10、Paper API `26.2.build.126-stable`。执行 `mvn package`；日志为 `target/final-build.log`，测试报告为 `target/surefire-reports/`。33 项测试，0 失败、0 错误；其中 1 项因 Windows 缺少符号链接创建权限跳过。

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

最新执行 `mvn -Dtest=HomeMenuConfigTest package`，5 项菜单专项测试全部通过，日志为 `target/help-test.log`。验证 10 个独立 GUI 文件的版本、材质、9 格 Shape、组件和动作映射，非法布局与动作报错、无重复斜体标签、动态文本最终非斜体，以及新建模板移除原版世界但保留旧存档加载定义。新增单帮助菜单迁移验证：旧分类跳转替换为详细 Lore、保留标题/Shape/名称/未知字段，重复迁移结果一致。

在 Leaf 26.2 build 124 的 `run/gui-flow` 中，用 `DreamGuiProbe.java` 运行真实 Bukkit 库存、物品元数据、SQLite 和世界服务；玩家、InventoryView 与点击/拖拽事件为合成对象。最新 `help-final-console.log` 含 `DREAM GUI PROBE ALL PASS`。通过无岛 / 有岛菜单分流、旧模板/设置/帮助配置迁移、单帮助菜单四分类 Lore 与唯一返回操作、预览不创建、创建二次确认与取消、关闭菜单后迟到点击丢弃、拖拽及 Shift 点击取消、合并后的权限设置与无岛只读预览、设置提交后世界生效、邀请人展示及接受、成员二次确认移除、最后一人退出限制、正常退出、分页边界、升级按钮不开子菜单和回岛服务调用。初次帮助探针错误地将空动作计为有效按钮，已修正测试断言并重跑通过；该次记录为 `help-probe-assertion-console.log`。

`DreamMenuTextProbe.java` 调用实际 `MessageService.componentRaw` 渲染 302 个配置文本及动态占位符；Neuron 校验无问题。证据在 `target/gui-components.json` 和 `target/help-neuron-check.json`。正式 JAR 为 14,132,597 字节，含 10 个 GUI 文件，只有 `gui/help.yml`，无帮助子菜单、测试探针或服务端 API 类。没有真实客户端截图、玩家数据包驱动的点击 / 拖拽或传送实测；邀请在线玩家列表在隔离服务器仅验证空状态，这些范围保留为未实测。

## 空列表留白修正（2026-10-14）

移除通用列表渲染器的纸张占位分支及不再使用的空状态参数、语言键。无数据和分页未填满的内容槽位保持空白，不注册动作，真实条目、边框与导航保留。

`target/empty-list-test.log` 记录 Maven package 成功，5 项现有菜单测试通过。Leaf 26.2 build 124 的 `run/gui-flow/empty-list-console.log` 包含 `DREAM GUI EMPTY LIST PASS` 和 `DREAM GUI PROBE ALL PASS`，验证空邀请列表、重复列表空槽无物品及动作、部分页面留白、导航和真实邀请接受。玩家与点击事件仍为合成对象，未连接真实客户端复查画面。

初次探针在无关的异步传送完成前断言，日志为 `empty-list-teleport-timing-console.log`；探针改为在原有超时范围内等待完成后重跑通过，未修改传送业务。重新渲染 299 条实际 MessageService 文本；正式 JAR 为 14,132,049 字节，10 个 GUI 配置，无测试类或服务端 API。Neuron 本轮证据为 `target/empty-list-neuron-check.json`，真实客户端显示和输入保留为未实测。

## 创建与模板预览合并（2026-10-14）

无岛主菜单保留一个创建/预览入口，点击后始终打开模板信息与选择菜单（只有一个模板也不跳过），选择后进入二次确认。旧主菜单同时配置创建和独立预览时，在内存中移除预览按钮及布局符号，保留创建按钮、自定义字段和原磁盘文件。直接 `/home create [template]` 的确认行为保留。

`target/merged-create-test.log` 记录 Maven package 成功，6 项菜单测试通过，新增迁移幂等性、布局留白及自定义字段保留验证。Leaf 26.2 build 124 的 `run/gui-flow/merged-create-console.log` 包含 `DREAM GUI MERGED CREATE PASS` 和 `DREAM GUI PROBE ALL PASS`：旧主菜单双入口合并、单模板预览、二次确认、取消不创建及后续真实创建流程通过。初次全流程探针在设置事务应用到世界之前断言；仅调整探针等待条件后重跑通过，日志保留在 `merged-create-settings-timing-console.log`。

正式 JAR 为 14,137,344 字节，10 个 GUI 配置，无测试类或服务端 API。实际 MessageService 渲染 338 条当前配置文本，本轮对修改的 `main.yml` 提交 21 条最终组件证据。Neuron 报告为 `target/merged-create-neuron-check.json`；服务端玩家与事件为合成对象，未连接真实客户端检查画面和数据包输入。

## 删除模板预览菜单（2026-10-14，最新流程）

按用户最新要求删除 `template_selection.yml`、菜单枚举、加载与导航路径。创建按钮的 Lore 使用实际模板描述与平台大小介绍虚空草地、树木和资源箱，点击直接进入二次确认。旧主菜单独立预览入口移除，仅有预览入口时转换为创建入口；旧预览引导转换为岛屿介绍，原配置文件和其余自定义字段保留。旧模板预览文件不再加载，世界模板及现有空岛保留。

`target/direct-create-test.log` 记录 Maven package 成功，6 项菜单测试通过；扩展已有迁移测试验证预览单入口转换、描述占位符、原自定义内容保留及源预览菜单删除。Leaf 26.2 build 124 的 `run/gui-flow/direct-create-console.log` 包含 `DREAM GUI DIRECT CREATE PASS` 和 `DREAM GUI PROBE ALL PASS`，验证旧主菜单岛屿 Lore、只加载 9 个菜单、直接确认、取消不创建、正常确认创建和后续菜单流程。

正式 JAR 为 14,137,335 字节，含 9 个 GUI 配置，没有 `gui/template_selection.yml`、测试类或服务端 API。实际 MessageService 渲染 328 条当前配置文本，本轮提交 `main.yml` 的 21 条组件证据；Neuron 报告为 `target/direct-create-neuron-check.json`。玩家与事件仍为合成对象，未连接真实客户端复查 Lore 排版与数据包输入。
