package io.github.aryeh95.mybiketraffic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import io.github.aryeh95.mybiketraffic.ui.screens.DashboardScreen
import io.github.aryeh95.mybiketraffic.ui.theme.MyBikeTrafficTheme

/**
 * Status screen. All configuration comes from the Karoo profile
 * (units) so there is nothing to set up here.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyBikeTrafficTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DashboardScreen(extension = MyBikeTrafficExtension.instance)
                }
            }
        }
    }
}
