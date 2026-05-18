package hssolutions.hscash.ui

import android.content.Context
import androidx.biometric.BiometricManager

object BiometricAuthHelper {
    fun canUseBiometric(context: Context): Boolean {
        return BiometricManager.from(context).canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
    }
}
