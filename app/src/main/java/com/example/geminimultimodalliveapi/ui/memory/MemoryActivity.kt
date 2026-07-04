package com.example.geminimultimodalliveapi.ui.memory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MemoryActivity : ComponentActivity() {

    private val viewModel: MemoryViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color.White,
                    background = Color.Black,
                    surface = Color(0xFF1E1E1E)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = {
                                    Text(
                                        text = "จัดการความจำ & ข้อมูลรถ",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = Color.White
                                    )
                                },
                                navigationIcon = {
                                    IconButton(onClick = { finish() }) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "กลับ",
                                            tint = Color.White
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = Color(0xFF1A1A1A)
                                )
                            )
                        }
                    ) { paddingValues ->
                        MemoryContent(
                            modifier = Modifier.padding(paddingValues),
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryContent(
    modifier: Modifier = Modifier,
    viewModel: MemoryViewModel
) {
    val licensePlate by viewModel.licensePlate.collectAsState()
    val taxCircle by viewModel.taxCircle.collectAsState()
    val maintenance by viewModel.maintenance.collectAsState()
    val memories by viewModel.memories.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var inputPlate by remember { mutableStateOf("") }
    var inputTax by remember { mutableStateOf("") }
    var inputMaint by remember { mutableStateOf("") }
    var inputNewFact by remember { mutableStateOf("") }

    // Sync input states once when loaded
    LaunchedEffect(licensePlate, taxCircle, maintenance) {
        inputPlate = licensePlate
        inputTax = taxCircle
        inputMaint = maintenance
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Vehicle Info Editor
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "EDIT VEHICLE INFORMATION",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        // License Plate
                        OutlinedTextField(
                            value = inputPlate,
                            onValueChange = { inputPlate = it },
                            label = { Text("ทะเบียนรถ (License Plate)", color = Color.Gray) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color(0x33FFFFFF),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Tax Circle Expiry
                        OutlinedTextField(
                            value = inputTax,
                            onValueChange = { inputTax = it },
                            label = { Text("วันหมดอายุภาษี (Expiry Date)", color = Color.Gray) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color(0x33FFFFFF),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Maintenance
                        OutlinedTextField(
                            value = inputMaint,
                            onValueChange = { inputMaint = it },
                            label = { Text("ข้อมูลการเช็คระยะ/บำรุงรักษา", color = Color.Gray) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color(0x33FFFFFF),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                viewModel.saveVehicleInfo("license_plate", "plate_number", inputPlate)
                                viewModel.saveVehicleInfo("tax_circle", "expiry_date", inputTax)
                                viewModel.saveVehicleInfo("maintenance", "oil_change", inputMaint)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("บันทึกข้อมูลรถ", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Section 2: Add Custom Fact Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "เพิ่มความทรงจำด้วยตนเอง (Add Fact)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        OutlinedTextField(
                            value = inputNewFact,
                            onValueChange = { inputNewFact = it },
                            label = { Text("ข้อความความจำ เช่น 'เติมลมยางล้อหน้า 29 ล้อหลัง 33'", color = Color.Gray) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color(0x33FFFFFF),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                if (inputNewFact.isNotBlank()) {
                                    viewModel.addCustomFact(inputNewFact, isPinned = false)
                                    inputNewFact = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("เพิ่มความทรงจำ", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Section 3: List of Memories Header
            item {
                Text(
                    text = "ความทรงจำและประวัติจดจำทั้งหมด (${memories.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Memories List Items
            if (memories.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("ไม่มีข้อมูลประวัติความจำ", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            } else {
                items(memories, key = { it.id }) { memory ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = memory.content,
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "หมวดหมู่: ${memory.category} • ความสำคัญ: ${memory.baseImportance}",
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }

                            // Pin Button
                            IconButton(onClick = { viewModel.togglePin(memory.id, !memory.isPinned) }) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = if (memory.isPinned) "Unpin" else "Pin",
                                    tint = if (memory.isPinned) Color(0xFF00BCD4) else Color.DarkGray
                                )
                            }

                            // Delete Button
                            IconButton(onClick = { viewModel.deleteMemory(memory.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "ลบ",
                                    tint = Color(0xFFFF5252)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        error?.let { errMsg ->
            AlertDialog(
                onDismissRequest = { viewModel.loadData() },
                title = { Text("เกิดข้อผิดพลาด") },
                text = { Text(errMsg) },
                confirmButton = {
                    TextButton(onClick = { viewModel.loadData() }) {
                        Text("ลองใหม่")
                    }
                }
            )
        }
    }
}
