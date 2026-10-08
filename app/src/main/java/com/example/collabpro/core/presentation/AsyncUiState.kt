package com.example.collabpro.core.presentation

import com.example.collabpro.core.domain.ApiFailure

/** Pure state: previews can construct it without Hilt, Android services or a network. */
sealed interface AsyncUiState<out T> {
    data object Idle : AsyncUiState<Nothing>
    data object Loading : AsyncUiState<Nothing>
    data class Content<T>(val value: T) : AsyncUiState<T>
    data class Error(val failure: ApiFailure) : AsyncUiState<Nothing>
}
