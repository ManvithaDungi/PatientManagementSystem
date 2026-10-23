package com.securehealth.backend.config;

import com.securehealth.backend.model.AuditLog;
import com.securehealth.backend.repository.AuditLogRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that logs incoming HTTP requests and their corresponding responses for auditing purposes.
 * <p>
 * This filter intercepts requests, processes them, and logs key details such as URI, status code, 
 * authenticated user, IP address, and user agent into the {@link AuditLogRepository}.
 * </p>
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Autowired
    private AuditLogRepository auditLogRepository;

    /**
     * Filters incoming requests to log relevant details for authenticated users.
     * <p>
     * It captures the request start time, proceed with the filter chain, then calculates the
     * duration and status code. If the user is authenticated and the request is not an auth 
     * endpoint, it saves an audit log entry.
     * </p>
     *
     * @param request     the {@link HttpServletRequest} object
     * @param response    the {@link HttpServletResponse} object
     * @param filterChain the {@link FilterChain} for further filter execution
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException      if an I/O error occurs during processing
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        long start = System.currentTimeMillis();

        filterChain.doFilter(request, response);

        long duration = System.currentTimeMillis() - start;

        String uri = request.getRequestURI();
        int status = response.getStatus();

        // 🔥 Extract authenticated user email
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = "ANONYMOUS";

        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            email = auth.getName();
        }

        String ip = request.getRemoteAddr();
        String agent = request.getHeader("User-Agent");

        try {

            // 🔹 Only log if user is authenticated
            if (auth != null && auth.isAuthenticated()
                    && !"anonymousUser".equals(auth.getPrincipal())
                    && !uri.startsWith("/api/auth")
                    && shouldAudit(request.getMethod(), status)) {

                String readableDetails = null;

                if (status >= 500) {
                    readableDetails = "System error occurred while accessing " + uri;
                }
                else if (status == 403) {
                    readableDetails = "Attempted to access restricted resource: " + uri;
                }
                else if (status == 401) {
                    readableDetails = "Unauthorized access attempt to " + uri;
                }
                else if (status >= 400) {
                    readableDetails = "Invalid request made to " + uri;
                }
                else if (status == 200) {
                    readableDetails = "Successfully accessed " + uri;
                }

                if (readableDetails != null) {
                    auditLogRepository.save(
                            new AuditLog(email, "API_ACTIVITY", ip, agent, readableDetails)
                    );
                }
            }

        } catch (Exception e) {
            logger.error("Request audit logging failed", e);
        }
    }

    /**
     * Routine reads are NOT written to the audit table (one row per GET made the table grow without bound and
     * put a database write on every request). Reads of clinical data are audited explicitly as
     * PATIENT_DATA_READ. This filter keeps only state-changing calls and denied/failed requests.
     */
    static boolean shouldAudit(String method, int status) {
        boolean mutation = "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method);
        return mutation || status == 403 || status >= 500;
    }
}
