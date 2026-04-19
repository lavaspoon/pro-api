package devlava.youproapi.exception;

import devlava.youproapi.dto.HttpErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

        /**
         * 사용자를 찾을 수 없는 경우
         */
        @ExceptionHandler(UserNotFoundException.class)
        public ResponseEntity<HttpErrorResponse> handleUserNotFoundException(UserNotFoundException e) {
                HttpErrorResponse errorResponse = HttpErrorResponse.builder()
                                .status(HttpStatus.NOT_FOUND.value())
                                .error("Not Found")
                                .message(e.getMessage())
                                .build();

                return ResponseEntity
                                .status(HttpStatus.NOT_FOUND)
                                .body(errorResponse);
        }

        /** @Valid 검증 실패 */
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<HttpErrorResponse> handleValidation(MethodArgumentNotValidException e) {
                String message = e.getBindingResult().getFieldErrors().stream()
                                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                                .collect(Collectors.joining(", "));
                HttpErrorResponse errorResponse = HttpErrorResponse.builder()
                                .status(HttpStatus.BAD_REQUEST.value())
                                .error("Validation Failed")
                                .message(message)
                                .build();
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }

        /** 잘못된 요청 (존재하지 않는 사례, 한도 초과 등) */
        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<HttpErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
                HttpErrorResponse errorResponse = HttpErrorResponse.builder()
                                .status(HttpStatus.BAD_REQUEST.value())
                                .error("Bad Request")
                                .message(e.getMessage())
                                .build();
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }

        /** 비즈니스 규칙 위반 (월 선정 한도 초과 등) */
        @ExceptionHandler(IllegalStateException.class)
        public ResponseEntity<HttpErrorResponse> handleIllegalState(IllegalStateException e) {
                HttpErrorResponse errorResponse = HttpErrorResponse.builder()
                                .status(HttpStatus.CONFLICT.value())
                                .error("Conflict")
                                .message(e.getMessage())
                                .build();
                return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
        }

        /**
         * 기타 예상치 못한 예외
         */
        @ExceptionHandler(Exception.class)
        public ResponseEntity<HttpErrorResponse> handleException(Exception e) {
                HttpErrorResponse errorResponse = HttpErrorResponse.builder()
                                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                                .error("Internal Server Error")
                                .message("서버 오류가 발생했습니다.")
                                .build();

                return ResponseEntity
                                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(errorResponse);
        }
}
