package com.mrredhood.nexus.domain.search

data class SearchQuery(
    val text: String,
    val connectorId: String? = null,
    val type: String? = null,
    val fromEpochMillis: Long? = null,
    val toEpochMillis: Long? = null
)

data class SearchResult(
    val id: String,
    val title: String,
    val snippet: String,
    val source: String,
    val type: String,
    val timestampEpochMillis: Long? = null
)

interface SearchRepository {
    suspend fun search(query: SearchQuery): Result<List<SearchResult>>
}
