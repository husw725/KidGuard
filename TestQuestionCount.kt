import kotlin.math.max

object QuestionBankMock {
    fun getTotalQuestionConfig(context: Any?): Int = 20
    fun isFirstQuizToday(context: Any?): Boolean = true
}

fun main() {
    val context = null
    var count = maxOf(5, QuestionBankMock.getTotalQuestionConfig(context))
    println("Initial count: $count")
    
    if (QuestionBankMock.isFirstQuizToday(context)) {
        count = maxOf(5, count / 2)
    }
    println("Final count: $count")
}
