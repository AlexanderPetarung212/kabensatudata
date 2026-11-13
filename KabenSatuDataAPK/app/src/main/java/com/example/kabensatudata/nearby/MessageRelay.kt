package com.example.kabensatudata.nearby

import android.util.Log

/**
 * Message relay handler with deduplication and TTL management.
 * Prevents infinite message loops in mesh topologies.
 */
class MessageRelay(private val maxCacheSize: Int = 1000) {

    private val seenIds = LinkedHashSet<String>()

    /**
     * Check if a message should be processed (not a duplicate).
     * Returns true if message is new, false if already seen.
     */
    fun shouldProcess(message: P2PMessage): Boolean {
        if (seenIds.contains(message.id)) {
            Log.d(TAG, "Duplicate message: ${message.id.take(8)}... type=${message.type}")
            return false
        }
        addToCache(message.id)
        return true
    }

    /**
     * Check if a message should be relayed to other peers.
     * Returns a new message with decremented TTL, or null if TTL exhausted.
     */
    fun prepareRelay(message: P2PMessage): P2PMessage? {
        if (message.ttl <= 0) {
            Log.d(TAG, "TTL exhausted for: ${message.id.take(8)}... type=${message.type}")
            return null
        }
        return message.copy(ttl = message.ttl - 1)
    }

    /**
     * Mark a locally-generated message as seen (to avoid processing our own relayed message).
     */
    fun markSeen(messageId: String) {
        addToCache(messageId)
    }

    private fun addToCache(id: String) {
        seenIds.add(id)
        // Evict oldest entries if cache is full
        while (seenIds.size > maxCacheSize) {
            val oldest = seenIds.first()
            seenIds.remove(oldest)
        }
    }

    fun clearCache() {
        seenIds.clear()
    }

    companion object {
        private const val TAG = "MessageRelay"
    }
}
