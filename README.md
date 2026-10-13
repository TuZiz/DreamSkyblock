# DreamSkyblock

基于 Kotlin 和 Maven 的 Minecraft 家园插件。

## 构建

- 使用 JDK 25 和 Maven。
- 当前项目使用 Kotlin 2.3.10，Paper API 依赖版本范围为 `[26.2.build,)`。
- 构建前将 `PlaceholderAPI-2.12.3.jar` 放入项目根目录的 `libs/` 文件夹；当前 `pom.xml` 通过本地路径引用该依赖。
- 在项目根目录执行 `mvn clean package`，产物位于 `target/`。

第三方 JAR、构建产物和 IDE 本地配置不纳入版本控制。

## 使用

将构建得到的插件 JAR 放入服务端的 `plugins/` 目录。插件声明支持 Folia，并将 PlaceholderAPI 声明为可选依赖；具体运行兼容性仍需在目标服务端验证。

主命令为 `/home`，别名为 `/dsb`。普通玩家权限为 `dreamskyblock.use`，管理权限为 `dreamskyblock.admin`。

默认配置位于 `src/main/resources/`，GUI 配置位于 `gui/`，中文语言文件位于 `lang/zh_cn.yml`。
