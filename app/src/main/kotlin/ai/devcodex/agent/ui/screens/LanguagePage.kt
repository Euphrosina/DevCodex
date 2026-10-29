package ai.devcodex.agent.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ai.devcodex.agent.AppViewModel
import ai.devcodex.agent.R
import ai.devcodex.agent.data.AppSettings
import ai.devcodex.agent.i18n.AppLanguage
import ai.devcodex.agent.i18n.AppLocale

/** 语言选择页：跟随系统 + 六种语言（各语言以本语言名称显示）。 */
@Composable
fun LanguagePage(vm: AppViewModel, settings: AppSettings, modifier: Modifier = Modifier) {
    val selected = AppLanguage.fromStored(settings.language)
    val effective by AppLocale.effective.collectAsState()
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        for (lang in AppLanguage.entries) {
            val title = if (lang == AppLanguage.SYSTEM) stringResource(R.string.language_follow_system) else lang.nativeName
            val supporting = if (lang == AppLanguage.SYSTEM) effective.nativeName else null
            ListItem(
                headlineContent = { Text(title) },
                supportingContent = supporting?.let { { Text(it) } },
                trailingContent = { RadioButton(selected = selected == lang, onClick = null) },
                modifier = Modifier.clickable {
                    if (selected != lang) vm.saveSettings { s -> s.copy(language = lang.name) }
                },
            )
        }
    }
}
