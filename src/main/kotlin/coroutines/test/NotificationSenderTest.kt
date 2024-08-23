package coroutines.test.notificationsendertest

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationSender(
    private val client: NotificationClient,
    private val exceptionCollector: ExceptionCollector,
    dispatcher: CoroutineDispatcher,
) {
    private val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            exceptionCollector.collectException(throwable)
        }
    val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + dispatcher + exceptionHandler
    )

    fun sendNotifications(notifications: List<Notification>) {
        notifications.forEach { notification ->
            scope.launch {
                client.send(notification)
            }
        }
    }

    fun cancel() {
        scope.coroutineContext.cancelChildren()
    }
}

data class Notification(val id: String)

interface NotificationClient {
    suspend fun send(notification: Notification)
}

interface ExceptionCollector {
    fun collectException(throwable: Throwable)
}

@OptIn(ExperimentalStdlibApi::class)
class NotificationSenderTest {
    @Test
    fun `should send notifications concurrently`() = runTest {
        // given
        val notifications = listOf(
            Notification("1"),
            Notification("2"),
            Notification("3")
        )
        val client = FakeNotificationClient(1000)
        val sender = NotificationSender(
            client,
            FakeExceptionCollector(),
            this.coroutineContext[CoroutineDispatcher]!!
        )

        // when
        sender.sendNotifications(notifications)
        advanceUntilIdle()

        // then
        assertEquals(notifications, client.sent)
        assertEquals(1000, currentTime)
    }

    @Test
    fun `should cancel all coroutines when cancel is called`() = runTest {
        // given
        val sender = NotificationSender(
            FakeNotificationClient(),
            FakeExceptionCollector(),
            this.coroutineContext[CoroutineDispatcher]!!
        )

        // when
        sender.sendNotifications(listOf(
            Notification("1"),
            Notification("2"),
            Notification("3")
        ))
        delay(500)
        sender.cancel()

        // then
        val children = sender.scope.coroutineContext.job.children
        children.forEach { assertTrue { it.isCancelled } }
        advanceUntilIdle()
        assertEquals(500, currentTime)
    }

    @Test
    fun `should not cancel other sending processes when one of them fails`() = runTest {
        // given
        val client = FakeNotificationClient(1000, 2)
        val sender = NotificationSender(
            client,
            FakeExceptionCollector(),
            this.coroutineContext[CoroutineDispatcher]!!
        )

        // when
        sender.sendNotifications(listOf(
            Notification("1"),
            Notification("2"),
            Notification("3")
        ))
        advanceUntilIdle()

        // then
        assertEquals(1000, currentTime)
        assertEquals(listOf(Notification("1"), Notification("3")), client.sent)
    }

    @Test
    fun `should collect exceptions from all coroutines`() = runTest {
        // given
        val exceptionCollector = FakeExceptionCollector()
        val sender = NotificationSender(
            FakeNotificationClient(1000, 2),
            exceptionCollector,
            this.coroutineContext[CoroutineDispatcher]!!
        )

        // when
        sender.sendNotifications(listOf(
            Notification("1"),
            Notification("2"),
            Notification("3")
        ))
        advanceUntilIdle()

        // then
        assertEquals(1000, currentTime)
        assertEquals(1, exceptionCollector.collected.size)
    }
}

class FakeNotificationClient(
    val delayTime: Long = 0L,
    val failEvery: Int = Int.MAX_VALUE
) : NotificationClient {
    var sent = emptyList<Notification>()
    var counter = 0
    var usedThreads = emptyList<String>()

    override suspend fun send(notification: Notification) {
        if (delayTime > 0) delay(delayTime)
        usedThreads += Thread.currentThread().name
        counter++
        if (counter % failEvery == 0) {
            throw FakeFailure(notification)
        }
        sent += notification
    }
}

class FakeFailure(val notification: Notification) : Throwable("Planned fail for notification ${notification.id}")

class FakeExceptionCollector : ExceptionCollector {
    var collected = emptyList<Throwable>()

    override fun collectException(throwable: Throwable) = synchronized(this) {
        collected += throwable
    }
}
