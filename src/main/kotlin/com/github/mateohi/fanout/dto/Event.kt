package com.github.mateohi.fanout.dto

import java.time.Instant

data class Event(
        val id: String,
        val type: String,
        val timestamp: Instant,
        val payload: String,
)