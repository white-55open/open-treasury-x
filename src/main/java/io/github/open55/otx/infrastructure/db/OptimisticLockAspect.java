//package io.github.open55.otx.infrastructure.db;
//
//import com.yourcompany.project.exception.OptimisticLockException;
//import org.aspectj.lang.ProceedingJoinPoint;
//import org.aspectj.lang.annotation.Around;
//import org.aspectj.lang.annotation.Aspect;
//import org.aspectj.lang.annotation.Pointcut;
//import org.springframework.stereotype.Component;
//
//@Aspect
//@Component
//public class OptimisticLockAspect {
//
//    // 拦截所有 Service 实现类中以 update、saveOrUpdate、edit 开头的方法
//    @Pointcut("execution(* com.yourcompany.project.service..*.update*(..)) " +
//              "|| execution(* com.yourcompany.project.service..*.saveOrUpdate*(..))")
//    public void updateMethods() {}
//
//    @Around("updateMethods()")
//    public Object handleOptimisticLock(ProceedingJoinPoint joinPoint) throws Throwable {
//        // 执行原始的更新方法
//        Object result = joinPoint.proceed();
//
//        // 1. 如果方法返回 boolean (例如 com.baomidou.mybatisplus.extension.service.IService.updateById)
//        if (result instanceof Boolean && !((Boolean) result)) {
//            throw new OptimisticLockException();
//        }
//
//        // 2. 如果方法返回 int (例如 Mapper 层的 updateById)
//        if (result instanceof Integer && (Integer) result == 0) {
//            throw new OptimisticLockException();
//        }
//
//        return result;
//    }
//}
