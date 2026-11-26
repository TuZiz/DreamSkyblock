2026-11-09 开关灰色横线修正：实服截图确认底板和unknown卡已加载。安装Invero的HandlerKt.updateTitle在isTitleUpdating为true时直接返回；初始菜单打开会锁两tick，旧脚本首tick更新却提前写Settings__State，存在丢失首次状态包且不重试的问题。刷新首延迟改为4tick，并在标题锁定期间跳过且不提交状态；setTitle成功返回后才记状态，下一轮补发。保留原贴图与两个命令。增加真实YAML解码后脚本源一致性检查、Nashorn初次/后续标题锁的延后补发回归；安装Invero解析和脚本测试通过。20个live/project目标备份、SHA256校验同步完成，manifest为target/verification/diagnostics/game-settings-pixel-deployment.json。本轮未在线重载、未客户端复测，仍待重新打开菜单确认；贴图未改，无需重建CE资源包。 
2026-11-09 游戏设置像素版已部署：复用用户指定 Quest 备份原卡与木框，5×9、底板176×108、卡片40×73；仅绘制全息显示、任务追踪，其他位置留白。两按钮仍由玩家执行 genlite holograms / quest hud toggle。前者是当前空岛共享设置，后者是个人任务HUD。每10 tick在玩家EntityScheduler读取真实缓存，状态变化才更新标题；未就绪显示灰色横线，关闭/离线/会话替换取消任务。Quest1.0.0私有runtime/preferences字段耦合已通过安装JAR签名检查，升级需重验。正式与项目镜像20目标已备份原子同步、SHA256回读一致。制作/部署入口 integrations/craftengine/invero-menus/game-settings/；当前导出 output/imagegen/game-settings-release/，旧game-settings-fix部署脚本停用，避免恢复旧菜单。

验证：安装Invero1.4.17真实YAML→JSON、动作/节点codec、Kether解析、Nashorn编译通过（1菜单3动作2节点3脚本）；post_open/close真实事件codec通过；Nashorn配测试替身验证ON/OFF/unknown、异步状态刷新、不重复发标题、错误限次日志和任务取消。7张PNG与配置引用已核对，mixed原生最近邻预览已目视检查。报告 target/verification/diagnostics/game-settings-pixel-deployment.json。仅配置和贴图更新，未修改/构建JAR；未重启、未执行在线重载、未生成或推送CE资源包、未游戏客户端验证。需执行 menu reload、ce reload pack 并接受最新资源包；物品最终斜体状态和实际热区仍待游戏验证。
# Invero 像素菜单

2026-11-09 游戏设置新增“任务信息追踪计分板”：Q书本按钮由玩家执行 `quest hud toggle`，与H生成器全息字在第二行左右排列，底部返回保持。正式workspace、项目镜像和game-settings-fix暂存源已备份同步SHA256一致；实际Invero1.4.17解析1菜单3动作3脚本通过。纯菜单配置修改，无需资源包更新。用户命令已按原文接入，未进行游戏运行验证。报告 `target/verification/diagnostics/quest-hud-toggle-deployment.json`。

2026-11-09 游戏设置纠正（替代下文空岛规则设置方案）：信息按钮恢复12×12草方块图标，文字仍12px；向左利用原空白格，首行 `WWWWIIIIG`，齿轮保持slot8。G打开新增独立 `游戏设置.yml`，按钮由玩家执行 `genlite holograms` 切换个人生成器全息字显示，返回空岛菜单；不猜测当前开关状态、不增加额外设置项。信息原左右键保持。贴图源 `output/imagegen/game-settings-fix/`，prepare.py已更新引用与布局。Invero1.4.17真实加载/codec/Kether通过2菜单11动作12脚本；6目标备份原子部署SHA256一致，报告 `target/verification/diagnostics/game-settings-fix-deployment.json`。未自动重载或生成/推送资源包，游戏运行待确认。

