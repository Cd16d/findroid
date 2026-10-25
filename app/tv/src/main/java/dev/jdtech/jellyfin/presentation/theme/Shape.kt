package dev.jdtech.jellyfin.presentation.theme

import androidx.compose.material3.Shapes
import androidx.tv.material3.Shapes as ShapesTv
import dev.jdtech.jellyfin.core.presentation.theme.CentralShapes

val shapes = Shapes(extraSmall = CentralShapes.ItemCard, small = CentralShapes.ItemCard)

val shapesTv = ShapesTv(extraSmall = shapes.extraSmall, small = shapes.small)
