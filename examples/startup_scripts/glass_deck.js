// KubeJS 风格蓝图示例
// 放到以下任一位置即可在游戏内「蓝图」按钮中切换使用：
//   config/industrial_platform/blueprints/*.js
//   kubejs/startup_scripts/industrial_platform/*.js
// 修改后输入 /ipblueprints reload，或在配置界面点击「刷新蓝图」。

blueprints.register({
    name: "玻璃观景台",
    border: "minecraft:iron_block",
    fill: "minecraft:glass",
    center: "minecraft:sea_lantern",
    boundary: "minecraft:concrete:4",
    edge: "minecraft:quartz_block",
    body: "minecraft:stone",
    channelLine: "minecraft:glowstone",
    centerMarkBlock: "minecraft:diamond_block",

    layers: 1,            // 平台层数 (平台生成在搭建器所在高度)
    intervalX: 20,        // 边界间隔 X (每块内部可用宽度, 单块宽 = intervalX + 2)
    intervalZ: 20,        // 边界间隔 Z
    countX: 1,            // X 方向平台数量, 2x2 / 3x3 就填 2 / 3
    countZ: 1,            // Z 方向平台数量
    linkWidth: 3,         // 块与块之间连接带的宽度
    channelWidth: 1,      // 通道宽度, 0 = 无通道
    channelMode: "HORSESHOE", // NONE / STRAIGHT / HORSESHOE
    centerMark: true      // 用 centerMarkBlock 标出平台中心
});

blueprints.register({
    name: "紧凑工作台",
    fill: "minecraft:stone",
    border: "minecraft:cobblestone",
    intervalX: 6,
    intervalZ: 6,
    layers: 1
});
