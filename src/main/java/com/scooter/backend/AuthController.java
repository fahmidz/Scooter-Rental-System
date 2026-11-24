package com.scooter.backend;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api")
public class AuthController {
 private final AuthService auth;
 public AuthController(AuthService auth){this.auth=auth;}
 public record Register(String name,String email,String password){}
 public record Login(String email,String password){}
 @PostMapping("/auth/register") public Object register(@RequestBody Register r){return auth.register(r.name(),r.email(),r.password());}
 @PostMapping("/auth/login") public Object login(@RequestBody Login r){return auth.login(r.email(),r.password());}
 @GetMapping("/auth/me") public Object me(@RequestHeader(value="Authorization",required=false) String token){return auth.profile(token);}
 @PostMapping("/auth/logout") public Object logout(@RequestHeader(value="Authorization",required=false) String token){auth.logout(token);return Map.of("message","Logged out");}
 @GetMapping("/health") public Object health(){return Map.of("status","UP");}
}
