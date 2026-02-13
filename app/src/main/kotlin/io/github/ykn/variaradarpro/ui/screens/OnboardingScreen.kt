package io.github.ykn.variaradarpro.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ykn.variaradarpro.R
import io.github.ykn.variaradarpro.ui.theme.Accent
import io.github.ykn.variaradarpro.ui.theme.RadarColors
import kotlinx.coroutines.launch

/**
 * Compact onboarding for Karoo 3.
 * 4 pages with icons, short titles, brief descriptions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val pages = listOf(
        Page.Welcome,
        Page.Connect,
        Page.Widget,
        Page.Ready
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RadarColors.background)
            .padding(16.dp)
    ) {
        // Skip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            if (pagerState.currentPage < pages.size - 1) {
                TextButton(onClick = onComplete) {
                    Text(
                        text = stringResource(R.string.onboarding_skip),
                        style = MaterialTheme.typography.labelLarge,
                        color = RadarColors.textSecondary
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(48.dp))
            }
        }

        // Page content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { index ->
            PageContent(page = pages[index])
        }

        // Indicators
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            pages.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == pagerState.currentPage) Accent
                            else RadarColors.textSecondary.copy(alpha = 0.3f)
                        )
                )
            }
        }

        // Button
        Button(
            onClick = {
                if (pagerState.currentPage == pages.size - 1) {
                    onComplete()
                } else {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = if (pagerState.currentPage == pages.size - 1)
                    stringResource(R.string.onboarding_start)
                else
                    stringResource(R.string.onboarding_next),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun PageContent(page: Page) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(Accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Title
        Text(
            text = stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = RadarColors.textPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Description
        Text(
            text = stringResource(page.descRes),
            style = MaterialTheme.typography.bodyLarge,
            color = RadarColors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

private sealed class Page(
    val icon: ImageVector,
    val titleRes: Int,
    val descRes: Int
) {
    data object Welcome : Page(
        icon = Icons.AutoMirrored.Filled.DirectionsBike,
        titleRes = R.string.onboarding_welcome,
        descRes = R.string.onboarding_welcome_desc
    )

    data object Connect : Page(
        icon = Icons.Default.Sensors,
        titleRes = R.string.onboarding_connect,
        descRes = R.string.onboarding_connect_desc
    )

    data object Widget : Page(
        icon = Icons.Default.PhonelinkSetup,
        titleRes = R.string.onboarding_widget,
        descRes = R.string.onboarding_widget_desc
    )

    data object Ready : Page(
        icon = Icons.Default.Check,
        titleRes = R.string.onboarding_ready,
        descRes = R.string.onboarding_ready_desc
    )
}
