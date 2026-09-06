# Architecture

The public project and application display name is `zero finish input`.
`dev.zeroinput.ime`, Kotlin namespaces, engine resource identifiers, encrypted file
names and Keystore aliases retain their existing identity. Debug uses the separate
`dev.zeroinput.ime.debug` package. App build assets include offline license texts
and source links copied from the repository; they add no runtime network path.

## Dependency direction

```text
app -> ime-core --------> engine-api
 |                         ^
 +-> ime-ui                |
 +-> engine-english -------+
 +-> engine-rime ----------+
 +-> engine-dictionary ----+
 +-> user-data ------------+
 +-> language-pack -----> engine-api

user-data -> security
language-pack -> security
```

业务层不引用 librime 类型。升级、替换或移除 librime 时，变化限制在 `engine-rime` 模块和语言数据部署器内。

## Runtime boundaries

1. `ZeroInputService` 是唯一输入法服务并持有当前输入会话。每个会话都有单调递增的令牌，
   安全剪贴板认证请求同时绑定令牌、原始 `InputConnection` 和发起时的交互序号；任一项变化都会
   取消认证后的待提交操作。
2. `InputSessionController` 根据编辑器类型应用隐私策略，再将按键交给当前引擎。设置变化会在
   当前会话的下一次交互和输入视图恢复时重新求值；若权限收紧，会取消组合文本并重建引擎状态。
   `onStartInput` 先安装纯内存的轻量降级引擎，不在 IME 主线程创建 Rime 会话或扫描语言包。
   会话初始语言优先读取 `InputMethodManager` 提供的当前 ZeroInput 子类型（`zh-CN`/`en-US`），
   再回退到本地设置；这样即使 Android 调整 subtype 回调时序，系统选择也不会被旧设置覆盖。
   键盘上的中英文切换同时更新 Android 当前子类型，避免旋转或切换输入框时恢复旧语言。
   `EngineWarmupCoordinator` 在共享的有界单线程队列中创建并启动目标引擎，结果携带会话令牌、
   编辑器包名、语言包键和完整隐私快照；只有仍处于同一编辑器且没有组合文本时才转移所有权，
   过期或拒绝的结果立即关闭。结果投递到 IME 所在线程前由独立的所有权交接器暂存，服务销毁或
   Handler 拒绝/移除回调时会回收尚未交接的引擎，避免后台 native 句柄成为孤儿。
3. 引擎返回不可变 `EngineUpdate`，控制器负责通过 `InputConnection` 提交或组合文本。
4. `PersonalizationStore` 是学习数据端口；加密实现位于 `user-data`，`ime-core` 不依赖具体仓库。
   应用层适配器负责在获得允许后按需后台预热和串行学习写入，输入主线程只读取内存缓存。
   会话或隐私策略变化以及清除个性化数据时都会提升队列代次，使此前排队但尚未执行的学习写入
   失效，并立即隐藏旧候选；用户主动清除还会删除用户词组和 emoji 历史各自的 Keystore 密钥。
5. emoji 最近记录与安全剪贴板无标签索引由后台线程读取并缓存。安全剪贴板正文只在一次性认证
   成功后于专用后台线程解密，回到主线程前再次校验输入会话与交互序号；设置、语言包、
   子类型或个性化状态变化也会使尚未完成的认证请求失效。emoji 写入带有清除代次，用户清除
   数据后，已经排队但尚未执行的历史写入不会复活。
   引擎预热、个性化写入、索引刷新和认证读取均使用有限容量队列；取消任务后清理已排队的
   Future，清除个性化数据时会先取消旧的可选任务并以控制操作优先；仅设置页的耐久清除路径
   在极端拒绝情况下允许其后台工作线程作最后一次同步尝试，不让 IME 输入线程执行阻塞 I/O。
6. 语言包仅允许包含经过清单验证的数据文件，不允许动态代码或原生库。安装后通过独立注册表
   发现、启用和实例化数据型引擎；完整性扫描及词典加载不会在设置页主线程执行，首次后台
   扫描完成前，设置页只读取内存快照。当前只接受明确映射为 `zh` 或 `en` 的 BCP-47 标签，并在
   激活安装前确认包内至少有一条可用词典数据，避免“导入成功但无法输入”的死包。

librime 自身的用户词典被禁用。中文候选选择的拼音输入码和使用频率由 ZeroInput 的加密仓库
统一保存，从而让全局关闭学习、隐私模式和编辑器的无个性化请求作用于所有引擎。
内置全拼使用 `express_editor`：选中覆盖全部输入的候选后直接提交，分段选择继续保留剩余组合；
`Return` 显式绑定 `commit_composition`。随包资源版本变化会重新部署配置，不改变加密用户数据格式。

## User lexicon persistence and transfer

`UserLexiconRepository` depends on the small `security/EncryptedStore` port;
production still uses `EncryptedFileStore`, Android Keystore and the existing
AES-GCM envelope. The repository serializes background reads and writes under
one lock. It builds a separate update, persists it, then publishes its immutable
memory snapshot. Failed writes leave the previous snapshot intact. Clearing
advances an atomic generation before waiting for the lock, rejects older waiting
updates, and removes the ciphertext and dedicated key after any in-flight write.
If deletion fails, the repository remains unavailable until deletion is retried.
The IME continues to access only `QueuedPersonalizationStore` memory snapshots.