2026-11-09 可点击设置版（替代下文标题区仅显示方案）：用户允许下移，空岛页首行改为 `WWWW IIIG`，G独占最右slot8；14×14齿轮坐标153,20。空岛信息缩至52px，保留12px文字及原左右键动作，移除草方块图标让位。G左键执行已有玩家命令 `dreamskyblock:home settings`，不使用缺失配置的settings menu-action桥。贴图源 `output/imagegen/island-settings-clickable/`，prepare.py已引用新图和布局。实际Invero1.4.17加载/codec/Kether通过1菜单9动作10脚本；4目标（两PNG、两YAML）备份原子同步SHA256一致。报告 `target/verification/diagnostics/island-settings-clickable-deployment.json`。未自动重载Invero或生成/推送CE资源包，客户端待验收。

2026-11-09：用户确认空岛页右上角设置齿轮预览。`island-settings-preview/island-settings-176x108.png` 已同步项目包与正式 CE 包，仍为176×108 RGB，仅(151,3)起14×14区域发生变化；prepare.py已引用此图。两目标备份、SHA256回读通过，所有Invero菜单YAML字节保持。齿轮位于标题区，没有原版槽位可接收点击，当前仅为外观；可点击方案需下移并给现有空岛信息按钮让位，已请求用户选择。未生成/推送资源包、未游戏实测。清单 `target/verification/diagnostics/island-settings-deployment.json`。

用户最新澄清：传送/普通副本的六个位置仅为空间预留，实际只绘制左上已配置的一张卡片，其余五处保持背景空白，不画空框。卡片大小/坐标/标题/返回和所有菜单动作不变。`export-six-cards.py` 已修正；四张正式/项目PNG已备份同步且hash一致，所有菜单YAML字节保持。报告 `target/verification/diagnostics/no-empty-frames-deployment.json`；需重新生成/应用CE资源包，未自动执行。

备用菜单最新显示标题为“服务器菜单”，文件ID仍为“备用菜单”。三个按钮缩至70×16、图标10×10，文字保持原生Xerr12px；主城无命令、大厅dungeon lobby和家园动作不变。贴图176×90，四行原布局不变，原生预览 `output/imagegen/backup-menu/preview-4x.png`。更新已备份同步且SHA256一致；需重新生成/应用CE资源包，客户端观感待确认。

最新传送/普通副本布局为5×9、176×108原生RGB：3列×2行六个52×30小卡片位，仅左上分类/石址卡有动作，另五个无文字无动作；底部返回。标题和卡片名保持Xerr12px。`export-six-cards.py` 导出，`prepare-dungeon-skins.mjs`同步5行与CE height108，部署工具为 `deploy-six-cards.mjs`。此条替代下文4行单卡片状态。

新增独立 `workspace/备用菜单.yml`，4×9/176×90，三个纵向入口：回到主城按用户最新要求不绑定动作，副本大厅执行玩家命令 `dungeon lobby`，个人家园复用现有主菜单动作。无新命令绑定，可按菜单ID“备用菜单”打开。独立CE `backup-menu.yml`/E915与贴图已同步；重现用 `prepare-backup-menu.mjs --none`。报告 `target/verification/diagnostics/backup-menu-deployment.json`。实际解析1菜单2动作2脚本通过，所有原菜单保持。

传送与普通副本现使用四行9列小卡片布局（176×90不透明RGB）：普通副本、石址各仅占左上一张54×44原生像素卡片，右侧留作后续分类/副本；不显示虚构空卡。卡片直接点击，石址难度/常驻说明在Lore，左下返回。传送返回主菜单，副本返回传送；石址仍由玩家执行 `dungeon enter lost_altar`。

素材及最近邻预览在 `output/imagegen/teleport-dungeon-preview/`，原生文字采用Xerr12px字体。`prepare-dungeon-skins.mjs` 可生成对应菜单和独立CE配置 `teleport-menus.yml`（E913/E914，height90、ascent13），与旧主页配置分开，不受prepare.py重建主页覆盖。10个正式/项目目标备份原子部署且SHA256一致，原主页菜单不变。实际Invero解析4菜单16动作17脚本通过，报告 `target/verification/diagnostics/dungeon-skins-deployment.json`。未自动重载或重建CE资源包，玩家运行和点击边界待客户端验收。

