# Cleanroom 开发环境配置（尝试记录）

> 状态：**未完成**。Cleanroom 官方开发链（UniMined 1.4.43-kappa）在当前网络环境下受阻：
> UniMined kappa 版要求 Java 25 运行 Gradle，而代理会截断 Gradle 9.0.0 下载的部分依赖 jar，
> 触发 `MergeInstrumentationAnalysisTransform` 失败。直连下载的 jar 本体是完好的（已验证）。
>
> 结论：验证 Cleanroom 兼容性最可靠的方式是把 FG2.3 构建出的 SRG jar 直接放进
> Cleanroom 实例的 `mods/` 文件夹——Cleanroom 兼容标准 Forge 1.12.2 mod 加载。

## 已验证的配置要点（供后续重试）

- 插件：`xyz.wagyourtail.unimined` 版本 `1.4.43-kappa`（仅 ArcSeekers 仓库有，
  `https://maven.arcseekers.com/releases`；Maven Central 只有旧版 1.4.1，API 不同）
- 仓库链：ArcSeekers（unimined 本体+kappa 依赖）+ WagYourTail（commons-kt-jvm）+
  FabricMC（class-tweaker）+ Maven Central（kotlin 等）
- 运行环境：Java 25（major 69 class 文件）+ Gradle 9.x（Gradle 8.x 的 Groovy 读不了 69）
- Gradle 9.0.0 已知问题：代理截断 jar 时报 `MergeInstrumentationAnalysisTransform`
  隔离失败；建议 Gradle 9.1+ 或关闭代理用可信网络

## 参考模板

CleanroomMC/CleanroomModTemplate（build.gradle 关键部分）：

```groovy
plugins {
    id 'xyz.wagyourtail.unimined' version '1.4.43-kappa'
}

unimined.minecraft {
    version "1.12.2"
    mappings {
        mcp("stable", "39-1.12")
    }
    cleanroom {
        // loader 版本由模板的 gradle.properties 传入
    }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}
```
