# DreamSkyblock

基于现有 Kotlin + Maven 插件的世界与数据存储重构，保留创建、邀请、团队权限、菜单、等级、传送和 PlaceholderAPI 功能。

## 构建

- 使用 JDK 25 和 Maven。
- 当前项目使用 Kotlin 2.3.10，Paper API 固定为 `26.2.build.126-stable`，编译目标为 Java 25。
- 构建前将 `PlaceholderAPI-2.12.3.jar` 放入项目根目录的 `libs/` 文件夹；当前 `pom.xml` 通过本地路径引用该依赖。
- 在项目根目录执行 `mvn package`，产物为 `target/DreamSkyblock-1.0-SNAPSHOT.jar`。

第三方 JAR、构建产物和 IDE 本地配置不纳入版本控制。

## 使用

将构建得到的 `target/DreamSkyblock-1.0-SNAPSHOT.jar` 放入服务端的 `plugins/` 目录，服务端需要 Java 25。PlaceholderAPI 为可选运行依赖。

## 平台与原生存储

支持普通主线程模型的 Paper / Purpur / Leaf 26.2；不声明 Folia 兼容，不使用 Canvas / Arbor 专用卸载接口。实测 Purpur 26.2 build 2633、Leaf 26.2 build 124、Zulu JDK 25.0.2。Leaf build 124 为实验构建，验证范围见 [VALIDATION.md](VALIDATION.md)。

每个家园有独立 UUID `home_id`，成员按玩家 UUID 关联，名称仅用于显示。世界 Key 为 `dreamskyblock:dreamskyblock_<home_id去横杠>`，通过 Paper `WorldCreator.ofKey` 按需加载。

默认主世界名为 `world` 时，永久存档位于 `world/dimensions/dreamskyblock/dreamskyblock_<home_id去横杠>/`；改过 `level-name` 时使用服务端实际主世界目录。加载和卸载不复制存档。新建与预览只提供虚空空岛模板；`retired_templates.yml` 仅支持加载已有原版世界，不能用于新建。

无人后按 `config.yml` 的秒数延迟保存、卸载，访客也计入世界人数。每个家园的加载、卸载及维护操作串行执行；升级仅修改边界，最高 384×384，不重新生成地形。

玩家设置、等级、经验、成员、权限、传送点、世界 Key 和生命周期存入 `plugins/DreamSkyblock/data/homes.db`。SQLite 使用独立数据库线程、WAL、FULL 同步、外键和事务。`config.yml` 保存全局运行设置，`levels.yml` 保存升级规则，`templates.yml` 保存世界模板；GUI 和语言文件沿用现有路径。

## 旧数据迁移与恢复

启动自动识别旧 `playerdata/<名称>_<UUID>/home.yml` 及其世界目录，也读取旧 `storage.data-folder` 指定的目录。旧名称目录不再用于运行时存储。

迁移先保留备份，再验证存档和 SHA-256 摘要，成功后才提交 SQLite；旧 YAML 和原始世界保留。26.2 之前的 `level.dat` 世界使用 Paper 官方导入机制。多个不一致来源、重复归属、损坏或缺失存档会明确记录错误，禁止静默选择或生成空世界覆盖。

初始化失败时暂停玩家操作，修复日志指出的问题后执行 `/home migrate` 重试。已迁移 YAML 仅作历史源，不会在重启时覆盖 SQLite 或复活已删除家园。中断创建的家园只有在生成、保存凭据与有效世界均存在时恢复为 ACTIVE，否则保留为 RECOVERY，需管理员恢复备份。

管理员维护命令使用 `dreamskyblock.admin`，普通功能统一使用 `dreamskyblock.use`：

- `/home backup [home_id]`：备份指定家园；玩家省略 ID 时使用自己的家园，控制台需提供 ID。
- `/home restore <home_id> <backup_id>`：恢复世界与对应元数据，也支持恢复已删除家园。成员已属于其他家园时拒绝恢复。
- `/home migrate`：重试初始化、迁移和待完成维护事务。

