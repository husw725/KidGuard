import unittest
import json

class TestQuestionLogic(unittest.TestCase):

    def setUp(self):
        with open('/Users/husw/demo/skills/xin-lock/builtin_questions.json', 'r', encoding='utf-8') as f:
            data = json.load(f)
            self.questions = data.get('questions', [])

    def test_options_integrity(self):
        """1. 检查选项长度是否为4，且是否存在重复项。"""
        for i, q in enumerate(self.questions):
            options = q.get('options', [])
            # 特殊情况处理：检查数据是否符合 4 个选项的需求
            self.assertEqual(len(options), 4, f"Question index {i} ('{q.get('text')}') has {len(options)} options, expected 4.")
            self.assertEqual(len(set(options)), 4, f"Question index {i} ('{q.get('text')}') has duplicate options: {options}")

    def test_correct_index_validity(self):
        """3. 确保 correctIndex 在 0-3 范围内。"""
        for i, q in enumerate(self.questions):
            options = q.get('options', [])
            correct_index = q.get('correctIndex', -1)
            
            self.assertTrue(0 <= correct_index < len(options), f"Question index {i} ('{q.get('text')}') has invalid correctIndex: {correct_index}")

    def test_unique_questions(self):
        """2. 验证题目是否唯一。"""
        texts = [q['text'] for q in self.questions]
        self.assertEqual(len(texts), len(set(texts)), "Found duplicate questions in builtin_questions.json")

if __name__ == '__main__':
    unittest.main()
