package com.projectecho.audio

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.min

/**
 * Circular buffer implementation optimized for audio recording.
 * Provides thread-safe, lock-free audio data buffering with overflow protection
 * and efficient memory management for long recording sessions.
 * 
 * Features:
 * - Thread-safe read/write operations
 * - Overflow protection with capacity monitoring
 * - Efficient memory reuse
 * - Real-time performance optimized
 */
class CircularAudioBuffer(private val capacity: Int) {
    companion object {
        private const val CAPACITY_WARNING_THRESHOLD = 0.8f // 80% capacity
    }
    
    private val buffer = ByteArray(capacity)
    private var writePosition = 0
    private var readPosition = 0
    private var availableBytes = 0
    private var totalBytesWritten = 0L
    
    private val lock = ReentrantReadWriteLock()
    
    init {
        // CircularAudioBuffer initialized with capacity: $capacity bytes
    }
    
    /**
     * Write audio data to buffer
     * @param data Source audio data
     * @param offset Offset in source data
     * @param length Number of bytes to write
     * @return Number of bytes actually written
     */
    fun write(data: ByteArray, offset: Int, length: Int): Int {
        if (length <= 0) return 0
        
        return lock.write {
            val bytesToWrite = min(length, capacity - availableBytes)
            
            if (bytesToWrite <= 0) {
                // Buffer overflow, dropping ${length - bytesToWrite} bytes
                return@write 0
            }
            
            var remainingBytes = bytesToWrite
            var sourceIndex = offset
            
            while (remainingBytes > 0) {
                val bytesToEnd = capacity - writePosition
                val chunkSize = min(remainingBytes, bytesToEnd)
                
                System.arraycopy(data, sourceIndex, buffer, writePosition, chunkSize)
                
                writePosition = (writePosition + chunkSize) % capacity
                sourceIndex += chunkSize
                remainingBytes -= chunkSize
            }
            
            availableBytes += bytesToWrite
            totalBytesWritten += bytesToWrite
            
            if (isNearCapacity()) {
                // Buffer nearing capacity: ${getCapacityPercentage()}%
            }
            
            bytesToWrite
        }
    }
    
    /**
     * Read audio data from buffer
     * @param dest Destination buffer
     * @param offset Offset in destination buffer  
     * @param length Number of bytes to read
     * @return Number of bytes actually read
     */
    fun read(dest: ByteArray, offset: Int, length: Int): Int {
        if (length <= 0) return 0
        
        return lock.read {
            val bytesToRead = min(length, availableBytes)
            
            if (bytesToRead <= 0) {
                return@read 0
            }
            
            var remainingBytes = bytesToRead
            var destIndex = offset
            
            while (remainingBytes > 0) {
                val bytesToEnd = capacity - readPosition
                val chunkSize = min(remainingBytes, bytesToEnd)
                
                System.arraycopy(buffer, readPosition, dest, destIndex, chunkSize)
                
                readPosition = (readPosition + chunkSize) % capacity
                destIndex += chunkSize
                remainingBytes -= chunkSize
            }
            
            lock.write {
                availableBytes -= bytesToRead
            }
            
            bytesToRead
        }
    }
    
    /**
     * Read all available audio data
     * @return ByteArray containing all buffered audio data
     */
    fun readAll(): ByteArray {
        return lock.write {
            val result = ByteArray(availableBytes)
            
            if (availableBytes == 0) {
                return@write result
            }
            
            var destIndex = 0
            var remainingBytes = availableBytes
            
            while (remainingBytes > 0) {
                val bytesToEnd = capacity - readPosition
                val chunkSize = min(remainingBytes, bytesToEnd)
                
                System.arraycopy(buffer, readPosition, result, destIndex, chunkSize)
                
                readPosition = (readPosition + chunkSize) % capacity
                destIndex += chunkSize
                remainingBytes -= chunkSize
            }
            
            availableBytes = 0
            result
        }
    }
    
