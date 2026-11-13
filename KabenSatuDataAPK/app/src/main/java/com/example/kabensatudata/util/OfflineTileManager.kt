package com.example.kabensatudata.util

import android.content.Context
import android.util.Log
import org.maplibre.android.MapLibre
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.geometry.LatLng

/**
 * Manages offline map tile caching for MapLibre GL.
 * Pre-downloads vector map tiles for the work area so the map
 * works offline in the field.
 *
 * Uses MapLibre's built-in OfflineManager which supports:
 * - Vector tile caching (much smaller than raster)
 * - Automatic tile expiration management
 * - Multiple offline regions
 * - Progress tracking
 */
object OfflineTileManager {

    private const val TAG = "OfflineTileManager"

    // Zoom levels to cache (12-18 covers village to building level)
    private const val MIN_ZOOM = 12
    private const val MAX_ZOOM = 18

    // Style URLs
    private const val DEFAULT_STYLE = "https://tiles.openfreemap.org/styles/liberty"
    private const val DARK_STYLE = "https://tiles.openfreemap.org/styles/dark"

    /**
     * Get the current offline cache size in MB.
     */
    fun getCacheSizeMb(context: Context): Long {
        val cacheDir = context.cacheDir.resolve("maplibre")
        if (!cacheDir.exists()) return 0
        return cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } / (1024 * 1024)
    }

    /**
     * Download tiles for a specific bounding box area.
     * Uses MapLibre's OfflineManager for efficient vector tile caching.
     *
     * @param context Application context
     * @param styleUrl The map style URL to cache (default: Liberty)
     * @param north Northern latitude bound
     * @param east Eastern longitude bound
     * @param south Southern latitude bound
     * @param west Western longitude bound
     * @param regionName Human-readable name for the offline region
     * @param minZoom Minimum zoom level to cache
     * @param maxZoom Maximum zoom level to cache
     * @param onProgress Callback with (completed percentage 0-100)
     * @param onComplete Callback when download finishes (success: Boolean)
     */
    fun downloadTilesForArea(
        context: Context,
        styleUrl: String = DEFAULT_STYLE,
        north: Double,
        east: Double,
        south: Double,
        west: Double,
        regionName: String = "work-area",
        minZoom: Int = MIN_ZOOM,
        maxZoom: Int = MAX_ZOOM,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        onComplete: (Boolean) -> Unit = {}
    ) {
        try {
            val offlineManager = OfflineManager.getInstance(context)

            // Define the region bounds
            val bounds = LatLngBounds.Builder()
                .include(LatLng(north, east))
                .include(LatLng(south, west))
                .build()

            // Create the region definition
            val definition = OfflineTilePyramidRegionDefinition(
                styleUrl,
                bounds,
                minZoom.toDouble(),
                maxZoom.toDouble(),
                context.resources.displayMetrics.density
            )

            // Encode region name as metadata
            val metadata = regionName.toByteArray(Charsets.UTF_8)

            // Create the offline region
            offlineManager.createOfflineRegion(
                definition,
                metadata,
                object : OfflineManager.CreateOfflineRegionCallback {
                    override fun onCreate(offlineRegion: OfflineRegion) {
                        Log.i(TAG, "Offline region created: $regionName")

                        // Set up download observer
                        offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                            override fun onStatusChanged(status: OfflineRegionStatus) {
                                val percentage = if (status.requiredResourceCount > 0) {
                                    (status.completedResourceCount * 100 / status.requiredResourceCount).toInt()
                                } else 0

                                onProgress(percentage, 100)

                                if (status.isComplete) {
                                    Log.i(TAG, "Offline region download complete: $regionName")
                                    onComplete(true)
                                }
                            }

                            override fun onError(error: OfflineRegionError) {
                                Log.e(TAG, "Offline region error: ${error.reason} - ${error.message}")
                            }

                            override fun mapboxTileCountLimitExceeded(limit: Long) {
                                Log.w(TAG, "Tile count limit exceeded: $limit")
                            }
                        })

                        // Start the download
                        offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                    }

                    override fun onError(error: String) {
                        Log.e(TAG, "Error creating offline region: $error")
                        onComplete(false)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading tiles", e)
            onComplete(false)
        }
    }

    /**
     * Download both light and dark mode tiles for a specific area.
     * This ensures the map works offline regardless of the selected theme.
     */
    fun downloadAllStylesForArea(
        context: Context,
        north: Double,
        east: Double,
        south: Double,
        west: Double,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        onComplete: (Boolean) -> Unit = {}
    ) {
        // Download Liberty (default) style first
        downloadTilesForArea(
            context = context,
            styleUrl = DEFAULT_STYLE,
            north = north, east = east, south = south, west = west,
            regionName = "work-area-liberty",
            onProgress = { p, t -> onProgress(p / 2, t) },
            onComplete = { success1 ->
                if (success1) {
                    // Then download Dark style
                    downloadTilesForArea(
                        context = context,
                        styleUrl = DARK_STYLE,
                        north = north, east = east, south = south, west = west,
                        regionName = "work-area-dark",
                        onProgress = { p, t -> onProgress(50 + p / 2, t) },
                        onComplete = onComplete
                    )
                } else {
                    onComplete(false)
                }
            }
        )
    }

    /**
     * Create a bounding box around a center point with a given radius in km.
     * Returns (north, east, south, west).
     */
    fun createBoundingBox(centerLat: Double, centerLng: Double, radiusKm: Double): Array<Double> {
        val latDelta = radiusKm / 111.0 // ~111 km per degree latitude
        val lngDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(centerLat)))

        return arrayOf(
            centerLat + latDelta, // north
            centerLng + lngDelta, // east
            centerLat - latDelta, // south
            centerLng - lngDelta  // west
        )
    }

    /**
     * List all offline regions.
     */
    fun listRegions(
        context: Context,
        onResult: (List<Pair<String, Long>>) -> Unit
    ) {
        val offlineManager = OfflineManager.getInstance(context)
        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val regions = offlineRegions?.map { region ->
                    val name = try {
                        String(region.metadata, Charsets.UTF_8)
                    } catch (_: Exception) {
                        "Unknown"
                    }
                    Pair(name, region.id)
                } ?: emptyList()
                onResult(regions)
            }

            override fun onError(error: String) {
                Log.e(TAG, "Error listing offline regions: $error")
                onResult(emptyList())
            }
        })
    }

    /**
     * Delete a specific offline region by ID.
     */
    fun deleteRegion(
        context: Context,
        regionId: Long,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val offlineManager = OfflineManager.getInstance(context)
        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val region = offlineRegions?.find { it.id == regionId }
                if (region != null) {
                    region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                        override fun onDelete() {
                            Log.i(TAG, "Offline region deleted: $regionId")
                            onComplete(true)
                        }

                        override fun onError(error: String) {
                            Log.e(TAG, "Error deleting region: $error")
                            onComplete(false)
                        }
                    })
                } else {
                    onComplete(false)
                }
            }

            override fun onError(error: String) {
                Log.e(TAG, "Error listing regions for deletion: $error")
                onComplete(false)
            }
        })
    }

    /**
     * Clear all offline regions and cache.
     */
    fun clearCache(context: Context) {
        try {
            val offlineManager = OfflineManager.getInstance(context)
            offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                    offlineRegions?.forEach { region ->
                        region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                            override fun onDelete() {
                                Log.i(TAG, "Region ${region.id} deleted")
                            }
                            override fun onError(error: String) {
                                Log.e(TAG, "Error deleting region: $error")
                            }
                        })
                    }
                }
                override fun onError(error: String) {
                    Log.e(TAG, "Error clearing cache: $error")
                }
            })
            Log.i(TAG, "Cache clear initiated")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
        }
    }
}