当前字体采用用户指定Xerr生成包的 `assets/xerr_font/font/fusion_pixel_quest_12_sc.ttf`：核对 `quest_ui_position_light.json` provider为size=12、oversample=1，菜单13项文字统一用原生12px字形和原生字宽排版，无额外字距、无文字阴影。此处复用字体文件字形绘入菜单贴图，不引入Xerr HUD定位shader或改Xerr配置。源及预览 `output/imagegen/menu-xerr-v10/`，运行176×108不透明RGB，文字框外像素变化0。4目标备份原子部署和SHA256校验通过，4份菜单字节保持；报告 `target/verification/diagnostics/menu-xerr-v10-deployment.json`。V9小字号版已淘汰，实际客户端效果待应用资源包确认。

最新文字修复使用 `output/imagegen/menu-type-v9/`：原生176×108，标题12px、正文10px，每字额外1px间距，二值字形且无文字阴影。清理旧字及重影后重排13项文字，文字框外像素变化为0，图标/边框/布局保持。4张正式/项目贴图已备份原子同步并SHA256一致，4份Invero菜单字节保持；prepare.py引用新图。部署报告 `target/verification/diagnostics/menu-type-v9-deployment.json`。未重建资源包或重载，实际客户端效果待确认。

当前运行素材为 `output/imagegen/menu-regenerated-v8/`：重新生成后导出176×108不透明RGB，预览704×432仅最近邻放大；两处传送在原生网格补成12px二值字形，其他导出像素未改。主菜单任务热区随生成图右移一格；现有动作、信息右键回岛及传送→普通副本→石址保持。两个prepare脚本同步本版本，以下V7/V6均为历史。

安装Invero1.4.17实际解析4菜单、15动作、16脚本通过。正式/项目8目标已备份、原子替换并SHA256回读，清单 `target/verification/diagnostics/menu-regenerated-v8-deployment.json`。未自动重载Invero或生成/推送CE资源包，客户端显示和点击边缘待游戏验收。

## 2026-11-06 当前版本：恢复用户选定截图风格

用户明确选择两张旧截图作为最喜欢的版本，V6生图版已撤销。当前从旧原生不透明贴图恢复边框、底色、图标、按钮和旧字形，只作逐像素范围内修订：服务器菜单原任务中心按钮替换为传送入口，任务书本置于左上小方块；空岛菜单将中间回岛按钮改为打开传送菜单，底部市场居中，空岛信息右键仍负责回岛，无重复回岛功能。资产与预览见 `output/imagegen/menu-favorite-v7/`。

本次以用户选定旧图为基准，不调用生图或重新排版。点击热区相应适配；普通副本和石址流程保持不变。

## 已撤销：Canvas V6

V5已被用户否决，下文V5/V4仅为历史。当前图片源为 `output/imagegen/menu-canvas-v6/{server,island}-176x108.png`。本地 `D:/infinite-canvas` 在Edge打开、Agent连接后，画布 `MWHZrfVgzphwUecBzWxUo` 使用 `gpt-image-2.6-flare` 和旧版贴图作参考实际生成两张图。导出176×108不透明RGB，仅对两处“传送”字作原生12px补正，并把主菜单书本放入左上实际槽位，不再通篇重排旧文字。原图、提示词、预览及 `finalize.py` 均保留在该目录。

空岛移除独立T回岛入口，信息右键回岛逐字保留，市场在底部居中；主菜单中间三条入口保留，任务为左上小块。点击区按本轮贴图调整，普通副本→石址不变。两个prepare脚本已改用V6图片和布局，避免重装恢复重复入口。

