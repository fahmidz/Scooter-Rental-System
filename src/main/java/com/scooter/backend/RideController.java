package com.scooter.backend;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api") public class RideController {
 private final RideService rides;private final AuthService auth;
 public RideController(RideService rides,AuthService auth){this.rides=rides;this.auth=auth;}
 public record Start(String qrCode){}
 @PostMapping("/rides/start") public Object start(@RequestHeader(value="Authorization",required=false)String token,@RequestBody Start request){return rides.start(auth.require(token).id(),request.qrCode());}
 @PostMapping("/rides/{id}/end") public Object end(@RequestHeader(value="Authorization",required=false)String token,@PathVariable int id){return rides.end(auth.require(token).id(),id);}
 @GetMapping("/rides") public Object history(@RequestHeader(value="Authorization",required=false)String token){return rides.history(auth.require(token).id());}
 @GetMapping("/rides/{id}/payment") public Object payment(@RequestHeader(value="Authorization",required=false)String token,@PathVariable int id){return rides.payment(auth.require(token).id(),id);}
 @PostMapping("/rides/{id}/payment/demo-complete") public Object demo(@RequestHeader(value="Authorization",required=false)String token,@PathVariable int id){return rides.simulatePayment(auth.require(token).id(),id);}
}
