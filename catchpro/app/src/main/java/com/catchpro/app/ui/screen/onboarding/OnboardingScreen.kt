package com.catchpro.app.ui.screen.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "CatchPro",
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "온보딩, 대시보드, 프리셋, 목적지, 이력, 설정 화면까지 바로 이어서 개발할 수 있는 기본 구조가 준비되었습니다.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Card {
            Text(
                text = "다음 단계는 저장소 연결, 런타임 상태 관리, 화면별 ViewModel 정리입니다.",
                modifier = Modifier.padding(16.dp),
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onContinue) {
            Text("대시보드 열기")
        }
    }
}
