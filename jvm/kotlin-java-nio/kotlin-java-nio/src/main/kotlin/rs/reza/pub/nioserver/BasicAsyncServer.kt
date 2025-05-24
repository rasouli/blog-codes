package rs.reza.pub.rs.reza.blog.nioserver

import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousChannelGroup
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.concurrent.Executors
import java.util.logging.Level
import java.util.logging.Logger

object BasicAsyncServer {
    private val logger = setupLogger()
    private val HOST = "localhost"
    private val PORT = 6070
    private val SOCKET_RECIEVE_BUFFER_SIZE = 1024

    @JvmStatic
    fun main(args: Array<String>) {
        val backingThreadPool = Executors.newFixedThreadPool(2)
        val asyncChannelGroup = AsynchronousChannelGroup.withThreadPool(backingThreadPool)
        val address = InetSocketAddress(HOST, PORT)
        val asyncServerSocketChannel = listen(address, asyncChannelGroup)
        asyncServerSocketChannel.use {
            ServerSocketChannelAttachment(it).acceptConnection()
            joinCurrentThread()
        }
    }

    private fun ServerSocketChannelAttachment.acceptConnection() = runCatching {
        asynchronousServerSocketChannel.accept(
            this,
            AcceptanceCompletionHandler
        )
    }.onFailure { exception ->
        logger.error("failed to register accept call back handler on server socket channel", exception)
    }.getOrThrow()

    class ServerSocketChannelAttachment(
        val asynchronousServerSocketChannel: AsynchronousServerSocketChannel
    )

    private class ClientSocketChannelAttachment(
        val clientAsyncChannel: AsynchronousSocketChannel,
        var inReadMode: Boolean = true,
        val buffer: ByteBuffer = ByteBuffer.allocate(1024)
    )

    private object AcceptanceCompletionHandler :
        CompletionHandler<AsynchronousSocketChannel, ServerSocketChannelAttachment> {
        override fun completed(
            clientSocketChannel: AsynchronousSocketChannel,
            asyncServerSocketAttachment: ServerSocketChannelAttachment
        ) {
            logger.info("got a connection from ${clientSocketChannel.remoteAddress}")
            asyncServerSocketAttachment.acceptConnection()
            ClientSocketChannelAttachment(clientAsyncChannel = clientSocketChannel).read()
        }

        override fun failed(exc: Throwable, attachment: ServerSocketChannelAttachment) {
            logger.error("accept handler failed with exception", exc)
        }
    }

    private fun ClientSocketChannelAttachment.read() = runCatching {
        clientAsyncChannel.read(
            buffer,
            this,
            ReadWriteCompletionHandler
        )
    }.onFailure { exception ->
        logger.error("failed to register read completion handler on client socket channel", exception)
    }.getOrThrow()

    private fun ClientSocketChannelAttachment.write() = runCatching {
        clientAsyncChannel.write(
            buffer,
            this,
            ReadWriteCompletionHandler
        )
    }.onFailure { exception ->
        logger.error("failed to register write completion handler on client socket channel", exception)
    }.getOrThrow()

    private object ReadWriteCompletionHandler : CompletionHandler<Int, ClientSocketChannelAttachment> {
        override fun completed(result: Int, attachment: ClientSocketChannelAttachment) {
            if (result == -1) {
                logger.info("client at  ${attachment.clientAsyncChannel.remoteAddress} disconnected")
                attachment.close()
                return
            }

            if (attachment.inReadMode) {
                // if reading 0 bytes, just register read callback.
                if (result == 0) {
                    attachment.inReadMode = true
                    attachment.read()
                    return
                }

                // in a different case, we would then process the data in the buffer.
                attachment.inReadMode = false
                attachment.buffer.flip()
                Utils.changeLetterCase(attachment.buffer)
                attachment.write()
            } else {
                // while not in read mode, since there is data in the buffer, we keep writing
                // until the buffer is empty.
                if (attachment.buffer.hasRemaining()) {
                    attachment.write()
                } else {
                    // since there is no data remaining in the buffer, we are now, ready to switch to read mode.
                    attachment.inReadMode = true
                    attachment.buffer.clear()
                    attachment.read()
                }
            }
        }

        override fun failed(exc: Throwable, attachment: ClientSocketChannelAttachment) {
            val op = if (attachment.inReadMode) "read" else "write"
            logger.error("$op failed for client: ${attachment.clientAsyncChannel.remoteAddress}", exc)
            attachment.close()
        }

        private fun ClientSocketChannelAttachment.close() = runCatching {
            clientAsyncChannel.close()
        }.onFailure { exception ->
            logger.error("IO exception while closing client connection", exception)
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

    private fun joinCurrentThread() = runCatching {
        Thread.currentThread().join()
    }.onFailure { exception: Throwable ->
        logger.error("main thread interrupted with exception", exception)
    }.getOrThrow()

    private fun setupLogger(): Logger {
        val logger = Logger.getLogger(BasicAsyncServer.javaClass.canonicalName)
        logger.level = Level.ALL
        return logger
    }

    private fun Logger.error(message: String, throwable: Throwable) = log(Level.SEVERE, message, throwable)
}