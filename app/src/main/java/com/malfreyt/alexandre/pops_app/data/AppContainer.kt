package com.malfreyt.alexandre.pops_app.data

import android.content.Context

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val settingsStore = SettingsStore(appContext)
    private val localStore = GradeLocalStore(appContext)

    val gradeRepository = GradeRepository(
        context = appContext,
        localStore = localStore,
        settingsStore = settingsStore,
        oasisRemoteDataSource = OasisRemoteDataSource(appContext),
    )
}