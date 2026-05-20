package com.catchpro.app.ui.screen.match

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MatchConfirmScreen(
    orderId: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "오더 확인",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("오더 ID: $orderId")
                Text("다음 단계로 넘어가기 전 마지막 확인을 진행하는 화면입니다.")
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onConfirm) {
            Text("확정 후 돌아가기")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onCancel) {
            Text("취소")
        }
    }
}
