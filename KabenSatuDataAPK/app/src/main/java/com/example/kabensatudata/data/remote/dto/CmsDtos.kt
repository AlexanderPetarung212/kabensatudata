package com.example.kabensatudata.data.remote.dto

import com.google.gson.annotations.SerializedName

// ─── Posts (Berita) ─────────────────────────────────────

data class PostDto(
    val id: Int,
    val title: String,
    val slug: String,
    val excerpt: String? = null,
    val content: String? = null,
    val author: String? = null,
    @SerializedName("featured_image_url") val featuredImageUrl: String? = null,
    @SerializedName("is_published") val isPublished: Boolean = false,
    @SerializedName("views_count") val viewsCount: Int = 0,
    val category: CategoryDto? = null,
    @SerializedName("published_at") val publishedAt: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class PostListResponse(
    val data: List<PostDto>,
    val meta: PaginationMeta
)

data class PostDetailResponse(
    val data: PostDto
)

// ─── Galleries (Galeri) ──────────────────────────────────

data class GalleryDto(
    val id: Int,
    val title: String,
    val description: String? = null,
    @SerializedName("image_url") val imageUrl: String,
    @SerializedName("event_date") val eventDate: String? = null,
    @SerializedName("is_published") val isPublished: Boolean = true,
    @SerializedName("sort_order") val sortOrder: Int = 0,
    val category: CategoryDto? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class GalleryListResponse(
    val data: List<GalleryDto>,
    val meta: PaginationMeta
)

data class GalleryBatchResponse(
    val message: String,
    val data: List<GalleryDto>
)

// ─── Messages (Pesan Masuk) ──────────────────────────────

data class MessageDto(
    val id: Int,
    val name: String,
    val phone: String? = null,
    val subject: String,
    val message: String,
    @SerializedName("is_read") val isRead: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null
)

data class MessageListResponse(
    val data: List<MessageDto>,
    val meta: PaginationMeta
)

data class MessageDetailResponse(
    val data: MessageDto
)

data class UnreadCountResponse(
    val count: Int
)

// ─── Categories ──────────────────────────────────────────

data class CategoryDto(
    val id: Int,
    val name: String,
    val slug: String? = null,
    val type: String? = null
)

data class CategoryListResponse(
    val data: List<CategoryDto>
)

// ─── Shared ──────────────────────────────────────────────

data class PaginationMeta(
    @SerializedName("current_page") val currentPage: Int,
    @SerializedName("last_page") val lastPage: Int,
    val total: Int
)

data class SimpleMessageResponse(
    val message: String
)
