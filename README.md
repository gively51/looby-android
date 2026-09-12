# Looby — Application Android

Client Android natif de Looby (annonces, Gively, messagerie temps réel, Livria), consommant l'API mobile
documentée dans [`docs/MOBILE_API.md`](../../docs/MOBILE_API.md).

## Stack

| Domaine | Technologie |
|---|---|
| Langage | Kotlin 2.x (K2), coroutines / Flow |
| UI | Jetpack Compose + Material 3 (dynamic color, edge‑to‑edge, animations) |
| Navigation | Navigation Compose typée (`@Serializable` routes) |
| DI | Hilt |
| Réseau | Retrofit + OkHttp + kotlinx.serialization |
| Auth | AppAuth (OIDC + PKCE, Viple ID) → `/api/mobile/v1/auth/exchange` → JWT Looby + refresh rotatif |
| Temps réel | SignalR Java client (`/hubs/messaging`) |
| Stockage sécurisé | EncryptedSharedPreferences (Android Keystore) |
| Images | Coil 3 |

## Structure

```
app/src/main/java/com/viple/looby/
├─ core/
│  ├─ auth/        TokenStore, AuthLauncher (AppAuth), SessionManager
│  ├─ network/     Retrofit APIs, AuthInterceptor, TokenRefresher, ApiError, MediaUploader
│  ├─ realtime/    MessagingHubClient (SignalR, reconnexion auto)
│  └─ model/       DTOs alignés sur loobyweb.Shared (enums sérialisés en int)
├─ data/           Repositories (Listing, Messaging, Account, Livria)
├─ di/             Modules Hilt
└─ ui/
   ├─ theme/       Palette Looby / Gively / Livria, typographie, formes
   ├─ components/  Composants réutilisables (cartes annonce, badges, états, top bar…)
   ├─ navigation/  Route.kt
   ├─ home | listings | messaging | livria | account | support | auth
   └─ LoobyApp.kt  NavHost + bottom bar
```

## Configuration

`app/build.gradle.kts` expose des `buildConfigField` :

- `API_BASE_URL` — `https://looby.viplenetwork.com/` (prod) / `https://10.0.2.2:5001/` (debug, émulateur)
- `OIDC_REDIRECT_URI` — `com.viple.looby://oauth2redirect` (à déclarer dans Viple ID)

La configuration OIDC (authority, clientId, scopes) et le chemin du hub sont récupérés au démarrage via
`GET /api/mobile/v1/app/config`.

## Build

```bash
cd mobile/android
./gradlew :app:assembleDebug
```

Prérequis : Android Studio Ladybug+ / JDK 17 / SDK 35. `minSdk 26`, `targetSdk 35`.

## Fonctionnalités

- Accueil : hero, catégories, dons Gively, tendances, nouveautés
- Explorer : recherche, filtres (catégorie, prix, dons, localisation), tri, pagination
- Annonce : galerie, attributs, vendeur, contact → conversation
- Vendre : wizard multi‑étapes (catégorie, détails, attributs, photos, prix/don), brouillons, mes annonces
- Messages : inbox temps réel, archivage, sourdine, conversation (texte, images, GIFs, réactions, réponses, édition, typing, lu)
- Livria : profil lecteur (onboarding, XP, quêtes, badges), catalogue, étagère, échanges, classement, commandes
- Compte : tableau de bord, préférences, notifications, adresses, paiements, commandes, appareils/sessions, RGPD (export/suppression)
- Support : tickets, idées, bugs, journal des versions, documents légaux
