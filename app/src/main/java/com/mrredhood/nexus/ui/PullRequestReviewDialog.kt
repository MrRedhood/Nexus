package com.mrredhood.nexus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mrredhood.nexus.core.workspace.GitHubPullRequest
import com.mrredhood.nexus.core.workspace.GitHubPullRequestComment
import com.mrredhood.nexus.core.workspace.GitHubPullRequestReview
import com.mrredhood.nexus.core.workspace.GitHubPullRequestService
import kotlinx.coroutines.launch

@Composable
fun PullRequestReviewDialog(
    repository: String,
    pullRequest: GitHubPullRequest,
    token: String,
    service: GitHubPullRequestService,
    onDismiss: () -> Unit,
    onError: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var comments by remember { mutableStateOf<List<GitHubPullRequestComment>>(emptyList()) }
    var reviews by remember { mutableStateOf<List<GitHubPullRequestReview>>(emptyList()) }
    var commentText by remember { mutableStateOf("") }
    var reviewText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            loading = true
            runCatching {
                comments = service.comments(repository, pullRequest.number, token)
                reviews = service.reviews(repository, pullRequest.number, token)
            }.onFailure { onError(it.message ?: "Unable to load PR discussion") }
            loading = false
        }
    }

    LaunchedEffect(pullRequest.number) { reload() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review #${pullRequest.number}") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text("Review status: ${reviews.lastOrNull()?.state ?: "Not reviewed"}")
                }
                item {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Comment") },
                        minLines = 2
                    )
                }
                item {
                    Button(enabled = commentText.isNotBlank() && !loading, onClick = {
                        scope.launch {
                            loading = true
                            runCatching { service.addComment(repository, pullRequest.number, commentText, token) }
                                .onSuccess { commentText = ""; reload() }
                                .onFailure { onError(it.message ?: "Unable to add comment") }
                            loading = false
                        }
                    }) { Text("Add comment") }
                }
                item {
                    OutlinedTextField(
                        value = reviewText,
                        onValueChange = { reviewText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Review message") },
                        minLines = 2
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(enabled = !loading, onClick = {
                            scope.launch {
                                loading = true
                                runCatching { service.submitReview(repository, pullRequest.number, reviewText, "APPROVE", token) }
                                    .onSuccess { reviewText = ""; reload() }
                                    .onFailure { onError(it.message ?: "Unable to approve PR") }
                                loading = false
                            }
                        }) { Text("Approve") }
                        OutlinedButton(enabled = reviewText.isNotBlank() && !loading, onClick = {
                            scope.launch {
                                loading = true
                                runCatching { service.submitReview(repository, pullRequest.number, reviewText, "REQUEST_CHANGES", token) }
                                    .onSuccess { reviewText = ""; reload() }
                                    .onFailure { onError(it.message ?: "Unable to request changes") }
                                loading = false
                            }
                        }) { Text("Request changes") }
                        TextButton(enabled = !loading, onClick = {
                            scope.launch {
                                loading = true
                                runCatching { service.submitReview(repository, pullRequest.number, reviewText, "COMMENT", token) }
                                    .onSuccess { reviewText = ""; reload() }
                                    .onFailure { onError(it.message ?: "Unable to submit review") }
                                loading = false
                            }
                        }) { Text("Review comment") }
                    }
                }
                item { Text("Reviews") }
                items(reviews, key = { it.id }) { review ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("${review.author} · ${review.state}")
                        if (review.body.isNotBlank()) Text(review.body)
                    }
                }
                item { Text("Comments") }
                items(comments, key = { it.id }) { comment ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(comment.author)
                        Text(comment.body)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
