package com.reink.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reink.data.remote.SubstackAuthInterceptor
import com.reink.data.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SignInStatus { Idle, Complete }

@HiltViewModel
class SubstackSignInViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val authInterceptor: SubstackAuthInterceptor,
) : ViewModel() {

    private val _status = MutableStateFlow(SignInStatus.Idle)
    val status: StateFlow<SignInStatus> = _status

    fun onSidDetected(sid: String) {
        viewModelScope.launch {
            preferencesRepository.setSubstackSid(sid)
            authInterceptor.clearCache()
            _status.value = SignInStatus.Complete
        }
    }
}
