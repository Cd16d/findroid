package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.FindroidItemPerson
import dev.jdtech.jellyfin.presentation.theme.spacings
import java.util.UUID

/**
 * Horizontal scrolling list row displaying cast and crew members.
 *
 * @param actors List of cast or crew persons to display.
 * @param onActorClick Callback invoked with person UUID when an actor card is clicked.
 * @param contentPadding Padding applied around the row content.
 */
@Composable
fun ActorsRow(
    actors: List<FindroidItemPerson>,
    onActorClick: (personId: UUID) -> Unit,
    contentPadding: PaddingValues,
) {
    Column(modifier = Modifier.padding(contentPadding)) {
        Text(
            text = stringResource(CoreR.string.cast_amp_crew),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(MaterialTheme.spacings.small))
    }
    LazyRow(
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
    ) {
        items(items = actors.distinctBy { it.id }, key = { person -> person.id }) { person ->
            PersonItem(person = person, onClick = { onActorClick(person.id) })
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun ActorsRowPreview() {
    dev.jdtech.jellyfin.presentation.theme.FindroidTheme {
        ActorsRow(
            actors =
                listOf(
                    FindroidItemPerson(
                        id = UUID.randomUUID(),
                        name = "John Doe",
                        type = org.jellyfin.sdk.model.api.PersonKind.ACTOR,
                        role = "Hero",
                        image =
                            dev.jdtech.jellyfin.models.FindroidItemPersonImage(
                                uri = null,
                                blurHash = null,
                            ),
                    )
                ),
            onActorClick = {},
            contentPadding = PaddingValues(16.dp),
        )
    }
}
