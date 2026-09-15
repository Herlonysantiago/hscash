package hssolutions.hscash.ui

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun hsCashOutlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color(0xFF111827),
    unfocusedTextColor = Color(0xFF111827),
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedBorderColor = Color(0xFF1565C0),
    unfocusedBorderColor = Color(0xFF64748B),
    focusedLabelColor = Color(0xFF0B3D6E),
    unfocusedLabelColor = Color(0xFF334155),
    cursorColor = Color(0xFF1565C0)
)