安装Invero1.4.17实际解析通过4菜单、15动作、16脚本。正式和项目8个目标已备份、原子替换、SHA256回读，清单见 `target/verification/diagnostics/menu-canvas-v6-deployment.json`。本轮不修改JAR，不运行Maven；未自动重载Invero、生成CE资源包或游戏实测，需重载菜单并生成/应用资源包后验收。

## 历史V5：恢复旧版统一12像素字（已否决）

用户实服截图反馈上一轮文字不如旧版，已废弃混用12/10/8px的排版。两张现使用`output/imagegen/menu-type-v5/`：13项标题和按钮文案均为原生12px Fusion Pixel二值字形，按旧版字号恢复；原图标不缩放，仅移动以将图标与文字作为整体居中。底部市场和回到空岛按钮复用原倒角扩为76px，完整显示四字回岛，不再缩小字；空岛底行热区同步为`KKKK TTTT`，按钮动作、Lore、绑定和其他行保留。背景、边框和已选入口不变。`render-runtime-labels.py`及`prepare.py`均指向新版本，旧V4源保留作历史。最终清晰度和偏好仍需游戏客户端验收。

## 2026-11-06 历史部署：清晰文字与普通副本

两张正式贴图现来自 `output/imagegen/menu-crisp-v4/{server,island}-176x108.png`。保留已选布局、图标和底板，在176×108原生尺寸使用Fusion Pixel的12/10/8px字体重新排字；不用大图缩小后的中文笔画。13项标签采用二值字形、整数坐标，无抗锯齿或文字缩放，标签框外像素变化为0。`render-runtime-labels.py`可重现，`prepare.py`已引用清晰源；历史V4缩小图只作为草稿。正式贴图已同步CraftEngine和本项目pack，未重生成资源包或客户端验收。

`workspace/传送.yml`现为分类入口：普通副本 → `workspace/普通副本.yml` → 石址（难度：新手；服务器常驻副本）。点击石址先关闭菜单，再由玩家执行 `dungeon enter lost_altar`，不以控制台提升权限；下方箭头返回传送菜单。本服Dungeon的`dungeons/lost_altar/farming.yml`已确认enabled=true、ID一致，语言文件称其为石址。此要求替代下文“传送无功能空壳”的历史状态。

两份菜单已同步正式Invero workspace；两张贴图已同步正式CE资源目录。两张主页YAML及其热区、命令绑定、信息右键回岛动作保持原字节。4个菜单/16组action/17条脚本通过安装Invero1.4.17真实YAML→JSON、codec、Kether检查；未进行真实玩家跳转或传送。备份与SHA256清单：`target/verification/diagnostics/crisp-dungeon-deployment.json`。需要重新载入Invero菜单，以及重新生成并向客户端应用CE资源包；本次未自动重载或重启服务端。

当前生图默认模型为 `gpt-image-2.6-flare`，另一个可选模型为 `gpt-image-2.6-sunburst`；本地 Infinite Canvas / Edge 的 XToken 渠道及默认生图设置已更新，历史2.5记录保留。当前贴图源在 `output/imagegen/teleport-menu-preview/`：空岛采用用户选定V2（island-v4-176x108.png），服务器V4为中间传送/家园/市场三条竖排＋左上任务小方块（server-v4-176x108.png）。贴图和两个菜单的新点击区已备份同步，游戏显示与边缘点击手感待实测。

用户确认新增独立 `传送.yml`，只保留三行箱子的装饰和指南针，无业务动作、地点或命令绑定。两个主菜单的P按钮均使用安装版已核对的 `menu open "传送"` 打开它。`prepare-teleport-menus.mjs` 从正式工作区重基并生成待部署菜单，保留所有原条目与绑定；`workspace/传送.yml` 为新菜单源。Invero需重载以载入新增文件；CE需重新生成和加载资源包以更新贴图，未自动执行。

空岛信息现有左键仍为管理，新增右键复用原T按钮回岛动作；正式workspace和项目镜像已备份同步，右键提示在Lore中。此动作独立于新增传送菜单入口，未修改现有T功能或其他动作。

