package ai.devcodex.agents.ui.screens.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("关于软件", style = MaterialTheme.typography.titleMedium)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            InfoSection(title = "软件信息") {
                InfoRow("应用名称", "DevCodex")
                InfoRow("版本", "1.0")
                InfoRow("类型", "AI 对话与项目文件辅助工具")
            }

            Spacer(Modifier.height(16.dp))
            InfoSection(title = "开发者") {
                InfoRow("作者", "林映雪")
                InfoRow("联系邮箱", "2386866276@qq.com")
            }

            Spacer(Modifier.height(16.dp))
            ExpandableSection(title = "隐私政策") { PrivacyPolicyText() }

            Spacer(Modifier.height(8.dp))
            ExpandableSection(title = "服务条款") { TermsOfServiceText() }

            Spacer(Modifier.height(8.dp))
            ExpandableSection(title = "用户协议") { UserAgreementText() }

            Spacer(Modifier.height(8.dp))
            ExpandableSection(title = "使用协议") { UsageAgreementText() }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        content()
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ExpandableSection(title: String, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 12.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(if (expanded) "收起" else "展开", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            content()
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun PrivacyPolicyText() {
    SectionText("生效日期：本政策自应用首次发布之日起生效。")
    SectionText("一、信息收集")
    SectionText("1. 本应用仅在您主动配置时收集以下信息：AI 服务商地址、API 密钥、模型名称及参数。上述信息仅存储于您的设备本地，用于向您所选择的服务商发起请求。")
    SectionText("2. 对话内容仅在您发送消息时传输至您配置的 AI 服务商，用于生成回复。本应用不自行收集、存储或分析您的对话内容。")
    SectionText("3. 项目文件仅在您主动通过文件选择器选取后读取，用于向 AI 服务商提供上下文。本应用不会在后台扫描或上传设备文件。")
    SectionText("二、数据存储")
    SectionText("1. 所有设置数据与对话记录均保存在设备本地存储中，本应用不设置任何远程服务器，亦不会主动上传您的数据。")
    SectionText("三、权限使用")
    SectionText("1. 文件访问权限仅在您主动选择项目目录时使用，您可以随时在系统设置中撤销该权限。")
    SectionText("四、用户权利")
    SectionText("1. 您可以随时在设置页面修改或清除全部本地数据，卸载应用将一并删除所有本地数据。")
    SectionText("五、第三方服务")
    SectionText("1. 当您使用 AI 对话功能时，您所配置的服务商将独立处理您的请求。请您自行查阅相应服务商的隐私政策。")
    SectionText("六、政策更新")
    SectionText("1. 如本政策发生变更，我们将通过应用内更新提示告知您。")
}

@Composable
private fun TermsOfServiceText() {
    SectionText("一、服务说明")
    SectionText("1. 本应用向您提供基于大语言模型的对话与项目文件辅助功能。实际服务内容取决于您所配置的服务商能力。")
    SectionText("二、使用责任")
    SectionText("1. 您应对使用本应用产生的全部行为及后果负责。")
    SectionText("2. 您应确保所配置的服务商与 API 密钥来源合法，并遵守相应服务商的使用条款。")
    SectionText("三、内容免责")
    SectionText("1. AI 生成的回复可能包含不准确、不完整或具有误导性的内容，仅供您参考，不构成任何专业建议。")
    SectionText("2. 因依赖 AI 输出内容而造成的任何损失，本应用不承担责任。")
    SectionText("四、知识产权")
    SectionText("1. 本应用代码及相关资源的知识产权归开发者所有。未经许可，不得用于商业性复制或再分发。")
    SectionText("五、服务变更与终止")
    SectionText("1. 我们保留随时更新、调整或停止服务（包括功能调整）的权利。")
}

@Composable
private fun UserAgreementText() {
    SectionText("一、协议确认")
    SectionText("1. 您安装并使用本应用，即视为已阅读并同意本协议全部条款。")
    SectionText("二、用户资格")
    SectionText("1. 您应具备完全民事行为能力。未成年人应在监护人指导下使用。")
    SectionText("三、行为规范")
    SectionText("您承诺不利用本应用从事以下行为：")
    SectionText("（1）生成或传播违反法律法规的内容；")
    SectionText("（2）侵犯他人知识产权、隐私权等合法权益；")
    SectionText("（3）干扰、破坏或绕过本应用的正常功能；")
    SectionText("（4）将本应用用于任何非法用途。")
    SectionText("四、内容责任")
    SectionText("1. 您对自己输入的提示词及使用 AI 输出内容的行为承担全部责任。")
    SectionText("五、协议变更")
    SectionText("1. 本协议可能随应用更新而调整，更新后继续使用即视为接受。")
}

@Composable
private fun UsageAgreementText() {
    SectionText("一、适用范围")
    SectionText("1. 本协议规范您对本应用及 AI 功能的使用行为。")
    SectionText("二、使用规范")
    SectionText("1. 本应用面向个人学习与日常辅助场景，请勿将其用于对准确性有严格要求的专业领域（如医疗、法律、金融决策）。")
    SectionText("2. 请勿使用本应用处理您无权访问的他人隐私数据。")
    SectionText("三、输出内容")
    SectionText("1. AI 输出内容受模型训练数据影响，可能存在时效性与准确性问题，请自行核实。")
    SectionText("2. 对 AI 输出内容的商用或再发布，请遵守所使用服务商的条款。")
    SectionText("四、免责声明")
    SectionText("1. 在法律允许的最大范围内，本应用按现状提供，不对其可用性、准确性或特定用途适用性作出任何明示或默示担保。")
}