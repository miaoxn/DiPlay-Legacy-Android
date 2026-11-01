package com.shilapi.xcertplay.ecarx;

import android.content.*;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;

/** 449 firmware INfCommandBluetooth Binder ABI; only query and callback operations. */
public final class FactoryDevices {
  private static final String COMMAND = "com.nforetek.bt.aidl.INfCommandBluetooth",
      CALLBACK = "com.nforetek.bt.aidl.INfCallbackBluetooth";
  private static volatile IBinder remote;
  private static boolean binding;
  private static final Object bound = new Object();
  // Keep the existing vendor service binding for this app process. Dropping its last
  // binding can call NfServiceBluetooth.onDestroy -> GocsdkService.destroy().
  private static final ServiceConnection connection =
      new ServiceConnection() {
        public void onServiceConnected(ComponentName n, IBinder b) {
          synchronized (bound) {
            remote = b;
            binding = false;
            bound.notifyAll();
          }
        }

        public void onServiceDisconnected(ComponentName n) {
          synchronized (bound) {
            remote = null;
            binding = false;
            bound.notifyAll();
          }
        }
      };

  public static final class Device {
    public final String address, name;

    Device(String a, String n) {
      address = a;
      name = n;
    }

    public String toString() {
      return name + "\n" + address;
    }
  }

  public static final class Result {
    public final List<Device> devices;
    public final String local;

    Result(List<Device> d, String l) {
      devices = d;
      local = l;
    }
  }

  public static List<Device> normalize(int count, String[] addresses, String[] names) {
    LinkedHashMap<String, Device> unique = new LinkedHashMap<String, Device>();
    if (addresses != null)
      for (int i = 0; i < Math.min(Math.max(count, 0), Math.min(addresses.length, 256)); i++) {
        try {
          String a = GocFirmware.mac(addresses[i]);
          String n =
              names != null && i < names.length && names[i] != null && !names[i].trim().isEmpty()
                  ? names[i].trim()
                  : "未命名设备";
          unique.put(a, new Device(a, n));
        } catch (IllegalArgumentException e) {
        } catch (NullPointerException e) {
        }
      }
    List<Device> list = new ArrayList<Device>(unique.values());
    Collections.sort(
        list,
        new Comparator<Device>() {
          public int compare(Device a, Device b) {
            return a.address.compareTo(b.address);
          }
        });
    return list;
  }

  private static IBinder service(final Context context) throws Exception {
    synchronized (bound) {
      if (remote != null && remote.isBinderAlive()) return remote;
      remote = null;
      if (!binding) {
        binding = true;
        new Handler(Looper.getMainLooper())
            .post(
                new Runnable() {
                  public void run() {
                    boolean ok = false;
                    try {
                      ok =
                          context
                              .getApplicationContext()
                              .bindService(
                                  new Intent()
                                      .setComponent(
                                          new ComponentName(
                                              "com.nforetek.bt",
                                              "com.nforetek.bt.service.NfServiceBluetooth")),
                                  connection,
                                  0);
                    } catch (Exception ignored) {
                    }
                    if (!ok)
                      synchronized (bound) {
                        binding = false;
                        bound.notifyAll();
                      }
                  }
                });
      }
      long end = SystemClock.elapsedRealtime() + 8000;
      while (remote == null && binding) {
        long left = end - SystemClock.elapsedRealtime();
        if (left <= 0) break;
        bound.wait(left);
      }
      if (remote == null) throw new java.io.IOException("原厂蓝牙服务未就绪，请先在原厂设置开启蓝牙后重试");
      return remote;
    }
  }

  private static boolean command(IBinder b, int code, IBinder cb) throws Exception {
    Parcel d = Parcel.obtain(), r = Parcel.obtain();
    try {
      d.writeInterfaceToken(COMMAND);
      if (cb != null) d.writeStrongBinder(cb);
      if (!b.transact(code, d, r, 0)) throw new java.io.IOException("原厂蓝牙接口不匹配");
      r.readException();
      return r.readInt() != 0;
    } finally {
      d.recycle();
      r.recycle();
    }
  }

  private static String local(IBinder b) throws Exception {
    Parcel d = Parcel.obtain(), r = Parcel.obtain();
    try {
      d.writeInterfaceToken(COMMAND);
      if (!b.transact(14, d, r, 0)) throw new java.io.IOException("无法读取车机地址");
      r.readException();
      return r.readString();
    } finally {
      d.recycle();
      r.recycle();
    }
  }

  private static final java.util.concurrent.atomic.AtomicBoolean querying =
      new java.util.concurrent.atomic.AtomicBoolean();

  public static Result read(final Context c) throws Exception {
    if (!querying.compareAndSet(false, true)) throw new java.io.IOException("原厂蓝牙服务仍在响应上一请求，请稍后重试");
    FutureTask<Result> task =
        new FutureTask<Result>(
            new Callable<Result>() {
              public Result call() throws Exception {
                try {
                  return readInternal(c.getApplicationContext());
                } finally {
                  querying.set(false);
                }
              }
            });
    Thread worker = new Thread(task, "Goc449Binder");
    worker.setDaemon(true);
    worker.start();
    try {
      return task.get(24, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      throw new java.io.IOException("原厂蓝牙服务响应超时，界面已恢复；请检查原厂蓝牙状态");
    } catch (ExecutionException e) {
      Throwable t = e.getCause();
      if (t instanceof Exception) throw (Exception) t;
      throw new java.io.IOException(t);
    }
  }

  private static Result readInternal(Context c) throws Exception {
    if (Build.VERSION.SDK_INT != 22 || !"xea406m".equals(Build.DEVICE))
      throw new java.io.IOException("此读取接口仅适用于 449 车机");

    final java.util.concurrent.atomic.AtomicBoolean requested =
        new java.util.concurrent.atomic.AtomicBoolean();
    final CountDownLatch done = new CountDownLatch(1);
    final java.util.concurrent.atomic.AtomicReference<List<Device>> result =
        new java.util.concurrent.atomic.AtomicReference<>();
    Binder callback =
        new Binder() {
          protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
              throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
              if (reply != null) reply.writeString(CALLBACK);
              return true;
            }
            if (code >= 1 && code <= 14) {
              data.enforceInterface(CALLBACK);
              if (code == 6) {
                int count = data.readInt();
                String[] addresses = data.createStringArray(), names = data.createStringArray();
                data.createIntArray();
                data.createByteArray();
                if (requested.get()) {
                  result.set(normalize(count, addresses, names));
                  done.countDown();
                }
              }
              if (reply != null) reply.writeNoException();
              return true;
            }
            return super.onTransact(code, data, reply, flags);
          }
        };
    IBinder b = service(c);
    if (!COMMAND.equals(b.getInterfaceDescriptor()))
      throw new java.io.IOException("Unexpected factory Bluetooth interface");
    boolean registered = false;
    try {
      registered = command(b, 2, callback);
      if (!registered) throw new java.io.IOException("原厂蓝牙回调注册失败");
      requested.set(true);
      if (!command(b, 11, null)) throw new java.io.IOException("原厂配对列表请求失败");
      if (!done.await(12, TimeUnit.SECONDS)) throw new java.io.IOException("读取原厂配对列表超时，请重试");
      String address = "";
      try {
        address = GocFirmware.mac(local(b));
      } catch (IllegalArgumentException ignored) {
      } catch (NullPointerException ignored) {
      }
      return new Result(result.get(), address);
    } finally {
      if (registered)
        try {
          command(b, 3, callback);
        } catch (Exception ignored) {
        }
    }
  }
}
