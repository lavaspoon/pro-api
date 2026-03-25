package devlava.youproapi.controller;

import devlava.youproapi.dto.LoginResponse;
import devlava.youproapi.service.LoginService;\

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class LoginController {

    private final LoginService loginService;

    @GetMapping("/login")
    public LoginResponse login(@RequestParam String skid) {
        return loginService.login(skid);
    }
}
