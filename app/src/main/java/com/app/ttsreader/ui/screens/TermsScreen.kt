package com.app.ttsreader.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.ttsreader.ui.components.GlassBackground

@Composable
fun TermsScreen(onAccept: () -> Unit) {
    var checked by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        GlassBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Terms & Conditions",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Please review and accept to continue.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
            Spacer(Modifier.height(20.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                color = Color.Black.copy(alpha = 0.45f),
                shape = RoundedCornerShape(16.dp)
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { SectionHeading("1. Acceptance of Terms") }
                    item { BodyText("By installing or using OmniLingo (\"the App\"), you agree to be bound by these Terms & Conditions. If you do not agree, do not use the App.") }

                    item { SectionHeading("2. License") }
                    item { BodyText("You are granted a personal, non-exclusive, non-transferable license to use the App on devices you own or control, solely for personal, non-commercial purposes.") }

                    item { SectionHeading("3. On-Device Processing & Privacy") }
                    item { BodyText("OmniLingo performs OCR, translation, summarization, and text-to-speech on your device. Camera frames and document contents are processed locally and are not transmitted to our servers. ML model files may be downloaded from Google ML Kit on first use.") }

                    item { SectionHeading("4. Camera & Storage Permissions") }
                    item { BodyText("Camera access is used solely for real-time text recognition. Storage access is used solely to import documents you choose. The App does not collect, store, or share captured images or document contents outside your device.") }

                    item { SectionHeading("5. User Content") }
                    item { BodyText("You retain all rights to any text, documents, or images you process through the App. You are solely responsible for ensuring you have the right to process such content.") }

                    item { SectionHeading("6. Disclaimer of Warranties") }
                    item { BodyText("The App is provided \"as is\" without warranty of any kind. OCR and translation outputs may be inaccurate; do not rely on them for safety-critical, legal, medical, or financial decisions.") }

                    item { SectionHeading("7. Limitation of Liability") }
                    item { BodyText("To the maximum extent permitted by law, the developers shall not be liable for any indirect, incidental, special, or consequential damages arising from your use of the App.") }

                    item { SectionHeading("8. Changes to Terms") }
                    item { BodyText("We may update these Terms from time to time. Continued use of the App after changes are posted constitutes acceptance of the revised Terms.") }

                    item { SectionHeading("9. Contact") }
                    item { BodyText("Questions about these Terms can be sent to the developer via the contact information listed on the Google Play Store listing.") }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { checked = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFF66FFD1),
                        uncheckedColor = Color.White.copy(alpha = 0.6f),
                        checkmarkColor = Color.Black
                    )
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = "I have read and agree to the Terms & Conditions.",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onAccept,
                enabled = checked,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF66FFD1),
                    contentColor = Color.Black,
                    disabledContainerColor = Color.White.copy(alpha = 0.18f),
                    disabledContentColor = Color.White.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = "Accept & Continue",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        color = Color(0xFF66FFD1),
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun BodyText(text: String) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.88f),
        fontSize = 13.sp,
        lineHeight = 19.sp
    )
}
