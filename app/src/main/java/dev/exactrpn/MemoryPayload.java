// SPDX-License-Identifier: Apache-2.0
package dev.exactrpn;

import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.SharedMemory;
import android.system.ErrnoException;
import android.system.OsConstants;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

/** Small transfers use Binder directly; larger ones use read-only shared memory. */
final class MemoryPayload implements AutoCloseable {
    static final int INLINE_LIMIT = 256 * 1024;
    private static final int LIMIT = 128 * 1024 * 1024;
    private final Parcelable handle;
    private final byte[] bytes;
    private final int length;

    private MemoryPayload(byte[] bytes, Parcelable handle, int length) {
        this.handle = handle;
        this.bytes = bytes;
        this.length = length;
    }

    static MemoryPayload create(byte[] bytes) throws IOException {
        if (bytes.length > LIMIT) throw new IOException("Calculation transfer is too large");
        if (bytes.length < INLINE_LIMIT) return new MemoryPayload(bytes, null, bytes.length);
        if (Build.VERSION.SDK_INT < 27)
            throw new IOException("Large calculation transfers require Android 8.1 or newer");
        return Api27.create(bytes);
    }

    void put(Bundle bundle, String key) {
        if (bytes != null) bundle.putByteArray(key, bytes);
        else {
            bundle.putParcelable(key, handle);
            bundle.putInt(key + "Length", length);
        }
    }

    static byte[] take(Bundle bundle, String key) throws IOException {
        Object data = bundle.get(key);
        bundle.remove(key);
        int length = bundle.getInt(key + "Length", -1);
        bundle.remove(key + "Length");
        try {
            if (data instanceof byte[]) {
                byte[] bytes = (byte[]) data;
                if (bytes.length > LIMIT)
                    throw new IOException("Invalid calculation transfer size");
                return bytes;
            }
            if (data == null) throw new IOException("Missing calculation transfer");
            if (length < 0 || length > LIMIT)
                throw new IOException("Invalid calculation transfer size");
            if (Build.VERSION.SDK_INT >= 27 && Api27.isMemory(data))
                return Api27.read((Parcelable) data, length);
            throw new IOException("Invalid calculation transfer");
        } finally {
            closeHandle(data);
        }
    }

    static void discard(Bundle bundle, String key) {
        Object data = bundle.get(key);
        bundle.remove(key);
        bundle.remove(key + "Length");
        closeHandle(data);
    }

    private static void closeHandle(Object handle) {
        if (handle instanceof Closeable) {
            try {
                ((Closeable) handle).close();
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void close() throws IOException {
        closeHandle(handle);
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
                return new MemoryPayload(null, memory, bytes.length);
            } catch (ErrnoException | IOException | RuntimeException ex) {
                if (memory != null) memory.close();
                throw new IOException("Could not allocate calculation transfer", ex);
            }
        }

        static boolean isMemory(Object handle) {
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
