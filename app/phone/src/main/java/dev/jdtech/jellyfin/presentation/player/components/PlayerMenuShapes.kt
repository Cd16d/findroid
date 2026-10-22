package dev.jdtech.jellyfin.presentation.player.components

import androidx.compose.ui.graphics.Shape
import dev.jdtech.jellyfin.core.presentation.theme.CentralShapes

internal val MenuTopShape = CentralShapes.GroupTop
internal val MenuBottomShape = CentralShapes.GroupBottom
internal val MenuMiddleShape = CentralShapes.GroupMiddle
internal val MenuSingleShape = CentralShapes.GroupSingle

internal fun getItemShape(index: Int, totalCount: Int): Shape =
    CentralShapes.getGroupShape(index, totalCount)