备份在 `plugins/DreamSkyblock/backups/<home_id>/<backup_id>/`，包含 `world/`、`home.yml` 快照和 `manifest.sha256`。备份与恢复要求家园无人；删除沿用玩家操作，允许将唯一在线玩家传送出家园后卸载。

删除先完整备份并移入 `trash/<home_id>/<backup_id>/`，SQLite 保留删除墓碑。恢复先校验备份，用持久操作日志协调世界目录替换与 SQLite 提交；启动时回滚未提交的替换或补齐已提交的恢复。被替换或损坏的原目录保留供诊断。无归属世界只记录告警，不自动删除或猜测归属。

维护日志中的家园 UUID 和备份 UUID 可直接用于恢复命令。不要手工删除 `data/operations` 或 `data/world-transactions` 的待完成记录。手工整服备份应停服后同时备份主世界及插件目录，避免单独复制运行中的 SQLite 主文件而遗漏 WAL。

## 查询与设施价值 API

其他插件声明 `softdepend: DreamSkyblock`，通过 Bukkit ServicesManager 获取只读服务：

```kotlin
val api = server.servicesManager.load(DreamSkyblockApi::class.java)
if (api?.isReady() == true) {
    val home = api.findHomeByMember(player.uniqueId)
    val canBuild = home?.let { api.permission(it.homeId, player.uniqueId, "stranger_place") } ?: false
}
```

接口位于 `ym.dreamSkyblock.api`，提供家园、成员、团队、Flag 和世界 Key 查询。读取返回不可变快照，无数据库 IO，可跨线程查询。设施价值写入使用异步 `setValue` / `removeValue`，按来源和设施唯一 ID 保存当前绝对价值，修订号防止重复及迟到请求。完整接入示例与重试、恢复约定见 [VALUE_API.md](docs/VALUE_API.md)；调用方不要直接修改 SQLite 或内部 Home。

空岛总价值采用累计门槛 0/100/300/600/1000/1500/2100/2800/3600 自动计算 1-9 级，放置/升级已接入设施增加价值，移除/降级设施扣除对应价值。降级不缩小已解锁的较大边界。旧 `experience` 字段和占位符仍可用，现在表示当前空岛总价值；旧经验作为系统保留记录迁移。SQLite v1 升级前自动备份，价值明细也随空岛备份、恢复。

原生保存机制仍可能在突然断电时丢失上次区块保存之后的修改；本重构保留永久存档和维护事务证据，不承诺超出 Minecraft 保存机制的零丢失。

主命令为 `/home`，别名为 `/dsb`。普通玩家权限为 `dreamskyblock.use`，管理权限为 `dreamskyblock.admin`。

## 空岛菜单

`/home` 按玩家当前归属打开主菜单；`/home tp` 仍可直接回岛。每个菜单独立配置在 `gui/<名称>.yml`：

- `main.yml`：无岛入口，创建按钮的 Lore 直接介绍虚空草地平台、大小、树木与资源箱；点击直接进入 `create_confirm.yml` 二次确认，取消不生成世界。另有好友邀请与玩法帮助入口。
- `island.yml`：我的空岛信息、成员管理、空岛设置、分类帮助、升级信息按钮与回岛。
- `invites.yml`：显示邀请人、人数和剩余有效时间，点击接受邀请。过期、归属变化或已满时由家园服务重新校验。
- `members.yml` / `invite_players.yml` / `member_confirm.yml`：查看共同成员，选择在线无岛玩家邀请，确认移除其他成员或退出团队。共同主人权限相同，最后一名成员不能直接退出。
- `settings.yml`：融合权限说明、世界规则与访客设置；从主菜单或 `/home settings` 打开。无岛玩家只能预览，加入空岛后可修改，提交成功后刷新状态。
- `help.yml`：单个帮助菜单，入门、团队、权限设置、等级边界分类按钮直接在 Lore 中展示说明，无帮助子菜单。

