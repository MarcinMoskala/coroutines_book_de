import kotlinx.coroutines.*

fun main() = runBlocking {
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    scope.launch {
        delay(500)
        throw Error()
    }
    val job = scope.launch {
        delay(4000)
        print("A")
    }

    job.join()
}
