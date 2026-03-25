package devlava.youproapi.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

@Getter
@Setter
@ToString
public class HttpResponseDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean result;

    private String errorMessage;

    private Object data;
}

