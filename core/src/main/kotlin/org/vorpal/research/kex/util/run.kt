package org.vorpal.research.kex.util

import org.vorpal.research.kthelper.KtException

class TimeoutException : KtException("")

//@Suppress("SameParameterValue")
//fun runWithTimeout(timeout: Long, body: () -> Unit) {
//    val thread = Thread(body)
//
//    thread.start()
//    thread.join(timeout)
//    if (thread.isAlive) {
//        @Suppress("DEPRECATION")
//        thread.stop()
//        throw TimeoutException()
//    }
//}

@Suppress("SameParameterValue")
fun <T : Any> runWithTimeout(timeout: Long, body: () -> T?): T? {
    var result: T? = null
    val actualBody = {
        result = body()
    }
    val thread = Thread(actualBody)

    thread.start()
    thread.join(timeout)
    if (thread.isAlive) {
        @Suppress("DEPRECATION")
        thread.stop()
        throw TimeoutException()
    }
    return result
}