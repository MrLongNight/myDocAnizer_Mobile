package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.ui.DocAnizerViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class DocAnizerViewModelScanWorkflowTest {
    @Test
    fun newViewModelStartsIdleWithoutSavedDocumentOrError() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = DocAnizerViewModel(application)

        assertEquals("Idle", viewModel.scanWorkflowState.value)
        assertNull(viewModel.scanWorkflowError.value)
        assertNull(viewModel.lastBulkSavedDoc.value)
    }
}
