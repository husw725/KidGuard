#!/usr/bin/env python3
"""
KidGuard 全部题目测试
- 验证静态题：选项数2-4、唯一性、correctIndex合法
- 统计数学题类型覆盖
"""
import re, os, json, sys
from collections import Counter

BASE = "/home/husw/.hermes/tasks/KidGuard/app/src/main/java/com/example/floating"

def parse_questions(fname):
    """Parse Question(text, listOf(...), index) from Kotlin source."""
    path = os.path.join(BASE, fname)
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    # Question("text", listOf("opt1", "opt2", ...), idx)
    pattern = r'Question\("((?:[^"\\]|\\.)*)",\s*listOf\((.*?)\),\s*(\d+)\)'
    questions = []
    for m in re.finditer(pattern, content):
        text = m.group(1)
        opts_str = m.group(2)
        correct_idx = int(m.group(3))
        options = re.findall(r'"([^"]*)"', opts_str)
        questions.append({"text": text, "options": options, "correctIndex": correct_idx})
    return questions

def validate(q, source, idx):
    errs = []
    opts = q["options"]
    ci = q["correctIndex"]
    if len(opts) < 2:
        errs.append(f"[{source}:{idx}] 选项<2: {len(opts)} -> {opts}")
    if len(opts) > 4:
        errs.append(f"[{source}:{idx}] 选项>4: {len(opts)} -> {opts}")
    if len(opts) != len(set(opts)):
        errs.append(f"[{source}:{idx}] 选项重复: {opts}")
    if ci < 0 or ci >= len(opts):
        errs.append(f"[{source}:{idx}] correctIndex越界: idx={ci}, opts={len(opts)}")
    return errs

def main():
    print("=" * 60)
    print("KidGuard 全部题目测试报告")
    print("=" * 60)

    all_errors = []

    # 1. Datas.kt
    print("\n📖 [Datas.kt] 内置语文题...")
    builtin = parse_questions("Datas.kt")
    print(f"   找到 {len(builtin)} 道题")
    errs = [e for q in builtin for i in range(1) for e in validate(q, "Datas", builtin.index(q))]
    dupes = [t for t, c in Counter(q["text"] for q in builtin).items() if c > 1]
    if dupes:
        errs.append(f"重复题目: {len(dupes)} 道")
    all_errors.extend(errs)
    print(f"   {'✅ 通过' if not errs else f'❌ {len(errs)} 个问题'}")
    for e in errs[:5]: print(f"      {e}")

    # 2. ThinkingChineseQuestions.kt
    print("\n📖 [ThinkingChineseQuestions.kt] 语文拓展题...")
    tc = parse_questions("ThinkingChineseQuestions.kt")
    print(f"   找到 {len(tc)} 道题")
    errs = [e for q in tc for i in range(1) for e in validate(q, "TC", tc.index(q))]
    dupes = [t for t, c in Counter(q["text"] for q in tc).items() if c > 1]
    if dupes:
        errs.append(f"重复题目: {len(dupes)} 道")
    all_errors.extend(errs)
    print(f"   {'✅ 通过' if not errs else f'❌ {len(errs)} 个问题'}")
    for e in errs[:5]: print(f"      {e}")

    all_verbal = builtin + tc
    print(f"\n📊 语文静态题: {len(all_verbal)} 道 (内置{len(builtin)} + 拓展{len(tc)})")

    # 3. 数学类型统计
    print("\n🔢 数学题类型覆盖:")
    with open(os.path.join(BASE, "QuestionBank.kt"), "r", encoding="utf-8") as f:
        qb = f.read()
    with open(os.path.join(BASE, "ThinkingMathGenerator.kt"), "r", encoding="utf-8") as f:
        tm = f.read()
    with open(os.path.join(BASE, "OlympiadMathGenerator.kt"), "r", encoding="utf-8") as f:
        om = f.read()

    g2m = re.search(r'grade2TypeNames\s*=\s*listOf\((.*?)\)', qb, re.DOTALL)
    g2 = re.findall(r'"([^"]+)"', g2m.group(1)) if g2m else []
    adm = re.search(r'advancedTypeNames\s*=\s*listOf\((.*?)\)', qb, re.DOTALL)
    adv = re.findall(r'"([^"]+)"', adm.group(1)) if adm else []
    tmv = re.search(r'typeNames\s*=\s*listOf\((.*?)\)', tm, re.DOTALL)
    tmk = re.findall(r'"([^"]+)"', tmv.group(1)) if tmv else []
    omv = re.search(r'olympiadTypeNames\s*=\s*listOf\((.*?)\)', om)
    omk = re.findall(r'"([^"]+)"', omv.group(1)) if omv else []

    print(f"   Grade2 基础: {len(g2)} 种")
    print(f"   Advanced 进阶: {len(adv)} 种")
    print(f"   ThinkingMath: {len(tmk)} 种")
    print(f"   Olympiad 奥数: {len(omk)} 种")
    print(f"   数学子类型总计: {len(g2)+len(adv)+len(tmk)+len(omk)} 种")

    # 4. 生成器检查
    print("\n🔍 生成器安全检查:")
    print(f"   createQuestion 兜底: {'✅' if '其他' in qb else '❌'}")
    print(f"   createMathQ fallback: {'✅' if 'fallbackOffsets' in qb else '❌'}")
    print(f"   Question require 2-4: {'✅' if 'require(options.size in 2..4)' in qb else '❌'}")
    print(f"   Question require unique: {'✅' if 'require(options.distinct().size == options.size)' in qb else '❌'}")
    print(f"   ThinkingMath else 兜底: {'✅' if 'else -> generateAgeDifference' in tm else '❌'}")
    print(f"   Olympiad else 兜底: {'✅' if 'else -> generateAlgebraicReasoning' in om else '❌'}")

    # 5. 汇总
    print("\n" + "=" * 60)
    print(f"📊 汇总: 语文{len(all_verbal)}道 + 数学{len(g2)+len(adv)+len(tmk)+len(omk)}种动态 + 错误{len(all_errors)}个")
    if all_errors:
        print("\n所有问题:")
        for e in all_errors:
            print(f"   - {e}")
    else:
        print("\n🎉 全部通过！")

    report = {
        "builtin_verbal": len(builtin),
        "thinking_chinese": len(tc),
        "total_verbal": len(all_verbal),
        "grade2_math": len(g2),
        "advanced_math": len(adv),
        "thinking_math": len(tmk),
        "olympiad_math": len(omk),
        "total_math_types": len(g2)+len(adv)+len(tmk)+len(omk),
        "errors": all_errors,
        "error_count": len(all_errors)
    }
    with open("/home/husw/.hermes/tasks/KidGuard/test_report.json", "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    print(f"\n📄 报告已保存: test_report.json")
    return 0 if not all_errors else 1

if __name__ == "__main__":
    sys.exit(main())
