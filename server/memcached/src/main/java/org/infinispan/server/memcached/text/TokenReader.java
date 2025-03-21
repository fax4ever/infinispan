package org.infinispan.server.memcached.text;

import java.util.BitSet;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.util.ByteProcessor;
import io.netty.util.Signal;

/**
 * Reads the next token from the buffer, accepting only valid characters. If a non-valid character is found, the
 * buffer is consumed to the end of the line and an {@link IllegalArgumentException} is thrown.
 */
public class TokenReader implements ByteProcessor {

   static final Signal INVALID_TOKEN = Signal.valueOf(TokenReader.class.getName() + ".INVALID");
   private final ByteBuf output;
   private BitSet token;
   private boolean found;
   private int bytesRead = 0;
   private int bytesAvailable;

   public TokenReader(ByteBuf output) {
      this.output = output;
   }

   public void release() {
      output.release();
   }

   public TokenReader forToken(BitSet token, int bytesAvailable) {
      found = false;
      bytesRead = 0;
      output.resetReaderIndex().resetWriterIndex();
      this.token = token;
      this.bytesAvailable = bytesAvailable;
      return this;
   }

   public ByteBuf output() {
      if (output.writerIndex() == 0) return null;
      return output.resetReaderIndex();
   }

   public int readBytesSize() {
      return bytesRead;
   }

   @Override
   public boolean process(byte b) throws Exception {
      if (found) return false;

      // A full buffer means the Memcached limit was reached.
      // This validation is done elsewhere, we can stop iterating.
      if (!output.isWritable()) return false;

      if (++bytesRead > bytesAvailable) {
         throw new TooLongFrameException("Request length exceeded");
      }

      // Found a space, means we found the complete token.
      // We go one byte ahead to skip the space.
      if (b == 32) {
         found = true;
         return true;
      }

      if (b == 10) return false;
      if (b == 13) {
         // If it has data, minus 1 and stop iterating;
         if (output.writerIndex() > 0) {
            bytesRead--;
            return false;
         }
         return true;
      }

      if (!token.get(b & 0xFF)) {
         bytesRead--;
         throw INVALID_TOKEN;
      }

      output.writeByte(b);
      return true;
   }
}
