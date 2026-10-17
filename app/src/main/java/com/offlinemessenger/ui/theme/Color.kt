package com.offlinemessenger.ui.theme

import androidx.compose.ui.graphics.Color

// iOS dark-mode palette (Cupertino feel)
val IOSBlack = Color(0xFF000000) // system background
val IOSGray6 = Color(0xFF1C1C1E) // secondary background / cards
val IOSGray5 = Color(0xFF2C2C2E) // grouped / incoming bubble
val IOSGray4 = Color(0xFF3A3A3C)
val IOSBlue = Color(0xFF0A84FF) // primary accent
val IOSBlueDim = Color(0xFF0A84FF).copy(alpha = 0.18f)
val IOSGreen = Color(0xFF30D158) // success / connected
val IOSRed = Color(0xFFFF453A) // destructive / error
val IOSOrange = Color(0xFFFF9F0A) // warning
val IOSText = Color(0xFFFFFFFF) // primary text
val IOSSecondary = Color(0xFF98989F) // secondary text
val IOSTertiary = Color(0xFF6E6E73) // tertiary text

// Message bubbles (iMessage style)
val BubbleOut = IOSBlue
val BubbleIn = IOSGray5

// Status
val AmberWarn = IOSOrange
val RedError = IOSRed
val ReadBlue = Color(0xFF53BDEB)

// Legacy aliases (being phased out)
val Charcoal950 = IOSBlack
val Charcoal900 = IOSBlack
val Charcoal800 = IOSGray6
val Charcoal700 = IOSGray5
val Charcoal600 = IOSGray4
val Emerald500 = IOSBlue
val Emerald400 = IOSBlue
val Emerald900 = IOSBlueDim
val InkHigh = IOSText
val InkMid = IOSSecondary
val InkLow = IOSTertiary
