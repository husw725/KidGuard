import json
import random

# Mocking the Question class and generators from QuestionBank.kt for testing
def create_question(text, correct, wrongs):
    unique_wrongs = list(set([w for w in wrongs if w != correct]))
    all_options = unique_wrongs[:3] + [correct]
    random.shuffle(all_options)
    correct_index = all_options.index(correct)
    return {
        "text": text,
        "options": all_options,
        "correctIndex": correct_index,
        "correctAnswer": correct
    }

def generate_mock_questions():
    questions = []
    # Mix of math and logic
    for i in range(5):
        val = i * 10
        questions.append(create_question(f"问题 {i+1}: {val} + 5 = ?", str(val + 5), [str(val), str(val + 10), str(val - 5)]))
    
    for i in range(5):
        items = ["苹果", "香蕉", "西瓜", "铅笔"]
        questions.append(create_question(f"问题 {i+6}: 找出不是同一类的词: ", "铅笔", ["苹果", "香蕉", "西瓜"]))
    
    return questions

def validate_questions(questions):
    for i, q in enumerate(questions):
        print(f"--- Validating Question {i+1} ---")
        print(f"Text: {q['text']}")
        print(f"Options: {q['options']}")
        
        # 1. Check options count
        if len(q['options']) != 4:
            print(f"Error: Question {i+1} does not have 4 options.")
            return False
            
        # 2. Check uniqueness
        if len(set(q['options'])) != 4:
            print(f"Error: Question {i+1} options are not unique.")
            return False
            
        # 3. Check correct index and answer
        correct_idx = q['correctIndex']
        if not (0 <= correct_idx <= 3):
            print(f"Error: Question {i+1} correctIndex out of bounds.")
            return False
            
        actual_answer = q['options'][correct_idx]
        if actual_answer != q['correctAnswer']:
            print(f"Error: Question {i+1} correctIndex mismatch. Expected {q['correctAnswer']}, got {actual_answer}")
            return False
            
    print("\nAll 10 questions passed validation.")
    return True

questions = generate_mock_questions()
for q in questions:
    print(f"Q: {q['text']}, Options: {q['options']}, Correct: {q['options'][q['correctIndex']]}")

validate_questions(questions)
