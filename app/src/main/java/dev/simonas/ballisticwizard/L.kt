package dev.simonas.ballisticwizard

import android.util.Log

inline fun <reified T> T.ld(msg: String) {
    Log.d(this!!::class.simpleName, msg)
}