package coroutines.test.mapasynctest

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

suspend fun <T, R> Iterable<T>.mapAsync(
    transformation: suspend (T) -> R
): List<R> = coroutineScope {
    map { async { transformation(it) } }
        .awaitAll()
}

class MapAsyncTest {

    private val list = listOf("A", "B", "C")
    private val mapping = { s: String -> s.plus("Z") }
    private val expected = list.map(mapping)

    @Test
    fun `should behave like a regular map`() = runTest {
        assertEquals(expected, list.mapAsync { delay(1000); mapping(it) })
    }

    @Test
    fun `should map async`() = runTest {
        list.mapAsync {
            delay(1000)
            mapping(it)
        }
        assertEquals(1000, currentTime)
    }

    @Test
    fun `should keep elements order`() = runTest {
        val result = listOf(
            suspend { delay(1000); "AZ" },
            suspend { delay(500); "BZ" },
            suspend { "CZ" },
        ).mapAsync { it() }
        assertEquals(expected, result)
    }

    @Test
    fun `should support context propagation`() = runTest {
        var capturedContext: CoroutineContext? = null
        withContext(CoroutineName("mapAsync")) {
            listOf("A").mapAsync {
                capturedContext = currentCoroutineContext()
            }
        }

        assertEquals("mapAsync", capturedContext?.get(CoroutineName)?.name)
    }

    @Test
    fun `should support cancellation`() = runTest {
        var capturedContext: CoroutineContext? = null
        val job = launch {
            list.mapAsync {
                capturedContext = currentCoroutineContext()
                delay(1000)
                mapping(it)
            }
        }
        advanceTimeBy(500)
        job.cancel()
        assertTrue { capturedContext?.isActive == false }
        advanceUntilIdle()
        assertEquals(500, currentTime)
    }

    @Test
    fun `should propagate exceptions`() = runTest {
        val e = object :  Exception() {}
        val result = runCatching {
            listOf(
                suspend { "A" },
                suspend { delay(1000); "B" },
                suspend { delay(500); throw e }
            ).mapAsync { it() }
        }
        assertEquals(e, result.exceptionOrNull())
        assertEquals(500, currentTime)
    }
}
