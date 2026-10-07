package com.example.collabpro.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.collabpro.ui.theme.Border
import com.example.collabpro.ui.theme.Ink
import com.example.collabpro.ui.theme.LightTeal
import com.example.collabpro.ui.theme.Muted
import com.example.collabpro.ui.theme.Teal

@Composable
fun Page(title: String, eyebrow: String = "COLLABPRO", subtitle: String = "", onBack: (() -> Unit)? = null, content: LazyListScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("‹ Volver") }
                Spacer(Modifier.width(8.dp))
            }
            Text("collab", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("pro", color = Teal, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = Border)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(eyebrow, color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text(title, color = Ink, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
                    if (subtitle.isNotBlank()) Text(subtitle, color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
            content()
        }
    }
}

@Composable
fun Panel(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Border)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) Text(title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}

@Composable
fun Action(text: String, onClick: () -> Unit, secondary: Boolean = false, enabled: Boolean = true, modifier: Modifier = Modifier) {
    if (secondary) OutlinedButton(onClick, modifier.fillMaxWidth(), enabled = enabled, shape = RoundedCornerShape(14.dp)) { Text(text) }
    else Button(onClick, modifier.fillMaxWidth(), enabled = enabled, shape = RoundedCornerShape(14.dp)) { Text(text) }
}

@Composable
fun Action(text: String, onClick: () -> Unit) {
    Action(text = text, onClick = onClick, secondary = false, enabled = true, modifier = Modifier)
}

@Composable
fun Status(text: String, color: Color = Teal) {
    Surface(color = LightTeal, shape = RoundedCornerShape(50.dp)) { Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun Notice(text: String) { Surface(color = LightTeal, shape = RoundedCornerShape(14.dp)) { Text(text, Modifier.fillMaxWidth().padding(14.dp), color = Ink, fontSize = 13.sp) } }

@Composable
fun Entry(label: String, value: String, onChange: (String) -> Unit, singleLine: Boolean = true, error: Boolean = false) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = singleLine, isError = error, shape = RoundedCornerShape(14.dp))
}

@Composable
fun LocalEntry(label: String, initial: String = "", singleLine: Boolean = true) {
    var value by remember { mutableStateOf(initial) }
    Entry(label, value, { value = it }, singleLine)
}

@Composable
fun ChoiceRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    FilterChip(selected = selected == option, onClick = { onSelect(option) }, label = { Text(option) })
                }
            }
        }
    }
}

@Composable
fun LinkCard(title: String, detail: String, onClick: () -> Unit, tag: String? = null) {
    Panel(modifier = Modifier.clickable(onClick = onClick)) {
        if (tag != null) Status(tag)
        Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(detail, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        Text("Ver detalle  →", color = Teal, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
