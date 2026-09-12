package com.viple.looby.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.viple.looby.ui.theme.LoobyTheme

@Composable
fun SignInScreen(
    onSignIn: () -> Unit,
    onBack: () -> Unit,
    isAuthenticated: Boolean,
    busy: Boolean,
    onAuthenticated: () -> Unit
) {
    LaunchedEffect(isAuthenticated) { if (isAuthenticated) onAuthenticated() }
    val brand = LoobyTheme.brand

    Box(Modifier.fillMaxSize().background(brand.heroGradient)) {
        IconButton(onClick = onBack, Modifier.statusBarsPadding().padding(8.dp)) {
            Icon(Icons.Rounded.Close, "Fermer", tint = Color.White)
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 28.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(Modifier.size(96.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Text("L", style = MaterialTheme.typography.displaySmall, color = Color.White, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(28.dp))
            Text("Bienvenue sur Looby", style = MaterialTheme.typography.headlineLarge, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "Achetez, vendez, donnez et échangez\navec la communauté Viple.",
                style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(40.dp))
            FeatureRow(Icons.Rounded.Storefront, "Looby", "Vendez en quelques étapes")
            FeatureRow(Icons.Rounded.Favorite, "Gively", "Donnez une seconde vie à vos objets")
            FeatureRow(Icons.Rounded.AutoStories, "Livria", "Lisez, échangez, progressez")
            Spacer(Modifier.height(48.dp))
            Button(
                onClick = onSignIn,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = brand.looby)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = brand.looby)
                else { Icon(Icons.Rounded.VerifiedUser, null); Spacer(Modifier.width(10.dp)); Text("Continuer avec Viple ID", fontWeight = FontWeight.SemiBold) }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Connexion sécurisée via votre compte Viple ID. Aucun mot de passe n'est stocké dans l'application.",
                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(MaterialTheme.shapes.medium).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
        }
    }
}
