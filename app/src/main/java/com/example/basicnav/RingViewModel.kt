package com.example.basicnav

import android.content.Context
import androidx.lifecycle.ViewModel

class RingViewModel(context: Context) : ViewModel() {
    val manager = SimpleRingManager(context.applicationContext)

    override fun onCleared() {
        super.onCleared()
        manager.disconnect()
    }
}
