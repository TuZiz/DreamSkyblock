# 空岛价值 API

平台为 Paper/Purpur/Leaf 26.2、Java 25、Kotlin 2.3.10。通过 Bukkit ServicesManager 获取 `ym.dreamSkyblock.api.DreamSkyblockApi`。消费插件使用 DreamSkyblock 作为 `provided` / `compileOnly` 依赖，插件描述中声明 `depend: DreamSkyblock`（YAML 使用块式列表），不要把 DreamSkyblock 类打进自己的 JAR。

## 计算方式

总价值是设施记录当前价值之和，累计门槛为 0、100、300、600、1000、1500、2100、2800、3600，最高 9 级。升级不消耗价值；拆除或更新设施后可降级。已解锁边界保留，升级只改变原世界边界。门槛与边界分别配置在 `levels.yml` 的 `progression.thresholds` 和 `border.sizes`。

原 `experience` 字段、HomeView.experience 和经验占位符作为兼容名称保留，现在表示当前总价值。旧版已有经验迁移为只读系统记录 `dreamskyblock-legacy / imported-experience`，不凭空丢失历史数值。原手动等级会按当前总值重新推导，较大边界保留。

## 接口与示例

```java
DreamSkyblockApi api = getServer().getServicesManager().load(DreamSkyblockApi.class);
if (api == null || !api.isReady()) return;
// 在事件所属线程捕获世界 Key 和坐标；不在异步线程读取 Bukkit 方块。
HomeView home = api.findHomeByWorldKey(block.getWorld().getKey().toString());
if (home == null) return;
HomeValueKey key = HomeValueKey.block("your-machines", block.getX(), block.getY(), block.getZ());

// 放置/属性升级：提交此设施的“当前绝对价值”，不是加多少经验。
api.setValue(home.getHomeId(), key, 100L, 1L).whenComplete((result, error) -> {
    // 仅在无错误时确认已入账；异常可重试，同一次修订必须使用相同 value/revision。
    // 回调线程不保证是玩家线程；玩家提示、实体操作请调度到正确线程。
});

// 拆除：只提交更大的修订号；插件按原记录扣除，不需要猜测原价值。
api.removeValue(home.getHomeId(), key, 2L);

// 机器已有持久 UUID 时，可用该 UUID，不强制使用坐标。
HomeValueKey machine = new HomeValueKey("your-machines", machineUuid.toString());
// 查已有记录（包含 value=0 的移除记录），用于重启后的修订号续接。
Map<HomeValueKey, HomeValueRecord> records = api.valueRecords(home.getHomeId());
```

`source` 使用集成插件的稳定小写标识（字母、数字、点、下划线、短横线，最多 64 字符）；`id` 为稳定非空设施 ID，最多 256 字符，不允许控制字符。坐标 Key 隶属于给定 home_id，世界必须通过 `findHomeByWorldKey` 确认，不能以放置玩家所属空岛推断。

DreamSkyblock 异步加载配置和数据，依赖插件 onEnable 时服务仍可能尚未注册或 isReady=false。可以按需查询 ServicesManager，或监听 ServiceRegisterEvent 后等待 isReady，再进行已存在机器的核对；不要在启动线程阻塞等待。

`value`、`revision` 为非负 Long。每次真实变更使用持久化的更大修订号：同修订、同值返回 UNCHANGED；旧修订返回 STALE；同修订不同值为错误。APPLIED 才表示应用了新状态；不要把 STALE 当作已应用请求中的数值。总值溢出会拒绝整笔事务。移除记录不删除，防止迟到的旧放置请求恢复价值；重放“先移除后收到旧登记”的顺序是安全的。

不要使用每次请求随机生成的 ID、每次重试递增的 revision 或重启后归零的内存计数器。设施拆除后在原坐标重新放置仍须使用更大修订号，或改用新的持久机器 UUID。同岛 API 请求按接受顺序串行；与其他成员修改、世界维护冲突时会异常完成，待维护结束后使用原修订重试。

## 接入责任与恢复

API 可从任意线程调用，写入返回 CompletableFuture。不要在游戏线程调用 get/join。成功回执表示 SQLite 明细、总值、等级和边界元数据已提交，内存已应用；实际世界边界设置会回到 Paper 调度器继续应用。插件停止后会拒绝新工作，已提交数据库事务会排空；重启后重放相同修订不会重复入账。

来源插件负责识别机器类型、物品属性并确定价值，也负责在成功放置、拆除、爆炸移除、移动、属性变化时调用 API；被取消的操作不得登记。此版本不自动识别未知机器或给所有原版方块计分，不扫描/加载整张空岛。

两种插件的数据库不能共享一个通用原子事务。来源插件应保存待提交操作，失败后重试，并在启动或空岛恢复后对照实际设施与 `valueRecords` 做核对，移除已不存在的条目。恢复空岛备份会同时恢复对应明细和修订号；应停止旧任务、重读记录并按恢复后的实际机器重新同步，不能继续发送旧时间线的数据。

只读原有归属/成员/权限 API 保持不变；返回对象和记录 Map 为不可变快照。`findHomeByWorldKey` 支持已卸载空岛的稳定世界 Key，不会加载世界。API 面向受信任的服务端插件，不用于向客户端直接开放任意价值写入。
