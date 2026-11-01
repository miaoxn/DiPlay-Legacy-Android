package com.shilapi.xcertplay.ecarx;

import java.io.*;
import java.util.*;
import org.junit.Test;

public class GocBufferedDuplexTest {
  static void check(boolean b, String m) {
    if (!b) throw new AssertionError(m);
  }

  @Test
  public void streamContract() throws Exception {
    final PipedInputStream in = new PipedInputStream(8192);
    final PipedOutputStream peer = new PipedOutputStream(in);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    BufferedDuplex d = new BufferedDuplex(in, out, in);
    check(d.recv(10, 25) == null, "idle timeout");
    peer.write(new byte[] {1, 2, 3, 4, 5});
    peer.flush();
    check(Arrays.equals(d.recv(2, 1000), new byte[] {1, 2}), "partial head");
    check(Arrays.equals(d.recv(10, 1000), new byte[] {3, 4, 5}), "partial tail");
    d.send(new byte[] {8, 9});
    check(Arrays.equals(out.toByteArray(), new byte[] {8, 9}), "raw send");
    peer.close();
    check(d.recv(100, 1000).length == 0, "EOF empty");
    d.close();
    d.close();
    final PipedInputStream in2 = new PipedInputStream();
    final PipedOutputStream p2 = new PipedOutputStream(in2);
    final BufferedDuplex d2 = new BufferedDuplex(in2, new ByteArrayOutputStream(), in2);
    final Throwable[] threadFailure = new Throwable[1];
    Thread waiting =
        new Thread(
            new Runnable() {
              public void run() {
                try {
                  check(d2.recv(100, 10000).length == 0, "close wakes receiver");
                } catch (Throwable e) {
                  threadFailure[0] = e;
                }
              }
            });
    waiting.start();
    Thread.sleep(30);
    d2.close();
    waiting.join(500);
    check(!waiting.isAlive(), "close bound");
    check(threadFailure[0] == null, "close receiver failed: " + threadFailure[0]);
    p2.close();
    final byte[] all = new byte[2100000];
    new Random(449).nextBytes(all);
    BufferedDuplex d3 =
        new BufferedDuplex(
            new ByteArrayInputStream(all),
            new ByteArrayOutputStream(),
            new Closeable() {
              public void close() {}
            });
    Thread.sleep(50);
    ByteArrayOutputStream got = new ByteArrayOutputStream();
    for (; ; ) {
      byte[] b = d3.recv(997, 1000);
      check(b != null, "backpressure stalled");
      if (b.length == 0) break;
      got.write(b);
    }
    check(Arrays.equals(all, got.toByteArray()), "bounded queue preserves all bytes");
    d3.close();
    BufferedDuplex d4 =
        new BufferedDuplex(
            new InputStream() {
              public int read() throws IOException {
                throw new IOException("injected");
              }
            },
            new ByteArrayOutputStream(),
            new Closeable() {
              public void close() {}
            });
    boolean failed = false;
    try {
      d4.recv(10, 1000);
    } catch (IOException e) {
      failed = e.getMessage().equals("injected");
    }
    check(failed, "error propagated");
    d4.close();
    System.out.println(
        "PASS timeout, partial reads, raw send, EOF, close wakeup, bounded queue, IOException");
  }
}
