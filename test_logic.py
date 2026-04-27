import math

def max_of(a, b):
    return max(a, b)

def get_total_question_config():
    return 20

def is_first_quiz_today():
    return True

def test_count_logic():
    count = max_of(5, get_total_question_config())
    print(f"Initial count: {count}")
    
    if is_first_quiz_today():
        count = max_of(5, count // 2)
    print(f"Final count: {count}")

test_count_logic()
