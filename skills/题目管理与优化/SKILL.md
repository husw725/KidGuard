# 技能名称：题目管理与优化

## 核心职责
该技能旨在高效处理、校验和优化项目中的题目数据，确保题库的高质量与一致性。

## 关键路径定位
- **Kotlin 数据模型**: `/Users/husw/demo/skills/xin-lock/app/src/main/java/com/example/floating/Datas.kt`
- **JSON 原始数据**: `/Users/husw/demo/skills/xin-lock/builtin_questions.json`

## 校验标准
1. **唯一性**: 确保题目内容在整个库中唯一，无重复记录。
2. **完整性**: 所有题目必须包含题干、选项列表、正确答案标识。
3. **奥数题规范**: 调用 `/Users/husw/demo/skills/xin-lock/app/src/main/java/com/example/floating/OlympiadMathGenerator.kt` 进行逻辑验证。

## 工作流指引
- 使用 `scripts/validate_questions.cjs` 进行定期扫描。
- 遵循 `references/patterns.md` 中的最佳实践进行数据维护。
