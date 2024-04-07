package dev.simonas.ballisticwizard

sealed class WizardServiceState {
    data object NotStarted : WizardServiceState()
    data object WaitingForDevice : WizardServiceState()
    data class Connected(
        val deviceData1: Int,
        val setData1: (Int) -> Unit,
        val deviceData2: Int,
        val setData2: (Int) -> Unit,
        val deviceData3: Int,
        val setData3: (Int) -> Unit,
    ) : WizardServiceState()

}