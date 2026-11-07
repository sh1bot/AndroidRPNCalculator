// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.os.SharedMemory;
import android.system.ErrnoException;
import android.system.OsConstants;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

/** Descriptor-only Binder transfer. Payload bytes never go through the filesystem. */
final class MemoryPayload implements AutoCloseable {
    private static final int LIMIT = 128 * 1024 * 1024;
    private final Parcelable handle;
    private final Closeable owner;
    private final int length;

    private MemoryPayload(Parcelable handle, Closeable owner, int length) {
        this.handle = handle;
        this.owner = owner;
        this.length = length;
    }

    static MemoryPayload create(byte[] bytes) throws IOException {
        if (bytes.length > LIMIT) throw new IOException("Calculation transfer is too large");
        return Build.VERSION.SDK_INT >= 27 ? Api27.create(bytes) : pipe(bytes);
    }

    // Android 8.0 has no SharedMemory API. Pipes are anonymous kernel buffers too.
    static MemoryPayload pipe(byte[] bytes) throws IOException {
        ParcelFileDescriptor[] ends = ParcelFileDescriptor.createPipe();
        Thread writer =
                new Thread(
                        () -> {
                            try (ParcelFileDescriptor.AutoCloseOutputStream out =
                                    new ParcelFileDescriptor.AutoCloseOutputStream(ends[1])) {
                                out.write(bytes);
                            } catch (IOException ignored) {
                                // The receiver may have cancelled and closed its end.
                            }
                        },
                        "calculation-transfer");
        writer.setDaemon(true);
        writer.start();
        return new MemoryPayload(ends[0], ends[0], bytes.length);
    }

    void put(Bundle bundle, String key) {
        bundle.putParcelable(key, handle);
        bundle.putInt(key + "Length", length);
    }

    static byte[] take(Bundle bundle, String key) throws IOException {
        Parcelable handle = bundle.getParcelable(key);
        bundle.remove(key);
        int length = bundle.getInt(key + "Length", -1);
        bundle.remove(key + "Length");
        if (handle == null) throw new IOException("Missing calculation transfer");
        try {
            if (length < 0 || length > LIMIT)
                throw new IOException("Invalid calculation transfer size");
            if (Build.VERSION.SDK_INT >= 27 && Api27.isMemory(handle))
                return Api27.read(handle, length);
            if (!(handle instanceof ParcelFileDescriptor))
                throw new IOException("Invalid calculation transfer");
            try (ParcelFileDescriptor.AutoCloseInputStream in =
                    new ParcelFileDescriptor.AutoCloseInputStream((ParcelFileDescriptor) handle)) {
                byte[] bytes = new byte[length];
                new java.io.DataInputStream(in).readFully(bytes);
                return bytes;
            }
        } finally {
            closeHandle(handle);
        }
    }

    static void discard(Bundle bundle, String key) {
        Parcelable handle = bundle.getParcelable(key);
        bundle.remove(key);
        closeHandle(handle);
    }

    private static void closeHandle(Parcelable handle) {
        if (handle instanceof Closeable) {
            try {
                ((Closeable) handle).close();
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void close() throws IOException {
        owner.close();
    }

    @android.annotation.TargetApi(27)
    private static final class Api27 {
        static MemoryPayload create(byte[] bytes) throws IOException {
            SharedMemory memory = null;
            try {
                memory = SharedMemory.create("calculator", Math.max(1, bytes.length));
                ByteBuffer mapping = memory.mapReadWrite();
                try {
                    mapping.put(bytes);
                } finally {
                    SharedMemory.unmap(mapping);
                }
                if (!memory.setProtect(OsConstants.PROT_READ))
                    throw new IOException("Could not protect calculation transfer");
                return new MemoryPayload(memory, memory, bytes.length);
            } catch (ErrnoException | IOException | RuntimeException ex) {
                if (memory != null) memory.close();
                throw new IOException("Could not allocate calculation transfer", ex);
            }
        }

        static boolean isMemory(Parcelable handle) {
            return handle instanceof SharedMemory;
        }

        static byte[] read(Parcelable handle, int length) throws IOException {
            SharedMemory memory = (SharedMemory) handle;
            if (memory.getSize() < length) throw new IOException("Truncated calculation transfer");
            try {
                ByteBuffer mapping = memory.mapReadOnly();
                try {
                    byte[] bytes = new byte[length];
                    mapping.get(bytes);
                    return bytes;
                } finally {
                    SharedMemory.unmap(mapping);
                }
            } catch (ErrnoException ex) {
                throw new IOException("Could not map calculation transfer", ex);
            }
        }
    }
}
