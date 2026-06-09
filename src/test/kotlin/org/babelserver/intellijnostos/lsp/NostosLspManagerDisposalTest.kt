package org.babelserver.intellijnostos.lsp

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Regression test for the disposal crash: the changeDebounce Alarm is created
 * via `by lazy`, so the first read constructs it and registers it as a child of
 * the manager. If the Alarm was never used during a session, that first read
 * happened inside stopServer() during dispose(), constructing it against an
 * already-disposing parent and throwing IncorrectOperationException on close.
 *
 * Note: calling stop() directly does NOT reproduce this — the manager is not in
 * the "disposing" state then. The crash only happens when the Disposer tears the
 * manager down, so the test registers it as a child and disposes the parent.
 */
class NostosLspManagerDisposalTest : BasePlatformTestCase() {

    fun testDisposingManagerWithUnusedAlarmDoesNotThrow() {
        val parent = Disposer.newDisposable("NostosLspManagerDisposalTest")
        // A throwaway manager instance whose changeDebounce Alarm is never touched.
        val manager = NostosLspServerManager(project)
        Disposer.register(parent, manager)

        // Before the fix this threw IncorrectOperationException because dispose()
        // -> stopServer() read the lazy Alarm and tried to register it under the
        // already-disposing manager. After the fix it is a no-op.
        Disposer.dispose(parent)
    }
}
