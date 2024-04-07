package dev.simonas.ballisticwizard

import android.util.Log

inline fun <reified T> T.dlog(msg: String) {
    Log.d(this!!::class.simpleName, msg)
    LogsRepository.log("DEBUG: $msg")
}

inline fun <reified T> T.elog(msg: String) {
    Log.e(this!!::class.simpleName, msg)
    LogsRepository.log("ERR: $msg")
}