升级按钮展示价值等级规则，不提供升级子菜单或手动升级操作。岛屿样式由 `templates.yml` 配置，创建菜单采用首个可创建的空岛模板；`/home create [template]` 保留指定模板的方式。模板预览菜单已删除。

邀请、在线好友等列表没有数据时，内容槽位保持空白，保留边框和导航，不显示纸张占位提示。

无岛玩家上线后等待 3 秒，随后每 15 秒收到一条聊天消息：“你还没有空岛，输入 /home 即可创建！”，其中 `/home` 可点击打开菜单。仅对拥有 `dreamskyblock.use` 的玩家提醒；空岛菜单打开时暂停计时，创建或加入空岛后停止。退出和插件禁用时清理提醒，文案位于 `lang/zh_cn.yml` 的 `home.no-island-reminder`，不使用 ActionBar。

点击切换菜单在下一 tick 执行，并重新检查当前 Inventory 身份、玩家归属和权限；关闭菜单后迟到点击不会生效。主菜单与列表支持配置式布局，邀请和成员列表按内容分页。配置不重复添加 `<italic:false>`，物品名称与 Lore 由统一渲染入口关闭默认斜体。旧设置、主菜单和帮助菜单以保留原文件的方式在内存中迁移；旧模板预览及帮助子菜单文件不再加载。

旧主菜单同时配置创建与模板预览时，加载时在内存中移除独立预览按钮；仅有预览入口则转换为创建入口。旧预览引导替换为岛屿介绍和确认提示，原配置文件及其余自定义字段保留。

默认配置位于 `src/main/resources/`，GUI 配置位于 `gui/`，中文语言文件位于 `lang/zh_cn.yml`。


## ArcMenu 游戏内主界面

`/home` 使用 ArcMenu 3.0.1 API v1：无岛打开 `dreamskyblock-main`，已有岛打开 `dreamskyblock-island`。两套菜单源文件在 `ui/arcmenu/menus/`，部署到 `plugins/ArcMenu/menus/`。ArcMenu 是可选依赖；未启用、API 服务不可用、菜单打开失败或 `arcmenus.yml` 关闭时回退原箱子菜单。编译另需 `libs/ArcMenu-3.0.1.jar`；ArcMenu API 不打包进 DreamSkyblock。

界面使用原生展示实体、矩形、线条、文字和方块构成，不再加载 PaiUI HTML。创建、好友邀请、帮助、成员、设置、升级提示和传送，均按原 `gui/main.yml` / `gui/island.yml` 的动作执行。仅替换两个主界面，确认及其他子菜单继续使用原箱子，返回主菜单重新打开 ArcMenu。原 GUI YAML 保留。普通玩家只需 `dreamskyblock.use`，其声明包含 `arcmenu.use`。

动态信息通过本插件 PlaceholderAPI 扩展读取真实岛屿、成员、等级、当前价值、边界和模板。悬停说明来自原菜单 Lore，升级操作展示价值规则说明。新菜单没有图片、字体贴图等额外材质包资源；ArcMenu 自身的鼠标/悬浮框外观沿用本地配置。默认 touch 模式使用准星指向并右键选择，输入方式可用 ArcMenu 自身设置切换。

每次打开创建独立令牌；按钮只接受当前页面允许的组件 ID，并复查玩家权限和空岛归属；下一 tick 调度前再次校验。切换原箱子前关闭 ArcMenu，重复或迟到操作不会覆盖新菜单。退出、配置重载、ArcMenu 停用和本插件停用清理会话与待处理动作。

首次安装菜单并更新 JAR 后正常重启服务器，再使用 `/home`。仅后续修改 ArcMenu YAML 时按 ArcMenu 自身菜单重载流程更新；不要热卸载世界插件。旧 `ui/paiui/` 仅作历史设计资料，当前服务器已无 PaiDaXingPlugins 插件和页面目录。
