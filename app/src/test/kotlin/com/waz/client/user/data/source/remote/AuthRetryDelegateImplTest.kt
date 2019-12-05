package com.waz.client.user.data.source.remote

import com.waz.zclient.user.data.source.remote.AuthRetryDelegateImpl
import io.reactivex.observers.TestObserver
import org.junit.Test


class AuthRetryDelegateImplTest {

    @Test
    fun `when new retry starts, emits true to retry observable`() {
        val retryDelegate = AuthRetryDelegateImpl()
        val testObserver: TestObserver<Boolean> = retryDelegate.getRetryObservable().test()

        //when
        retryDelegate.startRetryProcess()

        //then
        testObserver.assertValues(true)

        testObserver.dispose()
    }

    @Test
    fun `if retry is already started, does not emit new value to retry observable`() {
        val retryDelegate = AuthRetryDelegateImpl()
        val testObserver: TestObserver<Boolean> = retryDelegate.getRetryObservable().test()

        //given
        retryDelegate.startRetryProcess()

        //when
        retryDelegate.startRetryProcess()

        //then
        testObserver.assertValues(true) //only once, not twice

        testObserver.dispose()
    }

    @Test
    fun `when retry process is finished, emits false to retry observable`() {
        val retryDelegate = AuthRetryDelegateImpl()
        val testObserver: TestObserver<Boolean> = retryDelegate.getRetryObservable().test()

        //given
        retryDelegate.startRetryProcess()

        //when
        retryDelegate.onRetryFinished()

        //then
        testObserver.assertValues(true, false)

        testObserver.dispose()
    }
}
