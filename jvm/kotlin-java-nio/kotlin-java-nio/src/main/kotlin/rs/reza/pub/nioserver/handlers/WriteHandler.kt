package rs.reza.pub.nioserver.handlers

import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun AsynchronousSocketChannel.writeAsync(buffer: ByteBuffer): Int =
    suspendCancellableCoroutine { continuation ->
        write(
            buffer,
            Unit,
            object : CompletionHandler<Int, Unit> {
                override fun completed(bytesWritten: Int, attachment: Unit) {
                    continuation.resume(bytesWritten)
                }

                override fun failed(exception: Throwable, attachment: Unit) {
                    continuation.resumeWithException(exception)
                }

            }
        )

        continuation.invokeOnCancellation { close() }
    }