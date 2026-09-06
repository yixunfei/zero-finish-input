# ADR 0001: Stable engine boundary

Status: accepted

ZeroInput 使用自有 `InputEngine` 契约，librime 位于独立 Android library 和 JNI 适配层。应用不得直接引用 Rime 数据结构。librime 以固定 tag 和递归子模块构建，更新时必须先通过适配器契约测试。

这样可以保留成熟中文转换能力，同时把上游 API、构建系统、许可证或维护状态变化限制在一个模块内。

