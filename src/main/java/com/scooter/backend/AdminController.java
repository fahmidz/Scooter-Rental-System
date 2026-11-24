package com.scooter.backend;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import static com.scooter.backend.ApiError.Failure;
@RestController @RequestMapping("/api/admin") public class AdminController {
 private final JdbcTemplate db;private final AuthService auth;private final MqttGateway mqtt;
 public AdminController(JdbcTemplate db,AuthService auth,MqttGateway mqtt){this.db=db;this.auth=auth;this.mqtt=mqtt;}
 @GetMapping("/overview") public Object overview(@RequestHeader(value="Authorization",required=false)String token){auth.admin(token);return Map.of(
  "scooters",db.queryForList("SELECT status,count(*) AS count FROM scooters GROUP BY status"),
  "rides",db.queryForList("SELECT status,count(*) AS count FROM rides GROUP BY status"),
  "openMaintenance",db.queryForObject("SELECT count(*) FROM maintenance WHERE status<>'resolved'",Integer.class),
  "revenueDemo",db.queryForObject("SELECT COALESCE(sum(amount),0) FROM payments WHERE payment_status='completed'",java.math.BigDecimal.class));}
 @GetMapping("/users") public Object users(@RequestHeader(value="Authorization",required=false)String token){auth.admin(token);return db.queryForList("SELECT user_id,name,email,role FROM users ORDER BY user_id");}
 @GetMapping("/rides") public Object allRides(@RequestHeader(value="Authorization",required=false)String token){auth.admin(token);return db.queryForList("SELECT * FROM rides ORDER BY start_time DESC");}
 public record MaintenanceIssue(int scooterId,String issueDescription){}
 @PostMapping("/maintenance") @Transactional public Object report(@RequestHeader(value="Authorization",required=false)String token,@RequestBody MaintenanceIssue body){auth.admin(token);
  if(body.issueDescription()==null||body.issueDescription().isBlank())throw new Failure(HttpStatus.BAD_REQUEST,"Issue description required");
  var scooters=db.queryForList("SELECT scooter_id FROM scooters WHERE scooter_id=? FOR UPDATE",body.scooterId());if(scooters.isEmpty())throw new Failure(HttpStatus.NOT_FOUND,"Scooter not found");
  Integer active=db.queryForObject("SELECT count(*) FROM rides WHERE scooter_id=? AND status='active'",Integer.class,body.scooterId());
  if(active!=null&&active>0)throw new Failure(HttpStatus.CONFLICT,"End active ride before maintenance");
  mqtt.command(body.scooterId(),"LOCK");
  Integer id=db.queryForObject("INSERT INTO maintenance(scooter_id,issue_description,status) VALUES (?,?,'pending') RETURNING maintenance_id",Integer.class,body.scooterId(),body.issueDescription());
  db.update("UPDATE scooters SET status='maintenance' WHERE scooter_id=?",body.scooterId());return db.queryForMap("SELECT * FROM maintenance WHERE maintenance_id=?",id);
 }
 @GetMapping("/maintenance") public Object issues(@RequestHeader(value="Authorization",required=false)String token){auth.admin(token);return db.queryForList("SELECT * FROM maintenance ORDER BY reported_date DESC");}
 public record IssueStatus(String status){}
 @PatchMapping("/maintenance/{id}") @Transactional public Object update(@RequestHeader(value="Authorization",required=false)String token,@PathVariable int id,@RequestBody IssueStatus change){auth.admin(token);
  if(!List.of("pending","in_progress","resolved").contains(change.status()))throw new Failure(HttpStatus.BAD_REQUEST,"Invalid maintenance status");
  var rows=db.queryForList("SELECT scooter_id FROM maintenance WHERE maintenance_id=?",id);if(rows.isEmpty())throw new Failure(HttpStatus.NOT_FOUND,"Issue not found");
  int scooterId=((Number)rows.get(0).get("scooter_id")).intValue();
  db.queryForList("SELECT scooter_id FROM scooters WHERE scooter_id=? FOR UPDATE",scooterId);
  db.update("UPDATE maintenance SET status=?,resolved_date=CASE WHEN ?='resolved' THEN now() ELSE NULL END WHERE maintenance_id=?",change.status(),change.status(),id);
  if("resolved".equals(change.status())){
   Integer pending=db.queryForObject("SELECT count(*) FROM maintenance WHERE scooter_id=? AND status<>'resolved'",Integer.class,scooterId);
   if(pending==0)db.update("UPDATE scooters SET status=CASE WHEN battery_level<15 THEN 'maintenance' ELSE 'available' END WHERE scooter_id=? AND NOT EXISTS (SELECT 1 FROM rides WHERE scooter_id=? AND status='active')",scooterId,scooterId);
  }else{db.update("UPDATE scooters SET status='maintenance' WHERE scooter_id=?",scooterId);}
  return db.queryForMap("SELECT * FROM maintenance WHERE maintenance_id=?",id);
 }
 public record DeviceCommand(String command){}
 @PostMapping("/scooters/{id}/command") public Object command(@RequestHeader(value="Authorization",required=false)String token,@PathVariable int id,@RequestBody DeviceCommand c){auth.admin(token);
  if(!"LOCK".equals(c.command())&&!"UNLOCK".equals(c.command()))throw new Failure(HttpStatus.BAD_REQUEST,"Command must be LOCK or UNLOCK");
  if(db.queryForList("SELECT scooter_id FROM scooters WHERE scooter_id=?",id).isEmpty())throw new Failure(HttpStatus.NOT_FOUND,"Scooter not found");
  // Prevent admin from bypassing rental controls by unlocking an unreserved scooter.
  if("UNLOCK".equals(c.command()))throw new Failure(HttpStatus.CONFLICT,"Use ride start to unlock; prevents untracked rides");
  mqtt.command(id,c.command());return Map.of("scooterId",id,"command",c.command(),"published",true);
 }
}
