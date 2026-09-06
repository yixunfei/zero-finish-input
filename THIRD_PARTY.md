# Third-party notices

ZeroInput 自有源码使用 Apache-2.0。下列运行时组件和数据保持各自许可证；固定版本由
`tools/bootstrap-rime.ps1` 校验。完整许可原文位于 [`LICENSES`](LICENSES)。

| Component | Pinned version | License | License text | Purpose |
| --- | --- | --- | --- | --- |
| librime | 1.13.1 (`1c233581`) | BSD-3-Clause | `librime-BSD-3-Clause.txt` | 中文输入引擎 |
| darts-clone | librime bundled copy | BSD-3-Clause | `darts-clone-BSD-3-Clause.txt` | librime 数据结构 |
| rime-luna-pinyin | `46acf031` | LGPL-3.0 | `rime-data-LGPL-3.0.txt`, `GPL-3.0.txt` | 全拼词典 |
| rime-essay | `0766c929` | LGPL-3.0 | `rime-data-LGPL-3.0.txt`, `GPL-3.0.txt` | 词频数据 |
| Boost | 1.89.0 | BSL-1.0 | `Boost-1.0.txt` | librime 构建依赖 |
| OpenCC | `e5d6c5f1` | Apache-2.0 | `Apache-2.0.txt` | 离线简繁转换及 TS/ST 字符、词组数据 |
| LevelDB | `99b3c03b` | BSD-3-Clause | `leveldb-BSD-3-Clause.txt` | librime 用户数据 |
| yaml-cpp | `f7320141` | MIT | `yaml-cpp-MIT.txt` | 配置解析 |
| marisa-trie | `0d4e8ab5` | BSD-2-Clause（本项目采用双许可证中的 BSD 选项） | `marisa-trie-COPYING.md` | 词典数据结构 |
| AndroidX libraries | 见 `gradle/libs.versions.toml` | Apache-2.0 | `Apache-2.0.txt` | Android 运行时支持 |
| AndroidX Test runner / JUnit extension | 1.6.2 / 1.2.1 | Apache-2.0 | `Apache-2.0.txt` | 仅模拟器/设备测试 APK；使用已有缓存，不进入应用运行时，无联网或采集路径 |
| Material Components | 1.12.0 | Apache-2.0 | `Apache-2.0.txt` | Android UI |
| Kotlin runtime | 2.1.21 | Apache-2.0 | `Apache-2.0.txt` | Kotlin 运行时 |
| Android NDK runtime | 28.2.13676358 | 多许可证 | `android-ndk-NOTICE.txt` | `libc++_shared.so` 与 native 运行时 |

LGPL 数据以可直接读取和替换的源数据形式包含在 APK 资产中；ZeroInput 的独立源码不因此改用
LGPL。发布二进制时必须同时分发本目录、`NOTICE` 和对应源码获取方式。

OpenCC 的 `TSCharacters.txt`、`TSPhrases.txt`、`STCharacters.txt` 与 `STPhrases.txt`
从已固定且校验的 OpenCC 源码生成 APK 资产；每个文件的 SHA-256 同时固定在
`engine-rime/build.gradle.kts`。这些数据沿用 Apache-2.0，无新增运行时依赖、权限或联网路径。

九键的 `pinyin-syllables.txt` 在构建时由同一固定版本的 Luna Pinyin 词典提取，沿用该数据的
LGPL-3.0 许可；原始词典继续随 APK 提供。`engine-dictionary` 的算法和有限参考词表为 ZeroInput
自有 Apache-2.0 源码/数据，没有引入另一个第三方运行时或词典。