2026-11-06：用户要求统一有岛主页入口。DreamSkyblock的invero.yml启用，island-menu设为'空岛菜单'，/is gui与/ismenu现在共用本工作区的空岛菜单；main-menu为空保留内置创建页。此前“不接管内置入口”的说明仅为历史状态。

采用用户选定的 V5：两张运行贴图均为 **176×108**，中央按钮 **76×18**，中心为 `(88,54)`。放大预览不进入资源包。图标、按钮文字已经画在背景里，透明 PAPER 保留原名称、Lore、PAPI 和点击动作。

- 主菜单：任务中心、个人家园、玩家市场；2026-11-04 移除主城内冗余的返回主城入口，三个 76×18 按钮居中竖排。
- 空岛菜单：空岛信息、返回主城、居中回到空岛，以及任务、仓库、市场。
- 保留 `servermenu`、`菜单`、`cd`、`ismenu` 绑定；独立 Invero 菜单不接管 DreamSkyblock 内置菜单。
- 使用五行箱子、18 像素槽位间距。主菜单三个按钮分别位于 `(50,18)`、`(50,45)`、`(50,72)`，点击区分别占第 1 行、第 2–3 行、第 4 行的中间五列。空岛菜单保持原布局。边缘少量像素与槽位不完全重合，需客户端确认手感。

主菜单最新美术通过 Edge 已有 Infinite Canvas 画布与 `gpt-image-2.5-flare` 编辑生成。2026-11-04 根据用户图一“我的空岛”统一为灰米色石格底纹、灰绿立体按钮、棕色像素边框；前版平涂奶油背景已替换。参考图、提示词、原图、176×108 导出及并排预览位于 `output/imagegen/server-menu-island-style/`，历史去除返回主城版本保留在 `output/imagegen/server-menu-no-spawn/`。生成原图只作留档，资源包仅使用原版尺寸贴图。本轮仅替换主菜单贴图，菜单 YAML、动作与槽位不变；备份在 `plugins/DreamSkyblock/backups/server-menu-island-style-*`，未重启或重载服务器。

两张菜单背景统一完全不透明。`prepare.py` 导出时保存为 RGB PNG，避免历史空岛原图的半透明通道再次透出库存或场景。`make-opaque.py` 可核对并修正正式目录与项目源：仅去除意外 alpha，保持 176×108、原 RGB 像素和菜单 YAML，不重新生图；只有存在半透明像素的文件才备份替换。核验和预览位于 `output/imagegen/opaque-menus/`，备份位于 `plugins/DreamSkyblock/backups/opaque-menus-*`。透明 PAPER 仅用作点击区域，不影响背景贴图的不透明性。

后续用户明确服务器主菜单也要覆盖截图中的格子背景：该格子已烘焙进 RGB，单改 alpha 无法移除。最新主菜单使用 `output/imagegen/server-menu-solid-background/server-menu-176x108.png`，以内板采样方式换成空岛同款不透明实底，保留三个按钮和标题的原像素。此前石格版留作历史；正式版本不再含格子底纹。

## 文件与安装

`pack` 安装到 `plugins/CraftEngine/resources/dreamskyblock`，`workspace` 中两份菜单安装到 `plugins/Invero/workspace`。`prepare.py` 从当前正式菜单读取并逐项核对原动作、文本和绑定；`install.py` 部署前核对原文件 SHA-256，检测期间修改时拒绝覆盖。备份位于 `plugins/DreamSkyblock/backups/invero-pixel-menus-*`，不在 Invero 工作区内。

## Invero 1.4.17 标题修复

本地 JAR 的 `NMSImplModern.sendWindowOpen` 把 Adventure 标题转换成 legacy 字符串，再用 `Component.literal` 写包，导致 font 丢失。普通库存路径同样使用 String 标题，无法保留本皮肤字体。

