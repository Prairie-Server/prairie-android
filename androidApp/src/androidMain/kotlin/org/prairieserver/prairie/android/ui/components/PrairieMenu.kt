package org.prairieserver.prairie.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import org.prairieserver.prairie.android.ui.theme.MenuDimens
import org.prairieserver.prairie.android.ui.theme.SettingsTextStyles
import org.prairieserver.prairie.android.ui.theme.PrairieDestructive
import org.prairieserver.prairie.android.ui.theme.PrairieForeground
import org.prairieserver.prairie.android.ui.theme.PrairieSurfaceContainer
import org.prairieserver.prairie.android.ui.theme.siloRowTopDivider

// Menus share one surface with the dialogs: the raised card colour, a faint
// hairline, a 20dp radius and a real shadow, because a popup floats over
// artwork. Rows are 48dp with an optional leading icon — the Apple clients
// label menu items with symbols, and an icon is how a viewer finds "Sign out"
// in a list without reading it. Destructive rows are red, icon and all.

internal object PrairieMenuColors {
    val Surface = Color(0xFF1C1C1F)
    val Stroke = Color.White.copy(alpha = 0.07f)
    val Icon = PrairieForeground.copy(alpha = 0.78f)
    val Detail = PrairieForeground.copy(alpha = 0.58f)
    val Separator = Color.White.copy(alpha = 0.07f)
    val Selected = Color.White.copy(alpha = 0.07f)
}

/**
 * A [DropdownMenu] wearing the Prairie menu surface. Takes the same parameters as
 * the Material menu it wraps, so any stock menu can switch to it by name.
 */
@Composable
fun PrairieDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(min = MenuDimens.minWidth),
        offset = offset,
        scrollState = scrollState,
        properties = properties,
        shape = RoundedCornerShape(MenuDimens.cornerRadius),
        containerColor = PrairieMenuColors.Surface,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(MenuDimens.borderThickness, PrairieMenuColors.Stroke),
        content = content,
    )
}

/**
 * Drop-in replacement for [DropdownMenuItem] with the Prairie row: 48dp, the
 * menu label type, muted leading icon, and red when [destructive].
 */
@Composable
fun PrairieDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val labelColor = if (destructive) PrairieDestructive else PrairieForeground
    DropdownMenuItem(
        // No color in the style: the label takes the row's content color, so a
        // disabled row dims with disabledTextColor.
        text = { ProvideTextStyle(SettingsTextStyles.menuLabel) { text() } },
        onClick = onClick,
        modifier = modifier.heightIn(min = MenuDimens.rowMinHeight),
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        colors = MenuDefaults.itemColors(
            textColor = labelColor,
            leadingIconColor = if (destructive) PrairieDestructive else PrairieMenuColors.Icon,
            trailingIconColor = PrairieMenuColors.Detail,
            disabledTextColor = labelColor.copy(alpha = 0.38f),
            disabledLeadingIconColor = PrairieMenuColors.Icon.copy(alpha = 0.38f),
            disabledTrailingIconColor = PrairieMenuColors.Detail.copy(alpha = 0.38f),
        ),
        contentPadding = PaddingValues(horizontal = MenuDimens.rowHorizontalPadding),
    )
}

/**
 * One row of a [PrairieDropdownMenu]: an optional leading [icon], the label, an
 * optional [detail] line, and a check when [checked].
 *
 * @param showDivider Draws a hairline above this row, to separate groups.
 */
@Composable
fun PrairieMenuItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    detail: String? = null,
    checked: Boolean = false,
    destructive: Boolean = false,
    labelColor: Color = if (destructive) PrairieDestructive else PrairieForeground,
    showDivider: Boolean = false,
) {
    if (showDivider) PrairieMenuDivider()
    PrairieDropdownMenuItem(
        text = {
            Column {
                Text(
                    text = label,
                    color = labelColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!detail.isNullOrBlank()) {
                    Text(
                        text = detail,
                        color = PrairieMenuColors.Detail,
                        fontSize = 12.5.sp,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .then(if (checked) Modifier.background(PrairieMenuColors.Selected) else Modifier),
        leadingIcon = icon?.let { vector ->
            { Icon(imageVector = vector, contentDescription = null, modifier = Modifier.size(22.dp)) }
        },
        trailingIcon = if (checked) {
            { Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = PrairieForeground, modifier = Modifier.size(20.dp)) }
        } else {
            null
        },
        destructive = destructive,
    )
}

/** Hairline between groups of menu rows. */
@Composable
fun PrairieMenuDivider() {
    Box(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(PrairieMenuColors.Separator),
    )
}

/**
 * Who and where, above a menu's actions: an avatar or mark, a name, and a
 * detail line such as the server it belongs to.
 */
@Composable
fun PrairieMenuHeader(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = MenuDimens.rowHorizontalPadding, end = MenuDimens.rowHorizontalPadding, top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Box(Modifier.size(12.dp))
        }
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = title,
                style = SettingsTextStyles.menuLabel.copy(fontSize = 15.sp),
                color = PrairieForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    color = PrairieMenuColors.Detail,
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
