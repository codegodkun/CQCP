package com.cqcp.apiserver.modelgateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AdminApiAuthenticationFilterTokenConfigurationTest {

    @Test
    void rejectsEqualAdminAndReadonlyCredentialsAtStartup() {
        assertThatIllegalStateException()
                .isThrownBy(() -> new AdminApiAuthenticationFilter("same-token", "same-token"))
                .withMessageContaining("不得配置为相同值");
    }

    @Test
    void missingCredentialsRemainFailClosed() throws Exception {
        var filter = new AdminApiAuthenticationFilter("", "");

        assertThat(request(filter, "GET", "/api/admin/model-profiles", null).getStatus())
                .isEqualTo(401);
        assertThat(request(filter, "GET", "/api/review/tasks", null).getStatus())
                .isEqualTo(401);
    }

    @Test
    void adminOnlyAndReadonlyOnlyConfigurationsKeepTheirOwnBoundaries() throws Exception {
        var adminOnly = new AdminApiAuthenticationFilter("admin-only", "");
        var readonlyOnly = new AdminApiAuthenticationFilter("", "readonly-only");

        assertThat(request(
                        adminOnly,
                        "GET",
                        "/api/admin/model-profiles",
                        "Bearer admin-only")
                .getStatus()).isEqualTo(200);
        assertThat(request(
                        readonlyOnly,
                        "GET",
                        "/api/review/tasks",
                        "Bearer readonly-only")
                .getStatus()).isEqualTo(200);
        assertThat(request(
                        readonlyOnly,
                        "GET",
                        "/api/admin/model-profiles",
                        "Bearer readonly-only")
                .getStatus()).isEqualTo(403);
    }

    @Test
    void rotatingCredentialsInvalidatesBothPreviousValues() throws Exception {
        var rotated = new AdminApiAuthenticationFilter("admin-new", "readonly-new");

        assertThat(request(
                        rotated,
                        "GET",
                        "/api/admin/model-profiles",
                        "Bearer admin-old")
                .getStatus()).isEqualTo(401);
        assertThat(request(
                        rotated,
                        "GET",
                        "/api/review/tasks",
                        "Bearer readonly-old")
                .getStatus()).isEqualTo(401);
        assertThat(request(
                        rotated,
                        "GET",
                        "/api/admin/model-profiles",
                        "Bearer admin-new")
                .getStatus()).isEqualTo(200);
        assertThat(request(
                        rotated,
                        "GET",
                        "/api/review/tasks",
                        "Bearer readonly-new")
                .getStatus()).isEqualTo(200);
    }

    private static MockHttpServletResponse request(
            AdminApiAuthenticationFilter filter,
            String method,
            String path,
            String authorization) throws ServletException, IOException {
        var request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        var response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());
        return response;
    }
}
