package com.agenthub;

import com.agenthub.agent.infrastructure.security.SecretMasker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class SecretMaskingAndCredentialSecurityTest {

    @Test
    @DisplayName("测试 SecretMasker 脱敏算法：对标准 sk- 格式 API Key 进行前缀后缀保留及中间脱敏")
    void shouldMaskStandardApiKeyCorrectly() {
        String key = "sk-ant-api03-abcdef1234567890xyz";
        String masked = SecretMasker.maskSecret(key);

        assertThat(masked).doesNotContain("abcdef1234567890");
        assertThat(masked).startsWith("sk-ant");
        assertThat(masked).endsWith("0xyz");
        assertThat(masked).contains("***");
    }

    @Test
    @DisplayName("测试 短密文与边缘输入脱敏：短字符强制替换为 [REDACTED_SECRET]，空值安全处理")
    void shouldMaskShortAndEdgeCaseSecretsSafely() {
        assertThat(SecretMasker.maskSecret("short")).isEqualTo("[REDACTED_SECRET]");
        assertThat(SecretMasker.maskSecret("12345678")).isEqualTo("[REDACTED_SECRET]");
        assertThat(SecretMasker.maskSecret(null)).isEmpty();
        assertThat(SecretMasker.maskSecret("   ")).isEmpty();
    }

    @Test
    @DisplayName("测试 全文日志与异常栈脱敏：检测并清洗日志中泄漏的 Bearer Token 与 sk- 凭证")
    void shouldSanitizeTextContainingRawCredentials() {
        String rawLog = "Error connecting to provider with Authorization: Bearer sk-live-998877665544332211 and key sk-deepseek-abcdef12345678";
        String sanitized = SecretMasker.maskInText(rawLog);

        assertThat(sanitized).doesNotContain("sk-live-998877665544332211");
        assertThat(sanitized).doesNotContain("sk-deepseek-abcdef12345678");
        assertThat(sanitized).contains("Bearer");
        assertThat(sanitized).contains("***");
    }

    @Test
    @DisplayName("测试 URL 敏感查询参数脱敏：对 Google Gemini 等包含 ?key= 或 &apiKey= 的 URL 进行脱敏")
    void shouldMaskUrlQueryParameterSecrets() {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5:generateContent?key=AIzaSyA1B2C3D4E5F6G7H8I9J0K1L2M3N4O5P6";
        String maskedUrl = SecretMasker.maskUrl(url);

        assertThat(maskedUrl).doesNotContain("AIzaSyA1B2C3D4E5F6G7H8I9J0K1L2M3N4O5P6");
        assertThat(maskedUrl).contains("?key=");
        assertThat(maskedUrl).contains("***");
    }

    @Test
    @DisplayName("测试 环境变量注入解析：正确解析 env: 及 prop: 引用格式凭证")
    void shouldResolveSecretsFromEnvironment() {
        MockEnvironment mockEnv = new MockEnvironment();
        mockEnv.setProperty("agenthub.llm.openai.api-key", "sk-mock-env-secret-123456");

        String resolved = SecretMasker.resolveSecret("prop:agenthub.llm.openai.api-key", mockEnv, null);
        assertThat(resolved).isEqualTo("sk-mock-env-secret-123456");

        // Plain string passthrough
        String rawVal = SecretMasker.resolveSecret("regular-secret", mockEnv, null);
        assertThat(rawVal).isEqualTo("regular-secret");
    }
}
