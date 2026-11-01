package com.shilapi.xcertplay.ecarx;

import com.shilapi.xcertplay.transport.BlockingDuplexByteStream;
import java.io.*;
import java.util.*;

/** BlockingDuplexByteStream contract: null=timeout, empty=EOF, IOException=failure. */
public final class BufferedDuplex implements BlockingDuplexByteStream {
  private final java.util.concurrent.atomic.AtomicBoolean socketClosed =
      new java.util.concurrent.atomic.AtomicBoolean();

  private void closeSocket() throws IOException {
    if (socketClosed.compareAndSet(false, true)) socket.close();
  }

  private final Object lock = new Object(), writes = new Object();
  private final InputStream input;
  private final OutputStream output;
  private final Closeable socket;
  private final ArrayDeque<byte[]> queue = new ArrayDeque<byte[]>();
  private int bytes;
  private boolean closed, eof;
  private IOException error;

  public BufferedDuplex(InputStream in, OutputStream out, Closeable socket) {
    this.input = in;
    this.output = out;
    this.socket = socket;
    Thread t =
        new Thread(
            new Runnable() {
              public void run() {
                readLoop();
              }
            },
            "diplay-goc449-reader");
    t.setDaemon(true);
    t.start();
  }

  private void readLoop() {
    try {
      byte[] buf = new byte[4096];
      for (; ; ) {
        synchronized (lock) {
          while (!closed && bytes >= 1048576) lock.wait();
          if (closed) return;
        }
        int n = input.read(buf);
        synchronized (lock) {
          if (closed) return;
          if (n < 0) {
            eof = true;
            lock.notifyAll();
            return;
          }
          if (n > 0) {
            queue.add(Arrays.copyOf(buf, n));
            bytes += n;
            lock.notifyAll();
          }
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      fail(new IOException("Reader interrupted", e));
    } catch (IOException e) {
      fail(e);
    }
  }

  private void fail(IOException e) {
    synchronized (lock) {
      if (!closed) error = e;
      lock.notifyAll();
    }
    try {
      closeSocket();
    } catch (IOException ignored) {
    }
  }

  public boolean isClosed() {
    synchronized (lock) {
      return closed || eof || error != null;
    }
  }

  public byte[] recv(int max, long timeout) throws IOException {
    if (max <= 0 || timeout < 0) throw new IllegalArgumentException();
    long start = System.nanoTime(),
        span = timeout > Long.MAX_VALUE / 1000000 ? Long.MAX_VALUE : timeout * 1000000;
    synchronized (lock) {
      for (; ; ) {
        if (!queue.isEmpty()) {
          byte[] b = queue.removeFirst();
          int n = Math.min(max, b.length);
          bytes -= n;
          if (n < b.length) queue.addFirst(Arrays.copyOfRange(b, n, b.length));
          lock.notifyAll();
          return n == b.length ? b : Arrays.copyOf(b, n);
        }
        if (error != null) throw error;
        if (closed || eof) return new byte[0];
        long left = span - (System.nanoTime() - start);
        if (left <= 0) return null;
        try {
          lock.wait(left / 1000000, (int) (left % 1000000));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IOException("GOC receive interrupted", e);
        }
      }
    }
  }

  public void send(byte[] data) throws IOException {
    synchronized (writes) {
      synchronized (lock) {
        if (error != null) throw error;
        if (closed || eof) throw new IOException("GOC closed");
      }
      try {
        output.write(data);
        output.flush();
      } catch (IOException e) {
        fail(e);
        throw e;
      }
    }
  }

  public void close() throws IOException {
    synchronized (lock) {
      if (closed) return;
      closed = true;
      queue.clear();
      bytes = 0;
      lock.notifyAll();
    }
    closeSocket();
  }
}
