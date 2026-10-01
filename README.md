# Industrial Platform（工业平台）

[![Build](https://github.com/YDHusky/IndustrialPlatform/actions/workflows/build.yml/badge.svg)](https://github.com/YDHusky/IndustrialPlatform/actions/workflows/build.yml)

一个面向 **Minecraft 1.12.2**（Forge）的完全自定义工业平台搭建器模组。
放置搭建器方块、右键打开配置界面，选好材料、尺寸和偏移，一键生成整个平台——
搭建动画逐块出现、材料自动从背包扣除，已就位的位置免费复用。

合成配方：

```
 I S I
 S R S     I = 铁锭, S = 石头, R = 红石
 I S I
```

## 功能一览

- **4 种材料自定义**：边框方块 / 填充方块 / 道路材料 / 中心方块，点击列表在全局方块
  选择器中挑选（可搜索、可切换「背包」页签一键选中，支持数据值/meta 变体）
- **尺寸与网格**：块大小、平面数量 X/Z（1~8）、道路宽——一台搭建器直接生成
  2x2 / 3x3 合体大平台，格间自动铺路
- **锚点固定**：平台以搭建器所在**区块中心**为锚点生成，后期扩建（改尺寸/数量）
  始终围绕同一个中心向外扩展，机器不用挪
- **三轴偏移**：偏移 X / Y / Z（±64）微调平台落点，配置存在方块里，机器不拆永久保留
- **材料逻辑**：目标位置已是正确方块 → 复用不消耗；是别的方块 → 由「替换方块」开关决定
  替换并计费或保留跳过；基岩、容器、搭建器本体永不破坏
- **材料清单**：悬停搭建按钮 / 准星指向搭建器时显示完整清单，并对照背包标出够/缺
- **全息预览**：世界内半透明幽灵方块 + 体积线框，差异视图（只显示将要改变的位置），
  配置界面打开、准星悬停均可触发；「预览显示」开关存在方块里可常驻
- **搭建动画**：方块从下往上、从中心向外波纹式出现，带粒子效果，速度自适应，
  抑制光照更新不卡顿，完成后自动收起全息
- **俯视预览**：GUI 内 3D 等距方块预览（可拖动旋转）+ 右侧实时材料清单
- **KubeJS 风格蓝图**：用 JS 脚本或 JSON 注册预设，界面内循环套用、热加载

## 使用方法

1. 合成并放置**工业平台搭建器**。
2. 右键打开「平台配置」界面：
   - 左上：4 行材料，点击挑选方块（选择器内可切「背包」页签直接选）；
   - 右上：块大小、平面数量 X/Z、道路宽；
   - 左下「偏移」分组框：偏移 X / Y / Z；
   - 右下：预览显示、替换方块开关，蓝图循环，预览按钮，搭建主按钮。
3. 点 **搭建**。生存模式会校验材料（不够时按钮变灰并在悬停/聊天栏提示缺货），
   创造模式不消耗。
4. 配置随方块保存，关界面、走开、重进世界都不丢。

## 网格搭建（2x2 / 3x3）

把「平面数量 X/Z」设成 2 或 2x2、3x3，一台搭建器一次生成整片：
每格都是完整平台（边框环 + 填充内部 + 中心块），格间由**道路宽**指定纯路面宽度
（边框不算路宽，每侧额外 1 格边框），路面用「道路材料」铺设。
材料一张账单结算，全息预览与实际生成共用同一份布局代码，所见即所得。

## KubeJS 风格蓝图

脚本放到以下任一目录即可（**不需要安装 KubeJS**——由 1.12.2 自带的 Nashorn 引擎执行）：

- `kubejs/startup_scripts/industrial_platform/*.js`
- `config/industrial_platform/blueprints/*.js`（同目录 `*.json` 预设也支持）

```js
blueprints.register({
    name: "玻璃观景台",
    border: "minecraft:iron_block",
    fill: "minecraft:glass",
    center: "minecraft:sea_lantern",
    corner: "minecraft:glowstone",
    link: "minecraft:quartz_block",
    intervalX: 20, intervalZ: 20,
    countX: 2, countZ: 2,
    linkWidth: 3,
    channelWidth: 1, channelMode: "HORSESHOE",
    autoTorches: true
});
```

- 界面内「蓝图」按钮循环套用，「刷新蓝图」热加载，或 `/ipblueprints reload|list`
- 示例见 `examples/` 目录

### 蓝图字段

蓝图是**部分覆盖**：没写的字段沿用搭建器当前配置，应用后自动 clamp。
方块格式 `modid:block[:meta]`，`air` 表示「无 / 继承」。

| 字段 | 含义 | 范围 / 默认 |
| --- | --- | --- |
| `border` | 边框主方块，与 `border2` 沿边环逐格交替 | 默认石砖 |
| `border2` | 边框交替第二材质；`air` = 退化为纯 border | 默认平滑石砖半砖2 |
| `fill` | 内部填充（含未单独指定时的连接条） | 默认石头 |
| `center` | 每格正中心的中心块 | 默认铁块 |
| `corner` | 每格**有效区四角**的表面块替换为光源（替换而非叠放）；`air` = 继承普通表面块 | 默认萤石 |
| `link` | 格间连接条材质；`air` = 跟随 fill | 默认 air |
| `channel` | 表面开槽（`channelWidth > 0` 时）的槽底材质 | 默认萤石 |
| `layers` | 竖向层数，顶层带表面图案 | 1–128，默认 1 |
| `intervalX` `intervalZ` | 每格两排边框之间的内部净宽/净深 | 0–126，默认 14 |
| `countX` `countZ` | X/Z 方向格数，多格无缝拼成大平台 | 1–8，默认 1 |
| `linkWidth` | 格间道路宽；>0 即「纯道路」布局 | 0–31，默认 3 |
| `channelWidth` | 表面开槽宽，0 = 不开槽 | 0–31，默认 0 |
| `offsetX` `offsetZ` | 锚点相对区块中心的水平偏移 | ±64，默认 0 |
| `offsetY` | 相对搭建器自身 Y 的竖向偏移 | ±64，默认 0 |
| `channelMode` | 开槽形状 | `NONE` / `STRAIGHT` / `HORSESHOE` |
| `centerMark` | 中心标记开关（遗留开关，mark 无材质时无效果） | 布尔，默认 false |
| `replaceExisting` | true = 替换脚印内已有非匹配方块（照常计费）；false = 只填空气/流体/可替换方块 | 布尔，默认 true |
| `autoTorches` | 照明网格自动插火把（按光照计算的排布） | 布尔，默认 false |

`boundary` `body` `edge` `mark`（别名 `centerMarkBlock`）为旧版遗留角色，
当前布局引擎已不使用，写入会被忽略；`previewOn` 为界面临时行为，不进蓝图。

## 构建方法

代码目标 Java 8，但 ForgeGradle 2.3 的环境搭建任务依赖 Pack200，
**请用 JDK 8~13 运行 Gradle**（推荐 Temurin 11；JDK 14+ 会在
`applyBinaryPatches` 处报 `java/util/jar/Pack200` 错误）。

```bash
export JAVA_HOME=/path/to/jdk-11
./gradlew build
```

首次构建会下载 Forge 与 MCP 映射，如需代理：

```bash
GRADLE_OPTS="-Dhttp.proxyHost=127.0.0.1 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897" ./gradlew build
```

产物在 `build/libs/`。GitHub Actions 会在每次推送时自动构建，打 `v*` 标签时自动发布 Release。

> 注意：FG2 工具链最高只能用 Forge `14.23.5.2847`——更新的 1.12.2 构建不再发布
> 该工具链需要的 `userdev` 构件。

## Cleanroom 兼容性

本模组兼容 [Cleanroom](https://github.com/CleanroomMC/Cleanroom) 加载器
（渲染层已做 GL 状态加固：push/popAttrib 兜底、显式状态归一化）。
将构建出的 jar 直接放入 Cleanroom 实例的 `mods/` 文件夹即可。
Cleanroom 开发环境的搭建尝试与结论见 `docs/cleanroom-dev-setup.md`。

## 许可证

GNU LGPLv3
