package com.monosync.model

sealed class PlayerState {
    data object Idle : PlayerState()
    data object Buffering : PlayerState()
    data object Playing : PlayerState()
    data object Paused : PlayerState()
    data object Ended : PlayerState()
}
