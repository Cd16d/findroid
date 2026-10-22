package dev.jdtech.jellyfin.core.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

object CentralShapes {
    val GroupTop =
        RoundedCornerShape(
            topStart = 24.dp,
            topEnd = 24.dp,
            bottomStart = 4.dp,
            bottomEnd = 4.dp,
        )
    val GroupBottom =
        RoundedCornerShape(
            topStart = 4.dp,
            topEnd = 4.dp,
            bottomStart = 24.dp,
            bottomEnd = 24.dp,
        )
    val GroupMiddle = RoundedCornerShape(4.dp)
    val GroupSingle = RoundedCornerShape(24.dp)

    fun getGroupShape(index: Int, totalCount: Int): Shape {
        return when {
            totalCount <= 1 -> GroupSingle
            index == 0 -> GroupTop
            index == totalCount - 1 -> GroupBottom
            else -> GroupMiddle
        }
    }

    val Dialog = RoundedCornerShape(28.dp)
    val SheetTop = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val ExtraLargeCard = RoundedCornerShape(24.dp)
    val LargeCard = RoundedCornerShape(20.dp)
    val MediumCard = RoundedCornerShape(16.dp)
    val SmallCard = RoundedCornerShape(12.dp)
    val ItemCard = RoundedCornerShape(10.dp)
    val Chip = RoundedCornerShape(6.dp)
    val Tiny = RoundedCornerShape(4.dp)
}