两个皮肤显式使用 `virtual: true`。`PatchInveroTitle.java` 仅修改现代标题的 3 个调用点：保留现有 MiniMessage 解析，通过 Invero 的 `Message.transformToJson` 与本地已核对的 `CraftChatMessage.fromJSON` 写入原生 Component。其他类、资源与业务不变。补丁仅核对 Invero 1.4.17 / Purpur 26.2、Leaf 26.3 的当前依赖；升级 Invero 后必须重新核对，不可盲目套用。

使用本地 `asm-9.8.jar` 与 `asm-tree-9.8.jar` 组成 classpath，以 Java 25 源文件模式运行 `PatchInveroTitle.java <原Invero.jar> <输出.jar>`；输出路径为 `target/verification/probes/Invero-1.4.17.jar`。补丁器遇到不匹配字节码立即停止。

安装器将修复版放到 `plugins/update/Invero-1.4.17.jar`，原 JAR 完整备份，**下次重启生效**。没有自动重启或重载正式服。

重启并确认 Invero 正常加载后，在控制台执行 `ce reload pack`，让玩家接受更新资源包，再通过原菜单绑定进入检查。尚未执行真实 Minecraft 客户端截图和逐按钮操作验收；资源包生成、菜单加载通过也不代表客户端验收完成。

## 2026-11-04 旧窗口刷新串屏修复

安装版 `InventoryPacket.open$lambda$7` 每20 tick检查旧窗口 `isViewing()` 后发送整个库存；其 `closed` 字段只在构造和打开时赋值，关闭时没有恢复。所有虚拟菜单共用容器ID 119，因此切换后旧任务仍能覆盖新菜单物品，出现背景与按钮提示不一致、周期闪动。

`PatchInveroWindowRefresh.java` 将该定时回调的一处判断改成已核对的 `WindowManagerKt.isRegistered(window)`，让过期窗口取消自身任务。用户随后澄清现象为仅悬停按钮闪动、没有发白：旧窗口问题只是已确认的独立缺陷，尚不能认定是用户闪动的完整原因。

补充修复：`updatePlayerItems(true)` 原先无条件调用 `update()`，每秒重发包括按钮在内的全库存；现改用已有 `update(int...)` 只发送 `[getContainerSize(), windowItems.length)` 的玩家背包槽位，避免定时替换悬停按钮。保留初始完整打开、主动菜单刷新、背包槽位映射、原标题字体补丁及所有资源。不要全局改 `isViewing`：关闭流程先注销再检查显示状态，全局替换会影响正常关窗发包。该修改已验证发包范围，客户端悬停闪动是否消除仍待实测。

以 ASM 9.8 的 asm / asm-tree 为 classpath，运行 `PatchInveroWindowRefresh.java <已有标题补丁的Invero.jar> <不同路径的输出.jar>`。原版无标题补丁时先运行上一节标题补丁，再运行刷新补丁；两者不得覆盖同一输入文件。升级版本或已经应用本补丁时，补丁器拒绝匹配不符的字节码。

`VerifyInveroWindowRefresh.java` 用 ASM 的 asm / asm-tree / asm-commons 编译后，以 `java -Xverify:all` 执行，参数为补丁前后JAR；从两份实际JAR提取定时回调和 `updatePlayerItems` 字节码，仅替换平台边界为测试替身。已复现原版切换后仍发旧物品和活动窗口全库存重发；验证修复后活动窗口刷新、切换取消、关闭取消、初始仅填充不发包、连续5次只发送36个玩家槽位、原背包映射及菜单物品保持。此检查不等于真实客户端验收。

修复版暂存 `plugins/update/Invero-1.4.17.jar`，原JAR备份在 `plugins/DreamSkyblock/backups/invero-window-refresh-*`；下次完整重启应用，无需重新生成图片或资源包。本次没有重启或重载正式服，游戏内闪烁消除仍待重启后验证。

