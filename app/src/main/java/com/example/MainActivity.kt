package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme

import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.matchmaking.MatchmakingState
import com.example.matchmaking.MatchmakingViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Initialize Firebase to prevent crash if google-services.json is missing
    if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
        com.google.firebase.FirebaseApp.initializeApp(this, com.google.firebase.FirebaseOptions.Builder()
            .setProjectId("dummy-project-id")
            .setApplicationId("1:1234567890:android:abcdef123456")
            .setApiKey("dummy-api-key")
            .build())
    }

    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        GameCompanionApp()
      }
    }
  }
}

@Composable
fun GameCompanionApp() {
  var selectedTab by remember { mutableIntStateOf(0) }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    bottomBar = {
      NavigationBar {
        NavigationBarItem(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          icon = { Icon(Icons.Default.SportsEsports, contentDescription = "Play") },
          label = { Text("Play") }
        )
        NavigationBarItem(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          icon = { Icon(Icons.Default.Leaderboard, contentDescription = "Leaderboard") },
          label = { Text("Rankings") }
        )
        NavigationBarItem(
          selected = selectedTab == 2,
          onClick = { selectedTab = 2 },
          icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
          label = { Text("Profile") }
        )
      }
    }
  ) { innerPadding ->
    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
      when (selectedTab) {
        0 -> MatchmakingScreen()
        1 -> LeaderboardScreen()
        2 -> ProfileScreen()
      }
    }
  }
}

@Composable
fun MatchmakingScreen(viewModel: MatchmakingViewModel = viewModel()) {
  val matchmakingState by viewModel.matchmakingState.collectAsState()
  val matchId by viewModel.currentMatchId.collectAsState()
  
  val context = LocalContext.current
  var hasMicPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    )
  }
  var isMicMuted by remember { mutableStateOf(true) }

  val micPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
    onResult = { isGranted ->
      hasMicPermission = isGranted
      isMicMuted = !isGranted
    }
  )

  Column(
    modifier = Modifier.fillMaxSize().padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Text("Competitive 1v1", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(8.dp))
    Text("Ping: 24ms (US East)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    
    Spacer(modifier = Modifier.height(48.dp))

    when (matchmakingState) {
      MatchmakingState.IDLE -> {
        Button(
          onClick = { viewModel.startMatchmaking() },
          modifier = Modifier.fillMaxWidth(0.8f).height(56.dp)
        ) {
          Text("Find Match", style = MaterialTheme.typography.titleLarge)
        }
      }
      MatchmakingState.SEARCHING -> {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text("Searching for opponents...", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(onClick = { viewModel.cancelMatchmaking() }) {
          Text("Cancel Matchmaking")
        }
      }
      MatchmakingState.MATCH_FOUND -> {
        Icon(Icons.Default.SportsEsports, contentDescription = "Match Found", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Match Found!", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Room ID: $matchId", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Voice Chat:", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = {
                if (hasMicPermission) {
                    isMicMuted = !isMicMuted
                } else {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }) {
                Icon(
                    imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = if (isMicMuted) "Unmute Microphone" else "Mute Microphone",
                    tint = if (isMicMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = { viewModel.cancelMatchmaking() /* In a real app, transition to game */ }) {
            Text("Enter Match")
        }
      }
      MatchmakingState.ERROR -> {
        Text("Error finding a match.", color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { viewModel.cancelMatchmaking() }) {
          Text("Try Again")
        }
      }
    }
  }
}

@Composable
fun LeaderboardScreen() {
  val mockLeaderboard = listOf(
    "PlayerOne" to 15420,
    "ProGamer99" to 14800,
    "NoobSlayer" to 13250,
    "FastFingers" to 12100,
    "You" to 11050
  )

  Column(modifier = Modifier.fillMaxSize()) {
    TopAppBarMock("Global Rankings")
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
      items(mockLeaderboard.size) { index ->
        val (name, score) = mockLeaderboard[index]
        LeaderboardItem(rank = index + 1, name = name, score = score, isUser = name == "You")
      }
    }
  }
}

@Composable
fun LeaderboardItem(rank: Int, name: String, score: Int, isUser: Boolean) {
  Card(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    )
  ) {
    Row(
      modifier = Modifier.padding(16.dp).fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("#$rank", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.width(16.dp))
        Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (isUser) FontWeight.Bold else FontWeight.Normal)
      }
      Text("$score pts", style = MaterialTheme.typography.bodyMedium)
    }
  }
}

@Composable
fun ProfileScreen() {
  Column(
    modifier = Modifier.fillMaxSize().padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
    Spacer(modifier = Modifier.height(16.dp))
    Text("Authentication Boilerplate", style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(8.dp))
    Text("Implement Firebase Auth or Google Sign-In here to secure sessions and save stats across devices.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    
    Spacer(modifier = Modifier.height(32.dp))
    Button(onClick = { /* Integrate Firebase Auth */ }) {
      Text("Sign In with Google")
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBarMock(title: String) {
  TopAppBar(
    title = { Text(title) },
    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
  )
}

