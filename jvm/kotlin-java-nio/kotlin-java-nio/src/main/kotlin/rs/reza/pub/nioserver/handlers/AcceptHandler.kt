package rs.reza.pub.rs.reza.blog.nioserver.handlers

import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


suspend fun AsynchronousServerSocketChannel.acceptAsync(): AsynchronousSocketChannel =
    suspendCancellableCoroutine { continuation ->
        accept(
            Unit,
            object : CompletionHandler<AsynchronousSocketChannel, Unit> {
                override fun completed(clientChannel: AsynchronousSocketChannel, attachment: Unit) {
                    continuation.resume(clientChannel)
                }

                override fun failed(exception: Throwable, attachment: Unit) {
                    continuation.resumeWithException(exception)
                }
            })

        continuation.invokeOnCancellation { close() }
    }

