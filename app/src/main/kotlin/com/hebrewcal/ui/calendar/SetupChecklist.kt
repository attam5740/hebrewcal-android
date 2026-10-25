package com.hebrewcal.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** State of one checklist line. */
enum class SetupItem { NEEDED, DENIED, DONE }

/**
 * First-run checklist: notifications and location, each with its own Allow button, so a new
 * install can be ready in two taps. Shown once; everything here can be changed later in Settings.
 */
@Composable
fun SetupChecklist(
    hebrew: Boolean,
    notifications: SetupItem,
    location: SetupItem,
    dnd: SetupItem,
    city: String,
    cityDetect: CalendarViewModel.CityDetect,
    onNotifications: () -> Unit,
    onLocation: () -> Unit,
    onDnd: () -> Unit,
    onDone: () -> Unit
) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(24.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(Color(0xF0262626)).padding(20.dp)
        ) {
            Text(if (hebrew) "הגדרה מהירה" else "Quick setup", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (hebrew) "כמה אישורים כדי שהלוח יעבוד במלואו. אפשר לשנות בכל עת בהגדרות."
                else "A few permissions so everything works. You can change these any time in Settings.",
                color = INACTIVE, fontSize = 13.sp
            )
            Spacer(Modifier.height(16.dp))
            ChecklistRow(
                icon = "🔔",
                title = if (hebrew) "התראות" else "Notifications",
                detail = if (hebrew) "התאריך העברי וזמני היום במסך הנעילה" else "Hebrew date and zmanim on the lock screen",
                state = notifications, hebrew = hebrew, onAllow = onNotifications
            )
            Spacer(Modifier.height(12.dp))
            val locationDetail = when {
                location == SetupItem.DONE && city.isNotBlank() -> (if (hebrew) "העיר שלך: " else "Your city: ") + city
                location == SetupItem.DONE && cityDetect == CalendarViewModel.CityDetect.WORKING ->
                    if (hebrew) "מאתר את העיר…" else "Finding your city…"
                location == SetupItem.DONE && cityDetect == CalendarViewModel.CityDetect.FAILED ->
                    if (hebrew) "לא נמצאה עיר; בחר עיר בהגדרות" else "Couldn't find your city; choose one in Settings"
                else -> if (hebrew) "מוצא את העיר שלך לזמני היום, קשת השמש והמצפן. מיקום משוער בלבד."
                    else "Finds your city for zmanim, the sun arc and the compass. Approximate location only."
            }
            ChecklistRow(
                icon = "📍",
                title = if (hebrew) "מיקום" else "Location",
                detail = locationDetail,
                state = location, hebrew = hebrew, onAllow = onLocation
            )
            Spacer(Modifier.height(12.dp))
            ChecklistRow(
                icon = "🌙",
                title = if (hebrew) "עקיפת נא לא להפריע" else "Override Do Not Disturb",
                detail = if (hebrew) "התאריך וזמני היום נשארים גלויים גם במצב נא לא להפריע ומצב שינה. אופציונלי."
                    else "Keeps the date and zmanim visible during Do Not Disturb and Bedtime mode. Optional.",
                // Android has no prompt for this access; Allow opens its settings page.
                state = dnd, hebrew = hebrew, onAllow = onDnd
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                val allDone = notifications == SetupItem.DONE && location == SetupItem.DONE
                Text(
                    if (allDone) (if (hebrew) "סיום" else "Done") else (if (hebrew) "אחר כך" else "Later"),
                    color = ACTIVE_GOLD, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onDone() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ChecklistRow(icon: String, title: String, detail: String, state: SetupItem, hebrew: Boolean, onAllow: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 22.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = INACTIVE.copy(alpha = 0.75f), fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        when (state) {
            SetupItem.DONE -> Text("✓", color = ACTIVE_GOLD, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            else -> Text(
                // Once Android stops showing its dialog, the only way left is the app's system page.
                if (state == SetupItem.DENIED) (if (hebrew) "הגדרות" else "Settings") else (if (hebrew) "אפשר" else "Allow"),
                color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(ACTIVE_GOLD)
                    .clickable { onAllow() }.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}
