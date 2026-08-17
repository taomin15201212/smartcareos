package com.smartcareos.system;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfiguration {

    private static final String TENANT = "tenantHeader";
    private static final String PRINCIPAL = "principalHeader";
    private static final String API_KEY = "apiKeyHeader";
    private static final String OIDC = "oidcBearer";

    @Bean
    OpenAPI smartCareOpenApi() {
        Components components = new Components()
                .addSecuritySchemes(TENANT, headerScheme("X-SmartCare-Tenant",
                        "租户标识；必须与凭据所属租户一致"))
                .addSecuritySchemes(PRINCIPAL, headerScheme("X-SmartCare-Principal",
                        "调用主体标识；API Key 模式必填"))
                .addSecuritySchemes(API_KEY, headerScheme("X-SmartCare-Api-Key",
                        "SmartCareOS API Key；只在签发时展示一次"))
                .addSecuritySchemes(OIDC, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("OIDC Bearer Token；需包含 tenant_id、sub 与 roles 声明"));

        SecurityRequirement apiKeyAuthentication = new SecurityRequirement()
                .addList(TENANT).addList(PRINCIPAL).addList(API_KEY);
        SecurityRequirement oidcAuthentication = new SecurityRequirement().addList(OIDC);

        return new OpenAPI()
                .info(new Info()
                        .title("SmartCareOS 企业级智慧养老平台 API")
                        .version("1.0.0")
                        .description("覆盖身份、机构、老人、设备、告警、照护、通知、政务与运营模块。"
                                + " API Key 三请求头为一组认证条件，也可使用 OIDC Bearer Token。"
                                + " /api/v1/system/health 为匿名健康检查接口。")
                        .contact(new Contact().name("SmartCareOS Architecture Team"))
                        .license(new License().name("Internal Use")))
                .servers(List.of(new Server().url("/").description("当前 SmartCareOS 实例")))
                .components(components)
                .security(List.of(apiKeyAuthentication, oidcAuthentication));
    }

    @Bean
    GroupedOpenApi allApi() {
        return group("00-全部接口", new String[]{"/api/v1/**"});
    }

    @Bean
    GroupedOpenApi identityApi() {
        return group("01-身份与授权", new String[]{"/api/v1/api-credentials/**"});
    }

    @Bean
    GroupedOpenApi institutionApi() {
        return group("02-机构与空间", new String[]{
                "/api/v1/institutions/**", "/api/v1/rooms/**", "/api/v1/beds/**"});
    }

    @Bean
    GroupedOpenApi elderApi() {
        return group("03-老人与入住", new String[]{"/api/v1/elders/**", "/api/v1/admissions/**"});
    }

    @Bean
    GroupedOpenApi deviceApi() {
        return group("04-设备与IoT", new String[]{
                "/api/v1/device-products/**", "/api/v1/devices/**", "/api/v1/device-risk-events/**"});
    }

    @Bean
    GroupedOpenApi alarmApi() {
        return GroupedOpenApi.builder().group("05-告警中心")
                .pathsToMatch("/api/v1/alarms/**")
                .pathsToExclude("/api/v1/alarms/*/care-task")
                .build();
    }

    @Bean
    GroupedOpenApi careApi() {
        return group("06-照护管理", new String[]{
                "/api/v1/care-plans/**", "/api/v1/care-tasks/**", "/api/v1/alarms/*/care-task"});
    }

    @Bean
    GroupedOpenApi notificationApi() {
        return group("07-通知中心", new String[]{"/api/v1/notification-deliveries/**"});
    }

    @Bean
    GroupedOpenApi governmentApi() {
        return group("08-政务交换", new String[]{"/api/v1/government-exchanges/**"});
    }

    @Bean
    GroupedOpenApi operationsApi() {
        return group("09-运营与系统", new String[]{"/api/v1/dashboard/**", "/api/v1/system/**"});
    }

    private static GroupedOpenApi group(String name, String[] paths) {
        return GroupedOpenApi.builder().group(name).pathsToMatch(paths).build();
    }

    private static SecurityScheme headerScheme(String header, String description) {
        return new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER).name(header).description(description);
    }
}
