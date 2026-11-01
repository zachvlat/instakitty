package com.zachvlat.instakitty.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.TextUnit

// Matches "#hashtag" and "@mention" tags. Mentions may contain internal dots, hashtags may not.
// The lookbehind keeps e-mail addresses / URL fragments from becoming links.
private val TAG_REGEX = Regex(
    """(?<![\p{L}\p{N}_])(?:#[\p{L}\p{N}_]+|@[\p{L}\p{N}_]+(?:\.[\p{L}\p{N}_]+)*)"""
)

/**
 * Text that turns every visible `#hashtag` and `@mention` into a tappable link.
 * Hashtags call [onHashtagClick] with the tag without `#`; mentions call [onUserClick] without `@`.
 */
@Composable
fun RichText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onHashtagClick: (String) -> Unit = {},
    onUserClick: (String) -> Unit = {}
) {
    val currentOnHashtagClick by rememberUpdatedState(onHashtagClick)
    val currentOnUserClick by rememberUpdatedState(onUserClick)
    val linkColor = MaterialTheme.colorScheme.primary

    val annotated = remember(text, linkColor) {
        buildAnnotatedString {
            var lastIndex = 0
            TAG_REGEX.findAll(text).forEach { match ->
                append(text.substring(lastIndex, match.range.first))
                val tag = match.value
                val target = tag.substring(1)
                val link = LinkAnnotation.Clickable(
                    tag = tag,
                    styles = TextLinkStyles(
                        style = SpanStyle(color = linkColor, fontWeight = FontWeight.Medium)
                    ),
                    linkInteractionListener = {
                        if (tag.startsWith("#")) currentOnHashtagClick(target)
                        else currentOnUserClick(target)
                    }
                )
                withLink(link) { append(tag) }
                lastIndex = match.range.last + 1
            }
            append(text.substring(lastIndex))
        }
    }

    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = overflow
    )
}
