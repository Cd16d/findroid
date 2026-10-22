package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.FindroidItemPerson
import dev.jdtech.jellyfin.presentation.theme.spacings

/**
 * Media information text block displaying genres, director, and writers.
 *
 * @param genres List of genre names for the media item.
 * @param director Director person model if available.
 * @param writers List of writer person models.
 */
@Composable
fun InfoText(
    genres: List<String>,
    director: FindroidItemPerson?,
    writers: List<FindroidItemPerson>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small)) {
        if (genres.isNotEmpty()) {
            Text(
                text = "${stringResource(CoreR.string.genres)}: ${genres.joinToString()}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (director != null) {
            Text(
                text = "${stringResource(CoreR.string.director)}: ${director.name}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (writers.isNotEmpty()) {
            Text(
                text =
                    "${stringResource(CoreR.string.writers)}: ${writers.joinToString { it.name }}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun InfoTextPreview() {
    dev.jdtech.jellyfin.presentation.theme.FindroidTheme {
        InfoText(
            genres = listOf("Action", "Sci-Fi"),
            director =
                FindroidItemPerson(
                    id = java.util.UUID.randomUUID(),
                    name = "Robert Rodriguez",
                    type = org.jellyfin.sdk.model.api.PersonKind.DIRECTOR,
                    role = "Director",
                    image =
                        dev.jdtech.jellyfin.models.FindroidItemPersonImage(
                            uri = null,
                            blurHash = null,
                        ),
                ),
            writers = emptyList(),
        )
    }
}
