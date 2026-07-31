package com.cqcp.apiserver.modelgateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * M2 的最小后端管理面保护。
 *
 * <p>Token 由部署环境注入，仅通过 Authorization header 使用；不进入 URL、响应、
 * 数据库或浏览器持久化。未配置匹配 token 时管理接口保持 fail closed。Admin API
 * 只允许 Admin token；任务清单和合同原文读取允许 Admin 或只读管理 token。
 */
@Component
final class AdminApiAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final String adminToken;
    private final String readonlyToken;

    AdminApiAuthenticationFilter(
            @Value("${cqcp.admin-api.admin-token:}") String adminToken,
            @Value("${cqcp.admin-api.readonly-token:}") String readonlyToken) {
        this.adminToken = normalize(adminToken);
        this.readonlyToken = normalize(readonlyToken);
        if (this.adminToken != null
                && this.readonlyToken != null
                && secureEquals(this.adminToken, this.readonlyToken)) {
            throw new IllegalStateException(
                    "Admin 与只读管理凭据不得配置为相同值");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isAdminApi(request) && !isReviewWorkbenchRead(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        var reviewWorkbenchRead = isReviewWorkbenchRead(request);
        var presented = bearer(request.getHeader("Authorization"));
        if (presented == null) {
            rejectAuthentication(response, reviewWorkbenchRead);
            return;
        }
        if (adminToken != null && secureEquals(adminToken, presented)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (readonlyToken != null && secureEquals(readonlyToken, presented)) {
            if (reviewWorkbenchRead) {
                filterChain.doFilter(request, response);
                return;
            }
            reject(response, 403, "ADMIN_PERMISSION_REQUIRED", "当前身份不具备 Admin 权限");
            return;
        }
        rejectAuthentication(response, reviewWorkbenchRead);
    }

    private static void rejectAuthentication(
            HttpServletResponse response,
            boolean reviewWorkbenchRead) throws IOException {
        reject(
                response,
                401,
                reviewWorkbenchRead
                        ? "MANAGEMENT_AUTHENTICATION_REQUIRED"
                        : "ADMIN_AUTHENTICATION_REQUIRED",
                reviewWorkbenchRead
                        ? "需要有效的管理访问凭据"
                        : "需要有效的 Admin 身份凭据");
    }

    private static boolean isAdminApi(HttpServletRequest request) {
        var path = pathWithinApplication(request);
        return "/api/admin".equals(path) || path.startsWith("/api/admin/");
    }

    private static boolean isReviewWorkbenchRead(HttpServletRequest request) {
        if (!isSafeReadMethod(request.getMethod())) {
            return false;
        }
        var path = pathWithinApplication(request);
        if ("/api/review/tasks".equals(path)) {
            return true;
        }
        var segments = path.split("/", -1);
        return segments.length == 8
                && "api".equals(segments[1])
                && "review".equals(segments[2])
                && "tasks".equals(segments[3])
                && !segments[4].isBlank()
                && "executions".equals(segments[5])
                && !segments[6].isBlank()
                && ("document-preview".equals(segments[7])
                        || "document".equals(segments[7]));
    }

    /**
     * Spring MVC automatically maps HEAD to a matching GET handler. The
     * authentication boundary must therefore classify HEAD exactly like GET,
     * otherwise a HEAD request can enter a protected controller/service before
     * authentication and expose response metadata.
     */
    private static boolean isSafeReadMethod(String method) {
        return "GET".equals(method) || "HEAD".equals(method);
    }

    /**
     * 与 Spring MVC 的路径匹配语义保持一致：matrix parameter 不属于 controller
     * mapping identity。鉴权必须先移除每个 segment 的 semicolon content，否则
     * `/api/review/tasks;x=1` 会命中 controller，却可能绕过本 Filter。
     */
    private static String pathWithinApplication(HttpServletRequest request) {
        return UrlPathHelper.defaultInstance.getPathWithinApplication(request);
    }

    private static String bearer(String authorization) {
        if (authorization == null
                || !authorization.startsWith(PREFIX)
                || authorization.length() == PREFIX.length()) {
            return null;
        }
        var token = authorization.substring(PREFIX.length());
        return token.isBlank() || hasLineBreak(token) ? null : token;
    }

    private static String normalize(String token) {
        if (token == null || token.isBlank() || hasLineBreak(token)) return null;
        return token;
    }

    private static boolean secureEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean hasLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }

    private static void reject(
            HttpServletResponse response,
            int status,
            String title,
            String detail) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(
                "{\"type\":\"urn:cqcp:problem:"
                        + title.toLowerCase(java.util.Locale.ROOT)
                        + "\",\"title\":\""
                        + title
                        + "\",\"status\":"
                        + status
                        + ",\"detail\":\""
                        + detail
                        + "\"}");
    }
}
