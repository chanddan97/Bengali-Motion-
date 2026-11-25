# Data Model

```kotlin
data class Project(
  val id: String, val name: String, val language: String,
  val aspectRatio: String, val fps: Int, val resolution: String,
  val script: String, val audioAssetId: String?,
  val subtitleTrackId: String?, val sceneIds: List<String>,
  val updatedAt: Long, val watermarkSettings: WatermarkSettings
)

data class SubtitleCue(
  val id: String, val index: Int, val startMs: Long,
  val endMs: Long, val text: String
)

data class WatermarkSettings(
  val user: WatermarkLayer,
  val developer: WatermarkLayer
)

data class WatermarkLayer(
  val enabled: Boolean, val kind: String, val text: String?,
  val assetId: String?, val position: String, val scale: Float,
  val opacity: Float, val marginPercent: Float,
  val startMs: Long, val endMs: Long?
)
```

Store assets using stable IDs and app-managed URIs. Use migrations when schemas change. Never store API secrets in project data.
