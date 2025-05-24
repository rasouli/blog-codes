package rs.reza.pub.rs.reza.blog.nioserver

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import rs.reza.pub.nioserver.handlers.writeAsync
import rs.reza.pub.rs.reza.blog.nioserver.handlers.acceptAsync
import rs.reza.pub.rs.reza.blog.nioserver.handlers.readAsync
import java.io.IOException
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousChannelGroup
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.Executors
import java.util.logging.Level
import java.util.logging.Logger

object AsyncServer {
    private val logger = setupLogger()
    private val HOST = "localhost"
    private val PORT = 6070
    private val SOCKET_RECIEVE_BUFFER_SIZE = 1024

    private val asyncChannelGroupThreadPool = Executors.newFixedThreadPool(2)
    private val ioDispatcher = Dispatchers.IO
    private val applicationDispatcher = Dispatchers.Default

    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            run()
        }
    }

    suspend fun run() = coroutineScope {
        val address = InetSocketAddress(HOST, PORT)
        val asyncServerSocketChannel = withContext(applicationDispatcher) {
            val asynchronousChannelGroup = AsynchronousChannelGroup.withThreadPool(asyncChannelGroupThreadPool)
            listen(address = address, group = asynchronousChannelGroup)
        }

        withContext(applicationDispatcher) {
            asyncServerSocketChannel.use { serverSocketChannel ->
                while (serverSocketChannel.isOpen) {
                    try {
                        val clientChannel = serverSocketChannel.acceptAsync()
                        logger.info("accepted a new connection from client at ${clientChannel.remoteAddress}")
                        launch {
                            val coroutineName = CoroutineName("client_${clientChannel.remoteAddress}")
                            withContext(ioDispatcher + coroutineName) {
                                processClientChannel(clientChannel)
                            }
                        }
                    } catch (ex: Exception) {
                        logger.error("exception while accepting new connections", ex)
                    }
                }
            }
        }
    }

    private suspend fun processClientChannel(
        clientSocketChannel: AsynchronousSocketChannel
    ) = coroutineScope {
        val bufferChannel = Channel<ByteBuffer>(capacity = Channel.UNLIMITED, onBufferOverflow = BufferOverflow.SUSPEND)
        clientSocketChannel.use {
            try {
                val readCoroutine = launch {
                    withContext(ioDispatcher) {
                        read(clientSocketChannel, bufferChannel)
                    }
                }
                val writeCoroutine = launch {
                    withContext(ioDispatcher) {
                        write(clientSocketChannel, bufferChannel)
                    }
                }

                joinAll(readCoroutine, writeCoroutine)
            } catch (ex: CancellationException) {
                logger.warning("Coroutine ${coroutineContext[CoroutineName]} got cancelled.", ex)
                throw ex
            } catch (ex: IOException) {
                logger.error(
                    "I/O exception while processing client connection at ${clientSocketChannel.remoteAddress}",
                    ex
                )
            } catch (ex: Exception) {
                logger.error(
                    "unexpected exception while processing client connection at ${clientSocketChannel.remoteAddress}",
                    ex
                )
            }
        }
    }

    private suspend fun write(
        clientChannel: AsynchronousSocketChannel,
        channel: Channel<ByteBuffer>
    ) {
        for (buf in channel) {
            Utils.changeLetterCase(buf)
            while (buf.hasRemaining()) {
                buf.flip()
                val numsWritten = clientChannel.writeAsync(buf)
                if (numsWritten == -1) {
                    logger.info("client channel closed at ${clientChannel.remoteAddress}")
                    return
                }
            }
        }
    }

    private suspend fun read(
        clientChannel: AsynchronousSocketChannel,
        channel: Channel<ByteBuffer>
    ) {
        while (clientChannel.isOpen) {
            val buffer = ByteBuffer.allocate(80)
            val readBytes = clientChannel.readAsync(buffer)
            if (readBytes == -1) {
                logger.info("client at ${clientChannel.remoteAddress} closed the connection.")
                return
            }

            if (readBytes > 0) {
                channel.send(buffer)
            }
        }
    }

    private fun listen(address: InetSocketAddress, group: AsynchronousChannelGroup) = runCatching {
        AsynchronousServerSocketChannel.open(group)
            .bind(address)
            .setOption(StandardSocketOptions.SO_RCVBUF, SOCKET_RECIEVE_BUFFER_SIZE)

    }.onSuccess {
        logger.info("successfully bound to address ${address.hostName}:${address.port}")
    }.onFailure { exception ->
        logger.error("failed to bind to address ${address.hostName}:${address.port}", exception)
    }.getOrThrow()

    private fun setupLogger(): Logger {
        val logger = Logger.getLogger(AsyncServer.javaClass.canonicalName)
        logger.level = Level.ALL
        return logger
    }

    private fun Logger.error(message: String, throwable: Throwable) = log(Level.SEVERE, message, throwable)
    private fun Logger.warning(message: String, throwable: Throwable) = log(Level.WARNING, message, throwable)
}