package com.vikrant.careSync.security;

import com.vikrant.careSync.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditService auditService;

    @Pointcut("execution(* com.vikrant.careSync.service.PatientService.get*(..)) || " +
            "execution(* com.vikrant.careSync.service.MedicalHistoryService.get*(..))")
    public void phiReadMethods() {
    }

    @Pointcut("execution(* com.vikrant.careSync.service.PatientService.update*(..)) || " +
            "execution(* com.vikrant.careSync.service.MedicalHistoryService.update*(..)) || " +
            "execution(* com.vikrant.careSync.service.MedicalHistoryService.create*(..))")
    public void phiWriteMethods() {
    }

    @AfterReturning(pointcut = "phiReadMethods()", returning = "result")
    public void logPhiRead(JoinPoint joinPoint, Object result) {
        logAction(joinPoint, "READ_PHI", result);
    }

    @AfterReturning(pointcut = "phiWriteMethods()", returning = "result")
    public void logPhiWrite(JoinPoint joinPoint, Object result) {
        logAction(joinPoint, "WRITE_PHI", result);
    }

    private void logAction(JoinPoint joinPoint, String action, Object result) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = (auth != null) ? auth.getName() : "SYSTEM";

            HttpServletRequest request = null;
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
                    .getRequestAttributes();
            if (attributes != null) {
                request = attributes.getRequest();
            }

            String ip = (request != null) ? request.getRemoteAddr() : "0.0.0.0";
            String methodName = joinPoint.getSignature().getName();
            String args = Arrays.toString(joinPoint.getArgs());

            // Extract entity info if possible
            String entityName = joinPoint.getTarget().getClass().getSimpleName();
            String entityId = "N/A";

            if (joinPoint.getArgs().length > 0) {
                entityId = joinPoint.getArgs()[0].toString();
            }

            auditService.log(username, action, entityName, entityId,
                    "Method: " + methodName + " | Args: " + args, ip);

        } catch (Exception e) {
            log.error("Failed to log audit entry", e);
        }
    }
}
