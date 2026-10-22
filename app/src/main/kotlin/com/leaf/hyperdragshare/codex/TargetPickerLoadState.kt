package com.leaf.hyperdragshare.codex

/** Keeps the last usable target snapshot visible while a catalog refresh is in progress. */
internal data class TargetPickerLoadState(
    val rows: List<GestureTargetRow> = emptyList(),
    val refreshing: Boolean = true,
    val error: TargetPickerLoadError? = null,
) {
    fun beginRefresh(): TargetPickerLoadState = copy(refreshing = true, error = null)

    fun loaded(rows: List<GestureTargetRow>): TargetPickerLoadState =
        copy(rows = rows, refreshing = false, error = null)

    fun failed(): TargetPickerLoadState =
        copy(refreshing = false, error = TargetPickerLoadError.UNAVAILABLE)
}

internal enum class TargetPickerLoadError {
    UNAVAILABLE,
}
