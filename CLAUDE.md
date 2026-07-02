# 小欣学习 (KidGuard) — Android 强制学习锁屏 App

孩子玩手机前必须完成 语文/数学/英语 练习才能解锁。Kotlin，minSDK 24/target 33，Gradle 8.1.1，JDK 17。
飞书(Feishu)做家长报告 + 远程指令。（多智能体角色见 GEMINI.md，本文件是 Claude 版。）

## 构建 / 测试
- 安装到设备：`./gradlew installDebug --console=plain`（或 `./install.sh`）
- 题库全量校验（权威）：`python3 test_all_questions.py`
- 题目去重/完整性：`node skills/题目管理与优化/scripts/validate_questions.cjs`
- Android SDK 路径在 `local.properties`；JVM heap `-Xmx2048m`

## 关键文件
- `app/.../FloatingService.kt` (620行) — 锁屏 UI + 答题逻辑 + 飞书上报
- `app/.../QuestionBank.kt` (877行) — 出题调度/加权选择/难度引擎/艾宾浩斯复习
- `app/.../ThinkingMathGenerator.kt` — 奥数题型生成
- `builtin_questions.json` (v2, 208题) / `Datas.kt` — 内置题库
- `app/.../FeishuClient.kt` — 飞书 REST 集成（45s 轮询远程指令）

## 坑（重要）
- 🔑 `feishu.properties` 是**明文密钥**（appSecret 等），已 gitignore 但在磁盘上，**勿提交/勿外泄**。
- ⚠️ `test_all_questions.py` 第7行 `BASE` 硬编码 `/home/husw/.hermes/...`，本机会跑不通，需先改路径。
- Accessibility 服务会**自动复活** App；答题时会**抢占音频焦点**；Device Admin 防卸载（需先在设置里取消激活）。
- 阅读/应用题每次**重新参数化**（换名字/数字），不能靠背答案。
- `app/build/`(~2GB) 和 `__pycache__/` 可安全删除。
