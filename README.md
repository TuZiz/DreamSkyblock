# DreamSkyblock

基于 Kotlin 和 Maven 的 Minecraft 家园插件。

## 构建

- 使用 JDK 25 和 Maven。
- 当前项目使用 Kotlin 2.3.10，Paper API 固定为 `26.2.build.126-stable`，编译目标为 Java 25。
- 构建前将 `PlaceholderAPI-2.12.3.jar` 放入项目根目录的 `libs/` 文件夹；当前 `pom.xml` 通过本地路径引用该依赖。
- 在项目根目录执行 `mvn clean package`，产物位于 `target/`。

第三方 JAR、构建产物和 IDE 本地配置不纳入版本控制。

## 使用

将构建得到的 `target/DreamSkyblock-1.0-SNAPSHOT.jar` 放入服务端的 `plugins/` 目录，服务端需要 Java 25。PlaceholderAPI 为可选运行依赖。

## Purpur 26.2 兼容性

- 在 Purpur 26.2 build 2633、Zulu JDK 25.0.2 下通过插件启用、空岛家园创建、无人卸载、数据保存、重新加载、删除和插件禁用时卸载的自动化运行验证。
- Paper/Purpur 使用标准 `Server.unloadWorld` 接口，由 global 调度器在主线程执行。文件复制与清理由 IO 执行器处理；停止时先保存卸载世界，再异步同步数据目录。
- 数据存在性检查识别 26.2 的 `data/minecraft/world_gen_settings.dat` 和 `data/paper/metadata.dat`，避免将已经保存的家园误判为无数据。
- 保留 Canvas/Arbor 异步卸载入口。Folia 类平台缺少受支持的异步卸载扩展时会拒绝卸载，不进入 Paper/Purpur 的主线程路径；本次未实测这些平台。

运行验证使用玩家代理对象调用服务层，未覆盖真实玩家传送、GUI 交互和 PlaceholderAPI 联动。

主命令为 `/home`，别名为 `/dsb`。普通玩家权限为 `dreamskyblock.use`，管理权限为 `dreamskyblock.admin`。

默认配置位于 `src/main/resources/`，GUI 配置位于 `gui/`，中文语言文件位于 `lang/zh_cn.yml`。
