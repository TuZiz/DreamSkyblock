# 创建空岛像素菜单

仅用于 DreamSkyblock 内置的创建页 `main.yml` 和创建确认页 `create_confirm.yml`。两个画面都使用 **176×108 PNG**，与原版五行箱子上半区域对应；槽位间距 18 像素，不绘制或遮盖玩家背包。

资源位于 `pack/`，安装到 `plugins/CraftEngine/resources/dreamskyblock/`。`gui/` 两份配置安装到 `plugins/DreamSkyblock/gui/`。执行 `python install.py` 会先备份发生变化的文件，再复制并校验 SHA-256；重复执行且内容相同时不写文件。

本项目的渲染器增加可选 `item-model` 字段，使用 `dreamskyblock:gui_hitbox` 空模型保留悬停文字和点击功能。它只依赖当前 Paper API，不需要添加 CraftEngine Java 依赖。未设置此字段的菜单仍使用原有物品。

创建页的创建按钮为槽位 20–24，好友邀请 29–33，帮助 36，关闭 44。确认页的确认按钮为 28–30，返回 32–34；槽位 13 悬停查看初始参数。标题通过私有字体向左偏移 8 像素；图片字体 height=108、ascent=13。

安装后需要加载更新的 DreamSkyblock JAR，然后执行 `ce reload pack` 并接受新资源包，再打开菜单。未自动重启或重载正式服务器。资源包是皮肤显示的必要条件；ArcMenu/Invero 接管应保持关闭，当前服务器已是关闭状态。

两张设计稿由用户指定的 `gpt-image-2.5-flare` 通过 OpenAI 兼容接口生成。原稿、最终提示词、逐图记录及像素导出脚本保存在 `output/imagegen/island-creation/`；资源包只包含导出的原生尺寸 PNG，不包含高分辨率原稿。中文标签按原生像素尺寸重新栅格化，二值透明度、不使用抗锯齿；布局按槽位组装，避免模型生成的按钮位置误差。

游戏客户端最终观感、资源包下载后的显示与实际玩家点击仍需在服务器更新后确认。

已执行验证：12 项菜单测试、Maven 构建、隔离 Purpur 26.2 原生菜单渲染及透明 ItemMeta、CraftEngine 26.9.2 生成/校验/打包。打包前后两个 PNG 的 RGBA 内容一致；Neuron 检查 42 项实际渲染 Component 通过。文件已安装，重复安装幂等且 SHA-256 一致，详细证据见项目 `VALIDATION.md`。
