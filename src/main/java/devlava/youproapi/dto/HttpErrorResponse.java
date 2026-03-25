package devlava.youproapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class HttpErrorResponse {
    private Integer status;
    private String error;
    private String errorCode;
    private String message;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
