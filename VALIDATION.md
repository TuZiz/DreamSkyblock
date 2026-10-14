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
