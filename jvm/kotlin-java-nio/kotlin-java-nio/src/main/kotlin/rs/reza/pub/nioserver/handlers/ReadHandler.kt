package rs.reza.pub.rs.reza.blog.nioserver.handlers

import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


suspend fun AsynchronousSocketChannel.readAsync(buffer: ByteBuffer): Int =
    suspendCancellableCoroutine { continuation ->
        read(
            buffer,
            Unit,
            object : CompletionHandler<Int, Unit> {
                override fun completed(bytesRead: Int, attachment: Unit) {
                   continuation.resume(bytesRead)
                }

                override fun failed(exception: Throwable, attachment: Unit) {
                    continuation.resumeWithException(exception)
                }

            }
        )

        continuation.invokeOnCancellation { close() }
    }

