package com.leaf.hyperdragshare.codex

/** Evidence states are kept separate from the user-facing conclusion. */
internal enum class ActivationEvidence {
    CHECKING,
    READY,
    UNAVAILABLE,
    UNKNOWN,
    NOT_APPLICABLE,
}

internal enum class ActivationState {
    CHECKING,
    NORMAL,
    NEEDS_ACTION,
    UNKNOWN,
    NOT_APPLICABLE,
}

internal fun ModuleActivation.RootAccessOutcome.evidence(): ActivationEvidence = when (this) {
    ModuleActivation.RootAccessOutcome.GRANTED -> ActivationEvidence.READY
    ModuleActivation.RootAccessOutcome.DENIED -> ActivationEvidence.UNAVAILABLE
    ModuleActivation.RootAccessOutcome.UNKNOWN -> ActivationEvidence.UNKNOWN
}

internal fun ModuleActivation.ReportedStatus.evidence(): ActivationEvidence = when (this) {
    ModuleActivation.ReportedStatus.READY -> ActivationEvidence.READY
    ModuleActivation.ReportedStatus.UNAVAILABLE -> ActivationEvidence.UNAVAILABLE
    ModuleActivation.ReportedStatus.UNKNOWN -> ActivationEvidence.UNKNOWN
}

internal fun activationState(evidence: Iterable<ActivationEvidence>): ActivationState {
    val applicable = evidence.filterNot { it == ActivationEvidence.NOT_APPLICABLE }
    if (applicable.isEmpty()) return ActivationState.NOT_APPLICABLE
    if (applicable.any { it == ActivationEvidence.UNAVAILABLE }) return ActivationState.NEEDS_ACTION
    if (applicable.any { it == ActivationEvidence.UNKNOWN }) return ActivationState.UNKNOWN
    if (applicable.any { it == ActivationEvidence.CHECKING }) return ActivationState.CHECKING
    return ActivationState.NORMAL
}

internal fun activationFeatureState(
    enabled: Boolean,
    dependencies: Iterable<ActivationEvidence>,
): ActivationState = if (enabled) activationState(dependencies) else ActivationState.NOT_APPLICABLE
