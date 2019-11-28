package com.waz.zclient.core.usecase

import io.reactivex.*
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable

abstract class CompletableUseCase<in Params>(private val subscribeScheduler: Scheduler,
                                                private val postExecutionScheduler: Scheduler) {

    private val disposables: CompositeDisposable = CompositeDisposable()

    abstract fun buildUseCaseCompletable(params: Params?): Completable

    fun execute(observer: CompletableObserver, params: Params? = null) {
        val observable: Completable = this.buildUseCaseCompletable(params)
            .subscribeOn(subscribeScheduler)
            .observeOn(postExecutionScheduler)
        (observable.subscribeWith(observer) as? Disposable)?.let {
            disposables.add(it)
        }
    }

    fun dispose() {
        disposables.clear()
    }
}
