package rs.reza.pub.rs.reza.blog.nioserver

import java.nio.ByteBuffer
import kotlin.experimental.xor

object Utils {
     private const val SPACE_BYTE = ' '.code.toByte()
     fun changeLetterCase(buffer: ByteBuffer) {
        for (pos in 0..<buffer.limit()) {
            val char = buffer.get(pos).toInt().toChar()
            if (char.isLetter()) {
                buffer.put(
                    pos,
                    char.code.toByte().xor(SPACE_BYTE)
                )
            }
        }
    }
}