2026-11-09 Xerr字体统一已同步leaf与项目镜像：标题/卡片保持原生12px Xerr字体，返回文字改为同一字体直接绘制，取消旧返回牌字形缩放；仅底板footer像素变化，六张状态卡与旧版逐像素相同。7张PNG格式/尺寸与变化范围检查通过，14个目标备份同步SHA256一致。清单target/verification/diagnostics/game-settings-font-deployment.json。已目视检查mixed-4x.png；未重建推送资源包、未游戏验证。

2026-11-09 副本大厅服务器菜单补传送：备用菜单.yml扩为5×9/176×108，四个70×16按钮按传送→主城→副本大厅→家园排列；P执行menu open 传送，S/D/H条目与部署前逐项一致，主城仍无命令。其他四个菜单字节保持。真实Invero解析1菜单3动作3脚本通过，6个正式/项目目标备份原子部署SHA256一致，清单target/verification/diagnostics/backup-menu-teleport-deployment.json。像素预览已目视检查；未在线重载或生成推送资源包，客户端待确认。需menu reload、ce reload pack并接受最新资源包。

2026-11-09 当前有效版本：服务器菜单改为2×2，5×9/176×108RGB，卡片62×34（为槽位和文字边界从草案64×36微调），奶油底细棕边、绿色细线点缀。图标上/名称下：末影珍珠传送、指南针主城、遗迹大厅、草方块家园。Xerr原生12px字形，无文字缩放或阴影。热区PPPP SSSS两行/空一行/DDDD HHHH两行；三个原入口内容和传送命令保持。真实Invero加载codec/Kether1菜单3动作3脚本通过，预览目视检查并修正文字与底边相交，6正式/镜像目标备份同步SHA256一致，其他四菜单字节保持。清单target/verification/diagnostics/backup-menu-grid-deployment.json。旧纵向图/脚本保存在preview-vertical-history.png与export-vertical-history.py。未在线重载或生成推送资源包、未游戏验证，需menu reload与ce reload pack并接受最新资源包。

2026-11-09 用户指定主城命令warp spawn：备用菜单S左键先menu close，再playerPerform warp spawn，Lore更新返回服务器主城。prepare-backup-menu.mjs默认命令同步。2×2布局、贴图及其他条目保持；安装Invero真实解析1菜单4动作4脚本通过，两份workspace备份原子同步SHA256一致，清单target/verification/diagnostics/backup-spawn-deployment.json。未执行游戏内传送、未在线重载；无需重建资源包。

2026-11-09 用户确认卷轴单格按钮移左下并落地：按钮14×14、坐标(12,90)，完整位于slot36，J条目打开独立五行空玩法.yml。空菜单全AIR、不加虚构玩法或返回业务按钮。保留所有原条目，仅末行layout改J  KKK三个尾空格。正式与项目2PNG/4YAML共6目标备份原子同步SHA256一致，部署清单target/verification/diagnostics/gameplay-entry-deployment.json；prepare.py同步最终图和布局，避免重建回退。真实Invero1.4.17解析2菜单10动作11脚本通过，PNG176×108RGB、仅左下14×14变化、已目视检查。未执行在线重载、CE生成推送资源包或Minecraft客户端验证；需menu reload、ce reload pack并接受最新资源包。

### 2026-11-12 服务器菜单视觉统一
备用菜单.yml（显示名服务器菜单）的贴图改为与空岛菜单一致的羊皮纸外框、浅绿像素立体按钮，传送使用青色强调。保留5×9、2×2四入口和所有原命令。原生176×108 RGB，Xerr12px文字无缩放/阴影；preview：output/imagegen/backup-menu/preview-4x.png。仅两份backup-menu.png更新，leaf已备份同步并核对SHA256；配置引用通过，客户端待验收，需ce reload pack并接受新包。

2026-11-12 最新布局：用户要求参考主菜单，备用菜单已改为中轴纵排四个74×16按钮：传送、回到主城、副本大厅、个人家园。替代此前2×2版本；5行/176×108和12px字形保持，热区同步为每行中间五格。四入口动作/提示全部保留。配置及贴图已同步leaf并校验哈希，需menu reload、ce reload pack及客户端接受新资源包；客户端待验收。
