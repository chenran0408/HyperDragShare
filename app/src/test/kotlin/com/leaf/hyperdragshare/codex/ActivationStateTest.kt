package com.leaf.hyperdragshare.codex

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivationStateTest {
    @Test
    fun rootDenialAndProbeFailureHaveDifferentEvidence() {
        assertEquals(ActivationEvidence.READY, ModuleActivation.RootAccessOutcome.GRANTED.evidence())
        assertEquals(ActivationEvidence.UNAVAILABLE, ModuleActivation.RootAccessOutcome.DENIED.evidence())
        assertEquals(ActivationEvidence.UNKNOWN, ModuleActivation.RootAccessOutcome.UNKNOWN.evidence())
    }

    @Test
    fun distinguishesActionRequiredFromUnknownAndNotApplicable() {
        assertEquals(ActivationState.NEEDS_ACTION, activationState(listOf(ActivationEvidence.UNAVAILABLE)))
        assertEquals(ActivationState.UNKNOWN, activationState(listOf(ActivationEvidence.UNKNOWN)))
        assertEquals(ActivationState.NOT_APPLICABLE, activationState(listOf(ActivationEvidence.NOT_APPLICABLE)))
        assertEquals(ActivationState.CHECKING, activationState(listOf(ActivationEvidence.CHECKING)))
        assertEquals(ActivationState.NORMAL, activationState(listOf(ActivationEvidence.READY)))
    }

    @Test
    fun oneUnavailableFeatureDoesNotChangeAnotherFeature() {
        val module = ActivationEvidence.READY
        val rootInput = ActivationEvidence.UNAVAILABLE
        val pressReader = ActivationEvidence.READY

        val pressToSelect = activationFeatureState(true, listOf(module, rootInput, pressReader))
        val portalCopy = activationFeatureState(true, listOf(module))
        val disabledThumbTrigger = activationFeatureState(false, listOf(rootInput, pressReader))

        assertEquals(ActivationState.NEEDS_ACTION, pressToSelect)
        assertEquals(ActivationState.NORMAL, portalCopy)
        assertEquals(ActivationState.NOT_APPLICABLE, disabledThumbTrigger)
    }

    @Test
    fun unknownEvidenceIsNotReportedAsMissingPermission() {
        assertEquals(
            ActivationState.UNKNOWN,
            activationState(listOf(ActivationEvidence.READY, ActivationEvidence.UNKNOWN)),
        )
    }
}
