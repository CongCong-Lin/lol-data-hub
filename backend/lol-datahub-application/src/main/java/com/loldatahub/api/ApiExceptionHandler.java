package com.loldatahub.api;

import com.loldatahub.domain.statistics.MatchGameNotFoundException;
import com.loldatahub.domain.statistics.PlayerDetailNotFoundException;
import com.loldatahub.domain.statistics.TeamDetailNotFoundException;
import com.loldatahub.source.TjStatsSourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({
            IllegalArgumentException.class,
            MethodArgumentNotValidException.class,
            MissingServletRequestParameterException.class,
            TypeMismatchException.class
    })
    ResponseEntity<ApiResponse<Void>> badRequest(Exception exception) {
        return ResponseEntity.badRequest().body(ApiResponse.failure(exception.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiResponse<Void>> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure("接口不存在"));
    }

    @ExceptionHandler({PlayerDetailNotFoundException.class, MatchGameNotFoundException.class,
            TeamDetailNotFoundException.class})
    ResponseEntity<ApiResponse<Void>> detailNotFound(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(exception.getMessage()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> methodNotAllowed() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiResponse.failure("请求方法不支持"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Void>> unreadableMessage() {
        return ResponseEntity.badRequest().body(ApiResponse.failure("请求体格式错误"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> unsupportedMediaType() {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.failure("请求内容类型不支持"));
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ResponseEntity<ApiResponse<Void>> mediaTypeNotAcceptable() {
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                .body(ApiResponse.failure("无法生成客户端要求的响应类型"));
    }

    @ExceptionHandler(TjStatsSourceException.class)
    ResponseEntity<ApiResponse<Void>> upstreamFailure(TjStatsSourceException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponse.failure(exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> internalError(Exception exception) {
        log.error("未处理的接口异常", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure("服务内部错误，请查看服务日志"));
    }
}
