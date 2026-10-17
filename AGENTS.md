# 构建产物交付

- 用户指定的默认插件更新目录：`C:\Users\32633\Desktop\leaf\plugins`。
- 修改后 Maven 构建和本次相关验证成功时，自动将正式 DreamSkyblock JAR 同步到此目录，不再询问复制权限。
- 已有 DreamSkyblock JAR 时沿用其文件名进行更新，避免放入多个版本导致重复加载；没有时使用 Maven 正式产物文件名。
- 复制后校验目标文件与构建产物的 SHA-256 一致，并报告更新结果。
- 用户指定其他目录时以最新要求为准；复制文件不自动重启或重载服务器。

# 构建目录整理

- `target/` 根目录只保留正式插件 JAR 和 Maven 标准输出目录。
- 人工生成的构建与验证日志写入 `target/verification/logs/`，Neuron 报告与组件证据写入 `target/verification/neuron/`，临时验证工具写入 `target/verification/tools/`。
- API 证据写入 `target/verification/api/`，诊断文件写入 `target/verification/diagnostics/`，临时验证编译输出写入 `target/verification/probes/`。
- 需要保留的旧编译目录归档到 `target/verification/build-history/`，JAR 中间产物归档到 `target/verification/build-intermediates/`，不散放根目录。
- Maven clean 会清理整个 `target/`；需要长期保留的验证结论记录在 `VALIDATION.md`，历史报告中的原路径可通过 `target/verification/cleanup-manifest.json` 查找归档位置。