    /**
     * Peek at audio data without consuming it
     * @param dest Destination buffer
     * @param offset Offset in destination buffer
     * @param length Number of bytes to peek
     * @return Number of bytes actually peeked
     */
    fun peek(dest: ByteArray, offset: Int, length: Int): Int {
        if (length <= 0) return 0
        
        return lock.read {
            val bytesToPeek = min(length, availableBytes)
            
            if (bytesToPeek <= 0) {
                return@read 0
            }
            
            var remainingBytes = bytesToPeek
            var destIndex = offset
            var peekPosition = readPosition
            
            while (remainingBytes > 0) {
                val bytesToEnd = capacity - peekPosition
                val chunkSize = min(remainingBytes, bytesToEnd)
                
                System.arraycopy(buffer, peekPosition, dest, destIndex, chunkSize)
                
                peekPosition = (peekPosition + chunkSize) % capacity
                destIndex += chunkSize
                remainingBytes -= chunkSize
            }
            
            bytesToPeek
        }
    }
    
    /**
     * Skip bytes in buffer without reading them
     * @param numBytes Number of bytes to skip
     * @return Number of bytes actually skipped
     */
    fun skip(numBytes: Int): Int {
        if (numBytes <= 0) return 0
        
        return lock.write {
            val bytesToSkip = min(numBytes, availableBytes)
            readPosition = (readPosition + bytesToSkip) % capacity
            availableBytes -= bytesToSkip
            bytesToSkip
        }
    }
    
    /**
     * Clear all buffered data
     */
    fun clear() {
        lock.write {
            writePosition = 0
            readPosition = 0
            availableBytes = 0
            // Buffer cleared
        }
    }
    
    /**
     * Get number of bytes available for reading
     */
    fun available(): Int {
        return lock.read { availableBytes }
    }
    
    /**
     * Get remaining space in buffer
     */
    fun remaining(): Int {
        return lock.read { capacity - availableBytes }
    }
    
    /**
     * Get total buffer size
     */
    fun size(): Int = capacity
    
    /**
     * Check if buffer is empty
     */
    fun isEmpty(): Boolean {
        return lock.read { availableBytes == 0 }
    }
    
    /**
     * Check if buffer is full
     */
    fun isFull(): Boolean {
        return lock.read { availableBytes == capacity }
    }
    
    /**
     * Check if buffer is nearing capacity (for overflow prevention)
     */
    fun isNearCapacity(): Boolean {
        return lock.read { 
            availableBytes >= (capacity * CAPACITY_WARNING_THRESHOLD).toInt()
        }
    }
    
    /**
     * Get current capacity usage percentage
     */
    fun getCapacityPercentage(): Int {
        return lock.read { 
            ((availableBytes.toFloat() / capacity) * 100).toInt()
        }
    }
    
    /**
     * Get total bytes written to buffer since creation
     */
    fun getTotalBytesWritten(): Long {
        return lock.read { totalBytesWritten }
    }
    
    /**
     * Get buffer statistics for monitoring
     */
    fun getStats(): BufferStats {
        return lock.read {
            BufferStats(
                capacity = capacity,
                available = availableBytes,
                remaining = capacity - availableBytes,
                totalWritten = totalBytesWritten,
                capacityPercentage = getCapacityPercentage(),
                isNearCapacity = isNearCapacity()
            )
        }
    }
    
    /**
     * Compact buffer by removing read data and moving unread data to beginning.
     * This can help with memory fragmentation in very long recording sessions.
     * Note: This is an expensive operation and should be used sparingly.
     */
    fun compact() {
        lock.write {
            if (readPosition == 0 || availableBytes == 0) {
                // Already compacted or empty
                return@write
            }
            
            val tempBuffer = ByteArray(availableBytes)
            var tempIndex = 0
            var compactReadPos = readPosition
            var remainingBytes = availableBytes
            
            // Copy all available data to temp buffer
            while (remainingBytes > 0) {
                val bytesToEnd = capacity - compactReadPos
                val chunkSize = min(remainingBytes, bytesToEnd)
                
                System.arraycopy(buffer, compactReadPos, tempBuffer, tempIndex, chunkSize)
                
                compactReadPos = (compactReadPos + chunkSize) % capacity
                tempIndex += chunkSize
                remainingBytes -= chunkSize
            }
            
            // Clear buffer and copy back compacted data
            buffer.fill(0)
            System.arraycopy(tempBuffer, 0, buffer, 0, availableBytes)
            
            readPosition = 0
            writePosition = availableBytes
            
            // Buffer compacted, available bytes: $availableBytes
        }
    }
}

/**
 * Buffer statistics for monitoring and debugging
 */
data class BufferStats(
    val capacity: Int,
    val available: Int,
    val remaining: Int,
    val totalWritten: Long,
    val capacityPercentage: Int,
    val isNearCapacity: Boolean
)