`UserLexiconFormat` uses Android's strict streaming `JsonReader` and accepts only
the existing format-1 root object, terms array and flat term objects. It rejects
unknown or duplicate fields, nested values, duplicate identities/IDs, invalid
UTF-8, invalid scalar types, unsupported languages and oversized data before
mutation. Imports merge by language, case-insensitive input code and phrase;
existing local IDs remain stable and new entries receive fresh local IDs.
Manual additions and imports fail atomically beyond 20,000 terms or 5 MiB of
compact JSON. Learning retains its existing bounded frequency/recency eviction.
Exports use the same compact JSON representation so new writes fit the import
limit. The format version, encrypted file name and key alias are unchanged.

Transfer confirmations belong to the settings presentation layer. They explain
merge/overwrite behavior, export scope and plaintext exposure before opening the
system document picker. Parser exceptions are converted to content-free failure
codes; the UI uses localized messages and never displays raw exceptions. See
[ADR 0005](adr/0005-user-lexicon-boundary.md).

## Chinese input configuration

`ChineseInputOptions` is an immutable engine-api value (script, abbreviations,
eight independent fuzzy pairs, punctuation, bounded page size, and keyboard layout). Optional
`ConfigurableChineseEngineFactory` and explicit `EngineCapability` declarations
allow another Chinese engine to implement the same product settings without
exposing Rime options to the controller. `CandidateTextNormalizer` applies the
selected script to personal candidates before deduplication and routing; the
encrypted original and its learning identity are preserved.

The service captures options in every warm-up request and checks them again at
handoff. Pending authenticated actions are cancelled before a prepared engine
replaces the current engine. Ordinary setting changes wait until composition is idle. Before a
different syllable index is deployed, the old native session is retired to the
memory fallback. Deployment refuses to run while a native session remains alive;
release of a superseded session makes preparation retryable. Configuration,
deployment and OpenCC initialization stay on the existing bounded engine worker.
Only the current generated configuration and prism are retained. These files
contain settings and public dictionary indexes, never personal input.

The bundled schema uses JSON syntax, a YAML subset understood by librime, so
Android's structured JSON parser can derive bounded configurations. Rime compiles
fuzzy and abbreviation rules into its own prism; the app does not implement a
second pinyin parser. OpenCC text dictionaries come from the pinned source archive
and are hash-checked during the build. No runtime downloads are involved.

The input view reserves a 24dp composition/status row and a 48dp candidate row.
Expanded candidates reuse the keyboard's measured row geometry, including density
rounding. Candidate views are reused, reject clicks whose binding changed during
the gesture, and clear their text when retired. Held backspace clears a composition
once, or repeats editor deletion until release/cancellation; panel, session and
privacy transitions cancel scheduled gesture callbacks.

Each Rime update is copied through one JNI context read after processing the key,
instead of reading the same native context separately for each field. Candidate
paging does not rewrite unchanged Android composing spans, and commitText already
clears the previous span. The engine API and synchronous ownership of editor
mutations remain unchanged.

## Engine compatibility

Rime remains the default Chinese engine. `engine-dictionary` is a separate JVM
module with a small bundled Apache-2.0 reference dictionary and bounded prefix
lookup. It implements the same engine lifecycle and advertises only punctuation
and page-size support. The selected factory and full options snapshot travel in
every warm-up request; unsupported controls are disabled and the effective
keyboard stays full. No engine owns personalized storage.

Chinese nine-key input is an optional Rime capability. Rime's algebra compiles
telephone digits into its public spelling index on the preparation worker;
segmentation, ranking, abbreviations and fuzzy matching remain in librime.
`EngineSnapshot.readings` and the optional `ReadingSelectionEngine` port expose
bounded reading choices without leaking native types. The syllable table is
generated from the pinned Luna Pinyin data at build time. Choosing a reading
replaces the first unresolved numeric span through an allowlisted JNI call;
backspace can undo that choice. Partial candidate selection disables reading
replacement so fixed text cannot be overwritten. Reset and close clear history.
English, sensitive editors and engines without the capability keep the full
keyboard. Literal digits from the symbols page finish composition before direct
commit, so they cannot accidentally become nine-key spelling codes.

Keyboard rows allocate cumulative pixel boundaries across the full available
width. Rectangular keys dispatch on release in the current event, support
independent pointers and cancel when a gesture leaves the target. Input remains
synchronous through ime-core; public-fixture instrumentation measures key
dispatch, editor updates and the next frame separately.

`InputEngine` 是稳定端口。native 适配器还包含单独的 `NativeRimeBridge` 边界，因此 librime C API
变化不会传播到 Kotlin 业务层。Rime 运行时显式发布未初始化、初始化中、就绪和失败状态；native
库缺失、初始化失败或无法创建探针会话都会进入失败状态并使用降级引擎。发行构建通过
`requireRime` 属性阻止误用降级引擎。
