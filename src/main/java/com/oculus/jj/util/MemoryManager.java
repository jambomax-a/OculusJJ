package com.oculus.jj.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Utility for managing off-heap memory (Direct ByteBuffers).
 * Provides a standard way to allocate and handle buffers for large volume data.
 */
public class MemoryManager {

    /**
     * Allocates a direct ByteBuffer of the specified size and byte order.
     * Direct buffers are allocated outside of the JVM heap, reducing GC pressure 
     * during high-frequency rendering operations.
     *
     * @param size The number of bytes to allocate.
     * @return A direct ByteBuffer.
     */
    public static ByteBuffer allocateDirectBuffer(int size) {
        return ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
    }

    /**
     * Helper to get the capacity of a buffer safely.
     */
    public static int getCapacity(ByteBuffer buffer) {
        if (buffer == null) return 0;
        return buffer.capacity();
    }
}
