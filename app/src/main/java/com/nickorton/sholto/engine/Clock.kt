package com.nickorton.sholto.engine

/** Monotonic time source in milliseconds. Real and fake versions arrive in M1. */
fun interface Clock {
    fun now(): Long
}
