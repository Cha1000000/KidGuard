package ru.homelab.kidguard.ui.theme

import androidx.compose.ui.graphics.Color

// Фирменная палитра KidGuard (на основе иконки: сине-зелёный + золото).

// Светлая тема (по макету)
val LightPrimary = Color(0xFF2E6B7E)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFDCEDF2)
val LightOnPrimaryContainer = Color(0xFF0B2E37)
val LightSecondary = Color(0xFF4E7A88)
val LightSecondaryContainer = Color(0xFFDCEDF2)
val LightOnSecondaryContainer = Color(0xFF0B2E37)
val LightTertiary = Color(0xFFB07D1E)
val LightTertiaryContainer = Color(0xFFF7EBD2)
val LightOnTertiaryContainer = Color(0xFF4A3609)
val LightBackground = Color(0xFFEEF4F6)
val LightSurface = Color(0xFFF6FAFB)
val LightSurfaceVariant = Color(0xFFDDE7EA)
// Голубые surface-контейнеры (панель навигации, карточки) — в тон теме.
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFE9F1F4)
val LightSurfaceContainer = Color(0xFFDCEAEF)
val LightSurfaceContainerHigh = Color(0xFFD2E3E9)
val LightSurfaceContainerHighest = Color(0xFFC9DDE4)
val LightOnBackground = Color(0xFF16232A)
val LightOnSurface = Color(0xFF16232A)
val LightOnSurfaceVariant = Color(0xFF5B6B72)

// Тёмная тема (вариант A — Deep Teal)
val DarkPrimary = Color(0xFF4FC3B0)
val DarkOnPrimary = Color(0xFF04312B)
val DarkPrimaryContainer = Color(0xFF0E3B33)
val DarkOnPrimaryContainer = Color(0xFFB6EDE3)
val DarkSecondary = Color(0xFF6FB3A8)
val DarkSecondaryContainer = Color(0xFF0E3B33)
val DarkOnSecondaryContainer = Color(0xFF4FC3B0)
val DarkTertiary = Color(0xFFE0A73C)
val DarkTertiaryContainer = Color(0xFF473311)
val DarkOnTertiaryContainer = Color(0xFFF5D58C)
val DarkBackground = Color(0xFF0E1C21)
val DarkSurface = Color(0xFF17282E)
val DarkSurfaceVariant = Color(0xFF1E353C)
val DarkOnBackground = Color(0xFFE6EEF0)
val DarkOnSurface = Color(0xFFE6EEF0)
val DarkOnSurfaceVariant = Color(0xFF9DB2B8)
// Тёмно-бирюзовые surface-контейнеры (карточки, панели) в тон Deep Teal — чтобы тёмная тема была
// единой и в родительской, и в детской части (без них карточки берут дефолтные Material-цвета).
val DarkSurfaceContainerLowest = Color(0xFF0A161A)
val DarkSurfaceContainerLow = Color(0xFF13252B)
val DarkSurfaceContainer = Color(0xFF17282E)
val DarkSurfaceContainerHigh = Color(0xFF1E353C)
val DarkSurfaceContainerHighest = Color(0xFF264048)
