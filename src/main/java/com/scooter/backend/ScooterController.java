package com.scooter.backend;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import static com.scooter.backend.ApiError.Failure;

@RestController @RequestMapping("/api/scooters")
public class ScooterController {
 private final JdbcTemplate db;private final AuthService auth;
 public ScooterController(JdbcTemplate db,AuthService auth){this.db=db;this.auth=auth;}
 @GetMapping public Object list(@RequestHeader(value="Authorization",required=false)String token,@RequestParam(required=false)String status){
  auth.require(token);
  if(status!=null){if(!List.of("available","in_use","maintenance").contains(status))throw new Failure(HttpStatus.BAD_REQUEST,"Invalid status");return db.queryForList("SELECT * FROM scooters WHERE status=? ORDER BY scooter_id",status);}
  return db.queryForList("SELECT * FROM scooters ORDER BY scooter_id");
 }
 @GetMapping("/{id}") public Object one(@RequestHeader(value="Authorization",required=false)String token,@PathVariable int id){auth.require(token);var result=db.queryForList("SELECT * FROM scooters WHERE scooter_id=?",id);if(result.isEmpty())throw new Failure(HttpStatus.NOT_FOUND,"Scooter not found");return result.get(0);}
 @GetMapping("/{id}/telemetry") public Object telemetry(@RequestHeader(value="Authorization",required=false)String token,@PathVariable int id,@RequestParam(defaultValue="30")int limit){auth.require(token);return db.queryForList("SELECT telemetry_id,scooter_id,battery_level,latitude,longitude,speed_kmh,device_timestamp,received_at FROM scooter_telemetry WHERE scooter_id=? ORDER BY received_at DESC,telemetry_id DESC LIMIT ?",id,Math.max(1,Math.min(100,limit)));}
}
